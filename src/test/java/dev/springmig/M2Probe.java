package dev.springmig;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.google.gson.JsonObject;

/** Explicit real-integration probe; not executed by the fast unit suite. */
public class M2Probe {
    public static void main(String[] args) throws Exception {
        Path repo=Path.of(args[1]).toAbsolutePath().normalize(),out=Path.of(args[2]).toAbsolutePath().normalize();
        var options=new Engine.Options(true,repo.resolve("work/m2"),null,3,20,180,900);
        if(args[0].equals("final-failure")) {
            int exit=new Engine(repo,candidate->Files.writeString(candidate.resolve("src/main/java/demo/Application.java"),"deliberately invalid Java after a successful stage"))
                .run(repo.resolve("examples/mechanical-only"),repo.resolve("verification/mechanical-only/contract.json"),"agent-only",out,options);
            JsonObject report=Json.GSON.fromJson(Files.readString(out.resolve("report.json")),JsonObject.class);var stages=report.getAsJsonArray("stages");
            var previous=stages.get(stages.size()-2).getAsJsonObject();var last=stages.get(stages.size()-1).getAsJsonObject();
            if(exit!=2 || !previous.get("passed").getAsBoolean() || last.get("passed").getAsBoolean() || last.get("testsRun").getAsInt()!=0 || previous.get("workspace").equals(last.get("workspace")))throw new AssertionError("Fresh final failure proof failed");
            Json.write(out.resolve("probe-assertions.json"),Map.of("probe","real_final_failure_after_pass","exitCode",exit,"previousStagePassed",true,"finalBuildFailed",true,"freshWorkspaces",true,"finalTestsRun",0,"fault","host test seam inserts invalid Java immediately before final verification"));
        }else if(args[0].equals("interrupt")) {
            var exit=new AtomicInteger(-99);var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
            Thread worker=new Thread(()->{try {exit.set(new Engine(repo).run(repo.resolve("examples/mvc-validation"),repo.resolve("verification/mvc-validation/contract.json"),"hybrid",out,options));}catch(Throwable ex) {failure.set(ex);}},"M2-interrupted-run");worker.start();
            Path build=out.resolve("verifications/01-baseline/build.log");long end=System.nanoTime()+Duration.ofSeconds(15).toNanos();
            while((!Files.exists(build) || Files.size(build)==0) && System.nanoTime()<end)Thread.sleep(20);
            if(!Files.exists(build) || Files.size(build)==0)throw new AssertionError("Baseline child never started");
            worker.interrupt();worker.join(20000);
            if(worker.isAlive() || failure.get()!=null || exit.get()!=8)throw new AssertionError("Interrupted run did not finalize: "+failure.get());
            JsonObject report=Json.GSON.fromJson(Files.readString(out.resolve("report.json")),JsonObject.class);
            if(!report.get("status").getAsString().equals("interrupted") || !report.getAsJsonObject("interruptedProcess").get("cleanupComplete").getAsBoolean() || !report.get("originalInputIntact").getAsBoolean())throw new AssertionError("Interruption evidence failed");
            Json.write(out.resolve("probe-assertions.json"),Map.of("probe","real_interrupted_engine_maven_child","exitCode",8,"cleanupComplete",true,"originalInputIntact",true,"reportWritten",true));
        }else throw new IllegalArgumentException("final-failure or interrupt");
        System.out.println("M2 real probe passed: "+args[0]);
    }
}
