package br.com.poderazul.spikesbot;

/**
 * v2.1: predicts several possible flaps until the NEXT wall.
 * Each new frame re-plans and only the immediate first action can be sent.
 * This is not a fixed auto-clicker pattern.
 */
final class FlightLogic {
    static final double GRAVITY_PER_HEIGHT = 0.98;
    static final double FLAP_SPEED_PER_HEIGHT = -0.41;
    private static final double[] FIRST = {0, .08, .15, .23, .32, .45, .62};
    private static final double[] PERIOD = {.22, .26, .30, .36, .44, .55};

    static final class Plan {
        final boolean hasCorridor, jump;
        final int target;
        final double noTapY, tapY, wallSeconds;
        final int firstTapDelayMs;
        final boolean canTap;
        final String reason;
        Plan(boolean found,boolean jump,int target,double noTapY,double tapY,
             double seconds,int firstTapDelayMs,boolean canTap,String reason) {
            this.hasCorridor=found; this.jump=jump; this.target=target;
            this.noTapY=noTapY; this.tapY=tapY; this.wallSeconds=seconds;
            this.firstTapDelayMs=firstTapDelayMs;
            this.canTap=canTap;
            this.reason=reason;
        }
    }
    private static final class Candidate {
        double cost=Double.POSITIVE_INFINITY, firstTap=-1;
        int taps;
    }

    static Plan choose(int w,int h,double x,double y,double vx,double vy,
                       int direction,int[] spikes,int scanTop,int scanBottom,
                       int safety,long sinceLastTapMs) {
        double gravity=h*GRAVITY_PER_HEIGHT;
        double impulse=h*FLAP_SPEED_PER_HEIGHT;
        double speed=Math.abs(vx);
        if(speed<w*.32)speed=w*.625;
        speed=clip(speed,w*.43,w*.90);
        double distance=direction>0?w*.92-x:x-w*.08;
        double eta=clip(distance/speed,.035,1.35);
        int top=(int)(h*.24),bottom=(int)(h*.76),center=(int)(h*.47);
        // Approximate body radius + reaction margin.
        int radius=Math.max(16,(int)Math.round(h*
                (.022+clip(safety,0,100)*.00015)));
        int[] prefix=new int[h+1];
        boolean anySpike=false;
        for(int i=0;i<h;i++) {
            prefix[i+1]=prefix[i]+spikes[i];
            if(spikes[i]!=0)anySpike=true;
        }
        int target=center,largestGap=0;
        double bestLane=Double.POSITIVE_INFINITY;
        int from=-1;
        for(int row=top+radius;row<=bottom-radius+1;row++) {
            boolean free=row<=bottom-radius &&
                    isClear(prefix,row,radius,scanTop,scanBottom);
            if(free&&from<0)from=row;
            if(!free&&from>=0){
                int last=row-1,mid=(last+from)/2,gap=last-from+1;
                // A broad opening must also be reachable from CURRENT Y.
                // An unreachable high corridor is worse than a safe lower one.
                double horizon=Math.min(.45,eta);
                double coast=y+vy*horizon+.5*gravity*horizon*horizon;
                double flap=y+impulse*horizon+.5*gravity*horizon*horizon;
                double reachable=Math.min(Math.abs(mid-coast),
                                          Math.abs(mid-flap));
                double edgePenalty =
                        Math.max(0, mid-(bottom-62))*1.6 +
                        Math.max(0,(top+62)-mid)*1.6;
                double score=Math.abs(mid-center)*.52+
                        Math.abs(mid-y)*.30+reachable*.30+
                        edgePenalty + Math.max(0,24-gap)*6;
                if(score<bestLane){bestLane=score;target=mid;}
                largestGap=Math.max(largestGap,gap);
                from=-1;
            }
        }
        if(largestGap==0)
            return new Plan(false,false,center,y,y,eta,-1,false,
                    "sem corredor confirmado");

        double since=sinceLastTapMs<0?999:sinceLastTapMs/1000.0;
        double earliest=Math.max(0,.24-since);
        // A recently dispatched tap may not yet have changed the sprite.
        if(since<.095)
            vy=Math.min(vy,impulse+gravity*since);
        // Typical Android gesture duration in the submitted log ~55ms.
        // Occasionally it is much longer; estimates are uncertain.
        // A gesture may take 55ms normally but near the last collision
        // completions were slower. Account for game response and UI latency.
        final double activationDelay=.125;

        Candidate winner=evaluate(y,vy,gravity,impulse,eta,-1,0,0,
                top,bottom,center,prefix,radius,scanTop,scanBottom,
                target,anySpike,activationDelay);
        for(double initial:FIRST) {
            if(initial+.001<earliest)continue;
            for(double period:PERIOD) {
                for(int count=1;count<=6;count++) {
                    if(initial+(count-1)*period>eta+.01)break;
                    Candidate c=evaluate(y,vy,gravity,impulse,eta,
                            initial,period,count,top,bottom,center,
                            prefix,radius,scanTop,scanBottom,target,
                            anySpike,activationDelay);
                    if(c.cost<winner.cost)winner=c;
                }
            }
        }
        // Dispatch the first action of the best plan only. Re-plan every
        // new frame rather than continuing a preprogrammed tap sequence.
        boolean canTap=!(y<top+27 || (vy< -h*.26 && y<center));
        // An imminent floor danger takes precedence over "wait later".
        double futureFloor=y+vy*.19+.5*gravity*.19*.19;
        boolean floorDanger=futureFloor>bottom-26 && vy>h*.06;
        int delay=winner.taps>0 ? (int)Math.round(winner.firstTap*1000) : -1;
        if(floorDanger && canTap && since>=.22)delay=0;
        // v2.3: the v2.2 black box shows the bird already accelerating
        // down at y=250 (on a 484px field) and "AGUARDAR" was returned.
        // By y=288 the flap was too late. Predict actual floor impact
        // over the next ~240 ms and override the wall planner ONLY when
        // a fast downward motion makes it urgent.
        boolean rescueFall=needsFloorRescue(h,y,vy,sinceLastTapMs);
        if(rescueFall && canTap)delay=0;
        boolean now=delay>=0 && delay<=80 && since>=.225 && canTap;
        double noTapY=y+vy*eta+.5*gravity*eta*eta;
        double tapY=y+impulse*eta+.5*gravity*eta*eta;
        String reason=(rescueFall && now?"RESGATE CHAO (queda rapida), ":"")+
                "trajeto "+winner.taps+" pulos, "+
                (anySpike?"espinhos":"parede livre")+
                ", primeiro="+(delay>=0?delay+"ms":"nenhum")+
                ", custo="+(int)Math.min(99999,winner.cost);
        return new Plan(true,now,target,noTapY,tapY,eta,
                delay,canTap,reason);
    }

