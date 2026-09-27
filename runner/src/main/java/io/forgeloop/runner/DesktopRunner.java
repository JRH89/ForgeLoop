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
    private final JFrame frame=new JFrame("ForgeLoop Runner");
    private final JTextField endpoint=new JTextField("https://forgeloop.hookerhillstudios.com"),name=new JTextField("My runner"),input=new JTextField("2"),output=new JTextField("10");
    private final JComboBox<String> model=new JComboBox<>(new String[]{"claude-sonnet-5"});
    private final JTabbedPane steps=new JTabbedPane();
    private final JComboBox<String> provider=new JComboBox<>(new String[]{"anthropic","openai","gemini"});
    private final JPasswordField key=new JPasswordField();
    private final JCheckBox login=new JCheckBox("Start work at sign-in (can incur API charges)");
    private final JTextArea logs=new JTextArea(9,65);
    private final JLabel status=new JLabel("Not started. Setup does not spend API credits.");
    private final JLabel fingerprint=new JLabel(" ");
    private final JLabel connectionStatus=new JLabel("Not connected yet"),keyStatus=new JLabel("No saved provider settings");
    private final JButton reopen=new JButton("Reopen approval page"),cancelPairing=new JButton("Cancel connection");
    private volatile URI approvalPage;
    private final java.util.concurrent.atomic.AtomicBoolean pairingCancelled=new java.util.concurrent.atomic.AtomicBoolean();
    private final JButton connect=new JButton("Connect in browser"),save=new JButton("Save provider settings"),start=new JButton("Start runner"),pause=new JButton("Pause after current work"),check=new JButton("Check Git and Docker");
    private final java.util.concurrent.atomic.AtomicBoolean busy=new java.util.concurrent.atomic.AtomicBoolean();
    private volatile DesktopConfiguration configuration;
    private final Timer statusTimer;
    DesktopRunner(Path directory,boolean autoStart)throws Exception{
        this.directory=directory;worker=new DesktopWorker(directory);
        var icon=DesktopRunner.class.getResource("/desktop/favicon.png");if(icon!=null)frame.setIconImage(new ImageIcon(icon).getImage());
        model.setEditable(true);
        if(Files.exists(directory.resolve("config.json"))){configuration=JSON.readValue(directory.resolve("config.json").toFile(),DesktopConfiguration.class);endpoint.setText(configuration.endpoint());provider.setSelectedItem(configuration.provider());model.setSelectedItem(configuration.model());input.setText(configuration.inputUsdPerMillion().toPlainString());output.setText(configuration.outputUsdPerMillion().toPlainString());}
        if(Files.exists(directory.resolve("endpoint")))endpoint.setText(Files.readString(directory.resolve("endpoint")));
        endpoint.setEditable(!Files.exists(directory.resolve("identity")));
        if(Files.exists(directory.resolve("runner-name")))name.setText(Files.readString(directory.resolve("runner-name")));
        else if(Files.exists(directory.resolve("identity")))name.setText("Previously connected runner");
        name.setEditable(!Files.exists(directory.resolve("identity")));
        refreshSavedStatus();
        login.setSelected(configuration!=null&&configuration.startAtLogin());
        JPanel connection=new JPanel(new GridLayout(0,2,12,12));
        field(connection,"ForgeLoop address",endpoint);field(connection,"Runner name",name);connection.add(connect);connection.add(fingerprint);
        connection.add(connectionStatus);connection.add(new JLabel("No enrollment token to copy."));
        connection.add(reopen);connection.add(cancelPairing);reopen.setEnabled(false);cancelPairing.setEnabled(false);
        JButton checkConnection=new JButton("Check saved connection");connection.add(checkConnection);connection.add(new JLabel("Heartbeat only - does not claim work"));
        checkConnection.addActionListener(e->background(()->{if(!Files.exists(directory.resolve("identity")))throw new IllegalStateException("Connect this runner first");client(URI.create(endpoint.getText().trim())).heartbeat(new RunnerIdentityStore().load(directory.resolve("identity")));log("Connection verified. No task was claimed and no provider call was made.");SwingUtilities.invokeLater(()->connectionStatus.setText("Connected - heartbeat verified"));}));
        reopen.addActionListener(e->{if(approvalPage!=null)try{Desktop.getDesktop().browse(approvalPage);}catch(Exception failure){log("Could not open your browser. Check your default browser settings.");}});
        cancelPairing.addActionListener(e->{pairingCancelled.set(true);cancelPairing.setEnabled(false);log("Cancelling connection. Please wait for the current request to finish.");});
        JPanel providerForm=new JPanel(new GridLayout(0,2,12,12));
        field(providerForm,"Provider",provider);field(providerForm,"Model (choose or enter an ID)",model);field(providerForm,"API key (only stored on this computer)",key);
        providerForm.add(new JLabel("Credential status"));providerForm.add(keyStatus);
        key.setToolTipText("Leave blank to keep the saved key for this provider. Enter a key only to replace it.");
        JButton checkKey=new JButton("Check saved key locally");providerForm.add(checkKey);providerForm.add(new JLabel("Checks storage, not API credit or key validity"));
        checkKey.addActionListener(e->{String selected=(String)provider.getSelectedItem();background(()->{new DesktopSecretStore(directory,selected).load();log("Saved key is readable from protected storage. No API request was made.");SwingUtilities.invokeLater(()->{if(selected.equals(provider.getSelectedItem()))keyStatus.setText("Saved key readable - no API request made");});});});
        JPanel prices=new JPanel(new GridLayout(0,2,12,12));field(prices,"Input USD / million tokens",input);field(prices,"Output USD / million tokens",output);prices.setVisible(false);
        JButton advanced=new JButton("Pricing overrides");advanced.setToolTipText("Standard Sonnet 5 estimates verified 2026-09-26. Review account-specific prices before paid work.");advanced.addActionListener(e->prices.setVisible(!prices.isVisible()));providerForm.add(new JLabel("Sonnet 5: $2 input / $10 output per million."));providerForm.add(advanced);providerForm.add(login);providerForm.add(save);
        JPanel providerStep=new JPanel(new BorderLayout(12,12));providerStep.add(providerForm,BorderLayout.NORTH);providerStep.add(prices,BorderLayout.CENTER);
        JPanel controls=new JPanel(new GridLayout(0,2,12,12));controls.add(check);controls.add(new JLabel("Git and Docker are required; Java is bundled."));controls.add(start);controls.add(pause);
        JButton updates=new JButton("Downloads / updates");updates.addActionListener(e->{URI uri=URI.create(endpoint.getText().trim());background(()->{PairingRequest.validateEndpoint(uri);Desktop.getDesktop().browse(uri.resolve("/app/runner-downloads"));});});controls.add(updates);controls.add(new JLabel("Pause before installing updates; local state is retained."));
        JButton diagnostics=new JButton("Export safe diagnostics");controls.add(diagnostics);controls.add(new JLabel("No API keys, credentials, or task logs included"));
        diagnostics.addActionListener(e->{JFileChooser chooser=new JFileChooser();chooser.setSelectedFile(new java.io.File("forgeloop-diagnostics.txt"));if(chooser.showSaveDialog(frame)==JFileChooser.APPROVE_OPTION){Path target=chooser.getSelectedFile().toPath();if(Files.exists(target)&&JOptionPane.showConfirmDialog(frame,"Replace the existing diagnostics file?","Confirm replacement",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;String report=DesktopDiagnostics.summary(directory,configuration,worker.running());background(()->{DesktopFiles.writeAtomic(target.toAbsolutePath(),report.getBytes(java.nio.charset.StandardCharsets.UTF_8));log("Safe diagnostics exported. No secret material or raw logs were included.");});}});
        logs.setEditable(false);logs.setLineWrap(true);logs.setWrapStyleWord(true);
        logs.setFont(new Font(Font.MONOSPACED,Font.PLAIN,12));logs.setMargin(new Insets(16,16,16,16));
        JPanel runStep=new JPanel(new BorderLayout(12,12));runStep.add(controls,BorderLayout.NORTH);runStep.add(new JScrollPane(logs),BorderLayout.CENTER);
        steps.addTab("1. Connect",step(connection));steps.addTab("2. Provider",step(providerStep));steps.addTab("3. Run",stretchStep(runStep));
        if(Files.exists(directory.resolve("identity")))steps.setSelectedIndex(configuration==null?1:2);
        JPanel header=new JPanel(new BorderLayout(0,8));header.add(DesktopTheme.heading("ForgeLoop Runner"),BorderLayout.NORTH);header.add(new JLabel("Your machine. Your API keys. You control when work starts."),BorderLayout.SOUTH);
        JPanel root=new JPanel(new BorderLayout(20,24));root.setBorder(BorderFactory.createEmptyBorder(28,28,20,28));root.add(header,BorderLayout.NORTH);root.add(steps,BorderLayout.CENTER);root.add(status,BorderLayout.SOUTH);frame.setContentPane(root);
        DesktopTheme.primary(connect);DesktopTheme.primary(save);DesktopTheme.primary(start);
        if(configuration!=null)log("Saved settings restored. Your API key stays hidden; leave its field blank to keep it.");
        if(Files.exists(directory.resolve("identity")))log("Existing connection restored. You do not need to connect again.");
        connect.addActionListener(e->pair());save.addActionListener(e->save());check.addActionListener(e->background(()->{prerequisites();log("Git and Docker are ready.");}));
        start.addActionListener(e->{if(JOptionPane.showConfirmDialog(frame,"Start processing eligible issues? Model API calls can incur charges.","Start paid work",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)background(()->{if(configuration==null)throw new IllegalStateException("Save provider settings first");if(!Files.exists(directory.resolve("identity")))throw new IllegalStateException("Connect this runner first");startWorker();log("Runner started.");});});
        pause.addActionListener(e->background(()->{worker.pause();log("Pause requested. Current work will finish before the worker stops.");}));
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter(){@Override public void windowClosing(WindowEvent e){if(worker.running()||busy.get()){JOptionPane.showMessageDialog(frame,"Pause the runner and wait for current work/setup to finish before closing.");return;}System.exit(0);}});
        start.setEnabled(false);pause.setEnabled(false);
        statusTimer=new Timer(1000,e->{
            boolean enabled=!busy.get()&&!worker.running();
            connect.setEnabled(enabled&&!Files.exists(directory.resolve("identity")));save.setEnabled(enabled);check.setEnabled(!busy.get());
            start.setEnabled(enabled&&configuration!=null&&Files.exists(directory.resolve("identity")));
            pause.setEnabled(!busy.get()&&worker.running()&&!worker.pausing());
            var snapshot=worker.status();
            status.setText(worker.pausing()?"Pausing - waiting for current work or request to finish":busy.get()&&!worker.running()?"Setup in progress - no paid work started":snapshot.label());
            status.setToolTipText(snapshot.lastContactMillis()==0?"No successful worker poll in this session":"Last successful control-plane poll: "+java.time.Instant.ofEpochMilli(snapshot.lastContactMillis()));
        });statusTimer.start();
        JTextField modelEditor=(JTextField)model.getEditor().getEditorComponent();
        modelEditor.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){public void insertUpdate(javax.swing.event.DocumentEvent e){changed();}public void removeUpdate(javax.swing.event.DocumentEvent e){changed();}public void changedUpdate(javax.swing.event.DocumentEvent e){changed();}private void changed(){boolean preset="anthropic".equals(provider.getSelectedItem())&&"claude-sonnet-5".equals(modelEditor.getText());input.setText(preset?"2":"");output.setText(preset?"10":"");prices.setVisible(!preset);}});
        provider.addActionListener(e->{model.setModel(new DefaultComboBoxModel<>("anthropic".equals(provider.getSelectedItem())?new String[]{"claude-sonnet-5"}:new String[]{""}));refreshSavedStatus();});
        frame.setMinimumSize(new Dimension(900,640));frame.pack();frame.setLocationByPlatform(true);frame.setVisible(true);
        if(autoStart&&configuration!=null&&configuration.startAtLogin())background(()->{startWorker();log("Started using your saved sign-in consent.");});
    }
    private static void field(JPanel panel,String label,JComponent component){JLabel text=new JLabel(label);text.setLabelFor(component);panel.add(text);panel.add(component);}
    /** Keep expanded pricing and controls reachable on small or scaled displays. */
    private static JPanel stretchStep(JPanel content){JPanel panel=new JPanel(new BorderLayout());panel.setBorder(BorderFactory.createEmptyBorder(20,12,12,12));panel.add(content,BorderLayout.CENTER);return panel;}
    private static JPanel step(JPanel content){
        JPanel body=new JPanel(new BorderLayout());body.setBorder(BorderFactory.createEmptyBorder(20,12,12,12));body.add(content,BorderLayout.NORTH);
        JScrollPane scroll=new JScrollPane(body,JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setPreferredSize(new Dimension(820,430));
        JPanel panel=new JPanel(new BorderLayout());panel.add(scroll,BorderLayout.CENTER);return panel;
    }
    /** UI verification may dispose an idle window without terminating its test JVM. */
    void disposeIdle(){if(worker.running()||busy.get())throw new IllegalStateException("Cannot dispose active runner");statusTimer.stop();frame.dispose();}
    JFrame window(){return frame;}
    private RunnerClient client(URI uri){return new RunnerClient(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build(),uri);}
    /** Never decrypt credentials just to render the UI; distinguish configured from verified. */
    private void refreshSavedStatus(){
        connectionStatus.setText(Files.exists(directory.resolve("identity"))?"Connected - saved on this computer":"Not connected yet");
        keyStatus.setText(configuration!=null&&configuration.provider().equals(provider.getSelectedItem())?"Saved key configured - leave blank to keep":"Enter a key for this provider");
    }
    private void pair(){
        String address=endpoint.getText().trim(),runnerName=name.getText().trim();
        pairingCancelled.set(false);
        background(()->{try{URI uri=URI.create(address);PairingRequest request=new PairingRequest();URI approval=request.approvalUri(uri,runnerName);approvalPage=approval;SwingUtilities.invokeLater(()->{fingerprint.setText("Match: "+request.fingerprint());connectionStatus.setText("Waiting for browser approval");reopen.setEnabled(true);cancelPairing.setEnabled(true);});Desktop.getDesktop().browse(approval);RunnerClient client=client(uri);
            RunnerIdentity identity=new DesktopPairing().await(()->client.exchangePairing(request.verifier()),pairingCancelled::get,Duration.ofMinutes(10));
            if(identity!=null){DesktopFiles.writeAtomic(directory.resolve("endpoint"),address.getBytes(java.nio.charset.StandardCharsets.UTF_8));DesktopFiles.writeAtomic(directory.resolve("runner-name"),runnerName.getBytes(java.nio.charset.StandardCharsets.UTF_8));new RunnerIdentityStore().save(directory.resolve("identity"),identity);DesktopFiles.protect(directory.resolve("identity"));SwingUtilities.invokeLater(()->{endpoint.setEditable(false);name.setEditable(false);refreshSavedStatus();steps.setSelectedIndex(1);});log("Connected. Save your provider settings, then explicitly start when ready.");return;}
            log(pairingCancelled.get()?"Connection cancelled. Click Connect in browser for a fresh request.":"Pairing timed out. Click Connect in browser for a fresh request; close the old browser tab.");
            }finally{approvalPage=null;SwingUtilities.invokeLater(()->{reopen.setEnabled(false);cancelPairing.setEnabled(false);fingerprint.setText(" ");refreshSavedStatus();});}});
    }
    private void save(){
        DesktopConfiguration next;
        try{next=new DesktopConfiguration(endpoint.getText().trim(),(String)provider.getSelectedItem(),model.getEditor().getItem().toString().trim(),new BigDecimal(input.getText().trim()),new BigDecimal(output.getText().trim()),login.isSelected());}
        catch(Exception invalid){JOptionPane.showMessageDialog(frame,"Check the address, model, and nonnegative prices. Custom models need explicit input/output prices.");return;}
        char[] password=key.getPassword();key.setText("");
        background(()->{try{if(Files.exists(directory.resolve("endpoint"))&&!Files.readString(directory.resolve("endpoint")).equals(next.endpoint()))throw new IllegalStateException("Address must match this runner's enrollment");var secrets=new DesktopSecretStore(directory,next.provider());if(password.length>0)secrets.save(new String(password));else secrets.load();if(next.startAtLogin()||(configuration!=null&&configuration.startAtLogin()))DesktopLoginStartup.configure(next.startAtLogin());DesktopFiles.writeAtomic(directory.resolve("config.json"),JSON.writeValueAsBytes(next));configuration=next;SwingUtilities.invokeLater(()->{refreshSavedStatus();steps.setSelectedIndex(2);});log("Settings saved. No paid provider call was made.");}finally{java.util.Arrays.fill(password,'\0');}});
    }
    private void startWorker()throws Exception{if(!Files.exists(directory.resolve("identity")))throw new IllegalStateException("Connect this runner first");prerequisites();DesktopFiles.writeAtomic(directory.resolve("provider-policy.json"),JSON.writeValueAsBytes(configuration.policy()));worker.start(configuration,new DesktopSecretStore(directory,configuration.provider()).load(),this::log);}
    private static void prerequisites()throws Exception{
        for(String[] command:new String[][]{{"git","--version"},{"docker","info","--format","{{.OSType}}"}}){String tool=command[0];command[0]=DesktopToolPaths.executable(tool);Process process=new ProcessBuilder(command).redirectErrorStream(true).start();if(!process.waitFor(15,java.util.concurrent.TimeUnit.SECONDS)){process.destroyForcibly();throw new IllegalStateException("Git/Docker check timed out; start Docker and retry");}String result=new String(process.getInputStream().readNBytes(8192));if(process.exitValue()!=0||(tool.equals("docker")&&!result.trim().equals("linux")))throw new IllegalStateException("Install Git and start Docker with Linux containers, then check again");}
    }
    private void background(Work work){if(!busy.compareAndSet(false,true))return;Thread.ofVirtual().start(()->{try{work.run();}catch(Exception error){SwingUtilities.invokeLater(()->steps.setSelectedIndex(2));log("Action failed: "+(error instanceof IllegalArgumentException||error instanceof IllegalStateException?error.getMessage():error.getClass().getSimpleName()+"; check prerequisites and connectivity"));}finally{busy.set(false);}});}
    private void log(String message){SwingUtilities.invokeLater(()->{if(logs.getDocument().getLength()>24000)logs.setText("");logs.append(message+"\n");logs.setCaretPosition(logs.getDocument().getLength());});}
    @FunctionalInterface private interface Work{void run()throws Exception;}
    public static void main(String[] args)throws Exception{
        if(args.length==1&&args[0].equals("--self-test")){DesktopRuntimeCheck.verify();return;}
        if(args.length==1&&args[0].equals("--version")){System.out.println("ForgeLoop Runner Desktop 0.1.0");return;}
        Path directory=DesktopFiles.directory();FileChannel channel=FileChannel.open(directory.resolve("desktop.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);FileLock lock=channel.tryLock();
        if(lock==null){channel.close();JOptionPane.showMessageDialog(null,"ForgeLoop Runner is already open.");return;}
        Runtime.getRuntime().addShutdownHook(new Thread(()->{try{lock.release();channel.close();}catch(Exception ignored){}}));
        DesktopTheme.install();SwingUtilities.invokeLater(()->{try{new DesktopRunner(directory,java.util.Arrays.asList(args).contains("--autostart"));}catch(Exception error){JOptionPane.showMessageDialog(null,"Cannot load runner settings. Check your private runner directory permissions.");System.exit(1);}});
    }
}
