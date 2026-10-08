package dev.springmig;

import java.nio.file.*;
import java.util.*;
import com.google.gson.JsonObject;

public final class Main {
    public static void main(String[] args) {
        int exit;try {exit=execute(args);}catch(Exception ex) {System.err.println("unsupported_input/tool_error: "+ex.getMessage());exit=3;}System.exit(exit);
    }
    static int execute(String[] args) throws Exception {
        if(args.length==0 || args[0].equals("help")) {System.out.println("M2: demo|compare|migrate|prepare-cache [--fixture mvc-validation|mechanical-only|unresolved-repair] [--mode recipes-only|agent-only|hybrid] [--agent mock|replay --replay PATH] [--offline] --out NEW_PATH. Historical unsupported Boot 2.7.18 -> 3.0.13 fixture only; zero live model calls. Budgets: --max-proposals 1..3 --max-actions 1..20 --command-seconds 1..180 --run-seconds 1..900.");return 0;}
        Map<String,String> options=new HashMap<>();
        for(int i=1;i<args.length;i++) {
            if(!args[i].startsWith("--"))throw new IllegalArgumentException("Expected unique --option value pairs, or --offline");String key=args[i].substring(2);
            String value=key.equals("offline")?"true":++i<args.length?args[i]:null;
            if(value==null || options.put(key,value)!=null)throw new IllegalArgumentException("Missing/duplicate option: "+key);
        }
        for(String key:options.keySet())if(!Set.of("mode","agent","out","input","contract","target","repo","fixture","offline","cache","replay","max-proposals","max-actions","command-seconds","run-seconds").contains(key))throw new IllegalArgumentException("Unknown option: "+key);
        Path repo=Path.of(options.getOrDefault("repo",".")).toAbsolutePath().normalize();
        if(!Files.exists(repo.resolve("recipes/mvc-validation.yml")))throw new IllegalArgumentException("Run from repository root, or supply --repo");
        String agent=options.getOrDefault("agent","mock");if(!Set.of("mock","replay").contains(agent) || agent.equals("replay")!=options.containsKey("replay"))throw new IllegalArgumentException("Use --agent mock, or --agent replay --replay PATH");
        if(!options.getOrDefault("target",Engine.TARGET_ID).equals(Engine.TARGET_ID))throw new IllegalArgumentException("Unsupported target");
        String fixtureName=options.getOrDefault("fixture","mvc-validation");if(!Set.of("mvc-validation","mechanical-only","unresolved-repair").contains(fixtureName))throw new IllegalArgumentException("Unknown fixture");
        Path fixture=repo.resolve("examples/"+(fixtureName.equals("mechanical-only")?"mechanical-only":"mvc-validation"));
        Path contract=repo.resolve("verification/"+fixtureName+"/contract.json");
        Path cache=options.containsKey("cache")?Path.of(options.get("cache")).toAbsolutePath().normalize():repo.resolve("work/m2");
        if(!cache.startsWith(repo.resolve("work")) || cache.equals(repo.resolve("work")))throw new IllegalArgumentException("Cache must be inside repository work/");
        Path output=Path.of(options.getOrDefault("out","outputs/"+args[0])).toAbsolutePath().normalize();var engine=new Engine(repo);
        var runOptions=new Engine.Options(options.containsKey("offline"),cache,options.containsKey("replay")?Path.of(options.get("replay")):null,
            number(options,"max-proposals",3),number(options,"max-actions",20),number(options,"command-seconds",180),number(options,"run-seconds",900));
        switch(args[0]) {
            case "demo": return engine.run(fixture,contract,options.getOrDefault("mode","hybrid"),output,runOptions);
            case "migrate":
                if(!options.containsKey("input") || !options.containsKey("contract"))throw new IllegalArgumentException("migrate requires --input and external --contract");
                return engine.run(Path.of(options.get("input")),Path.of(options.get("contract")),options.getOrDefault("mode","hybrid"),output,runOptions);
            case "prepare-cache":
                if(options.containsKey("offline") || !agent.equals("mock"))throw new IllegalArgumentException("prepare-cache uses online resolution and fresh mock");
                int prepared=engine.run(repo.resolve("examples/mvc-validation"),repo.resolve("verification/mvc-validation/contract.json"),"hybrid",output,runOptions);
                Json.write(output.resolve("cache-preparation.json"),Map.of("schema","springmig.cache/v1","status",prepared==0?"prepared":"failed","offlineProofRequired",true,"cache",cache.toString(),"exitCode",prepared));return prepared;
            case "compare":
                if(!agent.equals("mock"))throw new IllegalArgumentException("Three-mode comparison uses fresh mocks; replay a single mode explicitly");
                if(Files.exists(output) || output.startsWith(fixture) || fixture.startsWith(output))throw new IllegalArgumentException("Comparison output must be new and outside fixture");
                Files.createDirectories(output);Path snapshot=output.resolve("snapshot");Trees.copyProject(fixture,snapshot);
                var results=new LinkedHashMap<String,Object>();String digest=null;boolean identical=true,expected=true;
                for(String mode:List.of("recipes-only","agent-only","hybrid")) {
                    int code=engine.run(snapshot,contract,mode,output.resolve(mode),runOptions);
                    JsonObject report=Json.GSON.fromJson(Files.readString(output.resolve(mode+"/report.json")),JsonObject.class);
                    String hash=report.get("snapshotSha256").getAsString();if(digest==null)digest=hash;else identical&=digest.equals(hash);
                    int expectedExit=fixtureName.equals("unresolved-repair")?2:fixtureName.equals("mechanical-only")?0:mode.equals("recipes-only")?2:0;expected&=code==expectedExit;
                    results.put(mode,Map.of("exitCode",code,"status",report.get("status").getAsString(),"agentKind",report.get("agentKind").getAsString(),"recipeExecuted",report.get("recipeExecuted").getAsBoolean(),"proposalCount",report.getAsJsonArray("repairProposals").size(),"toolActions",report.get("toolActions").getAsInt()));
                }
                Json.write(output.resolve("comparison.json"),Map.of("schema","springmig.comparison/v2","fixture",fixtureName,"evaluation","scripted_mock_harness","identicalSnapshots",identical,"snapshotSha256",digest,"modes",results,"expectedFixtureOutcome",identical&&expected));
                System.out.println("comparison: identical snapshots="+identical+", expected fixture outcome="+expected);return identical&&expected?0:2;
            default:throw new IllegalArgumentException("Unknown command: "+args[0]);
        }
    }
    private static int number(Map<String,String> options,String key,int fallback) {return Integer.parseInt(options.getOrDefault(key,Integer.toString(fallback)));}
}
