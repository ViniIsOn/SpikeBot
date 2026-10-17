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

        // v1.6: An imminent wall needs EARLY altitude preparation.
        // In the 14-point flight the bird was at x=69,Y=234, descending,
        // while the next wall's clear lane was Y=166. The old delayed
        // single-flap model WAITED twice and reacted too close to spikes.
        //
        // Use short-range vertical feedback in addition to the rollout:
        // the controller will reconsider after every fresh visual frame.
        double lookAhead=Math.min(.29,Math.max(.19,eta*.48));
        double nearFutureY=y+vy*lookAhead+.5*gravity*lookAhead*lookAhead;
        double altitudeError=nearFutureY-target;
        boolean fallingOrSlowRise=vy>-h*.21;
        boolean highLane=target<center-h*.075;
        boolean wallPreparation=eta<1.25 && y>target+h*.045 &&
                fallingOrSlowRise;
        boolean forecastTooLow=altitudeError>h*(highLane?.022:.041) &&
                fallingOrSlowRise;
        boolean stillAboveFloorSoon=nearFutureY<floor-h*.045;

        if ((wallPreparation || forecastTooLow) && stillAboveFloorSoon) {
            jump=true; // start climbing before the wall, not at its spikes
        }
        // Treat an imminent floor collision as urgent even when an assumed
        // delayed tap had a deceptively good score.
        if(urgentFall && y>center && !ceilingThreat && vy>-h*.14)jump=true;

        // Never fire a second flap while dangerously close to the roof.
        // A fast upward motion is already making progress toward a high lane.
        boolean futureCeilingThreat=y+Math.min(vy,0)*.16+
                .5*gravity*.16*.16 < ceiling+h*.028;
        if(ceilingThreat||futureCeilingThreat)jump=false;
        if(risingFast && !urgentFall)jump=false;

        // Don't double-tap on a stale frame, but reduce the excessive delay
        // from 285ms to 225ms; on the real phone the wall approaches rapidly.
        if(sinceLastTapMs>=0 && sinceLastTapMs<225)jump=false;

        String reason=jump?(wallPreparation?"antecipando parede":
                forecastTooLow?"corrigindo queda antecipadamente":
                "pulo necessario"):
                ceilingThreat?"proximo do teto":
                risingFast?"subindo rapidamente":
                bestDelay>.085 && bestFlapScore+3<waitScore?
                "aguardar momento do proximo pulo":"corrigindo altitude";
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
