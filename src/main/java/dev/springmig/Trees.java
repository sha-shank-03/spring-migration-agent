package dev.springmig;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

final class Trees {
    static String sha(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    static String sha(Path file) throws IOException { return sha(Files.readAllBytes(file)); }
    static String aggregate(Map<String, String> hashes) {
        StringBuilder text = new StringBuilder();
        hashes.forEach((path, hash) -> text.append(path).append('\0').append(hash).append('\n'));
        return sha(text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    static Map<String,String> hashes(Path root) throws IOException {
        Map<String,String> result = new TreeMap<>();
        if (!Files.exists(root)) return result;
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!dir.equals(root) && Set.of(".git", "target").contains(dir.getFileName().toString())) return FileVisitResult.SKIP_SUBTREE;
                rejectLink(dir);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                rejectLink(file);
                if (!attrs.isRegularFile()) throw new IOException("Non-regular input: " + file);
                if (attrs.size() > 1024 * 1024) throw new IOException("Input file exceeds 1 MiB: " + file);
                result.put(root.relativize(file).toString().replace('\\','/'), sha(file));
                if (result.size() > 500) throw new IOException("Input exceeds 500 files");
                return FileVisitResult.CONTINUE;
            }
        });
        return result;
    }
    static void rejectLink(Path path) throws IOException {
        if (Files.isSymbolicLink(path) || Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isOther())
            throw new IOException("Links/reparse entries unsupported: " + path);
        if (!path.toAbsolutePath().normalize().equals(path.toRealPath()))
            throw new IOException("Reparse entries unsupported: " + path);
    }
    static void copyProject(Path source, Path destination) throws IOException {
        copyProject(source,destination,true);
    }
    static void copyProject(Path source, Path destination, boolean includeTests) throws IOException {
        Map<String,String> entries = hashes(source);
        Files.createDirectories(destination);
        for (String relative : entries.keySet()) {
            if (!relative.equals("pom.xml") && !relative.startsWith("src/")) continue;
            if (!includeTests && relative.startsWith("src/test/")) continue;
            Path to = destination.resolve(relative);
            Files.createDirectories(to.getParent());
            Files.copy(source.resolve(relative), to);
        }
    }
    static Path allowedJava(Path candidate, String relative) throws IOException {
        if (relative==null || !relative.startsWith("src/main/java/") || !relative.endsWith(".java"))
            throw new IOException("Editing path denied: " + relative);
        return allowedEdit(candidate,relative);
    }
    static Path allowedEdit(Path candidate,String relative) throws IOException {
        if(relative==null || relative.contains("\\") || relative.contains(":") || relative.contains("..") || relative.startsWith("/") || relative.contains("//") || relative.contains("\0") ||
           !(relative.equals("pom.xml") || relative.equals("src/main/resources/application.properties") || (relative.startsWith("src/main/java/") && relative.endsWith(".java"))))
            throw new IOException("Editing path denied: "+relative);
        for(String part:relative.split("/"))if(part.isBlank() || part.endsWith(".") || part.endsWith(" ") || part.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?"))throw new IOException("Editing path denied: "+relative);
        candidate=candidate.toAbsolutePath().normalize();
        Path file = candidate.resolve(relative).normalize();
        if (!file.startsWith(candidate) || !Files.isRegularFile(file)) throw new IOException("Editing path denied: " + relative);
        for(Path p=file;p!=null && p.startsWith(candidate);p=p.getParent())rejectLink(p);
        // Windows is case-insensitive; require exact spelling of each directory entry.
        Path current=candidate;
        for(Path part:candidate.relativize(file)) {
            try(var entries=Files.list(current)) {if(entries.noneMatch(e->e.getFileName().toString().equals(part.toString())))throw new IOException("Noncanonical editing path: "+relative);}
            current=current.resolve(part);
        }
        return file;
    }
}
