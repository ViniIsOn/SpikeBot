package br.com.poderazul.spikesbot;

import android.graphics.Bitmap;
import android.graphics.Color;


/**
 * Image-only heuristic controller for classic Don't Touch The Spikes.
 * All units refer to the resized screenshot, no copyrighted game resources are bundled.
 * Experimental: game updates/skins may require changing calibration.
 */
final class VisionEngine {
    static final class Frame {
        final boolean boardFound;
        final boolean birdFound;
        final boolean jump;
        final String description;
        final double birdX, birdY, targetY;
        Frame(boolean board, boolean bird, boolean jump, String description,
              double x, double y, double target) {
            this.boardFound = board;
            this.birdFound = bird;
            this.jump = jump;
            this.description = description;
            this.birdX = x;
            this.birdY = y;
            this.targetY = target;
        }
    }

    private static final class Blob {
        double x, y;
        int count;
        int minX, minY, maxX, maxY;
        Blob(int sx, int sy) { minX = maxX = sx; minY = maxY = sy; }
    }
    private double lastX = -1, lastY = -1, vx = 0, vy = 0;
    private long lastSeenAt = 0, lastTapAt = 0;
    private int direction = 1; // 1 is going right

    void reset() {
        lastX = -1; lastY = -1; vx = vy = 0;
        lastSeenAt = 0; lastTapAt = 0; direction = 1;
    }

    void tapWasSent(long at) { lastTapAt = at; }

