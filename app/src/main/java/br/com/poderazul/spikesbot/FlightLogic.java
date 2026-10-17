package br.com.poderazul.spikesbot;

/**
 * Pure-Java predictive controller (v1.5).
 * The inputs are measurements from the CURRENT GAME, not a fixed tap sequence.
 *
 * Primary fixes: select the MIDDLE of a safe opening, compare a flap now
 * against short deliberate delays, and never score a trajectory as safe
 * merely because its final height looks close to the target.
 */
final class FlightLogic {
    static final double GRAVITY_PER_HEIGHT = 0.98;
    static final double FLAP_SPEED_PER_HEIGHT = -0.41;
    static final double[] DELAYS = {0, .08, .16, .24, .32, .40, .48};

    static final class Plan {
        final boolean hasCorridor;
        final boolean jump;
        final int target;
        final double noTapY, tapY, wallSeconds;
        final String reason;
        Plan(boolean found, boolean jump, int target, double noTapY,
             double tapY, double eta, String reason) {
            this.hasCorridor=found; this.jump=jump; this.target=target;
            this.noTapY=noTapY; this.tapY=tapY;
            this.wallSeconds=eta; this.reason=reason;
        }
    }

    static Plan choose(int w,int h,double x,double y,double vx,double vy,
                       int direction,int[] spikes,int scanTop,int scanBottom,
                       int safety,long sinceLastTapMs) {
        final double gravity=h*GRAVITY_PER_HEIGHT;
        final double impulse=h*FLAP_SPEED_PER_HEIGHT;
        final double fallbackSpeed=w*.625;
        double horizontalSpeed=Math.abs(vx);
        if(horizontalSpeed<w*.32)horizontalSpeed=fallbackSpeed;
        horizontalSpeed=clamp(horizontalSpeed,w*.43,w*.90);
        double distance=direction>0?(w*.92-x):(x-w*.08);
        double eta=clamp(distance/horizontalSpeed,.035,1.35);
        final int ceiling=(int)(h*.24), floor=(int)(h*.76);
        final int center=(int)(h*.47);
        final int margin=Math.max(17,(int)Math.round(h*(.027+clamp(safety,0,100)*.00017)));
        int[] prefix=new int[h+1];
        for(int i=0;i<h;i++)prefix[i+1]=prefix[i]+spikes[i];

        // For distant walls the exact ballistic trajectory is unreliable.
        // Prefer broad clear lanes, but keep the current bird Y reachable.
        double shortHorizon=Math.min(.45,eta);
        double coasting=y+vy*shortHorizon+.5*gravity*shortHorizon*shortHorizon;
        double flapping=y+impulse*shortHorizon+.5*gravity*shortHorizon*shortHorizon;
        int target=selectLane(prefix,ceiling,floor,scanTop,scanBottom,
                margin,center,y,coasting,flapping);
        if(target<0) return new Plan(false,false,center,coasting,flapping,eta,"parede sem abertura segura");

        // Predict the whole crossing at most 0.72s ahead: beyond that,
        // the controller will receive several new frames to replan.
        double forecast=Math.min(.72,eta);
        double noFlapEnd=y+vy*forecast+.5*gravity*forecast*forecast;
        double flapNowEnd=y+impulse*forecast+.5*gravity*forecast*forecast;

        double waitScore=pathCost(y,vy,gravity,-1,forecast,
                ceiling,floor,prefix,scanTop,scanBottom,margin,target,
                eta<=.72);
        double bestFlapScore=Double.POSITIVE_INFINITY;
        double bestDelay=-1;
        for(double delay:DELAYS) {
            if(delay>forecast-.035)continue;
            double score=pathCost(y,vy,gravity,delay,forecast,
                    ceiling,floor,prefix,scanTop,scanBottom,margin,target,
                    eta<=.72);
            // Discourage extra taps and extremely delayed decisions.
            score+=6+delay*6;
            if(score<bestFlapScore){bestFlapScore=score;bestDelay=delay;}
        }

        // A floor fall must be addressed before a position-based controller
        // can react in the next frame. The true position is never assumed
        // safe just because a wall corridor exists.
        boolean urgentFall=y+vy*.20+.5*gravity*.20*.20>floor-h*.045;
        boolean ceilingThreat=y<ceiling+h*.038;
        boolean risingFast=vy<-h*.25;

        boolean jump=bestDelay>=0 && bestFlapScore+3<waitScore && bestDelay<=.085;
        if(urgentFall && bestDelay>=0 && bestDelay<=.16 &&
                bestFlapScore<waitScore+35) jump=true;

        // v2.0: a v1.9 black box exposed a slow-fall-to-floor failure.
        // At 33.526s, bird Y=244, target=228, descending rapidly.
        // The old delayed-tap score still told the bot to WAIT until
        // 33.700s (Y=314), when a safe correction was no longer possible.
        // Track the bird's CURRENT trajectory, not only the end-wall cost.
        double look=.23;
        double futureY=y+vy*look+
                .5*gravity*look*look;
        boolean descending= vy>h*.055;
        boolean rising= vy< -h*.095;
        boolean floorGuard=futureY>floor-Math.max(34,h*.088);
        boolean wallGuard=eta<.84 && descending &&
                target<y+h*.022 && futureY>target+h*.050;
        boolean middleGuard=descending && y>center+h*.048 &&
                futureY>center+h*.070;
        boolean highGap=target<center-h*.10 && eta<1.10 &&
                y>target+h*.085 && descending;
        boolean rescue=(floorGuard || wallGuard || middleGuard || highGap);
        // Repeated upward flaps caused the v1.6 regression. A bird still
        // ascending or already close to the ceiling must never use rescue.
        boolean ceilingSoon=y+Math.min(vy,0)*.16+
                .5*gravity*.16*.16<ceiling+h*.030;
        if(rescue && !rising && !ceilingThreat && !ceilingSoon)jump=true;
        if(ceilingThreat || ceilingSoon || (risingFast && !urgentFall))jump=false;

        // A falling bird near the floor may need a second flap sooner than
        // an ordinary manoeuvre. Keep a minimum gesture spacing; never
        // queue arbitrary bursts or continue without a valid live frame.
        boolean emergency= floorGuard && descending && y>center+h*.10;
        long cooldown=emergency?195:245;
        if(sinceLastTapMs>=0 && sinceLastTapMs<cooldown)jump=false;

        String reason=jump?(rescue?(floorGuard?"queda: proteger chao":
                highGap?"alcancar passagem alta":
                wallGuard?"preparar parede":"estabilizar voo"):
                "pulo previsto"):
                ceilingThreat || ceilingSoon?"teto proximo":
                rising?"subindo: aguardar":
                bestDelay>.085 && bestFlapScore+3<waitScore?
                "espera planejada":"altitude estavel";
        return new Plan(true,jump,target,noFlapEnd,flapNowEnd,eta,reason);
    }

