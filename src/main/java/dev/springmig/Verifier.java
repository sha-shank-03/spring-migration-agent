package dev.springmig;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

final class Verifier {
    record Check(String id,String method,String path,String body,int expectedStatus,String expectedBody) {}
    record Contract(String schema,List<Check> checks) {}
    record Observation(String id,int expectedStatus,Integer actualStatus,String expectedBody,String actualBody,boolean passed,String error) {}
    record Result(String stage,String version,boolean buildPassed,int testsRun,List<Observation> checks,
                  boolean passed,Processes.Result build,String workspace,boolean protectedIntegrity,
                  String appLog,boolean appCleanupComplete,String error) {}
    private final Path repo,baseline,protectedRoot,verificationRoot,contractFile;
    private final Map<String,String> protectedHashes;
    private final String baselinePom;
    private final RunBudget budget;
    private final boolean offline;
    private final Path cache;
    private int sequence;
    Verifier(Path repo,Path baseline,Path protectedRoot,Path verificationRoot,Path originalContract) throws Exception {
        this(repo,baseline,protectedRoot,verificationRoot,originalContract,new RunBudget(900,180),false,repo.resolve("work/m2"));
    }
    Verifier(Path repo,Path baseline,Path protectedRoot,Path verificationRoot,Path originalContract,RunBudget budget,boolean offline,Path cache) throws Exception {
        this.repo=repo;this.baseline=baseline;this.protectedRoot=protectedRoot;this.verificationRoot=verificationRoot;
        this.budget=budget;this.offline=offline;this.cache=cache;
        Files.createDirectories(protectedRoot);
        Trees.copyProject(baseline,protectedRoot.resolve("original"));
        contractFile=protectedRoot.resolve("contract.json");Files.copy(originalContract,contractFile);
        Contract contract=Json.GSON.fromJson(Files.readString(contractFile),Contract.class);
        if(!"springmig.http-contract/v1".equals(contract.schema()) || contract.checks()==null || contract.checks().isEmpty())throw new IllegalArgumentException("Invalid behavior contract");
        for(Check c:contract.checks()) if(!Set.of("GET","POST").contains(c.method()) || c.path()==null || !c.path().startsWith("/") || c.path().contains(":") || c.expectedStatus()<100 || c.expectedStatus()>599)throw new IllegalArgumentException("Invalid HTTP check");
        baselinePom=Files.readString(baseline.resolve("pom.xml"));protectedHashes=Trees.hashes(protectedRoot);
    }
    Map<String,String> protectedHashes() {return protectedHashes;}
    boolean intact() throws IOException {return protectedHashes.equals(Trees.hashes(protectedRoot));}
    Result verify(Path candidate,String stage,String version) throws Exception {
        budget.check();
        if(!intact())throw new IllegalStateException("Protected verification inputs changed");
        Path workspace=verificationRoot.resolve(String.format("%02d-%s",++sequence,stage));
        Trees.copyProject(candidate,workspace,false);
        // Candidate tests and POM never control verification: replace them with host-owned copies.
        Path copiedTests=workspace.resolve("src/test");
        if(Files.exists(copiedTests))throw new IllegalStateException("Verification materialization must exclude candidate tests");
        copyDirectory(protectedRoot.resolve("original/src/test"),workspace.resolve("src/test"));
        Files.writeString(workspace.resolve("pom.xml"),PomPolicy.trustedPom(baselinePom,version));
        var build=Processes.run(Processes.maven(repo,workspace,List.of("clean","verify","-DskipTests=false","-Dmaven.test.skip=false","-DargLine=-Xmx2g"),offline,cache),workspace,workspace.resolve("build.log"),budget.command());
        int tests=testCount(workspace.resolve("target/surefire-reports"));
        boolean buildPassed=build.exitCode()==0 && !build.timedOut() && tests>0;
        if(!buildPassed) {
            String error=build.timedOut()?"command_timeout":"Build/test verification failed; inspect build log";
            String output=Files.readString(Path.of(build.log()));
            if(offline && (output.contains("in offline mode") || output.contains("has not been downloaded")))error="offline_cache_miss";
            return new Result(stage,version,false,tests,List.of(),false,build,workspace.toString(),intact(),null,true,error);
        }
        Path jar=workspace.resolve("target/mvc-validation-1.0.0.jar");
        if(!Files.exists(jar))throw new IOException("Requires mvc-validation-1.0.0.jar; fixture-compatible checkout only");
        budget.check();
        int port;
        try(var socket=new ServerSocket(0,0,InetAddress.getByName("127.0.0.1"))) {port=socket.getLocalPort();}
        Path log=workspace.resolve("app.log");
        var argv=Processes.javaCommand();argv.addAll(List.of("-jar",jar.toString(),"--server.address=127.0.0.1","--server.port="+port));
        Process app=Processes.builder(argv,workspace).start();
        var output=new Thread(()->{try(var in=app.getInputStream();var out=Files.newOutputStream(log)) {
            byte[] buffer=new byte[8192];int count;long copied=0;
            while((count=in.read(buffer))!=-1) {int writable=(int)Math.max(0,Math.min(count,Processes.LOG_LIMIT-copied));if(writable>0)out.write(buffer,0,writable);copied+=count;}
        }catch(IOException ignored){}},"springmig-app-log");output.setDaemon(true);output.start();
        List<Observation> observations=new ArrayList<>();boolean cleanup=false;String error=null;
        try {
            var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
            URI base=URI.create("http://127.0.0.1:"+port);
            long deadline=System.nanoTime()+budget.remaining(30).toNanos();boolean ready=false;
            while(app.isAlive() && System.nanoTime()<deadline) {
                budget.check();
                try {client.send(HttpRequest.newBuilder(base.resolve("/greeting")).timeout(budget.remaining(2)).GET().build(),HttpResponse.BodyHandlers.discarding());ready=true;break;}
                catch(IOException ex) {Thread.sleep(100);}
            }
            if(!ready)throw new IOException("Application did not become reachable within 30 seconds");
            var contract=Json.GSON.fromJson(Files.readString(contractFile),Contract.class);
            for(Check c:contract.checks()) {
                var request=HttpRequest.newBuilder(base.resolve(c.path())).timeout(budget.remaining(5));
                if(c.method().equals("POST"))request.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(c.body()==null?"":c.body()));else request.GET();
                try {
                    var response=client.send(request.build(),HttpResponse.BodyHandlers.ofString());
                    boolean passed=response.statusCode()==c.expectedStatus() && (c.expectedBody()==null || c.expectedBody().equals(response.body()));
                    observations.add(new Observation(c.id(),c.expectedStatus(),response.statusCode(),c.expectedBody(),response.body(),passed,null));
                }catch(IOException ex) {observations.add(new Observation(c.id(),c.expectedStatus(),null,c.expectedBody(),null,false,ex.getMessage()));}
            }
        }catch(InterruptedException | RunBudget.Exhausted ex) {throw ex;}
        catch(Exception ex) {error=ex.getClass().getSimpleName()+": "+ex.getMessage();}
        finally {
            boolean interrupted=Thread.interrupted();cleanup=Processes.cleanup(app);
            try {output.join(2000);if(output.isAlive()) {app.getInputStream().close();output.join(1000);cleanup=false;}}
            catch(InterruptedException ex) {interrupted=true;Thread.interrupted();}
            finally {Json.write(workspace.resolve("app-cleanup.json"),Map.of("pid",app.pid(),"cleanupComplete",cleanup,"interrupted",interrupted));if(interrupted)Thread.currentThread().interrupt();}
        }
        boolean protectedIntact=intact();
        boolean passed=error==null && !observations.isEmpty() && observations.stream().allMatch(Observation::passed) && protectedIntact && cleanup;
        return new Result(stage,version,true,tests,List.copyOf(observations),passed,build,workspace.toString(),protectedIntact,log.toString(),cleanup,error);
    }
    private static void copyDirectory(Path source,Path destination) throws IOException {
        if(!Files.exists(source))return;
        try(var files=Files.walk(source)) {for(Path path:files.filter(Files::isRegularFile).toList()) {Path to=destination.resolve(source.relativize(path));Files.createDirectories(to.getParent());Files.copy(path,to);}}
    }
    private static int testCount(Path reports) throws Exception {
        if(!Files.exists(reports))return 0;int count=0;
        var factory=DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
        try(var files=Files.list(reports)) {for(Path report:files.filter(p->p.getFileName().toString().startsWith("TEST-")&&p.toString().endsWith(".xml")).toList()) {
            var root=factory.newDocumentBuilder().parse(report.toFile()).getDocumentElement();
            if(Integer.parseInt(root.getAttribute("failures"))+Integer.parseInt(root.getAttribute("errors"))>0)return 0;
            count+=Integer.parseInt(root.getAttribute("tests"))-Integer.parseInt(root.getAttribute("skipped"));
        }}return count;
    }
}
