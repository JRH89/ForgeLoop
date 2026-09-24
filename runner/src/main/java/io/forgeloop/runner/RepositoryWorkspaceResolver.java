package io.forgeloop.runner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.List;

/** Resolves only a pre-cloned repository beneath a runner-owned root; it never accepts an arbitrary path from ForgeLoop. */
public final class RepositoryWorkspaceResolver {
    public Path resolve(Path repositoriesRoot, String repository) {
        if (repositoriesRoot == null || repository == null || !repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException("Repository context is unsafe");
        }
        Path root = repositoriesRoot.toAbsolutePath().normalize();
        Path checkout = root.resolve(repository).normalize();
        if (!checkout.startsWith(root) || !Files.isDirectory(checkout.resolve(".git"))) {
            throw new IllegalArgumentException("Configured runner repository checkout is unavailable");
        }
        return checkout;
    }
    /** Clones only the lease-bound GitHub repository, with the token held in process environment configuration. */
    public Path resolveOrClone(Path repositoriesRoot,GithubCheckoutGrant grant)throws Exception{synchronized(RepositoryWorkspaceResolver.class){return resolveOrCloneLocked(repositoriesRoot,grant);}}
    private Path resolveOrCloneLocked(Path repositoriesRoot,GithubCheckoutGrant grant)throws Exception{
        if(grant.repository()==null||!grant.repository().matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")||grant.token()==null||grant.token().isBlank()||!safeBranch(grant.baseBranch()))throw new IllegalArgumentException("Checkout grant is invalid");
        try{Path existing=resolve(repositoriesRoot,grant.repository());runAuthenticated(existing,grant,List.of("git","fetch","--prune","origin","+refs/heads/"+grant.baseBranch()+":refs/remotes/origin/"+grant.baseBranch()));return existing;}catch(IllegalArgumentException missing){
            Path root=repositoriesRoot.toAbsolutePath().normalize();Path checkout=root.resolve(grant.repository()).normalize();if(!checkout.startsWith(root))throw new IllegalArgumentException("Repository checkout escapes runner root");Files.createDirectories(checkout.getParent());
            String authorization=Base64.getEncoder().encodeToString(("x-access-token:"+grant.token()).getBytes(StandardCharsets.UTF_8));
            ProcessBuilder builder=new ProcessBuilder("git","clone","--origin","origin","--branch",grant.baseBranch(),"--single-branch","https://github.com/"+grant.repository()+".git",checkout.toString()).redirectErrorStream(true);authenticate(builder,authorization);
            Process process=builder.start();if(!process.waitFor(5,TimeUnit.MINUTES)){process.destroyForcibly();throw new IllegalStateException("Repository clone timed out");}String output=new String(process.getInputStream().readNBytes(4096),StandardCharsets.UTF_8);if(process.exitValue()!=0)throw new IllegalStateException("Repository clone failed: "+output.replaceAll("gh[opsu]_[A-Za-z0-9_]+","[REDACTED]"));return resolve(root,grant.repository());
        }
    }
    private static void runAuthenticated(Path directory,GithubCheckoutGrant grant,List<String> command)throws Exception{String authorization=Base64.getEncoder().encodeToString(("x-access-token:"+grant.token()).getBytes(StandardCharsets.UTF_8));ProcessBuilder builder=new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true);authenticate(builder,authorization);Process process=builder.start();if(!process.waitFor(5,TimeUnit.MINUTES)){process.destroyForcibly();throw new IllegalStateException("Repository fetch timed out");}String output=new String(process.getInputStream().readNBytes(4096),StandardCharsets.UTF_8);if(process.exitValue()!=0)throw new IllegalStateException("Repository fetch failed: "+output.replaceAll("gh[opsu]_[A-Za-z0-9_]+","[REDACTED]"));}
    private static void authenticate(ProcessBuilder builder,String authorization){builder.environment().put("GIT_CONFIG_COUNT","1");builder.environment().put("GIT_CONFIG_KEY_0","http.https://github.com/.extraheader");builder.environment().put("GIT_CONFIG_VALUE_0","AUTHORIZATION: basic "+authorization);}
    private static boolean safeBranch(String branch){return branch!=null&&branch.matches("[A-Za-z0-9][A-Za-z0-9._/-]{0,254}")&&!branch.contains("..")&&!branch.contains("//")&&!branch.contains("@{")&&!branch.endsWith("/")&&!branch.endsWith(".");}
}
