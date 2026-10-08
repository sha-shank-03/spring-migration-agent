package dev.springmig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TranscriptTest {
    @TempDir Path temp;
    @Test void replayUsesExactRequestAndNeverCallsMock() throws Exception {
        Path candidate=temp.resolve("candidate");Trees.copyProject(Path.of("examples/mvc-validation"),candidate);
        var request=Transcript.request("hybrid","recipe","contract",ControllerTest.failed(),candidate);
        var transcript=new Transcript(null);transcript.propose(request,(d,t)->Optional.empty(),ControllerTest.failed(),null);Path file=temp.resolve("recording.json");transcript.write(file);
        var replay=new Transcript(file);assertTrue(replay.propose(request,(d,t)->{fail("Replay must not call mock");return Optional.empty();},ControllerTest.failed(),null).isEmpty());replay.complete();
        Files.writeString(candidate.resolve("src/main/java/demo/Application.java"),"different source");
        var changed=Transcript.request("hybrid","recipe","contract",ControllerTest.failed(),candidate);
        assertThrows(EditingAgent.Denied.class,()->new Transcript(file).propose(changed,(d,t)->Optional.empty(),ControllerTest.failed(),null));
        var alteredContract=Transcript.request("hybrid","recipe","changed-contract",ControllerTest.failed(),candidate);
        assertThrows(EditingAgent.Denied.class,()->new Transcript(file).propose(alteredContract,(d,t)->Optional.empty(),ControllerTest.failed(),null));
        assertThrows(EditingAgent.Denied.class,()->new Transcript(file).complete());
        String json=Files.readString(file).replace(request.sourceSha256(),"corrupt");Files.writeString(file,json);
        assertThrows(EditingAgent.Denied.class,()->new Transcript(file).propose(request,(d,t)->Optional.empty(),ControllerTest.failed(),null));
    }
    @Test void malformedTranscriptSchemaRejected() throws Exception {
        Path file=temp.resolve("bad.json");Files.writeString(file,"{\"schema\":\"wrong\",\"kind\":\"live\",\"entries\":[]}");assertThrows(EditingAgent.Denied.class,()->new Transcript(file));
    }
}
