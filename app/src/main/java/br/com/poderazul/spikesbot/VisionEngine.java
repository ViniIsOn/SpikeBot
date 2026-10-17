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

    /**
     * Looks for the next wall's actual gaps and predicts where the bird would
     * arrive without another flap. Works on resized 216px-wide frames.
     * Uses NO fixed pattern or preset number of taps.
     */
    synchronized Frame analyze(Bitmap bitmap, int birdColor, boolean collectCandy,
                               int safety, int rate, long timestamp) {
        final int w=bitmap.getWidth(), h=bitmap.getHeight();
        if(w<100 || h<200)
            return new Frame(false,false,false,"Quadro pequeno demais",0,0,0);

        int[] pixels=new int[w*h];
        bitmap.getPixels(pixels,0,w,0,0,w,h);
        final int atX=(int)(w*.86);
        final int ceilingL=brightness(pixels[(int)(h*.11)*w+atX]);
        final int upperFieldL=brightness(pixels[(int)(h*.25)*w+atX]);
        final int lowerFieldL=brightness(pixels[(int)(h*.79)*w+atX]);
        final int floorL=brightness(pixels[(int)(h*.92)*w+atX]);
        // Check the actual board, not the screenshot-edit toolbar / score.
        final boolean roofSeen=upperFieldL-ceilingL>45;
        final boolean floorSeen=lowerFieldL-floorL>45;

        final int scanTop=(int)(h*.185), scanBottom=(int)(h*.81);
        int[] left=new int[h], right=new int[h];
        int leftCount=0,rightCount=0;
        int leftBgX=(int)(w*.13),rightBgX=(int)(w*.87);
        for(int y=scanTop;y<scanBottom;y++){
            int bgLeft=brightness(pixels[y*w+leftBgX]);
            int bgRight=brightness(pixels[y*w+rightBgX]);
            boolean l=isSpikeRow(pixels,w,y,(int)(w*.01),(int)(w*.10),1,bgLeft);
            boolean r=isSpikeRow(pixels,w,y,(int)(w*.90),(int)(w*.99),1,bgRight);
            if(l){left[y]=1;leftCount++;}
            if(r){right[y]=1;rightCount++;}
        }
        final boolean sidesDetected=leftCount+rightCount>=Math.max(8,(scanBottom-scanTop)/38);
        final boolean stageDetected=(roofSeen && floorSeen)||sidesDetected;
        if(!stageDetected)
            return new Frame(false,false,false,"Arena nao reconhecida",0,0,0);

        Blob bird=findBlob(pixels,w,h,birdColor,true);
        if(bird==null && birdColor!=3)bird=findBlob(pixels,w,h,3,true);
        if(bird==null){
            if(timestamp-lastSeenAt>450){lastX=-1;lastY=-1;vx=vy=0;}
            return new Frame(true,false,false,"Perdi o passaro • selecione Verde",0,0,0);
        }
        long seenBefore=lastSeenAt;
        double dt=(timestamp-seenBefore)/1000.0;
        boolean stableSample=seenBefore>0 && dt>=.035 && dt<=.32;
        if(stableSample){
            double newVx=(bird.x-lastX)/dt;
            double newVy=(bird.y-lastY)/dt;
            // A large jump is likely a tracking glitch or score overlay.
            if(Math.abs(newVx)<w*7 && Math.abs(newVy)<h*7){
                vx=vx*.34+newVx*.66;
                vy=vy*.34+newVy*.66;
                if(newVx>w*.14)direction=1;
                else if(newVx< -w*.14)direction=-1;
            }else{
                stableSample=false;vx=vy=0;
            }
        }
        if(!stableSample) {
            vx=0;vy=0;
            if(seenBefore==0 || dt>.4)direction=bird.x<w*.5?1:-1;
        }
        if(bird.x<w*.10)direction=1;
        if(bird.x>w*.90)direction=-1;
        lastX=bird.x;lastY=bird.y;lastSeenAt=timestamp;

        // Only the wall the bird is approaching matters for the next bounce.
        int[] wall=direction>0?right:left;
        int wallCount=direction>0?rightCount:leftCount;
        int clearance=Math.max(10,(int)(h*(.025+Math.max(0,Math.min(100,safety))*.00015)));
        int topLimit=(int)(h*.238),bottomLimit=(int)(h*.765);
        int center=(int)(h*.472);

        // Horizontal time to collision is estimated from consecutive frames.
        // When speed is unknown, use a conservative one-half screen per second.
        double horizontalSpeed=Math.max(Math.abs(vx),w*.68);
        double distance=direction>0 ? (w*.94-bird.x):(bird.x-w*.06);
        double wallTime=Math.max(.05,Math.min(.70,distance/horizontalSpeed));
        // Coarse gravity model on the 216 x ~483 field. Predicted Y is where
        // the bird would be if we did NOT flap again. This is an estimate,
        // NOT a proven exact model of Ketchapp's physics.
        double gravity=h*2.65;
        double predictedWallY=bird.y+vy*wallTime+.5*gravity*wallTime*wallTime;
        predictedWallY=Math.max(topLimit,Math.min(bottomLimit,predictedWallY));
        double lookahead=.18;
        double soonY=bird.y+vy*lookahead+.5*gravity*lookahead*lookahead;
        Blob candy=collectCandy?findBlob(pixels,w,h,6,false):null;
        boolean candyAhead=candy!=null &&
                ((direction>0 && candy.x>bird.x+w*.045) ||
                 (direction<0 && candy.x<bird.x-w*.045));

        int[] sums=new int[h+1];
        for(int i=0;i<h;i++)sums[i+1]=sums[i]+wall[i];
        double bestScore=Double.POSITIVE_INFINITY;
        int target=center;
        for(int y=topLimit;y<bottomLimit;y+=2){
            int from=Math.max(scanTop,y-clearance);
            int to=Math.min(scanBottom,y+clearance+1);
            if(sums[to]-sums[from]>0)continue;
            // Center keeps a safe distance from spikes on floor / ceiling.
            // The predicted arrival favors reachable gaps on the next wall.
            double cost=Math.abs(y-center)*.80+
                        Math.abs(y-predictedWallY)*.44;
            if(candyAhead)cost+=Math.abs(y-candy.y)*.24;
            if(cost<bestScore){bestScore=cost;target=y;}
        }
        if(!Double.isFinite(bestScore))
            return new Frame(true,true,false,
                    "Sem corredor seguro: parede "+(direction>0?"D":"E"),
                    bird.x,bird.y,center);

        // A flap must be predicted, not issued every N frames:
        // no extra tap when rising quickly or near the ceiling; trigger while
        // falling BEFORE the projected arrival falls below the selected gap.
        boolean risingFast=stableSample && vy< -h*.13;
        boolean atHighAltitude=bird.y<topLimit+h*.030 ||
                             (bird.y<topLimit+h*.09 && vy< -h*.18);
        boolean nearFloor=bird.y>bottomLimit-h*.083;
        boolean predictedDrop=soonY>target-h*.04;
        boolean wallDrop=wallTime<.40 && predictedWallY>target+h*.08;
        boolean jump=!atHighAltitude &&
                (nearFloor || (!risingFast && (predictedDrop || wallDrop)));
        // Only a live positive recommendation is acted on, then rate-limited
        // by SpikesService (the decision engine has no independent cooldown).
        String state=risingFast?"subindo":vy>h*.05?"caindo":"no apice";
        String reason="VISÃO "+(jump?"SUBIR":"AGUARDAR")+
                " • "+state+
                " • parede "+(direction>0?"D":"E")+
                " espinhos="+wallCount+
                " • Y "+(int)bird.y+" alvo "+target+
                " • chegada "+(int)predictedWallY+
                (candyAhead?" • doce":"");
        return new Frame(true,true,jump,reason,bird.x,bird.y,target);
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
