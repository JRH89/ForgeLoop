package io.forgeloop.runner;

/** Per-lease monotonic event reporter with deliberately generic, metadata-only messages. */
public final class RunnerEventReporter {
    private final RunnerClient client; private final RunnerIdentity identity; private final RunnerLease lease; private long sequence;
    public RunnerEventReporter(RunnerClient client,RunnerIdentity identity,RunnerLease lease){this.client=client;this.identity=identity;this.lease=lease;}
    public void info(String type,String message){send("INFO",type,message);}
    public void error(String message){send("ERROR","TASK_FAILED",message);}
    /** Progress telemetry must never corrupt or abort the repository operation it observes. */
    private void send(String level,String type,String message){
        long next=++sequence;
        for(int attempt=1;attempt<=3;attempt++)try{client.recordEvent(identity,lease,next,level,type,message);return;}catch(Exception unavailable){
            if(attempt==3)System.err.println("Runner event delivery deferred: sequence="+next);
            else try{Thread.sleep(100L*attempt);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();return;}
        }
    }
}
