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
        // Reproduce the v1.5 failure: 14 points, target near y=166,
        // right wall with five lower spike bands, 3 consecutive frames.
        int[] lateWall=bands(new int[][] {
            {222,234},{248,260},{275,287},{301,313},{328,339}
        });
        var early=FlightLogic.choose(216,484,69,234,135,170,1,
                lateWall,89,392,65,600);
        var middle=FlightLogic.choose(216,484,115,213,135,-70,1,
                lateWall,89,392,65,420);
        var tooLate=FlightLogic.choose(216,484,153,214,135,3,1,
                lateWall,89,392,65,420);
        require(early.hasCorridor && early.target<200 && early.jump,
                "Start climbing when the safe next-wall lane is high");
        require(middle.jump,
                "Maintain climb while approaching spikes instead of waiting twice");
        require(tooLate.jump,"Correct altitude before right-wall contact");
        var nearRoof=FlightLogic.choose(216,484,153,135,135,-70,1,
                lateWall,89,392,65,420);
        require(!nearRoof.jump,"Protect top spikes while correcting wall approach");
        System.out.println("SpikesBot v1.6: 13 flight and recorded-wall checks passed");
    }
}
