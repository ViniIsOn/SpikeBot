package br.com.poderazul.spikesbot;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.nio.ByteBuffer;

/**
 * Explicit, one-session screen capture for faster visual tracking.
 * Pixels are processed in RAM only, and only while the original game is active.
 */
public final class LiveCaptureService extends Service {
    static final String EXTRA_RESULT_CODE = "media_result";
    static final String EXTRA_RESULT_DATA = "media_data";
    private static final String CHANNEL = "spikesbot_capture";
    private static volatile boolean active = false;
    static boolean isCaptureActive() { return active; }

    private HandlerThread thread;
    private Handler worker;
    private ImageReader imageReader;
    private VirtualDisplay display;
    private MediaProjection projection;
    private MediaProjection.Callback callback;
    private boolean closing = false;
    private long lastFrame = 0;
    private long sessionStarted = 0;
    private int deliveredFrames = 0;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !intent.hasExtra(EXTRA_RESULT_DATA)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (active || projection != null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(new NotificationChannel(CHANNEL,
                    "SpikesBot • visão ao vivo", NotificationManager.IMPORTANCE_LOW));
            PendingIntent open = PendingIntent.getActivity(this, 0,
                    new Intent(this, MainActivity.class),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification notification = new Notification.Builder(this, CHANNEL)
                    .setContentTitle("SpikesBot — visão ao vivo")
                    .setContentText("Capturando para analisar o jogo. Abra o app para parar.")
                    .setSmallIcon(android.R.drawable.ic_menu_view)
                    .setOngoing(true)
                    .setContentIntent(open)
                    .build();
            startForeground(140, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);

            thread = new HandlerThread("SpikesBot-live-frames");
            thread.start();
            worker = new Handler(thread.getLooper());
            sessionStarted = SystemClock.uptimeMillis();

            MediaProjectionManager managerProjection =
                    (MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);
            Intent data = (Intent)intent.getParcelableExtra(EXTRA_RESULT_DATA);
            if (data == null) throw new IllegalStateException("Permissão sem token válido");
            projection = managerProjection.getMediaProjection(resultCode, data);
            if (projection == null) throw new IllegalStateException("Projeção não autorizada");

            callback = new MediaProjection.Callback() {
                @Override public void onStop() {
                    Config.status(LiveCaptureService.this, "Compartilhamento de tela encerrado");
                    stopSelf();
                }
            };
            projection.registerCallback(callback, worker);

            WindowManager wm = (WindowManager)getSystemService(WINDOW_SERVICE);
            DisplayMetrics dm = new DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(dm);
            int width = dm.widthPixels;
            int height = dm.heightPixels;
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3);
            imageReader.setOnImageAvailableListener(reader -> onImage(reader), worker);
            display = projection.createVirtualDisplay(
                    "SpikesBotGameVision", width, height, dm.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(), null, worker);
            active = true;
            Config.status(this, "Visão ao vivo iniciada: abra o jogo");
        } catch (RuntimeException e) {
            active = false;
            Config.get(this).edit().putBoolean(Config.RUN, false).putString(
                    Config.LAST_ERROR, "Captura ao vivo: " + e.getMessage()).apply();
            Config.status(this, "Falha iniciando visão ao vivo: " + e.getClass().getSimpleName());
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void onImage(ImageReader source) {
        Image image = null;
        try {
            image = source.acquireLatestImage();
            if (image == null || closing) return;
            long now = SystemClock.uptimeMillis();
            // Around 12fps is sufficient for a first real-time calibration.
            if (now - lastFrame < 80) return;
            // Explicitly requested long-running play: no artificial
            // two-minute limit in VISÃO AO VIVO. Android can still stop
            // MediaProjection; the user can stop it through the notification
            // or by pressing PARAR O BOT at any time.
            SpikesService target = SpikesService.activeInstance();
            if (target == null || !Config.get(this).getBoolean(Config.RUN, false) ||
                    Config.get(this).getInt(Config.MODE, 1) != 1) {
                if (sessionStarted > 0 && now - sessionStarted > 3000 &&
                        !Config.get(this).getBoolean(Config.RUN, false)) stopSelf();
                return;
            }
            lastFrame = now;
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer bytes = plane.getBuffer();
            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();
            if (pixelStride < 4) return;
            int width = image.getWidth(), height = image.getHeight();
            int outW = 216, outH = Math.max(210, Math.round((float)height*outW/width));
            int[] pixels = new int[outW*outH];
            for (int y = 0; y < outH; y++) {
                int sy = Math.min(height - 1, y*height/outH);
                for (int x = 0; x < outW; x++) {
                    int sx = Math.min(width - 1, x*width/outW);
                    int p = sy * rowStride + sx * pixelStride;
                    int r = bytes.get(p) & 255;
                    int g = bytes.get(p+1) & 255;
                    int b = bytes.get(p+2) & 255;
                    pixels[y*outW+x] = 0xFF000000 | (r<<16) | (g<<8) | b;
                }
            }
            Bitmap frame = Bitmap.createBitmap(pixels,outW,outH, Bitmap.Config.ARGB_8888);
            try {
                target.acceptLiveFrame(frame);
                deliveredFrames++;
                if (deliveredFrames % 20 == 0) {
                    Config.get(this).edit().putInt(Config.LIVE_FRAMES, deliveredFrames).apply();
                }
            } finally { frame.recycle(); }
        } catch (RuntimeException error) {
            Config.get(this).edit().putString(Config.LAST_ERROR,
                    "Fluxo de imagem: " + error.getClass().getSimpleName()).apply();
        } finally {
            if (image != null) image.close();
        }
    }

    @Override public void onDestroy() {
        closing = true;
        active = false;
        if (imageReader != null) {
            imageReader.setOnImageAvailableListener(null, null);
            imageReader.close();
            imageReader = null;
        }
        if (display != null) { display.release(); display = null; }
        if (projection != null) {
            if (callback != null) projection.unregisterCallback(callback);
            projection.stop();
            projection = null;
        }
        if (thread != null) { thread.quitSafely(); thread = null; }
        super.onDestroy();
    }
}
