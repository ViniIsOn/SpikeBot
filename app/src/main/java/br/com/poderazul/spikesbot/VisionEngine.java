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
    private long lastHorizontalMotionAt = 0;
    private long lastSeenAt = 0, lastTapAt = 0;
    private int direction = 1; // 1 is going right

    synchronized void reset() {
        lastX = -1; lastY = -1; vx = vy = 0;
        lastSeenAt = 0; lastTapAt = 0; lastHorizontalMotionAt = 0; direction = 1;
    }

    synchronized void tapWasSent(long at) { lastTapAt = at; }

    /**
     * Looks for the next wall's actual gaps and predicts where the bird would
     * arrive without another flap. Works on resized 216px-wide frames.
     * Uses NO fixed pattern or preset number of taps.
     */
    // Measured physical constants and control are in FlightLogic.java.
    // This class handles pixels and object tracking exclusively.

    synchronized Frame analyze(Bitmap bitmap, int birdColor, boolean collectCandy,
                               int safety, int rate, long timestamp) {
        final int w=bitmap.getWidth(),h=bitmap.getHeight();
        if(w<100||h<200)
            return new Frame(false,false,false,"Quadro pequeno demais",0,0,0);
        int[] colors=new int[w*h];
        bitmap.getPixels(colors,0,w,0,0,w,h);

        // Game-over displays a large bright RED central scoreboard and
        // REPLAY/SHARE bars. Startup only uses small pink labels/icons.
        // Threshold tested against samples from earlier shared black boxes.
        int redPixels=0;
        for(int yy=(int)(h*.24);yy<(int)(h*.70);yy+=2) {
            for(int xx=(int)(w*.16);xx<(int)(w*.84);xx+=2) {
                int c=colors[yy*w+xx];
                int rr=(c>>16)&255,gg=(c>>8)&255,bb=c&255;
                if(rr>175&&rr>gg*1.50&&rr>bb*1.35&&gg<140) redPixels++;
            }
        }
        if(redPixels>1200) {
            return new Frame(false,false,false,
                    "FIM_DE_JOGO: placar vermelho detectado",0,0,0);
        }

        int atX=(int)(w*.86);
        int roofDark=brightness(colors[(int)(h*.11)*w+atX]);
        int roofLight=brightness(colors[(int)(h*.25)*w+atX]);
        int groundLight=brightness(colors[(int)(h*.79)*w+atX]);
        int groundDark=brightness(colors[(int)(h*.92)*w+atX]);
        boolean roofSeen=roofLight-roofDark>45;
        boolean floorSeen=groundLight-groundDark>45;

        int scanTop=(int)(h*.185),scanBottom=(int)(h*.81);
        int[] left=new int[h],right=new int[h];
        int leftCount=0,rightCount=0;
        int leftBgX=(int)(w*.13),rightBgX=(int)(w*.87);
        for(int y=scanTop;y<scanBottom;y++){
            int leftBg=brightness(colors[y*w+leftBgX]);
            int rightBg=brightness(colors[y*w+rightBgX]);
            boolean L=isSpikeRow(colors,w,y,(int)(w*.01),(int)(w*.10),1,leftBg);
            boolean R=isSpikeRow(colors,w,y,(int)(w*.90),(int)(w*.99),1,rightBg);
            left[y]=L?1:0;right[y]=R?1:0;
            if(L)leftCount++;
            if(R)rightCount++;
        }
        boolean walls=leftCount+rightCount>=Math.max(8,(scanBottom-scanTop)/38);
        boolean stage=(roofSeen&&floorSeen)||walls;
        if(!stage)
            return new Frame(false,false,false,"Campo nao reconhecido",0,0,0);

        Blob bird=findBlob(colors,w,h,birdColor,true);
        if(bird==null&&birdColor!=3)bird=findBlob(colors,w,h,3,true);
        if(bird==null){
            if(timestamp-lastSeenAt>500){lastX=-1;lastY=-1;vx=vy=0;}
            return new Frame(true,false,false,"Passaro nao detectado • selecione Verde",0,0,0);
        }
        long last=lastSeenAt;
        double previousXForMotion=lastX;
        double dt=(timestamp-last)/1000.0;
        boolean reliableVelocity=last>0&&dt>=.035&&dt<=.35;
        if(reliableVelocity){
            double observedX=(bird.x-lastX)/dt;
            double observedY=(bird.y-lastY)/dt;
            if(Math.abs(observedX)<w*7&&Math.abs(observedY)<h*7){
                vx=vx*.33+observedX*.67;
                vy=vy*.33+observedY*.67;
                if(observedX>w*.14)direction=1;
                else if(observedX< -w*.14)direction=-1;
            } else reliableVelocity=false;
        }
        if(!reliableVelocity){
            vx=vy=0;
            if(last==0||dt>.4)direction=bird.x<w*.50?1:-1;
        }
        if(bird.x<w*.10)direction=1;
        if(bird.x>w*.90)direction=-1;
        lastX=bird.x;lastY=bird.y;lastSeenAt=timestamp;

        // Do NOT mistake the animated bird on the start screen for a
        // live match. Real gameplay always moves across the arena.
        if(reliableVelocity && last>=0 &&
                Math.abs(bird.x - previousXForMotion) > w*.022) {
            lastHorizontalMotionAt=timestamp;
        }
        if(lastHorizontalMotionAt==0 ||
                timestamp-lastHorizontalMotionAt > 550) {
            return new Frame(true,true,false,
                    "Aguardando partida em movimento (inicie manualmente)",bird.x,bird.y,h*.47);
        }

        int[] wall=direction>0?right:left;
        int wallCount=direction>0?rightCount:leftCount;

        // The planner has exactly ONE fixed gap target for both actions,
        // avoiding v1.3's tautology: picking different perfect-looking
        // corridors for "tap" and "wait", then almost always waiting.
        FlightLogic.Plan plan=FlightLogic.choose(w,h,bird.x,bird.y,
                vx,vy,direction,wall,scanTop,scanBottom,safety,
                timestamp-lastTapAt);
        if(!plan.hasCorridor)
            return new Frame(true,true,false,
                    "Parede "+(direction>0?"D":"E")+": nenhuma abertura segura",
                    bird.x,bird.y,h*.47);
        String status="VISÃO "+(plan.jump?"SUBIR":"AGUARDAR")+
                " • parede "+(direction>0?"D":"E")+
                " • espinhos="+wallCount+
                " • y="+(int)bird.y+
                " alvo="+plan.target+
                " • previsão "+(int)plan.noTapY+"/"+(int)plan.tapY+
                " • ETA="+(int)(plan.wallSeconds*1000)+"ms"+
                " • "+plan.reason;
        return new Frame(true,true,plan.jump,status,bird.x,bird.y,plan.target);
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
