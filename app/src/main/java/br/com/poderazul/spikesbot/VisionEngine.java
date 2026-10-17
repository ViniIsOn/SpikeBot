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

    synchronized void reset() {
        lastX = -1; lastY = -1; vx = vy = 0;
        lastSeenAt = 0; lastTapAt = 0; direction = 1;
    }

    synchronized void tapWasSent(long at) { lastTapAt = at; }

    synchronized Frame analyze(Bitmap bitmap, int birdColor, boolean collectCandy, int safety,
                  int rate, long timestamp) {
        final int w = bitmap.getWidth(), h = bitmap.getHeight();
        if (w < 100 || h < 200) {
            return new Frame(false, false, false, "Imagem pequena demais", 0, 0, 0);
        }
        int[] pixels = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);

        // The game has a dark ceiling and floor, including triangular spikes.
        // At 75% width we are outside the large central score graphic.
        // On all four screenshots the board runs ~17% to ~85% of screen height.
        int xBackground = Math.min(w - 1, (int)(w * .86));
        int outsideTop = brightness(pixels[(int)(h * .11) * w + xBackground]);
        int insideTop = brightness(pixels[(int)(h * .25) * w + xBackground]);
        int outsideBottom = brightness(pixels[(int)(h * .92) * w + xBackground]);
        int insideBottom = brightness(pixels[(int)(h * .79) * w + xBackground]);
        boolean roofSeen = insideTop - outsideTop > 48;
        boolean floorSeen = insideBottom - outsideBottom > 48;

        // Detect the actual left and right TRIANGULAR tips, not wide stripes.
        // Different wall sides must have separate references: averaging both
        // sides made the dark triangle disappear on some frames.
        int[] leftWall = new int[h];
        int[] rightWall = new int[h];
        int scanTop = (int)(h * .185), scanBottom = (int)(h * .805);
        int leftRows = 0, rightRows = 0;
        int leftReferenceX = (int)(w * .13), rightReferenceX = (int)(w * .87);
        for (int y = scanTop; y < scanBottom; y++) {
            int leftBackground = brightness(pixels[y*w + leftReferenceX]);
            int rightBackground = brightness(pixels[y*w + rightReferenceX]);
            // Tips extend about 0-6% of screen width in the screenshots.
            boolean l = isSpikeRow(pixels, w, y,
                    Math.max(1, (int)(w*.01)), (int)(w*.10),
                    1, leftBackground);
            boolean r = isSpikeRow(pixels, w, y,
                    (int)(w*.90), Math.min(w - 1, (int)(w*.99)),
                    1, rightBackground);
            leftWall[y] = l ? 1 : 0;
            rightWall[y] = r ? 1 : 0;
            if (l) leftRows++;
            if (r) rightRows++;
        }
        boolean wallSeen = leftRows + rightRows >= Math.max(8, (scanBottom - scanTop) / 38);
        boolean stageSeen = roofSeen && floorSeen;
        boolean boardSeen = stageSeen || wallSeen;

        if (!boardSeen) {
            return new Frame(false, false, false,
                    "Campo perdido: sem teto/chão ou espinhos detectáveis", 0, 0, 0);
        }

        // The bird body has far more connected green pixels than the star trail.
        Blob bird = findBlob(pixels, w, h, birdColor, true);
        if (bird == null && birdColor != 3) {
            bird = findBlob(pixels, w, h, 3, true);
        }
        if (bird == null) {
            if (timestamp - lastSeenAt > 450) {
                lastX = -1; lastY = -1; vx = vy = 0;
            }
            return new Frame(true, false, false,
                    "Pássaro perdido: ajuste a cor (verde nas suas imagens)", 0, 0, 0);
        }

        long previousSeen = lastSeenAt;
        double delta = (timestamp - previousSeen) / 1000.0;
        if (previousSeen > 0 && delta > .035 && delta < .45) {
            double observedX = (bird.x - lastX) / delta;
            double observedY = (bird.y - lastY) / delta;
            // Smooth the sprite's small detection shifts, but respond to a
            // rising/falling change within 2-3 frames.
            vx = .40 * vx + .60 * observedX;
            vy = .40 * vy + .60 * observedY;
            if (observedX > w*.14) direction = 1;
            if (observedX < -w*.14) direction = -1;
        } else if (delta >= .45 || previousSeen == 0) {
            vx = 0;
            vy = 0;
            direction = bird.x < w*.50 ? 1 : -1;
        }
        if (bird.x < w*.095) direction = 1;
        if (bird.x > w*.905) direction = -1;
        lastX = bird.x;
        lastY = bird.y;
        lastSeenAt = timestamp;

        // Decide which wall we are approaching, then find a corridor with
        // clearance for the bird body, not merely a gap between two pixels.
        int[] approachingWall = direction > 0 ? rightWall : leftWall;
        int approachingRows = direction > 0 ? rightRows : leftRows;
        int radius = Math.max(10, (int)(h * (.020 + Math.max(0, Math.min(100, safety)) * .00017)));
        // Ceiling and floor are always hazards, even when side walls are empty.
        int highLimit = (int)(h * .245);
        int lowLimit = (int)(h * .757);
        int preferred = (int)(h * .465);
        int bestTarget = preferred;
        double bestCost = Double.POSITIVE_INFINITY;
        Blob candy = collectCandy ? findBlob(pixels, w, h, 6, false) : null;
        boolean relevantCandy = candy != null && (
                (direction > 0 && candy.x > bird.x + w*.04) ||
                (direction < 0 && candy.x < bird.x - w*.04));

        // Prefix sums allow checking a whole bird-sized corridor per candidate.
        int[] prefix = new int[h+1];
        for (int y=0; y<h; y++) prefix[y+1] = prefix[y] + approachingWall[y];
        for (int y=highLimit; y<=lowLimit; y+=2) {
            int lo = Math.max(scanTop, y-radius);
            int hi = Math.min(scanBottom, y+radius);
            if (prefix[hi] - prefix[lo] != 0) continue;
            double cost = Math.abs(y - preferred) * 1.5 +
                    Math.abs(y - bird.y) * .08;
            // Candies can tip the decision only among safe gaps.
            if (relevantCandy) cost += Math.abs(y - candy.y) * .45;
            if (cost < bestCost) {
                bestCost = cost;
                bestTarget = y;
            }
        }
        if (!Double.isFinite(bestCost)) {
            return new Frame(true, true, false,
                    "Sem passagem segura na parede " + (direction>0?"direita":"esquerda"),
                    bird.x, bird.y, preferred);
        }

        // Use vertical motion to determine when the bird needs another flap.
        // Unlike v0.9, DO NOT run an independent cooldown inside vision:
        // SpikesService owns the single tap rate limit. Two competing cooldowns
        // produced two taps followed by an excessive wait.
        boolean knownVelocity = previousSeen > 0 && delta < .45;
        double verticalRate = knownVelocity ? vy : 0;
        double lookAhead = Math.max(-h*.10, Math.min(h*.10, verticalRate * .11));
        double projectedY = bird.y + lookAhead;
        boolean risingFast = knownVelocity && verticalRate < -h*.10;
        boolean tooHigh = bird.y < highLimit + h*.01;
        boolean floorThreat = bird.y > lowLimit - h*.060;
        boolean needsFlap = !tooHigh &&
                (floorThreat ||
                 (!risingFast && projectedY > bestTarget - h*.043));
        String flight = risingFast ? "subindo" : verticalRate > h*.05 ? "descendo" : "no ápice";
        String status = "VISÃO: " + (needsFlap ? "SUBIR" : "ESPERAR") +
                " • " + flight +
                " • " + (direction>0?"parede D":"parede E") +
                " (" + approachingRows + " linhas esp.)" +
                " • y=" + (int)bird.y + "/" + bestTarget +
                (relevantCandy ? " • doce" : "");
        return new Frame(true, true, needsFlap, status,
                bird.x, bird.y, bestTarget);
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
        int yMin = (int) (h * .185), yMax = (int) (h * (birdSearch ? .815 : .79));
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
                if (blob.count < (birdSearch ? 80 : 7) || blob.count > (birdSearch ? 2100 : 600)) continue;
                double width = blob.maxX - blob.minX + 1, height = blob.maxY - blob.minY + 1;
                if (width / height < .38 || width / height > 2.65 ||
                        (birdSearch && (width < 8 || height < 8))) continue;
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