    /**
     * Split all spike-free rows into consecutive LANES, then choose a lane
     * centre. This removes the bug where the target sits exactly at the
     * clearance threshold and the bird grazes the next triangle.
     */
    private static int selectLane(int[] prefix,int ceiling,int floor,
                                  int scanTop,int scanBottom,int margin,
                                  int center,double currentY,double coast,
                                  double flap) {
        int start=-1,chosen=-1;
        double best=Double.POSITIVE_INFINITY;
        for(int y=ceiling+12;y<=floor-12+1;y++){
            boolean clear=y<=floor-12 &&
                    clearAt(prefix,y,margin,scanTop,scanBottom);
            if(clear && start<0)start=y;
            if((!clear || y==floor-12+1) && start>=0){
                int end=y-1;
                int midpoint=(start+end)/2;
                int width=end-start+1;
                double reachable=Math.min(Math.abs(midpoint-coast),Math.abs(midpoint-flap));
                double score=Math.abs(midpoint-center)*.75 +
                        Math.abs(midpoint-currentY)*.12 + reachable*.13 +
                        Math.max(0,28-width)*1.5;
                if(score<best) {best=score;chosen=midpoint;}
                start=-1;
            }
        }
        return chosen;
    }

    /** Assess the entire path, both before and after a prospective delayed tap. */
    private static double pathCost(double y,double speed,double gravity,
                                   double tapDelay,double duration,
                                   int ceiling,int floor,int[] prefix,
                                   int scanTop,int scanBottom,int margin,
                                   int target,boolean reachingWall) {
        double afterTapY=0;
        if(tapDelay>=0)
            afterTapY=y+speed*tapDelay+.5*gravity*tapDelay*tapDelay;
        double impulse=prefix.length*FLAP_SPEED_PER_HEIGHT;
        double cost=0;
        double endY=y;
        // Sample intermediate elevations, not just the eventual landing point.
        for(int i=1;i<=12;i++) {
            double t=duration*i/12.0;
            double position;
            if(tapDelay<0 || t<tapDelay) {
                position=y+speed*t+.5*gravity*t*t;
            } else {
                double d=t-tapDelay;
                position=afterTapY+impulse*d+.5*gravity*d*d;
            }
            endY=position;
            if(position<ceiling+12)cost+=500+(ceiling+12-position)*20;
            if(position>floor-12)cost+=500+(position-(floor-12))*20;
        }
        cost+=Math.abs(endY-target)*1.08;
        if(reachingWall) {
            int wallRow=(int)Math.round(endY);
            if(!clearAt(prefix,wallRow,margin,scanTop,scanBottom)) cost+=900;
            else if(!clearAt(prefix,wallRow,margin+6,scanTop,scanBottom)) cost+=65;
        }
        return cost;
    }

    private static boolean clearAt(int[] prefix,int y,int radius,int scanTop,int scanBottom) {
        if(y-radius<scanTop || y+radius+1>scanBottom) return false;
        if(y-radius<0 || y+radius+1>=prefix.length) return false;
        return prefix[y+radius+1]-prefix[y-radius]==0;
    }
    private static double clamp(double x,double low,double high) {
        return Math.max(low,Math.min(high,x));
    }
}
