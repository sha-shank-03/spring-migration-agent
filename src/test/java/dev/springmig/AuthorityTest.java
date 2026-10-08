package dev.springmig;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AuthorityTest {
    @TempDir Path temp;
    Path baseline,candidate;
    @BeforeEach void copy() throws Exception {baseline=temp.resolve("baseline");candidate=temp.resolve("candidate");Trees.copyProject(Path.of("examples/mvc-validation"),baseline);Trees.copyProject(baseline,candidate);}
    EditingAgent.Gateway gateway() throws Exception {return new EditingAgent.Gateway(candidate,baseline,20);}
    EditingAgent.Edit javaEdit() throws Exception {String p="src/main/java/demo/GreetingController.java",s=Files.readString(candidate.resolve(p));return EditingAgent.edit(p,s,"@GetMapping(\"/greeting\")","@GetMapping({\"/greeting\", \"/greeting/\"})");}
    @Test void forbiddenPathsCannotLeakFirstValidEdit() throws Exception {
        for(String path:List.of("src/test/java/demo/GreetingControllerTest.java","contract.json",".mvn/maven.config","pom.xml/../pom.xml","../outside.java","C:/outside.java","src/main/java/demo/GreetingController.java:stream","src/main/java/demo/CON.java","src/main/java/demo/greetingController.java","src/main/java//demo/GreetingController.java","src/main/java/demo/GreetingController.java ","src/main/java/demo/../../test.java")) {
            var before=Trees.hashes(candidate);var forbidden=new EditingAgent.Edit(path,"any","a","b");
            assertThrows(EditingAgent.Denied.class,()->gateway().apply(new EditingAgent.Proposal(List.of(javaEdit(),forbidden),"deny")),path);assertEquals(before,Trees.hashes(candidate),path);
        }
    }
    @Test void staleSecondEditAndMalformedPayloadLeaveAllBytesIntact() throws Exception {
        var before=Trees.hashes(candidate);var stale=new EditingAgent.Edit("pom.xml","stale","2.7.18","3.0.13");
        assertThrows(EditingAgent.Denied.class,()->gateway().apply(new EditingAgent.Proposal(List.of(javaEdit(),stale),"stale")));
        for(EditingAgent.Proposal p:Arrays.asList(null,new EditingAgent.Proposal(null,"bad"),new EditingAgent.Proposal(List.of(),"bad"),new EditingAgent.Proposal(List.of(javaEdit()),null),new EditingAgent.Proposal(List.of(javaEdit(),javaEdit()),"duplicate"),new EditingAgent.Proposal(List.of(new EditingAgent.Edit("pom.xml",Trees.sha(candidate.resolve("pom.xml")),"",null)),"bad")))assertThrows(EditingAgent.Denied.class,()->gateway().apply(p));
        assertEquals(before,Trees.hashes(candidate));
    }
    @Test void exactMultiFileUpgradeAndCommitRollback() throws Exception {
        String p=Files.readString(candidate.resolve("pom.xml")),properties=Files.readString(candidate.resolve("src/main/resources/application.properties"));
        var proposal=new EditingAgent.Proposal(List.of(javaEdit(),EditingAgent.edit("pom.xml",p,p,PomPolicy.trustedPom(p,PomPolicy.TARGET)),EditingAgent.edit("src/main/resources/application.properties",properties,properties,properties.replace("server.max-http-header-size=","server.max-http-request-header-size="))),"mechanical");
        var original=Trees.hashes(candidate);
        var failing=new EditingAgent.Gateway(candidate,baseline,20,index->{if(index==2)throw new IOException("injected_commit_failure");});
        assertThrows(IOException.class,()->failing.apply(proposal));assertEquals(original,Trees.hashes(candidate));
        gateway().apply(proposal);assertEquals(PomPolicy.TARGET,PomPolicy.version(Files.readString(candidate.resolve("pom.xml"))));assertTrue(Files.readString(candidate.resolve("src/main/resources/application.properties")).contains("server.max-http-request-header-size="));
        Engine.assertCandidate(baseline,candidate);
    }
    @Test void pomCannotSmuggleTestsPluginsDependenciesOrOtherVersions() throws Exception {
        String source=Files.readString(candidate.resolve("pom.xml")),target=PomPolicy.trustedPom(source,PomPolicy.TARGET);
        PomPolicy.validateCandidate(source,target);
        for(String bad:List.of(target.replace("<java.version>17</java.version>","<java.version>17</java.version><maven.test.skip>true</maven.test.skip>"),target.replace("</dependencies>","<dependency><groupId>x</groupId><artifactId>y</artifactId></dependency></dependencies>"),target.replace("<failIfNoTests>true</failIfNoTests>","<failIfNoTests>false</failIfNoTests>"),target.replace("3.0.13","3.0.12"),target+"<!-- unapproved POM change -->"))assertThrows(EditingAgent.Denied.class,()->gateway().apply(new EditingAgent.Proposal(List.of(EditingAgent.edit("pom.xml",source,source,bad)),"deny")));
        assertEquals(source,Files.readString(candidate.resolve("pom.xml")));
    }
    @Test void selectedPropertiesOnlyNoOpAndPayloadBounds() throws Exception {
        var before=Trees.hashes(candidate);String path="src/main/resources/application.properties",source=Files.readString(candidate.resolve(path));
        assertThrows(EditingAgent.Denied.class,()->gateway().apply(new EditingAgent.Proposal(List.of(EditingAgent.edit(path,source,source,source+"\nspring.main.web-application-type=none\n")),"deny")));
        var edit=javaEdit();assertThrows(EditingAgent.NoOp.class,()->gateway().apply(new EditingAgent.Proposal(List.of(new EditingAgent.Edit(edit.relativePath(),edit.preimageSha256(),edit.before(),edit.before())),"no-op")));
        assertThrows(EditingAgent.Denied.class,()->gateway().apply(new EditingAgent.Proposal(List.of(new EditingAgent.Edit(edit.relativePath(),edit.preimageSha256(),edit.before(),"x".repeat(256*1024))),"large")));
        assertEquals(before,Trees.hashes(candidate));
        Files.writeString(candidate.resolve(edit.relativePath()),"x".repeat(65537));assertThrows(EditingAgent.Denied.class,()->gateway().read(edit.relativePath()));
    }
    @Test void actionBudgetStopsBeforeExtraRead() throws Exception {
        var gateway=new EditingAgent.Gateway(candidate,baseline,1);gateway.javaFiles();assertThrows(EditingAgent.ActionBudget.class,()->gateway.read("pom.xml"));assertEquals(1,gateway.actions());
    }
    @Test void protectedVerifierRejectsTamperingBeforeAnyBuild() throws Exception {
        Path protectedRoot=temp.resolve("protected");var verifier=new Verifier(Path.of("."),baseline,protectedRoot,temp.resolve("verification"),Path.of("verification/mvc-validation/contract.json"));
        Files.writeString(protectedRoot.resolve("original/src/test/java/demo/GreetingControllerTest.java"),"tampered");
        assertThrows(IllegalStateException.class,()->verifier.verify(candidate,"denied",PomPolicy.TARGET));assertFalse(Files.exists(temp.resolve("verification")));
    }
    @Test void upgradedParentCannotBeDowngradedByAnotherProposal() throws Exception {
        String source=Files.readString(candidate.resolve("pom.xml")),target=PomPolicy.trustedPom(source,PomPolicy.TARGET);var gateway=gateway();
        gateway.apply(new EditingAgent.Proposal(List.of(EditingAgent.edit("pom.xml",source,source,target)),"upgrade"));var before=Trees.hashes(candidate);
        assertThrows(EditingAgent.Denied.class,()->gateway.apply(new EditingAgent.Proposal(List.of(javaEdit(),EditingAgent.edit("pom.xml",target,target,source)),"downgrade")));assertEquals(before,Trees.hashes(candidate));
    }
}
