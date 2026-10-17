package br.com.poderazul.spikesbot;

/** Receding-horizon and safety regressions on real Android timestamps. */
public final class TapScheduleRegression {
    static void check(boolean b,String message){
        if(!b)throw new AssertionError(message);
    }
    public static void main(String[] args) {
        TapSchedule s=new TapSchedule();
        s.observe(1000,true,false,450,-1,215,true);
        check(s.getDeadlineForTest()==1450,"initial 450ms plan");
        s.observe(1180,true,false,320,-1,218,true);
        check(s.getDeadlineForTest()==1450,
                "do not postpone earlier committed tap at next frame");
        s.observe(1300,true,false,240,-1,218,true);
        check(s.getDeadlineForTest()==1450,"do not postpone by re-planning");
        check(!s.consumeIfDue(1400),"wait until due");
        check(s.consumeIfDue(1450),"execute on committed deadline");
        check(!s.consumeIfDue(1460),"execute only once");

        s.observe(2000,true,false,300,-1,215,true);
        s.observe(2100,true,false,320,1,215,true);
        check(s.getDeadlineForTest()==2420,"change wall resets deadline");
        s.observe(2210,true,false,280,1,345,true);
        check(s.getDeadlineForTest()==2490,"large lane change resets deadline");
        s.observe(2240,false,false,-1,0,0,false);
        check(!s.consumeIfDue(2490),"lost frame cancels pending action");
        s.observe(3000,true,false,100,-1,205,false);
        check(!s.consumeIfDue(3100),"roof/rising disarms queued taps");
        s.observe(3300,true,true,0,-1,205,true);
        check(s.consumeIfDue(3300),"immediate flap works");
        System.out.println("TapSchedule v2.2: 10 tests passed.");
    }
}
