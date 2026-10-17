package br.com.poderazul.spikesbot;

/**
 * Run with a plain JDK before the Android build. Test representative flight
 * states taken from the user's 216x484 capture dimensions.
 */
public final class FlightLogicRegression {
    static FlightLogic.Plan plan(double x,double y,double vx,double vy,long sinceTap) {
        return FlightLogic.choose(216,484,x,y,vx,vy,1,new int[484],90,392,65,sinceTap);
    }
    static void check(boolean pass,String label) {
        if(!pass) throw new AssertionError(label);
    }
    public static void main(String[] ignored) {
        var falling=plan(100,285,135,155,500);
        var climbing=plan(100,215,135,-215,500);
        var ceiling=plan(100,126,135,-60,500);
        var grounded=plan(100,337,135,200,500);
        var recent=plan(100,285,135,155,40);
        var central=plan(105,224,135,-15,500);
        check(falling.hasCorridor && falling.jump,
                "A falling bird above target must receive a new flap");
        check(!climbing.jump,"Don't add flaps while rapidly climbing");
        check(!ceiling.jump,"No flap near ceiling");
        check(grounded.jump,"Anticipate floor collision");
        check(!recent.jump,"No double-flap before prior impulse is visible");
        check(central.hasCorridor,"Safe target must exist in empty wall");
        check(falling.wallSeconds>.4 && falling.wallSeconds<.8,
                "Time to wall must match observed horizontal speed");
        // A wall with a center hazard requires selecting an alternative gap.
        int[] blocked=new int[484];
        for(int i=202;i<264;i++)blocked[i]=1;
        var alternate=FlightLogic.choose(216,484,97,250,136,60,1,
                blocked,90,392,65,500);
        check(alternate.hasCorridor,"Alternative safe gap should be found");
        check(alternate.target<185||alternate.target>280,
                "Target must clear the wall spike band + bird radius");
        System.out.println("7 SpikesBot physics regression tests passed.");
    }
}