    Frame analyze(Bitmap bitmap, int birdColor, boolean collectCandy, int safety,
                  int rate, long timestamp) {
        final int w = bitmap.getWidth(), h = bitmap.getHeight();
        if (w < 100 || h < 200) {
            return new Frame(false, false, false, "Resolução muito pequena", 0, 0, 0);
        }
        int[] colors = new int[w * h];
        bitmap.getPixels(colors, 0, w, 0, 0, w, h);

        int[] spikeL = new int[h];
        int[] spikeR = new int[h];
        // Calibrado com 4 capturas 686x1536 da versão atual do jogo.
        // A barra de captura do Android aparece abaixo de ~0.71h em algumas
        // imagens: ignoramos essa parte para não classificar a interface como espinhos.
        final int minY = (int) (0.17 * h), maxY = (int) (0.70 * h);
        final int stride = 1;
        int spikeEvidence = 0;

        for (int y = minY; y < maxY; y++) {
            // Amostrar o fundo no corredor e os triângulos nos 9% externos.
            // O HUD branco do placar fica no meio e não serve de fundo.
            int bgLuminance = (brightness(colors[y * w + (int)(w * .12)]) +
                    brightness(colors[y * w + (int)(w * .88)])) / 2;
            boolean left = isSpikeRow(colors, w, y, (int)(w * .01), (int)(w * .09), stride, bgLuminance);
            boolean right = isSpikeRow(colors, w, y, (int)(w * .91), (int)(w * .99), stride, bgLuminance);
            spikeL[y] = left ? 1 : 0;
            spikeR[y] = right ? 1 : 0;
            if (left || right) spikeEvidence++;
        }
        // The start menu has spikes at top/bottom but not normally on both side walls.
        // Never tap automatically until the playing field is recognizable.
        boolean board = spikeEvidence > (maxY - minY) / 32;
        if (!board) {
            return new Frame(false, false, false, "Campo não detectado. Toque para iniciar a partida; se continuar, mande um print do jogo", 0, 0, 0);
        }

        Blob bird = findBlob(colors, w, h, birdColor, true);
        // A skin enviada é verde. A alternativa automática ajuda quando
        // uma configuração antiga deixou "rosa" selecionada por padrão.
        if (bird == null && birdColor != 3) bird = findBlob(colors, w, h, 3, true);
        if (bird == null) {
            return new Frame(true, false, false, "Pássaro não encontrado. Escolha Verde na cor do pássaro.", 0, 0, 0);
        }

        long previousFrameAt = lastSeenAt;
        double dt = (timestamp - previousFrameAt) / 1000.0;
        if (previousFrameAt > 0 && dt > .055 && dt < 1.3) {
            double measuredVx = (bird.x - lastX) / dt;
            double measuredVy = (bird.y - lastY) / dt;
            // Smooth noisy detections. Jump causes abrupt vy changes.
            vx = vx * .28 + measuredVx * .72;
            vy = vy * .28 + measuredVy * .72;
            if (measuredVx > w * .08) direction = 1;
            if (measuredVx < -w * .08) direction = -1;
        }
        if (bird.x < w * .18 && vx >= 0) direction = 1;
        if (bird.x > w * .82 && vx <= 0) direction = -1;
        lastX = bird.x;
        lastY = bird.y;
        lastSeenAt = timestamp;

        // Select the widest spike-free window on the approaching wall.
        int[] nextWall = direction > 0 ? spikeR : spikeL;
        int radius = (int) (h * (.029 + safety * .00023));
        int bestY = (int) (h * .46);
        double bestCost = Double.POSITIVE_INFINITY;
        Blob candy = collectCandy ? findBlob(colors, w, h, 6, false) : null;
        boolean candyOnPath = candy != null &&
                ((direction > 0 && candy.x >= bird.x + w * .035) ||
                 (direction < 0 && candy.x <= bird.x - w * .035));

        // Avoid top/bottom spikes regardless of settings.
        for (int y = (int) (h * .24); y <= (int) (h * .67); y += 4) {
            int danger = 0;
            for (int k = Math.max(minY, y - radius); k < Math.min(maxY, y + radius); k++) {
                danger += nextWall[k];
            }
            if (danger > 0) continue;
            // BUGFIX v0.6: the previous cost followed the bird's current Y.
            // That made "bestY" move with the bird, so after one tap the
            // controller often decided no further flap was needed.
            // Keep a stable central altitude, then choose a gap near it.
            double cost = Math.abs(y - h * .46) * 1.70 +
                    Math.abs(y - bird.y) * .14;
            // Candies are optional and NEVER override spike clearance.
            if (candyOnPath) cost += Math.abs(y - candy.y) * .30;
            if (cost < bestCost) { bestCost = cost; bestY = y; }
        }
        if (Double.isInfinite(bestCost)) {
            // No convincing free window: do not invent a safe path.
            return new Frame(true, true, false, "Sem corredor seguro reconhecível", bird.x, bird.y, bestY);
        }

        // Frame.jump now means "a flap is recommended", NOT "do a single
        // immediate tap". The service owns repeated, rate-limited gestures.
        // Screenshots may only arrive about every 250ms. We must keep a
        // short-lived flap recommendation between two successful frames.
        // v0.7: vertical physics are important. The old "rhythmNeeded"
        // sent another flap while the bird was still ASCENDING, which could
        // push it into the top spikes in only two taps.
        // Project position 140 ms forward (bounded for noisy velocities).
        double futureY = bird.y + Math.max(-h * .08, Math.min(h * .08, vy * .14));
        final boolean high = bird.y < h * .235;
        final boolean descendingOrAtApex = previousFrameAt == 0 || dt >= 1.3 || vy > -h * .065;
        final boolean dangerouslyLow = bird.y > h * .615;
        final boolean belowSafeBand = futureY > bestY - h * .030;
        final long interval = Math.max(245L, 335L - (rate * 55L / 100L));
        final boolean cooldownDone = timestamp - lastTapAt >= interval;
        boolean jump = cooldownDone && !high &&
                (dangerouslyLow || (descendingOrAtApex && belowSafeBand));

        String state = high ? "muito alto" :
                !cooldownDone ? "aguardando intervalo" :
                !descendingOrAtApex ? "subindo, sem novo toque" :
                jump ? "queda: subir" : "altitude segura: aguardar";
        String status = (collectCandy && candyOnPath ?
                "VISÃO • doce opcional" : "VISÃO • altitude") +
                " • " + state +
                " • Y " + (int)bird.y + "→" + bestY;
        return new Frame(true, true, jump, status, bird.x, bird.y, bestY);
    }

    private static boolean isSpikeRow(int[] pix, int w, int y, int x0, int x1,
                                      int stride, int background) {
        int n = 0, dark = 0;
        for (int x = x0; x < x1; x += stride) {
            int c = pix[y * w + x];
            int r = Color.red(c), g = Color.green(c), b = Color.blue(c);
            int lum = (r * 3 + g * 6 + b) / 10;
            if (Math.abs(r-g) < 37 && Math.abs(g-b) < 37 &&
                    lum < Math.min(200, background - 34)) dark++;
            n++;
        }
        // Os espinhos reais ocupam frequentemente só 4–12px da captura
        // reduzida; a exigência antiga de 32% em uma faixa larga falhava.
        return n > 0 && dark >= Math.max(3, (int) Math.ceil(n * .23));
    }

