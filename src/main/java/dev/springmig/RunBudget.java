package dev.springmig;

import java.io.IOException;
import java.time.Duration;

final class RunBudget {
    static final class Exhausted extends IOException { Exhausted() { super("run_budget_exhausted"); } }
    private final long deadline;
    private final int commandSeconds;
    RunBudget(int runSeconds,int commandSeconds) {
        if(runSeconds<1 || runSeconds>900 || commandSeconds<1 || commandSeconds>180)throw new IllegalArgumentException("Budgets must be 1..900 run seconds and 1..180 command seconds");
        deadline=System.nanoTime()+Duration.ofSeconds(runSeconds).toNanos();this.commandSeconds=commandSeconds;
    }
    void check() throws IOException,InterruptedException {
        if(Thread.currentThread().isInterrupted())throw new InterruptedException("run_interrupted");
        if(System.nanoTime()>=deadline)throw new Exhausted();
    }
    Duration remaining(int capSeconds) throws IOException,InterruptedException {
        check();return Duration.ofNanos(Math.max(1,Math.min(Duration.ofSeconds(capSeconds).toNanos(),deadline-System.nanoTime())));
    }
    Duration command() throws IOException,InterruptedException { return remaining(commandSeconds); }
}
