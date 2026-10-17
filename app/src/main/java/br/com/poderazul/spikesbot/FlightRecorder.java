package br.com.poderazul.spikesbot;

import android.content.Context;
import android.graphics.Bitmap;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Black-box diagnostic, not an MP4 recorder.
 * Records ONLY frames presented by SpikesService.acceptLiveFrame() while the
 * original game is foreground. No internet permission, no background upload.
 */
final class FlightRecorder {
    private static final int MAX_FRAMES = 70;
    private static final long MIN_INTERVAL_MS = 280L;
    private static long currentSession = -1;
    private static long lastSavedAt = 0;
    private FlightRecorder() {}

    private static File directory(Context context) {
        return new File(context.getFilesDir(), "flight_debug");
    }

    private static String csvField(String input) {
        String s = input == null ? "" : input.replace("\"", "\"\"");
        return "\"" + s.replace("\n", " ").replace("\r", " ") + "\"";
    }

    static synchronized void capture(Context context, Bitmap frame,
                                     VisionEngine.Frame observation,
                                     long now, long session, int touchCount) {
        // The diagnostic must NEVER save another app's screen or a
        // transition away from the gameplay. Only valid game frames.
        if (session <= 0 || !observation.boardFound || !observation.birdFound ||
                now - lastSavedAt < MIN_INTERVAL_MS) return;
        File dir = directory(context);
        try {
            if (session != currentSession) {
                File[] previous = dir.listFiles();
                if (previous != null) for (File f : previous) f.delete();
                currentSession = session;
                lastSavedAt = 0;
            }
            if (!dir.exists() && !dir.mkdirs()) return;
            lastSavedAt = now;
            long elapsed = Math.max(0, now - session);
            String base = String.format(Locale.US, "quadro_%010d.jpg", elapsed);
            File image = new File(dir, base);
            try (FileOutputStream output = new FileOutputStream(image)) {
                frame.compress(Bitmap.CompressFormat.JPEG, 52, output);
            }
            File csv = new File(dir, "decisoes.csv");
            boolean newFile = !csv.exists();
            try (FileWriter out = new FileWriter(csv, true)) {
                if (newFile) {
                    out.write("tempo_ms;campo;passaro;pular;bird_x;bird_y;alvo_y;toques;decisao\n");
                }
                out.write(elapsed + ";" + observation.boardFound + ";" +
                        observation.birdFound + ";" + observation.jump + ";" +
                        (int) observation.birdX + ";" + (int) observation.birdY + ";" +
                        (int) observation.targetY + ";" + touchCount + ";" +
                        csvField(observation.description) + "\n");
            }
            File[] shots = dir.listFiles((root, name) -> name.endsWith(".jpg"));
            if (shots != null && shots.length > MAX_FRAMES) {
                Arrays.sort(shots, Comparator.comparing(File::getName));
                for (int i = 0; i < shots.length - MAX_FRAMES; i++) shots[i].delete();
            }
        } catch (IOException ignored) {
            // Never let a diagnostic file interrupt the actual pilot.
        }
    }

    static synchronized File export(Context context) throws IOException {
        File dir = directory(context);
        File[] files = dir.listFiles((root, name) ->
                name.endsWith(".jpg") || name.equals("decisoes.csv"));
        if (files == null || files.length == 0) return null;
        File shared = new File(context.getCacheDir(), "shared_debug");
        if (!shared.exists() && !shared.mkdirs()) throw new IOException("Diretório temporário");
        File zipFile = new File(shared, "SpikesBot-caixa-preta.zip");
        try (ZipOutputStream zip = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(zipFile)))) {
            byte[] buffer = new byte[8192];
            String notice = "SpikesBot - Caixa-preta de teste\n" +
                    "Imagens JPEG espaçadas, NÃO vídeo MP4.\n" +
                    "decisoes.csv: tempo em ms desde o início da sessão; " +
                    "pássaro, campo, comando de subir e altura alvo.\n" +
                    "As imagens podem conter notificações sobrepostas: revise antes de compartilhar.\n" +
                    "O app não envia nada sozinho.\n";
            zip.putNextEntry(new ZipEntry("LEIA-ME.txt"));
            zip.write(notice.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            Arrays.sort(files, Comparator.comparing(File::getName));
            for (File file : files) {
                zip.putNextEntry(new ZipEntry(file.getName()));
                try (FileInputStream in = new FileInputStream(file)) {
                    int n;
                    while ((n = in.read(buffer)) != -1) zip.write(buffer, 0, n);
                }
                zip.closeEntry();
            }
        }
        return zipFile;
    }
}
