package dev.springmig;

import java.nio.file.Path;
import java.util.*;

final class RepairController {
    interface Verify {Verifier.Result run(int attempt) throws Exception;}
    record Result(Verifier.Result last,String stopReason,int attempts,List<EditingAgent.Proposal> accepted) {}
    static Result run(String mode,String recipeHash,String contractHash,Path candidate,Verifier.Result after,
                      EditingAgent agent,EditingAgent.Gateway gateway,Transcript transcript,RunBudget budget,int maximum,Verify verify) throws Exception {
        return run(mode,recipeHash,contractHash,candidate,after,agent,gateway,transcript,budget,maximum,verify,new ArrayList<>());
    }
    static Result run(String mode,String recipeHash,String contractHash,Path candidate,Verifier.Result after,
                      EditingAgent agent,EditingAgent.Gateway gateway,Transcript transcript,RunBudget budget,int maximum,Verify verify,List<EditingAgent.Proposal> accepted) throws Exception {
        Set<String> seen=new HashSet<>();seen.add(Trees.aggregate(Trees.hashes(candidate)));
        int attempts=0;String stop="verified";
        while(!after.passed() && attempts<maximum) {
            budget.check();attempts++;
            var request=Transcript.request(mode,recipeHash,contractHash,after,candidate);
            var proposal=transcript.propose(request,agent,after,gateway);budget.check();
            if(proposal.isEmpty()) {stop="unsupported_diagnostics";break;}
            try {gateway.apply(proposal.get());}catch(EditingAgent.NoOp ex) {stop="no_op_proposal";break;}
            accepted.add(proposal.get());
            if(!seen.add(Trees.aggregate(Trees.hashes(candidate)))) {stop="repeated_candidate";break;}
            after=verify.run(attempts);budget.check();
        }
        if(!after.passed() && attempts==maximum && stop.equals("verified"))stop="proposal_budget_exhausted";
        return new Result(after,stop,attempts,List.copyOf(accepted));
    }
}
