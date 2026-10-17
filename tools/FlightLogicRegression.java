package br.com.poderazul.spikesbot;

/** Real-size 216x484 regression cases, including hazard rows from v1.4 ZIP. */
public final class FlightLogicRegression {
    static FlightLogic.Plan empty(double x,double y,double vx,double vy,long since) {
        return FlightLogic.choose(216,484,x,y,vx,vy,1,new int[484],89,392,65,since);
    }
    static void require(boolean ok,String test) {
        if(!ok)throw new AssertionError(test);
    }
    static int[] bands(int[][] ranges) {
        int[] a=new int[484];
        for(int[] pair:ranges)for(int y=pair[0];y<=pair[1];y++)a[y]=1;
        return a;
    }
    public static void main(String[] args) {
        var floor=empty(100,344,135,185,600);
        var rising=empty(100,214,135,-230,600);
        var roof=empty(100,126,135,-70,600);
        var recent=empty(100,344,135,185,40);
        var center=empty(100,228,135,0,600);
        require(floor.hasCorridor && floor.jump,"fall near floor must be rescued");
        require(!rising.jump,"do not flap while rising fast");
        require(!roof.jump,"never flap near ceiling");
        require(!recent.jump,"avoid a duplicate flap before physics responds");
        require(center.hasCorridor && center.target>=215&&center.target<=250,"center of empty arena");
        require(center.wallSeconds>.6&&center.wallSeconds<.9,"real horizontal speed, not inflated");
        // Right wall from the user's box at 14.386s:
        int[] right=bands(new int[][] {{197,206},{223,233},{276,286}});
        var gap=FlightLogic.choose(216,484,107,265,135,50,1,right,89,392,65,400);
        require(gap.hasCorridor,"find middle gap between real side-spike bands");
        require(gap.target>=250 && gap.target<=259,
                "choose MIDDLE of narrow gap, not y=249 touching its edge");
        // If a spike suddenly fills the center, do not choose its rows.
        int[] blocked=bands(new int[][] {{202,264}});
        var avoid=FlightLogic.choose(216,484,97,250,136,60,1,blocked,89,392,65,500);
        require(avoid.hasCorridor && (avoid.target<185 || avoid.target>285),
                "find a different wall opening when middle is blocked");
        // v1.9 real black-box regressions: do NOT wait until the bird
        // reaches Y=314 when it was already falling at Y=244.
        var beforeFall=empty(115,244,135,212,850);
        require(beforeFall.hasCorridor && beforeFall.jump,
                "rescue descent at y=244 before wall, not at y=314");
        var approaching=empty(76,251,135,144,800);
        require(approaching.jump,
                "react to dangerous falling trajectory before reaching side wall");
        var stillRising=empty(171,168,135,-160,700);
        require(!stillRising.jump,
                "never flap again while climbing near the ceiling");
        var safe=empty(90,215,135,25,700);
        require(!safe.jump,
                "do not overreact to small mid-screen changes");
        var justTapped=empty(115,244,135,212,100);
        require(!justTapped.jump,
                "prevent repeated flaps before the previous one can take effect");
        System.out.println("SpikesBot v2.0: 14 safety and flight checks passed");
    }
}
