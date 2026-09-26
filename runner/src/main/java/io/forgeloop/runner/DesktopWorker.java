package io.forgeloop.runner;

import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Owns one child JVM, with secrets in its environment rather than command arguments. */
public final class DesktopWorker {
    private final Path directory;
    private Process process;
    public DesktopWorker(Path directory){this.directory=directory;}
    public synchronized boolean running(){return process!=null&&process.isAlive();}
    public synchronized void start(DesktopConfiguration config,String key,Consumer<String> log)throws Exception{
        if(running())throw new IllegalStateException("Runner is already working");
        Files.deleteIfExists(directory.resolve("pause"));
        String java=Path.of(System.getProperty("java.home"),"bin",com.sun.jna.Platform.isWindows()?"java.exe":"java").toString();
        var args=new ArrayList<>(List.of(java,"-cp",System.getProperty("java.class.path"),RunnerMain.class.getName(),"serve",config.endpoint()));
        for(String path:List.of("identity","repositories","worktrees","provider-policy.json"))args.add(directory.resolve(path).toString());
        args.add(".");args.add(directory.resolve("leases").toString());args.add("1");
        var builder=new ProcessBuilder(args).redirectErrorStream(true);
        builder.environment().put("PATH",DesktopToolPaths.searchPath());
        for(String variable:List.of("ANTHROPIC_API_KEY","OPENAI_API_KEY","GEMINI_API_KEY"))builder.environment().remove(variable);
        builder.environment().put(config.keyVariable(),key);
        builder.environment().put("FORGELOOP_RUNNER_PAUSE_FILE",directory.resolve("pause").toString());
        String credential=new RunnerIdentityStore().load(directory.resolve("identity")).credential();
        process=builder.start();Process child=process;
        Thread.ofVirtual().start(()->{try(var reader=child.inputReader()){String line;while((line=reader.readLine())!=null)log.accept(redact(line,key,credential));log.accept("Worker stopped. Exit code: "+child.waitFor());}catch(Exception ignored){log.accept("Worker log connection closed.");}});
    }
    static String redact(String line,String key,String credential){return line.replace(key,"[REDACTED]").replace(credential,"[REDACTED]");}
    public synchronized void pause()throws Exception{if(running())Files.writeString(directory.resolve("pause"),"pause after current batch");}
}
