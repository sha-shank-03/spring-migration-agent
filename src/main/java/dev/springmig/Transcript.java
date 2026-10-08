package dev.springmig;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

final class Transcript {
    record Request(String schema,String mode,String target,String recipeSha256,String contractSha256,
                   String stage,String version,boolean buildPassed,int testsRun,List<Verifier.Observation> diagnostics,
                   Map<String,String> sourceManifest,String sourceSha256) {}
    record Entry(Request request,String requestSha256,EditingAgent.Proposal proposal) {}
    record Recording(String schema,String kind,List<Entry> entries) {}
    private final List<Entry> captured=new ArrayList<>(),replay;
    private int cursor;
    Transcript(Path file) throws Exception {
        if(file==null) {replay=null;return;}
        if(Files.size(file)>1024*1024)throw new EditingAgent.Denied("transcript_too_large");
        Recording recording;
        try {recording=Json.GSON.fromJson(Files.readString(file),Recording.class);}catch(com.google.gson.JsonParseException ex) {throw new EditingAgent.Denied("malformed_transcript_json");}
        if(recording==null || !"springmig.transcript/v1".equals(recording.schema()) || !"recorded_mock".equals(recording.kind()) || recording.entries()==null || recording.entries().size()>3)throw new EditingAgent.Denied("malformed_transcript");
        replay=recording.entries();
    }
    static String hash(Request request) {return Trees.sha(Json.GSON.toJson(request).getBytes(StandardCharsets.UTF_8));}
    static Request request(String mode,String recipeHash,String contractHash,Verifier.Result diagnostics,Path candidate) throws Exception {
        var manifest=Trees.hashes(candidate);
        // Only contract-relevant response content belongs to the request. Spring error bodies
        // contain timestamps, so keep their status/result and omit unspecified body content.
        var observations=diagnostics.checks().stream().map(c->new Verifier.Observation(c.id(),c.expectedStatus(),c.actualStatus(),c.expectedBody(),c.actualStatus()!=null && c.actualStatus()==c.expectedStatus() && c.expectedBody()!=null?c.actualBody():null,c.passed(),c.error())).toList();
        return new Request("springmig.agent/v1",mode,Engine.TARGET_ID,recipeHash,contractHash,diagnostics.stage(),diagnostics.version(),diagnostics.buildPassed(),diagnostics.testsRun(),observations,manifest,Trees.aggregate(manifest));
    }
    Optional<EditingAgent.Proposal> propose(Request request,EditingAgent agent,Verifier.Result diagnostics,EditingAgent.ReadTools tools) throws Exception {
        String hash=hash(request);EditingAgent.Proposal proposal;
        if(replay!=null) {
            if(cursor>=replay.size())throw new EditingAgent.Denied("replay_exhausted");
            Entry entry=replay.get(cursor++);
            if(entry==null || entry.request()==null || !hash(entry.request()).equals(entry.requestSha256()) || !hash.equals(entry.requestSha256()))throw new EditingAgent.Denied("replay_request_source_mismatch");
            proposal=entry.proposal();
        }else proposal=agent.propose(diagnostics,tools).orElse(null);
        captured.add(new Entry(request,hash,proposal));return Optional.ofNullable(proposal);
    }
    void complete() throws EditingAgent.Denied {if(replay!=null && cursor!=replay.size())throw new EditingAgent.Denied("replay_unused_entries");}
    void write(Path output) throws Exception {Json.write(output,new Recording("springmig.transcript/v1","recorded_mock",List.copyOf(captured)));}
    List<Entry> entries() {return List.copyOf(captured);}
}
