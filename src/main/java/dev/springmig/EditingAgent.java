package dev.springmig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

interface EditingAgent {
    record Edit(String relativePath,String preimageSha256,String before,String after) {}
    record Proposal(List<Edit> edits,String reason) {}
    Optional<Proposal> propose(Verifier.Result diagnostics,ReadTools tools) throws Exception;
    interface ReadTools {List<String> javaFiles() throws Exception;String read(String relative) throws Exception;}

    final class MockAgent implements EditingAgent {
        @Override public Optional<Proposal> propose(Verifier.Result diagnostics,ReadTools tools) throws Exception {
            String pom=tools.read("pom.xml");
            if(PomPolicy.version(pom).equals(PomPolicy.SOURCE)) {
                var edits=new ArrayList<Edit>();
                edits.add(edit("pom.xml",pom,pom,PomPolicy.trustedPom(pom,PomPolicy.TARGET)));
                for(String path:tools.javaFiles()) {
                    String source=tools.read(path),after=source.replace("javax.validation", "jakarta.validation").replace("javax.servlet","jakarta.servlet");
                    if(!source.equals(after))edits.add(edit(path,source,source,after));
                }
                String path="src/main/resources/application.properties",source=tools.read(path);
                String after=source.replace("server.max-http-header-size=","server.max-http-request-header-size=");
                if(!source.equals(after))edits.add(edit(path,source,source,after));
                return Optional.of(new Proposal(List.copyOf(edits),"Scripted mechanical mock: exact parent, Jakarta package names and selected header key."));
            }
            if(!diagnostics.buildPassed() || diagnostics.checks().stream().filter(c->!c.passed()).anyMatch(c->!c.id().equals("get_trailing_slash")) || diagnostics.checks().stream().noneMatch(c->c.id().equals("get_trailing_slash")&&!c.passed()))return Optional.empty();
            for(String path:tools.javaFiles()) {
                String source=tools.read(path),before="@GetMapping(\"/greeting\")";
                if(source.contains(before))return Optional.of(new Proposal(List.of(edit(path,source,before,"@GetMapping({\"/greeting\", \"/greeting/\"})")),"Scripted route repair preserves the protected trailing-slash contract."));
            }
            return Optional.empty();
        }
    }
    static Edit edit(String path,String source,String before,String after) {return new Edit(path,Trees.sha(source.getBytes(StandardCharsets.UTF_8)),before,after);}
    final class Denied extends IOException {Denied(String reason) {super(reason);}}
    final class ActionBudget extends IOException {ActionBudget() {super("tool_budget_exhausted");}}
    final class NoOp extends IOException {NoOp() {super("no_op_proposal");}}

    final class Gateway implements ReadTools {
        private final Path candidate;
        private final String originalPom,originalProperties;
        private final int maxActions;
        private int actions;
        interface CommitObserver {void committed(int index) throws IOException;}
        private final CommitObserver observer;
        Gateway(Path candidate,Path baseline,int maxActions) throws IOException {this(candidate,baseline,maxActions,index->{});}
        Gateway(Path candidate,Path baseline,int maxActions,CommitObserver observer) throws IOException {
            this.candidate=candidate.toAbsolutePath().normalize();this.maxActions=maxActions;this.observer=observer;
            originalPom=Files.readString(baseline.resolve("pom.xml"));originalProperties=Files.readString(baseline.resolve("src/main/resources/application.properties"));
        }
        int actions() {return actions;}
        private void charge() throws ActionBudget {if(actions>=maxActions)throw new ActionBudget();actions++;}
        @Override public List<String> javaFiles() throws Exception {charge();return Trees.hashes(candidate).keySet().stream().filter(p->p.startsWith("src/main/java/")&&p.endsWith(".java")).toList();}
        @Override public String read(String relative) throws Exception {charge();Path path=allowed(relative);if(Files.size(path)>65536)throw new Denied("Source read exceeds 64 KiB");return Files.readString(path);}
        private Path allowed(String relative) throws Denied {try {return Trees.allowedEdit(candidate,relative);}catch(IOException ex) {throw new Denied(ex.getMessage());}}
        synchronized void apply(Proposal proposal) throws Exception {
            charge();
            if(proposal==null || proposal.edits()==null || proposal.edits().isEmpty() || proposal.edits().size()>20 || proposal.reason()==null || proposal.reason().isBlank())throw new Denied("malformed_proposal");
            Map<Path,byte[]> before=new LinkedHashMap<>(),after=new LinkedHashMap<>();long total=0;
            for(Edit edit:proposal.edits()) {
                if(edit==null || edit.before()==null || edit.before().isEmpty() || edit.after()==null || edit.preimageSha256()==null)throw new Denied("malformed_edit");
                Path path=allowed(edit.relativePath());
                if(before.containsKey(path))throw new Denied("duplicate_edit_path");
                byte[] bytes=Files.readAllBytes(path);String source=new String(bytes,StandardCharsets.UTF_8);
                if(!Trees.sha(bytes).equals(edit.preimageSha256()))throw new Denied("stale_preimage");
                if(source.indexOf(edit.before())<0 || source.indexOf(edit.before())!=source.lastIndexOf(edit.before()))throw new Denied("Expected one exact replacement block");
                String replacement=source.replace(edit.before(),edit.after());
                if(edit.relativePath().equals("pom.xml") && !(replacement.equals(originalPom) && source.equals(originalPom)) && !replacement.equals(PomPolicy.trustedPom(originalPom,PomPolicy.TARGET)))throw new Denied("Only exact forward parent-version edit permitted");
                if(edit.relativePath().equals("src/main/resources/application.properties") && !(replacement.equals(originalProperties) && source.equals(originalProperties)) && !replacement.equals(originalProperties.replace("server.max-http-header-size=","server.max-http-request-header-size=")))throw new Denied("Only selected forward header-key rename permitted");
                byte[] changed=replacement.getBytes(StandardCharsets.UTF_8);total+=changed.length;
                if(total>256*1024)throw new Denied("Proposal exceeds 256 KiB");
                before.put(path,bytes);after.put(path,changed);
            }
            if(before.keySet().stream().allMatch(p->Arrays.equals(before.get(p),after.get(p))))throw new NoOp();
            Map<Path,Path> staged=new LinkedHashMap<>();List<Path> committed=new ArrayList<>();
            try {
                for(Path path:before.keySet()) {Path temp=Files.createTempFile(path.getParent(),".springmig-",".tmp");staged.put(path,temp);Files.write(temp,after.get(path));}
                for(Path path:before.keySet())if(!Arrays.equals(before.get(path),Files.readAllBytes(allowed(candidate.relativize(path).toString().replace('\\','/')))))throw new Denied("stale_preimage_before_commit");
                int index=0;
                for(Path path:before.keySet()) {Files.move(staged.get(path),path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);committed.add(path);observer.committed(++index);}
            }catch(Exception failure) {
                for(Path path:committed)try {Path rollback=Files.createTempFile(path.getParent(),".springmig-rollback-",".tmp");try {Files.write(rollback,before.get(path));Files.move(rollback,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally {Files.deleteIfExists(rollback);}}
                catch(IOException rollbackFailure) {failure.addSuppressed(rollbackFailure);}
                throw failure;
            }finally {for(Path temp:staged.values())Files.deleteIfExists(temp);}
        }
    }
}
