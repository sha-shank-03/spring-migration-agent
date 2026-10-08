package dev.springmig;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

final class PomPolicy {
    static final String SOURCE="2.7.18", TARGET="3.0.13";
    private static Document parse(String xml) throws Exception {
        var factory=DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
        factory.setXIncludeAware(false);factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    private static Element child(Element root,String name) {
        for(Node n=root.getFirstChild();n!=null;n=n.getNextSibling()) if(n instanceof Element e && e.getTagName().equals(name)) return e;
        return null;
    }
    private static String text(Element root,String name) { var node=child(root,name); return node==null?"":node.getTextContent().trim(); }
    static String version(String xml) throws Exception {
        var parent=child(parse(xml).getDocumentElement(),"parent");
        return parent==null?"":text(parent,"version");
    }
    static void preflight(Path input) throws Exception {
        Trees.rejectLink(input);
        String xml=Files.readString(input.resolve("pom.xml"));
        var root=parse(xml).getDocumentElement();var parent=child(root,"parent");
        if(parent==null || !text(parent,"groupId").equals("org.springframework.boot") || !text(parent,"artifactId").equals("spring-boot-starter-parent") || !text(parent,"version").equals(SOURCE) || child(parent,"relativePath")==null || !text(parent,"relativePath").isEmpty())
            throw new IllegalArgumentException("Expected exact external Boot parent 2.7.18");
        if(child(root,"modules")!=null || child(root,"profiles")!=null || child(root,"dependencyManagement")!=null || child(root,"repositories")!=null || child(root,"pluginRepositories")!=null)
            throw new IllegalArgumentException("Only the declared single-module Maven surface is supported");
        var properties=child(root,"properties");
        if(properties==null || !text(properties,"java.version").equals("17")) throw new IllegalArgumentException("Fixture source level must be 17");
        for(Node n=properties.getFirstChild();n!=null;n=n.getNextSibling()) if(n instanceof Element e && !Set.of("java.version","project.build.sourceEncoding").contains(e.getTagName()))
            throw new IllegalArgumentException("Unsupported Maven property: "+e.getTagName());
        var dependencies=child(root,"dependencies");
        if(dependencies==null) throw new IllegalArgumentException("Missing supported starters");
        Set<String> seen=new HashSet<>();
        for(Node n=dependencies.getFirstChild();n!=null;n=n.getNextSibling()) if(n instanceof Element e) {
            String artifact=text(e,"artifactId");
            if(!text(e,"groupId").equals("org.springframework.boot") || !Set.of("spring-boot-starter-web","spring-boot-starter-validation","spring-boot-starter-test").contains(artifact) || child(e,"version")!=null || child(e,"exclusions")!=null)
                throw new IllegalArgumentException("Dependency outside MVC/validation surface");
            seen.add(artifact);
        }
        if(!seen.containsAll(Set.of("spring-boot-starter-web","spring-boot-starter-validation"))) throw new IllegalArgumentException("Web and validation starters required");
        var build=child(root,"build");
        if(build!=null) {
            for(Node n=build.getFirstChild();n!=null;n=n.getNextSibling()) if(n instanceof Element e && !e.getTagName().equals("plugins")) throw new IllegalArgumentException("Unsupported custom build layout");
            var plugins=child(build,"plugins");
            if(plugins!=null) for(Node n=plugins.getFirstChild();n!=null;n=n.getNextSibling()) if(n instanceof Element e && !Set.of("org.springframework.boot:spring-boot-maven-plugin","org.apache.maven.plugins:maven-surefire-plugin").contains(text(e,"groupId")+":"+text(e,"artifactId")))
                throw new IllegalArgumentException("Unsupported build plugin");
        }
        if(Files.exists(input.resolve(".mvn")) || Files.exists(input.resolve("build.gradle"))) throw new IllegalArgumentException("Custom Maven configuration/Gradle unsupported");
        Trees.hashes(input);
    }
    static String trustedPom(String baseline,String version) throws Exception {
        if(!Set.of(SOURCE,TARGET).contains(version)) throw new IllegalArgumentException("Unknown target version");
        // Preflight fixes an exact source parent; only its version is changed in the host-owned POM.
        int start=baseline.indexOf("<parent>"), end=baseline.indexOf("</parent>",start);
        if(start<0 || end<0) throw new IllegalArgumentException("Parent block required");
        String parent=baseline.substring(start,end);
        String replacement=parent.replace("<version>"+SOURCE+"</version>","<version>"+version+"</version>");
        if(!version.equals(SOURCE) && parent.equals(replacement)) throw new IllegalArgumentException("Unsupported parent formatting");
        return baseline.substring(0,start)+replacement+baseline.substring(end);
    }
    private static String semantic(Node node) {
        if(node instanceof Element e) {
            StringBuilder value=new StringBuilder("<"+e.getTagName());
            var attrs=e.getAttributes();List<String> attributes=new ArrayList<>();
            for(int i=0;i<attrs.getLength();i++) attributes.add(attrs.item(i).getNodeName()+"="+attrs.item(i).getNodeValue());
            Collections.sort(attributes);value.append(attributes).append('>');
            for(Node n=e.getFirstChild();n!=null;n=n.getNextSibling())value.append(semantic(n));
            return value.append("</"+e.getTagName()+">").toString();
        }
        return node.getNodeType()==Node.TEXT_NODE?node.getTextContent().trim():"";
    }
    static void validateCandidate(String baseline,String candidate) throws Exception {
        if(!version(candidate).equals(TARGET) || !semantic(parse(trustedPom(baseline,TARGET)).getDocumentElement()).equals(semantic(parse(candidate).getDocumentElement())))
            throw new IllegalArgumentException("Candidate POM must differ only by target parent version");
    }
}
