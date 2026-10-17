package br.com.poderazul.spikesbot;

/**
 * Pure-Java flight planner. Every suggestion comes from a detected live bird,
 * its observed motion, and the spike-free gap of the approaching wall.
 * There is no fixed number of taps and no background automation.
 */
final class FlightLogic {
    static final double GRAVITY_PER_HEIGHT = 0.98;
    static final double FLAP_SPEED_PER_HEIGHT = -0.41;

    static final class Plan {
        final boolean hasCorridor;
        final boolean jump;
        final int target;
        final double noTapY;
        final double tapY;
        final double wallSeconds;
        final String reason;
        Plan(boolean found, boolean jump, int target,
             double noTapY, double tapY, double eta, String reason) {
            this.hasCorridor=found; this.jump=jump; this.target=target;
            this.noTapY=noTapY; this.tapY=tapY;
            this.wallSeconds=eta; this.reason=reason;
        }
    }

    static Plan choose(int w,int h,double x,double y,double vx,double vy,
                       int direction,int[] spikes,int scanTop,int scanBottom,
                       int safety,long sinceLastTapMs) {
        double gravity=h*GRAVITY_PER_HEIGHT;
        double impulse=h*FLAP_SPEED_PER_HEIGHT;

        // Calibrated from user's real CSV: 12 untouched flights measured
        // approximately 96-159 pixels/sec across a 216px field (median 135).
        double fallback=w*.625;
        double speed=Math.abs(vx);
        if(speed<w*.32) speed=fallback;
        speed=Math.max(w*.43, Math.min(w*.88, speed));
        double remaining=direction>0?(w*.92-x):(x-w*.08);
        double eta=Math.max(.035, Math.min(1.05,remaining/speed));
        // We can correct again before reaching a distant wall. For a
        // decision NOW only forecast the upcoming 0.34 seconds.
        double horizon=Math.max(.13, Math.min(.34,eta));
        double noTapY=y+vy*horizon+.5*gravity*horizon*horizon;
        double tapY=y+impulse*horizon+.5*gravity*horizon*horizon;
        int top=(int)(h*.24), bottom=(int)(h*.76);
        int center=(int)(h*.47);
        int radius=Math.max(12,(int)(h*(.022+Math.max(0,Math.min(100,safety))*.00014)));

        // Identify a single, action-independent target inside a gap which
        // accommodates the entire bird, with extra margin for reaction time.
        int[] prefix=new int[h+1];
        for(int i=0;i<h;i++)prefix[i+1]=prefix[i]+spikes[i];
        int target=-1;
        double best=Double.POSITIVE_INFINITY;
        for(int candidate=top+radius/3;candidate<=bottom-radius/3;candidate+=2){
            if(!clearAt(prefix,candidate,radius,scanTop,scanBottom)) continue;
            // Center preference, safe-gap depth, and reachability.
            // Avoid picking the exact no-tap projection as a moving target.
            int wideRadius=radius+Math.max(5,h/55);
            boolean wide=clearAt(prefix,candidate,wideRadius,scanTop,scanBottom);
            double reachable=Math.min(Math.abs(candidate-noTapY),
                                      Math.abs(candidate-tapY));
            double cost=Math.abs(candidate-center)*.82
                    +reachable*.13+Math.abs(candidate-y)*.09
                    +(wide?0:14);
            if(cost<best){best=cost;target=candidate;}
        }
        if(target<0) {
            return new Plan(false,false,center,noTapY,tapY,eta,"sem corredor");
        }
        // Compare two trajectories against the SAME target and roof/floor.
        // Avoid rewarding "wait" for having chosen a perfect wait-only target.
        double waitRisk=trajectoryRisk(y,vy,gravity,horizon,top,bottom);
        double tapRisk=trajectoryRisk(y,impulse,gravity,horizon,top,bottom);
        if(eta < .34) {
            // Evaluate imminent contact with the wall using the real margin.
            // Only compare a tap if it can still affect the crossing.
            double atWallWait=y+vy*eta+.5*gravity*eta*eta;
            double atWallTap=y+impulse*eta+.5*gravity*eta*eta;
            if(!clearAt(prefix,(int)Math.round(atWallWait),radius,scanTop,scanBottom))
                waitRisk+=150;
            if(!clearAt(prefix,(int)Math.round(atWallTap),radius,scanTop,scanBottom))
                tapRisk+=150;
        }
        double waitCost=Math.abs(noTapY-target)*1.2+waitRisk;
        double tapCost=Math.abs(tapY-target)*1.2+tapRisk+11;
        boolean ceilingThreat=y<top+h*.048;
        boolean risingHard=vy< -h*.22;
        boolean impendingFloor=y+vy*.19+.5*gravity*.19*.19>bottom-h*.055;
        boolean wantsTap=tapCost+6 < waitCost;
        if(impendingFloor && tapRisk < waitRisk) wantsTap=true;
        // Don't undo an upward flap before the newest image shows its effect.
        if(sinceLastTapMs>=0 && sinceLastTapMs<145) wantsTap=false;
        if(ceilingThreat) wantsTap=false;
        if(risingHard && !impendingFloor && waitRisk<160) wantsTap=false;
        return new Plan(true,wantsTap,target,noTapY,tapY,eta,
                wantsTap?"correção prevista":ceilingThreat?"teto próximo":
                risingHard?"subindo":"altura acompanhada");
    }

    private static boolean clearAt(int[] prefix,int y,int radius,
                                    int top,int bottom) {
        if(y<top+radius||y>=bottom-radius)return false;
        int start=y-radius, finish=y+radius+1;
        if(start<0||finish>=prefix.length)return false;
        return prefix[finish]-prefix[start]==0;
    }

    private static double trajectoryRisk(double y,double speed,double g,
                                          double time,int top,int bottom) {
        double risk=0;
        for(int i=1;i<=5;i++) {
            double t=time*i/5.0;
            double position=y+speed*t+.5*g*t*t;
            double dTop=position-(top+11);
            double dBottom=(bottom-11)-position;
            if(dTop<0)risk+=140+(-dTop)*7;
            if(dBottom<0)risk+=140+(-dBottom)*7;
        }
        return risk;
    }
}
