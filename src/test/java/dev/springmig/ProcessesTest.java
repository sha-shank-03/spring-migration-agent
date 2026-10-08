package dev.springmig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class ProcessesTest {
    @TempDir Path temp;
    List<String> argv(String mode) {var command=Processes.javaCommand();command.addAll(List.of("-cp",Path.of("target/test-classes").toAbsolutePath()+java.io.File.pathSeparator+Path.of("target/classes").toAbsolutePath(),ProcessChild.class.getName(),mode));return command;}
    void assertGone(Path log) throws Exception {for(String line:Files.readAllLines(log))if(line.matches("(pid|child)=\\d+"))assertFalse(ProcessHandle.of(Long.parseLong(line.split("=")[1])).map(ProcessHandle::isAlive).orElse(false),line);}
    @Test void timeoutKillsControlledChildAndGrandchildWithSpaceInPath() throws Exception {
        Path cwd=temp.resolve("path with spaces");Files.createDirectories(cwd);Path log=cwd.resolve("child.log");
        var result=Processes.run(argv("parent"),cwd,log,Duration.ofMillis(2500));assertTrue(result.timedOut());assertTrue(result.cleanupComplete());assertTrue(Files.readString(log).contains("child="));assertGone(log);
    }
    @Test void interruptionKillsChildrenAndPreservesReceipt() throws Exception {
        Path log=temp.resolve("interrupt.log");var failure=new AtomicReference<Throwable>();var receipt=new AtomicReference<Processes.Result>();
        Thread worker=new Thread(()->{try {Processes.run(argv("parent"),temp,log,Duration.ofSeconds(30));}catch(Processes.Interrupted ex) {receipt.set(ex.result);}catch(Throwable ex) {failure.set(ex);}});worker.start();
        long deadline=System.nanoTime()+Duration.ofSeconds(5).toNanos();while((!Files.exists(log) || !Files.readString(log).contains("child=")) && System.nanoTime()<deadline)Thread.sleep(20);
        worker.interrupt();worker.join(10000);assertFalse(worker.isAlive());assertNull(failure.get());assertNotNull(receipt.get());assertTrue(receipt.get().cleanupComplete());assertGone(log);
    }
    @Test void outputCaptureRemainsBoundedAndOfflineArgIsActualMavenFlag() throws Exception {
        Path log=temp.resolve("bounded.log");var result=Processes.run(argv("log"),temp,log,Duration.ofSeconds(10));assertEquals(0,result.exitCode());assertTrue(result.logTruncated());assertEquals(Processes.LOG_LIMIT,Files.size(log));assertEquals(2L*Processes.LOG_LIMIT,result.bytesObserved());
        var args=Processes.maven(Path.of(".").toAbsolutePath(),temp,List.of("verify"),true,Path.of("work/m2"));assertTrue(args.contains("-o"));assertTrue(args.contains("-Xmx2g"));
    }
}
