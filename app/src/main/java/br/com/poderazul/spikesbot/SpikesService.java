package br.com.poderazul.spikesbot;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.graphics.Path;
import android.hardware.HardwareBuffer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.content.SharedPreferences;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/** Accessibility service running only on the named game and only after manual START. */
public class SpikesService extends AccessibilityService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final VisionEngine vision = new VisionEngine();
    private boolean screenshotPending = false;
    private boolean gesturePending = false;
    private String lastWindowPackage = "";
    private long lastCapture = 0;
    private long lastGesture = 0;
    private int frames = 0;

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        Config.status(this, "Acessibilidade conectada; toque em INICIAR no aplicativo");
        handler.removeCallbacks(loop);
        handler.post(loop);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        CharSequence pkg = event.getPackageName();
        if (pkg != null && (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED)) {
            lastWindowPackage = pkg.toString();
        }
    }

    @Override public void onInterrupt() {
        Config.status(this, "Interrompido pelo Android");
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        Config.get(this).edit().putBoolean(Config.RUN, false).apply();
        super.onDestroy();
    }

    private final Runnable loop = new Runnable() {
        @Override public void run() {
            handler.postDelayed(this, 85);
            SharedPreferences pref = Config.get(SpikesService.this);
            if (!pref.getBoolean(Config.RUN, false)) {
                screenshotPending = false;
                frames = 0;
                vision.reset();
                return;
            }
            // Do not touch any application other than the selected game.
            String activePackage = lastWindowPackage;
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                try {
                    CharSequence pkg = root.getPackageName();
                    if (pkg != null) activePackage = pkg.toString();
                } finally {
                    root.recycle();
                }
            }
            if (!Config.PACKAGE.equals(activePackage)) {
                if (!screenshotPending) Config.status(SpikesService.this, "Aguardando Don't Touch The Spikes ficar em primeiro plano");
                return;
            }
            if (screenshotPending || gesturePending) return;
            final long now = SystemClock.uptimeMillis();
            // Avoid repeated screenshots faster than Android can deliver them.
            final long gap = 95 + (100 - pref.getInt(Config.RATE, 65)) / 2;
            if (now - lastCapture < gap) return;
            screenshotPending = true;
            lastCapture = now;
            try {
                takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
                    @Override public void onSuccess(ScreenshotResult screenshot) {
                        screenshotPending = false;
                        if (!Config.get(SpikesService.this).getBoolean(Config.RUN, false)) return;
                        HardwareBuffer buffer = screenshot.getHardwareBuffer();
                        Bitmap original = null;
                        Bitmap small = null;
                        try {
                            ColorSpace space = screenshot.getColorSpace();
                            if (space == null) space = ColorSpace.get(ColorSpace.Named.SRGB);
                            if (buffer == null) throw new IllegalStateException("Sem imagem");
                            original = Bitmap.wrapHardwareBuffer(buffer, space);
                            if (original == null) throw new IllegalStateException("Captura vazia");
                            int targetWidth = 216;
                            int targetHeight = Math.max(210, Math.round(original.getHeight() * (216f / original.getWidth())));
                            small = Bitmap.createScaledBitmap(original, targetWidth, targetHeight, false);
                        } catch (Exception error) {
                            Config.status(SpikesService.this, "Falha lendo captura: " + error.getClass().getSimpleName());
                        } finally {
                            if (original != null) original.recycle();
                            if (buffer != null) buffer.close();
                        }
                        if (small != null) {
                            try { processFrame(small); }
                            finally { small.recycle(); }
                        }
                    }
                    @Override public void onFailure(int errorCode) {
                        screenshotPending = false;
                        Config.status(SpikesService.this, "Captura bloqueada pelo Android (código " + errorCode + ")");
                        lastCapture = SystemClock.uptimeMillis() + 500;
                    }
                });
            } catch (Exception failure) {
                screenshotPending = false;
                Config.status(SpikesService.this, "Erro de captura: " + failure.getClass().getSimpleName());
            }
        }
    };

    private void processFrame(Bitmap small) {
        SharedPreferences pref = Config.get(this);
        if (!pref.getBoolean(Config.RUN, false)) return;
        VisionEngine.Frame frame = vision.analyze(
                small,
                pref.getInt(Config.COLOR, 0),
                pref.getBoolean(Config.CANDY, false),
                pref.getInt(Config.SAFETY, 65),
                pref.getInt(Config.RATE, 65),
                SystemClock.uptimeMillis());
        frames++;
        if (frames % 3 == 0 || !frame.birdFound) {
            Config.status(this, frame.description);
            pref.edit().putString(Config.STATS,
                    frame.birdFound ?
                        "Pássaro detectado • alvo em " + Math.round(frame.targetY / small.getHeight() * 100) + "% da altura" :
                        "Nenhum toque enviado até encontrar uma partida reconhecível").apply();
        }
        if (!frame.jump || !frame.birdFound || !frame.boardFound) return;
        if (gesturePending || SystemClock.uptimeMillis() - lastGesture < 90) return;
        if (!Config.PACKAGE.equals(lastWindowPackage)) {
            // The active UI could be a permission dialog or a different app.
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return;
            try {
                if (root.getPackageName() == null ||
                        !Config.PACKAGE.contentEquals(root.getPackageName())) return;
            } finally { root.recycle(); }
        }
        sendTap();
    }

    private void sendTap() {
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        float cx = dm.widthPixels * .51f;
        float cy = dm.heightPixels * .55f;
        Path path = new Path();
        path.moveTo(cx, cy);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, 35);
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();
        gesturePending = true;
        boolean submitted;
        try {
            submitted = dispatchGesture(gesture, new GestureResultCallback() {
                @Override public void onCompleted(GestureDescription description) {
                    gesturePending = false;
                }
                @Override public void onCancelled(GestureDescription description) {
                    gesturePending = false;
                }
            }, handler);
        } catch (Exception e) {
            submitted = false;
        }
        if (submitted) {
            lastGesture = SystemClock.uptimeMillis();
            vision.tapWasSent(lastGesture);
        } else {
            gesturePending = false;
            Config.status(this, "Não foi possível enviar o toque");
        }
    }
}
