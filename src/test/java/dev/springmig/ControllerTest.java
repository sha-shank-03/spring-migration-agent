package dev.springmig;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ControllerTest {
    @TempDir Path temp;Path baseline,candidate;String path="src/main/java/demo/GreetingController.java";
    @BeforeEach void copy() throws Exception {baseline=temp.resolve("baseline");candidate=temp.resolve("candidate");Trees.copyProject(Path.of("examples/mvc-validation"),baseline);Trees.copyProject(baseline,candidate);}
    static Verifier.Result failed() {return new Verifier.Result("post-recipe",PomPolicy.TARGET,true,3,List.of(new Verifier.Observation("get_trailing_slash",200,404,"Hello, visitor","missing",false,null)),false,null,"fake-only",true,null,true,null);}
    RepairController.Result run(EditingAgent agent,int max,RunBudget budget) throws Exception {return RepairController.run("hybrid","recipe","contract",candidate,failed(),agent,new EditingAgent.Gateway(candidate,baseline,20),new Transcript(null),budget,max,n->failed());}
    @Test void noOpStopsWithoutVerification() throws Exception {
        var result=run((d,t)->{String s=t.read(path);return Optional.of(new EditingAgent.Proposal(List.of(EditingAgent.edit(path,s,s,s)),"no-op"));},3,new RunBudget(30,10));
        assertEquals("no_op_proposal",result.stopReason());assertEquals(1,result.attempts());assertTrue(result.accepted().isEmpty());
    }
    @Test void repeatedCandidateStopsAtSecondProposal() throws Exception {
        var result=run((d,t)->{String s=t.read(path);String after=s.contains("/* flip */")?s.replace("/* flip */",""):s+"/* flip */";return Optional.of(new EditingAgent.Proposal(List.of(EditingAgent.edit(path,s,s,after)),"flip"));},3,new RunBudget(30,10));
        assertEquals("repeated_candidate",result.stopReason());assertEquals(2,result.attempts());assertEquals(Trees.hashes(baseline),Trees.hashes(candidate));
    }
    @Test void proposalLimitAndUnsupportedDiagnosticsAreExplicit() throws Exception {
        var counter=new AtomicInteger();var result=run((d,t)->{String s=t.read(path);return Optional.of(new EditingAgent.Proposal(List.of(EditingAgent.edit(path,s,s,s+"/* "+counter.incrementAndGet()+" */")),"bounded"));},2,new RunBudget(30,10));
        assertEquals("proposal_budget_exhausted",result.stopReason());assertEquals(2,result.attempts());
        assertEquals("unsupported_diagnostics",run((d,t)->Optional.empty(),3,new RunBudget(30,10)).stopReason());
    }
    @Test void deadlineAfterAgentResponsePreventsWrite() throws Exception {
        var before=Trees.hashes(candidate);
        assertThrows(RunBudget.Exhausted.class,()->run((d,t)->{String s=t.read(path);Thread.sleep(1100);return Optional.of(new EditingAgent.Proposal(List.of(EditingAgent.edit(path,s,s,s+"/* late */")),"late"));},3,new RunBudget(1,1)));
        assertEquals(before,Trees.hashes(candidate));
    }
    @Test void invalidBudgetAndInterruptedRunProduceEvidence() throws Exception {
        assertThrows(IllegalArgumentException.class,()->new Engine.Options(false,temp,null,4,20,180,900));
        Path output=temp.resolve("interrupted");Thread.currentThread().interrupt();
        try {assertEquals(8,new Engine(Path.of(".")).run(baseline,Path.of("verification/mvc-validation/contract.json"),"hybrid",output));}
        finally {Thread.interrupted();}
        var report=Json.GSON.fromJson(Files.readString(output.resolve("report.json")),com.google.gson.JsonObject.class);
        assertEquals("interrupted",report.get("status").getAsString());assertTrue(report.get("originalInputIntact").getAsBoolean());assertTrue(Files.exists(output.resolve("report.md")));assertTrue(Files.exists(output.resolve("events.jsonl")));
    }
}
