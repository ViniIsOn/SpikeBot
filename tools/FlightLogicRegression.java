package br.com.poderazul.spikesbot;

/**
 * Verifies flight decisions against measured states from the user's black box
 * and earlier regression scenarios. Does not claim real-world invincibility.
 */
public final class FlightLogicRegression {
    static void require(boolean ok,String name) {
        if(!ok)throw new AssertionError(name);
    }
    static int[] bands(int[][] ranges) {
        int[] result=new int[484];
        for(int[] span:ranges)
            for(int y=span[0];y<=span[1];y++)result[y]=1;
        return result;
    }
    static FlightLogic.Plan plan(double x,double y,double vx,double vy,
                                 int direction,int[][] spikes,long since) {
        return FlightLogic.choose(216,484,x,y,vx,vy,direction,
                bands(spikes),89,392,65,since);
    }
    public static void main(String[] args) {
        var empty=new int[][]{};
        var falling=plan(100,344,135,185,1,empty,700);
        var rising=plan(100,214,135,-230,1,empty,700);
        var recent=plan(100,344,135,185,1,empty,100);
        var centre=plan(100,228,135,0,1,empty,700);
        require(falling.jump,"prevenir colisao no chao");
        require(!rising.jump,"nao tocar durante subida rapida");
        require(!recent.jump,"respeitar intervalo do toque");
        require(centre.hasCorridor,"reconhecer centro seguro");

        // Recorded 35.098s: x=186, y=308, approaching the LEFT wall.
        // The actual left-wall spike bands came from quadro_0000035098.jpg.
        int[][] left={{251,258},{278,284},{330,337},{357,363}};
        var startClimb=plan(186,308,-135,190,-1,left,700);
        var continueClimb=plan(120,270,-135,110,-1,left,700);
        require(startClimb.hasCorridor && startClimb.target<215,
                "abrir passagem superior na parede esquerda");
        require(startClimb.jump,
                "iniciar subida com varios pulos ainda longe da parede");
        require(continueClimb.jump,
                "corrigir altura antes da chegada");

        // Earlier real wall: separate narrow spikes at rows 197..206,
        // 223..233 and 276..286. The gap between them is valid.
        int[][] right={{197,206},{223,233},{276,286}};
        var narrow=plan(107,265,135,50,1,right,700);
        // This apparent narrow gap is NOT large enough for the bird and
        // its reaction margin. Select a reachable wider corridor instead.
        require(narrow.hasCorridor &&
                (narrow.target<180||narrow.target>307),
                "nao escolher abertura estreita menor que o passaro");

        // After a fresh tap the bot must NOT queue another instantly.
        var noDouble=plan(186,308,-135,190,-1,left,55);
        require(!noDouble.jump,"nao acumular toques");
        // v2.2 real black-box timeline at end of 8-point match:
        // 40.953s Y196, 41.122s Y250 (falling ~320px/s, WAIT),
        // 41.293s Y288 (falling, SUBIR), 41.480s Y376 (near floor).
        require(FlightLogic.needsFloorRescue(484,250,320,1000),
                "v2.3: rescue fast fall at Y250, not at Y288");
        var firstRescue=plan(23,250,-135,320,-1,empty,1000);
        require(firstRescue.jump && firstRescue.firstTapDelayMs==0,
                "v2.3: command immediate flap before floor danger");
        require(!FlightLogic.needsFloorRescue(484,196,168,1000),
                "do not flap prematurely during moderate descent");
        require(!FlightLogic.needsFloorRescue(484,250,-160,1000),
                "never mistake ascent for falling");
        require(!FlightLogic.needsFloorRescue(484,250,320,100),
                "do not double-flap after a recent gesture");
        require(!FlightLogic.needsFloorRescue(484,135,320,1000),
                "avoid emergency override near ceiling");
        System.out.println("SpikesBot v2.3: 14 regressoes fisicas passaram");
    }
}
