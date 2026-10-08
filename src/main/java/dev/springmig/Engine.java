package dev.springmig;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

final class Engine {
    private static final class Stop extends Exception {
        final String state;final int exit;
        Stop(String state,int exit) {super(state);this.state=state;this.exit=exit;}
    }
    record Options(boolean offline,Path cache,Path replay,int proposals,int actions,int commandSeconds,int runSeconds) {
        static Options defaults(Path repo) {return new Options(false,repo.resolve("work/m2"),null,3,20,180,900);}
        Options {if(proposals<1 || proposals>3 || actions<1 || actions>20)throw new IllegalArgumentException("Proposal/action bounds: 1..3 and 1..20");new RunBudget(runSeconds,commandSeconds);}
    }
    static final String TARGET_ID="boot-2.7.18-to-3.0.13";
    private final Path repo;
    interface BeforeFinal {void run(Path candidate) throws Exception;}
    private final BeforeFinal beforeFinal;
    Engine(Path repo) {this(repo,candidate->{});}
    // Package-private fault injection for the manually executed M2 verifier probe.
    Engine(Path repo,BeforeFinal beforeFinal) {this.repo=repo.toAbsolutePath().normalize();this.beforeFinal=beforeFinal;}
    int run(Path input,Path contract,String mode,Path out) throws Exception {return run(input,contract,mode,out,Options.defaults(repo));}
    int run(Path input,Path contract,String mode,Path out,Options options) throws Exception {
        if(!Set.of("recipes-only","agent-only","hybrid").contains(mode))throw new IllegalArgumentException("Modes: recipes-only, agent-only, hybrid");
        if(mode.equals("recipes-only") && options.replay()!=null)throw new IllegalArgumentException("recipes-only does not use an agent");
        input=input.toAbsolutePath().normalize();out=out.toAbsolutePath().normalize();contract=contract.toAbsolutePath().normalize();
        if(!Files.isDirectory(input) || Files.exists(out) || out.startsWith(input) || input.startsWith(out) || contract.startsWith(out) || out.startsWith(contract.getParent()))throw new IllegalArgumentException("Input must exist; output must be new and outside input/contract");
        PomPolicy.preflight(input);
        Map<String,String> originalHashes=Trees.hashes(input),contractHashes=Trees.hashes(contract.getParent());String originalContractHash=Trees.sha(contract);
        Files.createDirectories(out);Path baseline=out.resolve("baseline"),candidate=out.resolve("candidate"),logs=out.resolve("logs");
        Trees.copyProject(input,baseline);Trees.copyProject(baseline,candidate);Map<String,String> baselineHashes=Trees.hashes(baseline);
        var report=new LinkedHashMap<String,Object>();var commands=new ArrayList<Processes.Result>();var stages=new ArrayList<Verifier.Result>();var proposals=new ArrayList<EditingAgent.Proposal>();
        String recipeHash=Trees.sha(repo.resolve("recipes/mvc-validation.yml"));
        report.put("schema","springmig.report/v2");report.put("target",TARGET_ID);report.put("historicalTargetUnsupported",true);report.put("mode",mode);
        report.put("agentKind",mode.equals("recipes-only")?"none":options.replay()==null?"scripted_mock":"recorded_mock_replay");report.put("liveModelCalls",0);
        report.put("input",input.toString());report.put("snapshotSha256",Trees.aggregate(baselineHashes));report.put("sourceManifest",baselineHashes);report.put("contractSha256",originalContractHash);
        report.put("recipeName","springmig.recipes.MvcValidationBoot3_0_13");report.put("recipeRevision",1);report.put("recipeSha256",recipeHash);report.put("rewritePluginVersion","5.46.0");report.put("rewriteBomVersion","8.41.0");
        report.put("javaVersion",System.getProperty("java.version"));report.put("mavenVersion","3.9.11");report.put("offline",options.offline());report.put("cache",options.cache().toAbsolutePath().normalize().toString());report.put("startedAt",Instant.now().toString());
        report.put("commands",commands);report.put("stages",stages);report.put("repairProposals",proposals);report.put("recipeExecuted",false);
        report.put("budgets",Map.of("repairProposals",options.proposals(),"toolActions",options.actions(),"commandSeconds",options.commandSeconds(),"jvmMaxHeap","2g","runSeconds",options.runSeconds()));
        var budget=new RunBudget(options.runSeconds(),options.commandSeconds());int exit=2;String state="verification_failed";Verifier verifier=null;String baseTree=null,recipeTree=null;Transcript transcript=null;EditingAgent.Gateway gateway=null;
        Thread runner=Thread.currentThread(),hook=new Thread(()->{runner.interrupt();try {runner.join(15000);}catch(InterruptedException ignored) {Thread.currentThread().interrupt();}},"springmig-shutdown-cleanup");
        Runtime.getRuntime().addShutdownHook(hook);
        boolean interrupted=false;
        try {
            command(commands,List.of("git","init","--quiet"),candidate,logs.resolve("git-init.log"),budget);
            command(commands,List.of("git","config","core.autocrlf","false"),candidate,logs.resolve("git-config.log"),budget);
            command(commands,List.of("git","add","--","pom.xml","src"),candidate,logs.resolve("git-add-baseline.log"),budget);
            baseTree=gitTree(commands,candidate,logs.resolve("git-baseline-tree.log"),budget);
            verifier=new Verifier(repo,baseline,out.resolve("protected"),out.resolve("verifications"),contract,budget,options.offline(),options.cache());
            report.put("protectedManifest",verifier.protectedHashes());
            var baselineResult=verifier.verify(candidate,"baseline",PomPolicy.SOURCE);stages.add(baselineResult);infrastructure(baselineResult,budget);
            if(!baselineResult.passed())throw new Stop("baseline_failed",7);
            Verifier.Result after;
            if(!mode.equals("agent-only")) {
                var recipe=command(commands,Processes.maven(repo,candidate,recipeArgs(),options.offline(),options.cache()),candidate,logs.resolve("rewrite.log"),budget);
                report.put("recipeExecuted",true);report.put("recipeExitCode",recipe.exitCode());
                if(recipe.timedOut()) {budget.check();throw new Stop("command_timeout",4);}
                if(recipe.exitCode()!=0)throw new Stop(options.offline()?"offline_tool_failed":"recipe_tool_failed",4);
                assertCandidate(baseline,candidate);patch(commands,candidate,baseTree,out.resolve("recipe.patch"));
                command(commands,List.of("git","add","--","pom.xml","src"),candidate,logs.resolve("git-add-recipe.log"),budget);recipeTree=gitTree(commands,candidate,logs.resolve("git-recipe-tree.log"),budget);
                after=verifier.verify(candidate,"post-recipe",PomPolicy.TARGET);stages.add(after);infrastructure(after,budget);
            }else {
                recipeTree=baseTree;Files.writeString(out.resolve("recipe.patch"),"");
                // Target compilation exposes the unmigrated namespace to agent-only diagnostics.
                after=verifier.verify(candidate,"agent-start",PomPolicy.TARGET);stages.add(after);infrastructure(after,budget);
            }
            String stop="verified";
            if(!mode.equals("recipes-only")) {
                transcript=new Transcript(options.replay());gateway=new EditingAgent.Gateway(candidate,baseline,options.actions());
                final Verifier active=verifier;
                var result=RepairController.run(mode,recipeHash,originalContractHash,candidate,after,new EditingAgent.MockAgent(),gateway,transcript,budget,options.proposals(),attempt->{
                    assertCandidate(baseline,candidate);var checked=active.verify(candidate,"repair-"+attempt,PomPolicy.TARGET);stages.add(checked);infrastructure(checked,budget);return checked;
                },proposals);
                stop=result.stopReason();report.put("repairAttempts",result.attempts());report.put("repairStopReason",stop);transcript.complete();
            }
            if(recipeTree!=null)patch(commands,candidate,recipeTree,out.resolve("agent.patch"));
            beforeFinal.run(candidate);
            assertCandidate(baseline,candidate);
            var finalResult=verifier.verify(candidate,"final",PomPolicy.TARGET);stages.add(finalResult);infrastructure(finalResult,budget);
            if(finalResult.passed()) {state="verified";exit=0;}
            else if(stop.equals("proposal_budget_exhausted")) {state="budget_exhausted";exit=5;}
        }catch(Stop stop) {state=stop.state;exit=stop.exit;}
        catch(InterruptedException ex) {state="interrupted";exit=8;interrupted=true;Thread.interrupted();if(ex instanceof Processes.Interrupted process)report.put("interruptedProcess",process.result);report.put("error",ex.getMessage());}
        catch(RunBudget.Exhausted | EditingAgent.ActionBudget ex) {state="budget_exhausted";exit=5;report.put("error",ex.getMessage());}
        catch(Exception ex) {state=ex instanceof EditingAgent.Denied || ex instanceof IllegalArgumentException?"policy_denied":"tool_failed";exit=state.equals("policy_denied")?6:4;report.put("error",ex.getClass().getSimpleName()+": "+ex.getMessage());}
        finally {
            interrupted|=Thread.interrupted();
            boolean originalIntact=originalHashes.equals(Trees.hashes(input)),contractIntact=contractHashes.equals(Trees.hashes(contract.getParent())) && originalContractHash.equals(Trees.sha(contract)),protectedIntact=verifier==null || verifier.intact();
            if(!originalIntact || !contractIntact || !protectedIntact) {state="integrity_failed";exit=6;}
            if(baseTree!=null)try {patch(commands,candidate,baseTree,out.resolve("migration.patch"));}catch(Exception ex) {report.put("patchError",ex.getMessage());}
            if(transcript!=null)try {
                transcript.write(out.resolve("mock-transcript.json"));report.put("agentRequests",transcript.entries());report.putIfAbsent("repairAttempts",transcript.entries().size());
                report.put("unacceptedProposals",transcript.entries().stream().map(Transcript.Entry::proposal).filter(Objects::nonNull).filter(p->!proposals.contains(p)).toList());
            }catch(Exception ex) {report.put("transcriptError",ex.getMessage());}
            report.put("toolActions",gateway==null?0:gateway.actions());report.put("finalCandidateSha256",Trees.aggregate(Trees.hashes(candidate)));report.put("status",state);report.put("exitCode",exit);
            report.put("originalInputIntact",originalIntact);report.put("originalContractIntact",contractIntact);report.put("protectedVerificationIntact",protectedIntact);report.put("endedAt",Instant.now().toString());
            report.put("limitations",List.of("Historical unsupported versions; fixture-compatible trusted checkout only","Scripted mock or recorded mock replay; zero live model calls","Fresh verification trees and edit policy are not an OS security sandbox","Multi-file commits use validated staging and rollback, not filesystem-wide transaction isolation","Forceful OS termination cannot guarantee a final report; graceful JVM shutdown interrupts and cleans up"));
            Json.write(out.resolve("report.json"),report);writeMarkdown(out,report,stages,proposals);
            StringBuilder events=new StringBuilder();for(var stage:stages)events.append(new com.google.gson.Gson().toJson(Map.of("schema","springmig.event/v1","event","verification","stage",stage))).append('\n');Files.writeString(out.resolve("events.jsonl"),events);
            try {Runtime.getRuntime().removeShutdownHook(hook);}catch(IllegalStateException ignored) {}
            System.out.println(mode+": "+state+"; snapshot="+report.get("snapshotSha256")+"; proposals="+proposals.size()+"; report="+out.resolve("report.json"));
            if(interrupted)Thread.currentThread().interrupt();
        }
        return exit;
    }
    private List<String> recipeArgs() {return List.of("org.openrewrite.maven:rewrite-maven-plugin:5.46.0:run","-Drewrite.configLocation="+repo.resolve("recipes/mvc-validation.yml"),"-Drewrite.activeRecipes=springmig.recipes.MvcValidationBoot3_0_13","-Drewrite.exportDatatables=true");}
    private static void infrastructure(Verifier.Result result,RunBudget budget) throws Exception {
        budget.check();if("offline_cache_miss".equals(result.error()))throw new Stop("offline_cache_miss",4);if(result.build().timedOut())throw new Stop("command_timeout",4);
        if(!result.build().cleanupComplete() || !result.appCleanupComplete())throw new Stop("cleanup_failed",4);
    }
    private static Processes.Result command(List<Processes.Result> commands,List<String> argv,Path cwd,Path log,RunBudget budget) throws Exception {
        try {var result=Processes.run(argv,cwd,log,budget.command());commands.add(result);budget.check();return result;}
        catch(Processes.Interrupted ex) {commands.add(ex.result);throw ex;}
    }
    private static String gitTree(List<Processes.Result> commands,Path candidate,Path log,RunBudget budget) throws Exception {
        var result=command(commands,List.of("git","write-tree"),candidate,log,budget);if(result.exitCode()!=0)throw new IOException("git write-tree failed");return Files.readString(log).trim();
    }
    private static void patch(List<Processes.Result> commands,Path candidate,String tree,Path out) throws Exception {
        var result=Processes.run(List.of("git","diff","--no-color","--no-ext-diff","--no-textconv","--binary",tree,"--","pom.xml","src"),candidate,out,Duration.ofSeconds(5));commands.add(result);
        if(result.exitCode()!=0 || result.logTruncated())throw new IOException("Patch generation failed/truncated");
    }
    static void assertCandidate(Path baseline,Path candidate) throws Exception {
        Map<String,String> before=Trees.hashes(baseline),after=Trees.hashes(candidate);Set<String> paths=new TreeSet<>(before.keySet());paths.addAll(after.keySet());
        for(String path:paths)if(!Objects.equals(before.get(path),after.get(path))) {
            if(!path.equals("pom.xml") && !path.equals("src/main/resources/application.properties") && !(path.startsWith("src/main/java/")&&path.endsWith(".java")))throw new IllegalArgumentException("Candidate edited protected/unsupported file: "+path);
            if(before.get(path)==null || after.get(path)==null)throw new IllegalArgumentException("File additions/deletions denied");Trees.allowedEdit(candidate,path);
        }
        PomPolicy.validateCandidate(Files.readString(baseline.resolve("pom.xml")),Files.readString(candidate.resolve("pom.xml")));
        String old=Files.readString(baseline.resolve("src/main/resources/application.properties")),current=Files.readString(candidate.resolve("src/main/resources/application.properties"));
        if(!current.equals(old) && !current.equals(old.replace("server.max-http-header-size=","server.max-http-request-header-size=")))throw new IllegalArgumentException("Unsupported properties change");
    }
    private static void writeMarkdown(Path out,Map<String,Object> report,List<Verifier.Result> stages,List<EditingAgent.Proposal> proposals) throws IOException {
        StringBuilder text=new StringBuilder("# Migration evidence\n\nHistorical unsupported Boot 2.7.18 -> 3.0.13 fixture. "+report.get("mode")+"; agent: "+report.get("agentKind")+". No live model calls.\n\nStatus: **"+report.get("status")+"**.\n\n");
        text.append("| Stage | Maven/tests | Tests run | HTTP checks passed | Overall |\n| --- | --- | --- | --- | --- |\n");
        for(var stage:stages)text.append("| ").append(stage.stage()).append(" | ").append(stage.buildPassed()).append(" | ").append(stage.testsRun()).append(" | ").append(stage.checks().stream().filter(Verifier.Observation::passed).count()).append('/').append(stage.checks().size()).append(" | ").append(stage.passed()).append(" |\n");
        text.append("\nAccepted proposals: ").append(proposals.size()).append(". Original input intact: ").append(report.get("originalInputIntact")).append("; protected verifier intact: ").append(report.get("protectedVerificationIntact")).append(".\n\nSee report.json for argv, hashes, bounded logs and observations. Maven builds, HTTP requests and recipes are real; agent execution is mock/replay.\n");Files.writeString(out.resolve("report.md"),text);
    }
}
