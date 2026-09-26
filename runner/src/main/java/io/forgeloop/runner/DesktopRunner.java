package io.forgeloop.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.*;
import java.awt.event.*;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.channels.*;
import java.nio.file.*;
import java.math.BigDecimal;
import java.time.Duration;
import javax.swing.*;

/** Native desktop shell; slow operations run off the UI thread and never start paid work implicitly. */
public final class DesktopRunner {
    private static final ObjectMapper JSON=new ObjectMapper();
    private final Path directory;
    private final DesktopWorker worker;
    private final DesktopSecretStore secrets;
    private final JFrame frame=new JFrame("ForgeLoop Runner");
    private final JTextField endpoint=new JTextField("https://forgeloop.hookerhillstudios.com"),name=new JTextField("My runner"),model=new JTextField("claude-sonnet-5"),input=new JTextField("2"),output=new JTextField("10");
    private final JComboBox<String> provider=new JComboBox<>(new String[]{"anthropic","openai","gemini"});
    private final JPasswordField key=new JPasswordField();
    private final JCheckBox login=new JCheckBox("Start work at sign-in (can incur API charges)");
    private final JTextArea logs=new JTextArea(9,65);
    private final JLabel status=new JLabel("Not started. Setup does not spend API credits.");
    private final JLabel fingerprint=new JLabel(" ");
    private final JButton connect=new JButton("Connect in browser"),save=new JButton("Save provider settings"),start=new JButton("Start runner"),pause=new JButton("Pause after current work"),check=new JButton("Check Git and Docker");
    private final java.util.concurrent.atomic.AtomicBoolean busy=new java.util.concurrent.atomic.AtomicBoolean();
    private volatile DesktopConfiguration configuration;
    private DesktopRunner(Path directory,boolean autoStart)throws Exception{
        this.directory=directory;worker=new DesktopWorker(directory);secrets=new DesktopSecretStore(directory);
        if(Files.exists(directory.resolve("config.json"))){configuration=JSON.readValue(directory.resolve("config.json").toFile(),DesktopConfiguration.class);endpoint.setText(configuration.endpoint());provider.setSelectedItem(configuration.provider());model.setText(configuration.model());input.setText(configuration.inputUsdPerMillion().toPlainString());output.setText(configuration.outputUsdPerMillion().toPlainString());}
        if(Files.exists(directory.resolve("endpoint")))endpoint.setText(Files.readString(directory.resolve("endpoint")));
        endpoint.setEditable(!Files.exists(directory.resolve("identity")));
        login.setSelected(configuration!=null&&configuration.startAtLogin());
        JPanel form=new JPanel(new GridLayout(0,2,12,10));
        field(form,"ForgeLoop address",endpoint);field(form,"Runner name",name);form.add(connect);form.add(fingerprint);
        field(form,"Provider",provider);field(form,"Model ID",model);field(form,"API key (stored only on this computer)",key);
        field(form,"Input USD / million tokens",input);field(form,"Output USD / million tokens",output);
        form.add(new JLabel("Sonnet 5 defaults: 2026-09-26 estimates; editable."));form.add(save);
        form.add(login);form.add(new JLabel("Optional; Docker must also start at sign-in."));
        form.add(check);form.add(new JLabel("Docker must be running with Linux containers."));form.add(start);form.add(pause);
        logs.setEditable(false);logs.setLineWrap(true);logs.setWrapStyleWord(true);
        JPanel root=new JPanel(new BorderLayout(12,12));root.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));root.add(form,BorderLayout.NORTH);root.add(new JScrollPane(logs),BorderLayout.CENTER);root.add(status,BorderLayout.SOUTH);frame.setContentPane(root);
        connect.addActionListener(e->pair());save.addActionListener(e->save());check.addActionListener(e->background(()->{prerequisites();log("Git and Docker are ready.");}));
        start.addActionListener(e->{if(JOptionPane.showConfirmDialog(frame,"Start processing eligible issues? Model API calls can incur charges.","Start paid work",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)background(()->{if(configuration==null)throw new IllegalStateException("Save provider settings first");if(!Files.exists(directory.resolve("identity")))throw new IllegalStateException("Connect this runner first");prerequisites();worker.start(configuration,secrets.load(),this::log);log("Runner started.");});});
        pause.addActionListener(e->background(()->{worker.pause();log("Pause requested. Current work will finish before the worker stops.");}));
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter(){@Override public void windowClosing(WindowEvent e){if(worker.running()||busy.get()){JOptionPane.showMessageDialog(frame,"Pause the runner and wait for current work/setup to finish before closing.");return;}System.exit(0);}});
        new Timer(1000,e->{boolean enabled=!busy.get()&&!worker.running();connect.setEnabled(enabled&&!Files.exists(directory.resolve("identity")));save.setEnabled(enabled);check.setEnabled(!busy.get());start.setEnabled(enabled&&configuration!=null&&Files.exists(directory.resolve("identity")));pause.setEnabled(!busy.get()&&worker.running());status.setText(worker.running()?"Running — eligible work can spend API credits":busy.get()?"Setup in progress — no paid work started":"Stopped — no work is being claimed");}).start();
        provider.addActionListener(e->{if(!"anthropic".equals(provider.getSelectedItem())){model.setText("");input.setText("");output.setText("");}});
        frame.pack();frame.setLocationByPlatform(true);frame.setVisible(true);
        if(autoStart&&configuration!=null&&configuration.startAtLogin())background(()->{prerequisites();worker.start(configuration,secrets.load(),this::log);log("Started using your saved sign-in consent.");});
    }
    private static void field(JPanel panel,String label,JComponent component){JLabel text=new JLabel(label);text.setLabelFor(component);panel.add(text);panel.add(component);}
    private RunnerClient client(URI uri){return new RunnerClient(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build(),uri);}
    private void pair(){
        String address=endpoint.getText().trim(),runnerName=name.getText().trim();
        background(()->{URI uri=URI.create(address);PairingRequest request=new PairingRequest();URI approval=request.approvalUri(uri,runnerName);SwingUtilities.invokeLater(()->fingerprint.setText("Match: "+request.fingerprint()));Desktop.getDesktop().browse(approval);RunnerClient client=client(uri);
            long deadline=System.nanoTime()+Duration.ofMinutes(10).toNanos();
            while(System.nanoTime()<deadline){RunnerIdentity identity=client.exchangePairing(request.verifier());if(identity!=null){new RunnerIdentityStore().save(directory.resolve("identity"),identity);DesktopFiles.protect(directory.resolve("identity"));Files.writeString(directory.resolve("endpoint"),address);client.heartbeat(identity);SwingUtilities.invokeLater(()->endpoint.setEditable(false));log("Connected. Save your provider settings, then explicitly start when ready.");return;}Thread.sleep(2000);}
            throw new IllegalStateException("Pairing timed out. Click Connect to try again.");});
    }
    private void save(){
        DesktopConfiguration next;
        try{next=new DesktopConfiguration(endpoint.getText().trim(),(String)provider.getSelectedItem(),model.getText().trim(),new BigDecimal(input.getText().trim()),new BigDecimal(output.getText().trim()),login.isSelected());}
        catch(Exception invalid){log("Check the address, model, and nonnegative prices.");return;}
        char[] password=key.getPassword();key.setText("");
        background(()->{try{if(Files.exists(directory.resolve("endpoint"))&&!Files.readString(directory.resolve("endpoint")).equals(next.endpoint()))throw new IllegalStateException("Address must match this runner's enrollment");if(password.length>0)secrets.save(new String(password));else secrets.load();if(next.startAtLogin()||(configuration!=null&&configuration.startAtLogin()))DesktopLoginStartup.configure(next.startAtLogin());JSON.writeValue(directory.resolve("provider-policy.json").toFile(),next.policy());JSON.writeValue(directory.resolve("config.json").toFile(),next);configuration=next;log("Settings saved. No paid provider call was made.");}finally{java.util.Arrays.fill(password,'\0');}});
    }
    private static void prerequisites()throws Exception{
        for(String[] command:new String[][]{{"git","--version"},{"docker","info","--format","{{.OSType}}"}}){Process process=new ProcessBuilder(command).redirectErrorStream(true).start();if(!process.waitFor(15,java.util.concurrent.TimeUnit.SECONDS)){process.destroyForcibly();throw new IllegalStateException("Git/Docker check timed out; start Docker and retry");}String result=new String(process.getInputStream().readNBytes(8192));if(process.exitValue()!=0||(command[0].equals("docker")&&!result.trim().equals("linux")))throw new IllegalStateException("Install Git and start Docker with Linux containers, then check again");}
    }
    private void background(Work work){if(!busy.compareAndSet(false,true))return;Thread.ofVirtual().start(()->{try{work.run();}catch(Exception error){log("Action failed: "+(error instanceof IllegalArgumentException||error instanceof IllegalStateException?error.getMessage():error.getClass().getSimpleName()+"; check prerequisites and connectivity"));}finally{busy.set(false);}});}
    private void log(String message){SwingUtilities.invokeLater(()->{if(logs.getDocument().getLength()>24000)logs.setText("");logs.append(message+"\n");logs.setCaretPosition(logs.getDocument().getLength());});}
    @FunctionalInterface private interface Work{void run()throws Exception;}
    public static void main(String[] args)throws Exception{
        if(args.length==1&&args[0].equals("--version")){System.out.println("ForgeLoop Runner Desktop 0.1.0");return;}
        Path directory=DesktopFiles.directory();FileChannel channel=FileChannel.open(directory.resolve("desktop.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);FileLock lock=channel.tryLock();
        if(lock==null){channel.close();JOptionPane.showMessageDialog(null,"ForgeLoop Runner is already open.");return;}
        Runtime.getRuntime().addShutdownHook(new Thread(()->{try{lock.release();channel.close();}catch(Exception ignored){}}));
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());SwingUtilities.invokeLater(()->{try{new DesktopRunner(directory,java.util.Arrays.asList(args).contains("--autostart"));}catch(Exception error){JOptionPane.showMessageDialog(null,"Cannot load runner settings. Check your private runner directory permissions.");System.exit(1);}});
    }
}
