package br.com.poderazul.spikesbot;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.graphics.Path;
import android.hardware.HardwareBuffer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * Pilot for the original game only. Never injects events in other packages.
 * Captures and image recognition run on a worker, not on Android's UI thread.
 * This is an experimental controller, not an accuracy guarantee.
 */
public class SpikesService extends AccessibilityService {
    private static final String TAG = "SpikesBot";
    private static final long CAPTURE_GAP_MS = 230;
    private static final long TIMED_START_DELAY_MS = 4000;
    private static final long TIMED_SESSION_LIMIT_MS = 60000;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final VisionEngine vision = new VisionEngine();
    private HandlerThread analysisThread;
    private Handler worker;
    private volatile boolean destroyed = false;
    private volatile boolean screenshotPending = false;
    private volatile boolean gesturePending = false;
    private volatile String lastWindowPackage = "";
    private volatile long lastCapture = 0;
    private volatile long lastGesture = 0;
    private long lastTimedStatus = 0;
    private int frames = 0;
    private boolean wasActive = false;

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        destroyed = false;
        analysisThread = new HandlerThread("SpikesBot-vision");
        analysisThread.start();
        worker = new Handler(analysisThread.getLooper());
        Config.get(this).edit().putBoolean(Config.RUN, false)
                .putBoolean(Config.TEST_TAP, false).apply();
        Config.status(this, "Serviço conectado. Abra o jogo usando o botão INICIAR.");
        main.removeCallbacks(loop);
        main.post(loop);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            CharSequence pkg = event.getPackageName();
            if (pkg != null) lastWindowPackage = pkg.toString();
        }
    }

    @Override public void onInterrupt() {
        Config.status(this, "Serviço de acessibilidade interrompido");
    }

    @Override public void onDestroy() {
        destroyed = true;
        main.removeCallbacks(loop);
        Config.get(this).edit().putBoolean(Config.RUN, false)
                .putBoolean(Config.TEST_TAP, false).apply();
        if (analysisThread != null) analysisThread.quitSafely();
        super.onDestroy();
    }

    private void problem(String stage, Throwable t) {
        Log.e(TAG, "Falha em " + stage, t);
        Config.status(this, "Falha em " + stage + ": " + t.getClass().getSimpleName());
        Config.get(this).edit().putString(Config.LAST_ERROR,
                stage + ": " + t.getClass().getSimpleName() + " — " +
                        (t.getMessage() == null ? "sem detalhes" : t.getMessage())).apply();
    }

    private String activePackage() {
        String pkg = lastWindowPackage;
        AccessibilityNodeInfo root = null;
        try {
            root = getRootInActiveWindow();
            if (root != null && root.getPackageName() != null) {
                pkg = root.getPackageName().toString();
            }
        } catch (RuntimeException error) {
            problem("janela ativa", error);
        } finally {
            if (root != null) root.recycle();
        }
        return pkg;
    }

    private final Runnable loop = new Runnable() {
        @Override public void run() {
            if (destroyed) return;
            main.postDelayed(this, 65);
            try {
                tick();
            } catch (RuntimeException error) {
                problem("ciclo do bot", error);
            }
        }
    };

    private void tick() {
        SharedPreferences prefs = Config.get(this);
        boolean playing = prefs.getBoolean(Config.RUN, false);
        boolean test = prefs.getBoolean(Config.TEST_TAP, false);
        if (!playing && !test) {
            if (wasActive) {
                wasActive = false;
                frames = 0;
                if (worker != null) worker.post(vision::reset);
            }
            return;
        }
        wasActive = true;
        if (!Config.PACKAGE.equals(activePackage())) {
            if (!screenshotPending) Config.status(this, "Aguardando o jogo original ficar em primeiro plano");
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (test) {
            long armedAt = prefs.getLong(Config.TEST_TIME, 0L);
            if (now - armedAt < 1800) {
                Config.status(this, "Teste: aguardando o jogo abrir (1 toque em breve)");
                return;
            }
            prefs.edit().putBoolean(Config.TEST_TAP, false).apply();
            sendTap(true);
            return;
        }
        if (!playing || gesturePending) return;

        // Timed mode is deliberately separate from screen analysis. It proves that
        // gestures can repeat and runs only while the original game is foreground.
        // It is NOT a spike-avoidance mode and never runs indefinitely.
        if (prefs.getInt(Config.MODE, 0) == 0) {
            long start = prefs.getLong(Config.SESSION_START, 0L);
            if (start <= 0) {
                Config.status(this, "Inicie uma sessão no aplicativo SpikesBot");
                return;
            }
            long elapsed = now - start;
            if (elapsed > TIMED_SESSION_LIMIT_MS) {
                prefs.edit().putBoolean(Config.RUN, false).apply();
                Config.status(this, "Sessão de 60 segundos encerrada. Volte ao SpikesBot para reiniciar.");
                return;
            }
            if (elapsed < TIMED_START_DELAY_MS) {
                Config.status(this, "Modo contínuo: abra a partida; toques começam em " +
                        ((TIMED_START_DELAY_MS - elapsed + 999) / 1000) + " s");
                return;
            }
            int interval = Math.max(250, Math.min(850, prefs.getInt(Config.INTERVAL, 420)));
            if (!gesturePending && now - lastGesture >= interval) {
                sendTap(false);
            }
            if (now - lastTimedStatus > 1000) {
                lastTimedStatus = now;
                int count = prefs.getInt(Config.TAPS, 0);
                Config.status(this, "Modo contínuo ATIVO • toque a cada " + interval + " ms");
                prefs.edit().putString(Config.STATS,
                        "Toques enviados: " + count + " • tempo: " + (elapsed / 1000) +
                        "/60 s • sem leitura de espinhos").apply();
            }
            return;
        }
        if (screenshotPending) return;
        // Android throttles accessibility screenshots. Slower, stable reads beat fast failures.
        long gap = CAPTURE_GAP_MS + (100 - prefs.getInt(Config.RATE, 65)) / 2;
        if (now - lastCapture < gap) return;
        screenshotPending = true;
        lastCapture = now;
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, runnable -> {
                Handler w = worker;
                if (!destroyed && w != null && !w.post(runnable)) screenshotPending = false;
            }, new TakeScreenshotCallback() {
                @Override public void onSuccess(ScreenshotResult result) {
                    try {
                        convertAndAnalyze(result);
                    } catch (RuntimeException error) {
                        problem("captura/visão", error);
                    } catch (OutOfMemoryError oom) {
                        problem("memória da captura", oom);
                    } finally {
                        screenshotPending = false;
                    }
                }
                @Override public void onFailure(int code) {
                    screenshotPending = false;
                    String hint;
                    switch (code) {
                        case ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT:
                            hint = "Limite de velocidade do Android";
                            lastCapture = SystemClock.uptimeMillis() + 400;
                            break;
                        case ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS:
                            hint = "Acesso de captura negado";
                            break;
                        default: hint = "Falha de captura do sistema";
                    }
                    Config.status(SpikesService.this, hint + " (código " + code + ")");
                }
            });
        } catch (RuntimeException error) {
            screenshotPending = false;
            problem("solicitar captura", error);
        }
    }

    private void convertAndAnalyze(ScreenshotResult screenshot) {
        if (destroyed || !Config.get(this).getBoolean(Config.RUN, false)) return;
        HardwareBuffer buffer = screenshot.getHardwareBuffer();
        Bitmap hardware = null, software = null, scaled = null;
        try {
            if (buffer == null) throw new IllegalStateException("HardwareBuffer ausente");
            ColorSpace space = screenshot.getColorSpace();
            if (space == null) space = ColorSpace.get(ColorSpace.Named.SRGB);
            hardware = Bitmap.wrapHardwareBuffer(buffer, space);
            if (hardware == null) throw new IllegalStateException("Imagem vazia");
            // CRITICAL FIX: wrapHardwareBuffer returns HARDWARE format. getPixels() on
            // a hardware bitmap (including a scaled one) throws IllegalStateException.
            software = hardware.copy(Bitmap.Config.ARGB_8888, false);
            if (software == null) throw new IllegalStateException("Falha convertendo pixels para software");
            int width = 216;
            int height = Math.round(software.getHeight() * width / (float) software.getWidth());
            scaled = Bitmap.createScaledBitmap(software, width, Math.max(210, height), false);
            processFrame(scaled);
        } finally {
            if (scaled != null && scaled != software && !scaled.isRecycled()) scaled.recycle();
            if (software != null && !software.isRecycled()) software.recycle();
            if (hardware != null && !hardware.isRecycled()) hardware.recycle();
            if (buffer != null) buffer.close();
        }
    }

    private void processFrame(Bitmap scaled) {
        if (destroyed || !Config.get(this).getBoolean(Config.RUN, false)) return;
        SharedPreferences prefs = Config.get(this);
        VisionEngine.Frame result = vision.analyze(scaled,
                prefs.getInt(Config.COLOR, 0), prefs.getBoolean(Config.CANDY, false),
                prefs.getInt(Config.SAFETY, 65), prefs.getInt(Config.RATE, 65),
                SystemClock.uptimeMillis());
        frames++;
        if (frames % 2 == 0 || !result.birdFound || !result.boardFound) {
            Config.status(this, result.description);
            prefs.edit().putString(Config.STATS,
                    "Capturas: " + frames + " • campo: " + (result.boardFound ? "OK" : "não detectado") +
                    " • pássaro: " + (result.birdFound ? "OK" : "não detectado") +
                    " • toques: " + prefs.getInt(Config.TAPS, 0)).apply();
        }
        if (!result.jump || !result.birdFound || !result.boardFound) return;
        main.post(() -> {
            if (destroyed || gesturePending || !Config.get(this).getBoolean(Config.RUN, false)) return;
            if (SystemClock.uptimeMillis() - lastGesture < 95) return;
            if (Config.PACKAGE.equals(activePackage())) sendTap(false);
        });
    }

    private void sendTap(boolean manualTest) {
        if (destroyed || gesturePending || !Config.PACKAGE.equals(activePackage())) {
            Config.status(this, "Toque não enviado: jogo não está ativo");
            return;
        }
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        Path path = new Path();
        path.moveTo(metrics.widthPixels * .5f, metrics.heightPixels * .55f);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, 50);
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();
        gesturePending = true;
        boolean submitted = false;
        try {
            submitted = dispatchGesture(gesture, new GestureResultCallback() {
                @Override public void onCompleted(GestureDescription description) {
                    gesturePending = false;
                    if (manualTest) Config.status(SpikesService.this,
                            "TESTE: toque enviado ao jogo. O pássaro reagiu?");
                }
                @Override public void onCancelled(GestureDescription description) {
                    gesturePending = false;
                    Config.status(SpikesService.this, "Toque cancelado pelo sistema");
                }
            }, main);
        } catch (RuntimeException e) {
            problem("enviar toque", e);
        }
        if (submitted) {
            lastGesture = SystemClock.uptimeMillis();
            Config.get(this).edit().putInt(Config.TAPS,
                    Config.get(this).getInt(Config.TAPS, 0) + 1).apply();
            Handler w = worker;
            if (w != null) w.post(() -> vision.tapWasSent(lastGesture));
        } else {
            gesturePending = false;
            lastGesture = SystemClock.uptimeMillis(); // Avoid hammering Android on errors.
            Config.status(this, "Não foi possível enviar toque. Verifique a acessibilidade.");
        }
    }
}
