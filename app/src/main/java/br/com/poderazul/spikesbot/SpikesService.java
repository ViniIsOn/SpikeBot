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
    private static final long CAPTURE_GAP_MS = 700;
    private static volatile SpikesService activeInstance;
    static SpikesService activeInstance() { return activeInstance; }
    private static final long CAPTURE_TIMEOUT_MS = 1700;
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
    // The worker sets these after seeing a LIVE bird and sidewall spikes.
    // Stale decisions cannot keep tapping menus or a game-over screen.
    private volatile long lastVisionValidAt = 0;
    private volatile boolean visionFlapSuggested = false;
    private volatile long capturePendingSince = 0;
    // The Android accessibility screenshot API rejects overly fast capture
    // requests with error 3. Back off adaptively rather than spamming it.
    private volatile long captureGapMs = CAPTURE_GAP_MS;
    private volatile int captureSuccessStreak = 0;
    private volatile String lastVisionReason = "Sem quadro";
    private volatile int goodFrames = 0, lostBirdFrames = 0, lostBoardFrames = 0, captureFailures = 0, gestureCancels = 0;
    private volatile int gestureCompletions = 0, captureTimeouts = 0;
    private long lastTimedStatus = 0;
    private long lastSessionId = -1;
    private volatile long latestProcessedLiveFrameAt = 0;
    private int frames = 0;
    private boolean wasActive = false;

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        destroyed = false;
        activeInstance = this;
        visionFlapSuggested = false;
        lastVisionValidAt = 0;
        capturePendingSince = 0;
        captureGapMs = CAPTURE_GAP_MS;
        captureSuccessStreak = 0;
        lastVisionReason = "Aguardando primeira captura";
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
        if (activeInstance == this) activeInstance = null;
        stopService(new android.content.Intent(this, LiveCaptureService.class));
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
                visionFlapSuggested = false;
                lastVisionValidAt = 0;
                capturePendingSince = 0;
            }
            return;
        }
        wasActive = true;
        long sessionId = prefs.getLong(Config.SESSION_START, 0L);
        if (sessionId > 0 && sessionId != lastSessionId) {
            lastSessionId = sessionId;
            frames = 0; goodFrames = 0; lostBirdFrames = 0;
            lostBoardFrames = 0; captureFailures = 0; captureTimeouts = 0;
            gestureCompletions = 0; gestureCancels = 0;
            visionFlapSuggested = false;
            lastVisionValidAt = 0;
            if (worker != null) worker.post(vision::reset);
        }
        if (!Config.PACKAGE.equals(activePackage())) {
            visionFlapSuggested = false;
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
        // v0.6: keep the pilot responsive BETWEEN screenshots.
        // A fresh detection lasts only 550ms. If visual tracking is lost,
        // stop tapping instead of falling back to blind autoplay.
        if (prefs.getInt(Config.MODE, 1) == 1) {
            // MediaProjection delivers fresh frames to acceptLiveFrame().
            // The expensive legacy takeScreenshot API is NOT called.
            if (!LiveCaptureService.isCaptureActive()) {
                visionFlapSuggested = false;
                Config.status(this, "Aguardando autorização da captura contínua");
                return;
            }
            long cadence = Math.max(145L, 228L - prefs.getInt(Config.RATE, 65) / 2L);
            if (visionFlapSuggested && !gesturePending &&
                    lastVisionValidAt > 0 &&
                    now - lastVisionValidAt < 280L &&
                    now - lastGesture >= cadence) {
                sendTap(false);
            }
            return;
        }
        // One pending screenshot can sometimes never call back. A watchdog
        // recovers the capture loop instead of leaving the bot frozen.
        if (screenshotPending && capturePendingSince > 0 &&
                now - capturePendingSince > CAPTURE_TIMEOUT_MS) {
            screenshotPending = false;
            capturePendingSince = 0;
            captureTimeouts++;
            visionFlapSuggested = false;
            lastVisionReason = "Captura travou; reiniciando leitura";
            Config.status(this, lastVisionReason);
            updateVisualStats(prefs);
        }
        long visionCadence = Math.max(280L, 340L - prefs.getInt(Config.RATE, 65) / 2L);
        // A verified stage and bird are valid for a SHORT period between
        // throttled screenshots. After that, stop: no hidden blind autoclicker.
        long visionValidityMs = Math.min(1350L, captureGapMs + 490L);
        if (visionFlapSuggested && !gesturePending &&
                lastVisionValidAt > 0 && now - lastVisionValidAt < visionValidityMs &&
                now - lastGesture >= visionCadence) {
            sendTap(false);
        }
        if (screenshotPending) return;
        // Android throttles accessibility screenshots. Slower, stable reads beat fast failures.
        long gap = captureGapMs + (100 - prefs.getInt(Config.RATE, 65)) / 2;
        if (now - lastCapture < gap) return;
        screenshotPending = true;
        capturePendingSince = now;
        lastCapture = now;
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, runnable -> {
                Handler w = worker;
                if (!destroyed && w != null && !w.post(runnable)) screenshotPending = false;
            }, new TakeScreenshotCallback() {
                @Override public void onSuccess(ScreenshotResult result) {
                    captureSuccessStreak++;
                    // Recover slowly; never reduce below a conservative 700ms.
                    if (captureSuccessStreak >= 10) {
                        captureGapMs = Math.max(CAPTURE_GAP_MS, captureGapMs - 40);
                        captureSuccessStreak = 0;
                    }
                    try {
                        convertAndAnalyze(result);
                    } catch (RuntimeException error) {
                        problem("captura/visão", error);
                    } catch (OutOfMemoryError oom) {
                        problem("memória da captura", oom);
                    } finally {
                        screenshotPending = false;
                        capturePendingSince = 0;
                    }
                }
                @Override public void onFailure(int code) {
                    screenshotPending = false;
                    capturePendingSince = 0;
                    captureFailures++;
                    String hint;
                    switch (code) {
                        case ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT:
                            captureSuccessStreak = 0;
                            captureGapMs = Math.min(1900L, captureGapMs + 280L);
                            hint = "Captura limitada pelo Android; intervalo ajustado para " +
                                    captureGapMs + " ms";
                            break;
                        case ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS:
                            hint = "Acesso de captura negado";
                            break;
                        default: hint = "Falha de captura do sistema";
                    }
                    lastVisionReason = hint + " (" + code + ")";
                    Config.status(SpikesService.this, lastVisionReason);
                    updateVisualStats(prefs);
                }
            });
        } catch (RuntimeException error) {
            screenshotPending = false;
            capturePendingSince = 0;
            captureFailures++;
            problem("solicitar captura", error);
            updateVisualStats(prefs);
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

    /** Called on the projection worker; only the original game is examined. */
    void acceptLiveFrame(Bitmap frame) {
        if (destroyed || Config.get(this).getInt(Config.MODE, 1) != 1 ||
                !Config.get(this).getBoolean(Config.RUN, false) ||
                !Config.PACKAGE.equals(lastWindowPackage)) return;
        try {
            latestProcessedLiveFrameAt = SystemClock.uptimeMillis();
            processFrame(frame);
        } catch (RuntimeException failure) {
            visionFlapSuggested = false;
            problem("visão ao vivo", failure);
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
        boolean valid = result.birdFound && result.boardFound;
        if (valid) {
            goodFrames++;
            lastVisionValidAt = SystemClock.uptimeMillis();
            visionFlapSuggested = result.jump;
        } else {
            if (!result.boardFound) lostBoardFrames++;
            else if (!result.birdFound) lostBirdFrames++;
            // Stop when a full image proves tracking was lost.
            visionFlapSuggested = false;
        }
        lastVisionReason = result.description;
        if (frames % 2 == 0 || !valid) {
            Config.status(this, result.description);
            updateVisualStats(prefs);
        }
        // The next main-thread tick schedules taps. Never dispatch gestures
        // from the image worker and never depend on a single frame callback.
    }

    private void updateVisualStats(SharedPreferences prefs) {
        long age = lastVisionValidAt == 0 ? -1 :
                SystemClock.uptimeMillis() - lastVisionValidAt;
        prefs.edit().putString(Config.STATS,
                "Toques: " + prefs.getInt(Config.TAPS, 0) +
                " • gestos OK: " + gestureCompletions +
                " • cancelados: " + gestureCancels +
                "\nQuadros: " + frames + " • válidos: " + goodFrames +
                " • pássaro perdido: " + lostBirdFrames +
                " • campo perdido: " + lostBoardFrames +
                "\nFalhas de captura: " + captureFailures +
                " • capturas travadas: " + captureTimeouts +
                (prefs.getInt(Config.MODE, 1) == 1 ?
                " • captura: fluxo contínuo" :
                " • intervalo: " + captureGapMs + " ms") +
                " • último quadro válido: " + age + " ms" +
                "\nDecisão: " + lastVisionReason).apply();
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
                    gestureCompletions++;
                    if (manualTest) Config.status(SpikesService.this,
                            "TESTE: toque enviado ao jogo. O pássaro reagiu?");
                }
                @Override public void onCancelled(GestureDescription description) {
                    gesturePending = false;
                    gestureCancels++;
                    lastVisionReason = "Gesto cancelado pelo Android";
                    Config.status(SpikesService.this, lastVisionReason);
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
