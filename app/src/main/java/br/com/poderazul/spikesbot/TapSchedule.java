package br.com.poderazul.spikesbot;

/**
 * Keeps a proposed tap deadline across successive valid frames.
 * The previous implementation constantly replaced "tap in N ms" with a
 * new relative N on every image; therefore it often procrastinated.
 *
 * No input is issued by this class. The accessibility service still
 * checks that the original game is active and the picture is recent.
 */
final class TapSchedule {
    private long deadline=-1L;
    private long freshAt=-1L;
    private int direction, target;
    private boolean authorized;

    synchronized void reset() {
        deadline=-1;freshAt=-1;direction=0;target=0;authorized=false;
    }
    synchronized void observe(long frameAt,boolean valid,boolean jump,
                              int delayMs,int wallDirection,int newTarget,
                              boolean canTap) {
        if(!valid || !canTap || wallDirection==0) {
            reset(); return;
        }
        boolean newWall=wallDirection!=direction;
        boolean changedLane=target>0 && Math.abs(newTarget-target)>48;
        if(newWall || changedLane)deadline=-1;
        direction=wallDirection;target=newTarget;
        freshAt=frameAt; authorized=true;
        if(jump) {
            deadline=frameAt;return;
        }
        if(delayMs>=0 && delayMs<=550) {
            long suggested=frameAt+delayMs;
            if(deadline<0 || frameAt>deadline+170)
                deadline=suggested;
            else deadline=Math.min(deadline,suggested);
        } else {
            // A fresh model that recommends no flap is a cancellation,
            // not permission to execute an old plan blindly.
            deadline=-1;
        }
    }
    synchronized boolean consumeIfDue(long now) {
        if(!authorized || freshAt<0 || now-freshAt>245 ||
                deadline<0 || now<deadline || now>deadline+210)
            return false;
        deadline=-1;
        return true;
    }
    synchronized long getDeadlineForTest(){return deadline;}
}
