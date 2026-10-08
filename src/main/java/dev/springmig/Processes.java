package dev.springmig;

import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

final class Processes {
    record Result(List<String> argv, String cwd, int exitCode, boolean timedOut, long elapsedMs,
                  String log, long bytesObserved, boolean logTruncated, boolean cleanupComplete) {}
    static final class Interrupted extends InterruptedException {
        final Result result;
        Interrupted(Result result) {super("Child process interrupted; cleanup="+result.cleanupComplete());this.result=result;}
    }
    static final int LOG_LIMIT=1024*1024;
    static ProcessBuilder builder(List<String> argv,Path cwd) {
        var builder=new ProcessBuilder(argv).directory(cwd.toFile()).redirectErrorStream(true);
        for(String key:List.of("JAVA_TOOL_OPTIONS","_JAVA_OPTIONS","JDK_JAVA_OPTIONS","MAVEN_OPTS","MAVEN_ARGS")) builder.environment().remove(key);
        return builder;
    }
    static Result run(List<String> argv,Path cwd,Path log,Duration timeout) throws Exception {
        Files.createDirectories(log.getParent());long started=System.nanoTime();
        Process process=builder(argv,cwd).start();
        long[] observed={0}; IOException[] readFailure={null};
        var reader=new Thread(()->{
            try(var in=process.getInputStream();var out=Files.newOutputStream(log)) {
                byte[] buffer=new byte[8192];int count;
                while((count=in.read(buffer))!=-1) {
                    int writable=(int)Math.max(0,Math.min(count,LOG_LIMIT-observed[0]));
                    if(writable>0)out.write(buffer,0,writable);observed[0]+=count;
                }
            } catch(IOException e) {readFailure[0]=e;}
        },"springmig-log-drain");reader.setDaemon(true);reader.start();
        boolean done=false,interrupted=false,cleaned;
        try {done=process.waitFor(Math.max(1,timeout.toMillis()),TimeUnit.MILLISECONDS);}
        catch(InterruptedException ex) {interrupted=true;}
        finally {
            // Cleanup also runs on thread interruption; clear it until child/drain joins finish.
            Thread.interrupted();cleaned=cleanup(process);
            try {reader.join(5000);if(reader.isAlive()) {process.getInputStream().close();reader.join(1000);cleaned=false;}}
            catch(InterruptedException ex) {interrupted=true;Thread.interrupted();}
        }
        var result=new Result(List.copyOf(argv),cwd.toString(),done?process.exitValue():-1,!done && !interrupted,
            Duration.ofNanos(System.nanoTime()-started).toMillis(),log.toString(),observed[0],observed[0]>LOG_LIMIT,cleaned);
        if(interrupted) {Thread.currentThread().interrupt();throw new Interrupted(result);}
        if(readFailure[0]!=null && done)throw readFailure[0];
        return result;
    }
    static boolean cleanup(Process process) {
        var children=process.descendants().toList();children.forEach(ProcessHandle::destroyForcibly);
        if(process.isAlive())process.destroyForcibly();
        long end=System.nanoTime()+Duration.ofSeconds(5).toNanos();
        while((process.isAlive() || children.stream().anyMatch(ProcessHandle::isAlive)) && System.nanoTime()<end) {
            try {Thread.sleep(10);}catch(InterruptedException ex) {Thread.interrupted();}
        }
        return !process.isAlive() && children.stream().noneMatch(ProcessHandle::isAlive);
    }
    static List<String> javaCommand() {
        return new ArrayList<>(List.of(Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString(),"-Xmx2g"));
    }
    static List<String> maven(Path repo,Path project,List<String> goals) throws IOException {
        return maven(repo,project,goals,false,repo.resolve("work/m2"));
    }
    static List<String> maven(Path repo,Path project,List<String> goals,boolean offline,Path cache) throws IOException {
        Path home=repo.resolve("work/tools/apache-maven-3.9.11");
        if(!Files.isDirectory(home)) {
            Path wrapper=repo.resolve("work/maven-home/wrapper/dists");
            if(Files.exists(wrapper))try(var paths=Files.walk(wrapper)) {home=paths.filter(p->p.getFileName().toString().equals("apache-maven-3.9.11")&&Files.isDirectory(p)).findFirst().orElse(home);}
        }
        if(!Files.isDirectory(home))throw new IOException("Pinned Maven unavailable: run mvnw.cmd --version first");
        Path boot;
        try(var files=Files.list(home.resolve("boot"))) {boot=files.filter(p->p.getFileName().toString().startsWith("plexus-classworlds-")&&p.toString().endsWith(".jar")).findFirst().orElseThrow();}
        var argv=javaCommand();
        argv.addAll(List.of("-Dmaven.home="+home,"-Dmaven.conf="+home.resolve("conf"),"-Dclassworlds.conf="+home.resolve("bin/m2.conf"),"-Dmaven.multiModuleProjectDirectory="+project,
            "-classpath",boot.toString(),"org.codehaus.plexus.classworlds.launcher.Launcher","-B","-ntp","-gs",repo.resolve(".mvn/settings.xml").toString(),"-s",repo.resolve(".mvn/settings.xml").toString(),
            "-Dmaven.repo.local="+cache.toAbsolutePath().normalize(),"-f",project.resolve("pom.xml").toString()));
        if(offline)argv.add("-o");
        argv.addAll(goals);return argv;
    }
}