    /** Time-to-floor fail-safe. NEVER add taps for gentle drift or ascent. */
    static boolean needsFloorRescue(int h,double birdY,double verticalSpeed,
                                   long sinceLastTapMs) {
        if(h<200 || verticalSpeed<=h*.11 || birdY<h*.44)
            return false;
        // The previous flap needs a chance to change velocity visibly.
        if(sinceLastTapMs>=0 && sinceLastTapMs<245)
            return false;
        final double dt=.24;
        final double g=h*GRAVITY_PER_HEIGHT;
        double predicted=birdY+verticalSpeed*dt+.5*g*dt*dt;
        double safeFloor=h*(.76-.09);
        return predicted>=safeFloor;
    }

    private static Candidate evaluate(double y,double v,double gravity,
                                      double impulse,double eta,double first,
                                      double period,int taps,int top,int bottom,
                                      int center,int[] prefix,int radius,
                                      int scanTop,int scanBottom,int target,
                                      boolean anySpike,double latency) {
        Candidate c=new Candidate();
        c.taps=taps;c.firstTap=first;
        double time=0,cost=0;
        int nextTap=0;
        // Small time steps model the WHOLE path, including multiple flaps,
        // before evaluating clearance when the bird reaches the wall.
        while(time<eta-.00001) {
            double next=Math.min(eta,time+.025);
            if(nextTap<taps) {
                double event=first+nextTap*period+latency;
                if(event<=next+.000001 && event>=time-.000001) {
                    double dt=Math.max(0,event-time);
                    y+=v*dt+.5*gravity*dt*dt;
                    v+=gravity*dt;
                    time=event;
                    v=impulse;
                    nextTap++;
                    continue;
                }
            }
            double dt=next-time;
            y+=v*dt+.5*gravity*dt*dt;
            v+=gravity*dt;
            time=next;
            if(y<top+10)cost+=25000+(top+10-y)*400;
            if(y>bottom-10)cost+=25000+(y-(bottom-10))*400;
            if(y<top+25)cost+=.4*(top+25-y);
            if(y>bottom-25)cost+=.4*(y-(bottom-25));
        }
        int row=(int)Math.round(y);
        if(!isClear(prefix,row,radius,scanTop,scanBottom)) {
            cost+=45000+3*Math.abs(row-target);
        } else {
            if(!isClear(prefix,row,radius+12,scanTop,scanBottom))
                cost+=100;
            cost+=1.4*Math.abs(row-target);
        }
        // Discourage unnecessary taps, but never at expense of a crash.
        cost+=taps*8;
        if(!anySpike)cost+=.12*Math.abs(y-center);
        c.cost=cost;
        return c;
    }

    private static boolean isClear(int[] prefix,int y,int radius,
                                   int scanTop,int scanBottom) {
        if(y-radius<scanTop||y+radius+1>scanBottom)return false;
        if(y-radius<0||y+radius+1>=prefix.length)return false;
        return prefix[y+radius+1]-prefix[y-radius]==0;
    }
    private static double clip(double x,double lo,double hi) {
        return Math.max(lo,Math.min(hi,x));
    }
}
