package io.forgeloop.runner;

import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Owns one child JVM, with secrets in its environment rather than command arguments. */
public final class DesktopWorker {
    private final Path directory;
    private Process process;
    private boolean pauseRequested;
    public DesktopWorker(Path directory){this.directory=directory;}
    public synchronized boolean running(){return process!=null&&process.isAlive();}
    public synchronized WorkerStatus.Snapshot status(){
        var saved=WorkerStatus.read(directory.resolve("worker-status.json"));
        if(!running())return saved!=null&&saved.phase()==WorkerStatus.Phase.ATTENTION?saved:new WorkerStatus.Snapshot(WorkerStatus.Phase.STOPPED,0,saved==null?0:saved.lastContactMillis());
        return saved==null?new WorkerStatus.Snapshot(WorkerStatus.Phase.STARTING,0,0):saved;
    }
    public synchronized boolean pausing(){return running()&&pauseRequested;}
    public synchronized void start(DesktopConfiguration config,String key,Consumer<String> log)throws Exception{
        start(config,key,log,Map.of());
    }
    /** Keep the verified local engine selected even if Docker Desktop changes the global CLI context. */
    synchronized void start(DesktopConfiguration config,String key,Consumer<String> log,Map<String,String> dockerEnvironment)throws Exception{
        if(running())throw new IllegalStateException("Runner is already working");
        Files.deleteIfExists(directory.resolve("pause"));
        pauseRequested=false;
        new WorkerStatus(directory.resolve("worker-status.json")).publish(WorkerStatus.Phase.STARTING,0);
        String java=Path.of(System.getProperty("java.home"),"bin",com.sun.jna.Platform.isWindows()?"java.exe":"java").toString();
        var args=new ArrayList<>(List.of(java,"-cp",System.getProperty("java.class.path"),RunnerMain.class.getName(),"serve",config.endpoint()));
        for(String path:List.of("identity","repositories","worktrees","provider-policy.json"))args.add(directory.resolve(path).toString());
        args.add(".");args.add(directory.resolve("leases").toString());args.add("1");
        var builder=new ProcessBuilder(args).redirectErrorStream(true);
        builder.environment().put("PATH",DesktopToolPaths.searchPath());
        applyDockerEnvironment(builder.environment(),dockerEnvironment);
        for(String variable:List.of("ANTHROPIC_API_KEY","OPENAI_API_KEY","GEMINI_API_KEY"))builder.environment().remove(variable);
        builder.environment().put(config.keyVariable(),key);
        builder.environment().put("FORGELOOP_RUNNER_PAUSE_FILE",directory.resolve("pause").toString());
        builder.environment().put("FORGELOOP_RUNNER_STATUS_FILE",directory.resolve("worker-status.json").toString());
        String credential=new RunnerIdentityStore().load(directory.resolve("identity")).credential();
        process=builder.start();Process child=process;
        Thread.ofVirtual().start(()->{try(var reader=child.inputReader()){String line;while((line=reader.readLine())!=null)log.accept(redact(line,key,credential));log.accept("Worker stopped. Exit code: "+child.waitFor());}catch(Exception ignored){log.accept("Worker log connection closed.");}});
    }
    /** Limit caller overrides to the connection selection, retaining existing TLS/context configuration. */
    static void applyDockerEnvironment(Map<String,String> environment,Map<String,String> dockerEnvironment){
        for(String variable:List.of("DOCKER_CONTEXT","DOCKER_HOST")){
            if(!dockerEnvironment.containsKey(variable))continue;
            String value=dockerEnvironment.get(variable);
            if(value.isEmpty())environment.remove(variable);else environment.put(variable,value);
        }
    }
    static String redact(String line,String key,String credential){return line.replace(key,"[REDACTED]").replace(credential,"[REDACTED]");}
    public synchronized void pause()throws Exception{if(running()){Files.writeString(directory.resolve("pause"),"pause after current batch");pauseRequested=true;}}
}