    private static int brightness(int c) {
        return (Color.red(c) * 3 + Color.green(c) * 6 + Color.blue(c)) / 10;
    }

    private static boolean matches(int c, int kind) {
        int r = Color.red(c), g = Color.green(c), b = Color.blue(c);
        switch (kind) {
            case 0: // Rosa Ketchapp, bright pink/magenta
                return r > 155 && g < 165 && r > g * 1.44 && b > 36 && r > b * 1.05;
            case 1: // Azul
                return b > 120 && b > r * 1.28 && (b > g * 1.06 || g > r * 1.35);
            case 2: // Roxo
                return b > 99 && r > 65 && b > g * 1.35 && r > g * 1.30;
            case 3: // Verde
                return g > 115 && g > r * 1.28 && g > b * 1.09;
            case 4: // Escuro (usar somente nas capturas sem fundo escuro)
                return r < 85 && g < 85 && b < 93;
            case 5: // Vermelho
                return r > 140 && g < 110 && b < 115 && r > 1.55 * g;
            case 6: // Laranja dos doces
                return r > 165 && g > 64 && g < 220 && b < 115 && r > g * 1.15 && g > b * 1.28;
            default: return false;
        }
    }

    private Blob findBlob(int[] pix, int w, int h, int kind, boolean birdSearch) {
        // Na captura o pássaro encosta nas paredes (x de 0.07 a 0.82).
        // Não procurar apenas no centro: isso ocultava um dos rebotes.
        int xMin = (int) (w * (birdSearch ? .02 : .10));
        int xMax = (int) (w * (birdSearch ? .98 : .90));
        int yMin = (int) (h * .19), yMax = (int) (h * .70);
        int total = w * h;
        byte[] mask = new byte[total];
        // Build a mask only for the center part of the playfield.
        for (int y = yMin; y < yMax; y++) {
            int base = y * w;
            for (int x = xMin; x < xMax; x++) {
                if (matches(pix[base + x], kind)) mask[base + x] = 1;
            }
        }
        Blob best = null;
        double bestValue = -1e12;
        // A primitive queue avoids boxing tens of thousands of Integers per frame.
        // Each pixel enters the queue at most once, so total capacity is sufficient.
        int[] flood = new int[total];
        for (int y = yMin; y < yMax; y++) {
            for (int x = xMin; x < xMax; x++) {
                int start = y*w + x;
                if (mask[start] != 1) continue;
                mask[start] = 2;
                Blob blob = new Blob(x,y);
                long sumX = 0, sumY = 0;
                int head = 0, tail = 0;
                flood[tail++] = start;
                while (head < tail) {
                    int p = flood[head++];
                    int px = p % w, py = p / w;
                    blob.count++;
                    sumX += px; sumY += py;
                    if (px < blob.minX) blob.minX = px;
                    if (px > blob.maxX) blob.maxX = px;
                    if (py < blob.minY) blob.minY = py;
                    if (py > blob.maxY) blob.maxY = py;
                    if (px > xMin && mask[p-1] == 1) { mask[p-1]=2; flood[tail++]=p-1; }
                    if (px+1 < xMax && mask[p+1] == 1) { mask[p+1]=2; flood[tail++]=p+1; }
                    if (py > yMin && mask[p-w] == 1) { mask[p-w]=2; flood[tail++]=p-w; }
                    if (py+1 < yMax && mask[p+w] == 1) { mask[p+w]=2; flood[tail++]=p+w; }
                }
                if (blob.count < (birdSearch ? 18 : 7) || blob.count > (birdSearch ? 1700 : 600)) continue;
                double width = blob.maxX - blob.minX + 1, height = blob.maxY - blob.minY + 1;
                if (width / height < .38 || width / height > 2.65) continue;
                blob.x = (double) sumX / blob.count;
                blob.y = (double) sumY / blob.count;
                double proximity = lastX >= 0 && birdSearch ?
                        Math.hypot(blob.x - lastX, blob.y - lastY) : 0;
                double value = blob.count * 1.4 - proximity * (birdSearch ? .9 : 0);
                // Center field is less likely to contain menu controls than the borders.
                if (birdSearch && lastX < 0) value -= Math.abs(blob.x - w * .50) * .15;
                if (value > bestValue) { bestValue = value; best = blob; }
            }
        }
        return best;
    }
}
