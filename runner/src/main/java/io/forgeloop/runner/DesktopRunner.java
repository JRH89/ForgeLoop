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
    private final JTextField endpoint=new JTextField("https://forgeloop.hookerhillstudios.com"),name=new JTextField("My runner"),input=new JTextField(),output=new JTextField();
    private final JComboBox<String> model=new JComboBox<>(new String[]{"claude-sonnet-5"});
    private final JTabbedPane steps=new JTabbedPane();
    private final JComboBox<String> provider=new JComboBox<>(new String[]{"anthropic","openai","gemini"});
    private final JPasswordField key=new JPasswordField();
    private final JCheckBox login=new JCheckBox("Start work at sign-in (can incur API charges)");
    private final JTextArea logs=new JTextArea(9,65);
    private final JLabel status=new JLabel("Not started. Setup does not spend API credits.");
    private final JLabel fingerprint=new JLabel(" ");
    private final JLabel connectionStatus=new JLabel("Not connected yet"),keyStatus=new JLabel("No saved provider settings");
    private final JLabel gitPrerequisite=new JLabel("Not checked"),dockerPrerequisite=new JLabel("Not checked");
    private final JLabel priceStatus=new JLabel("Open Provider to check public model pricing.");
    private final ModelPriceCatalog priceCatalog=new ModelPriceCatalog();
    private volatile ModelPriceCatalog.Quote selectedQuote;
    private volatile boolean manualPrices;
    private volatile boolean lookingUpPrice;
    private final java.util.concurrent.atomic.AtomicLong priceLookupVersion=new java.util.concurrent.atomic.AtomicLong();
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
        if(Files.exists(directory.resolve("config.json"))){configuration=JSON.readValue(directory.resolve("config.json").toFile(),DesktopConfiguration.class);endpoint.setText(configuration.endpoint());provider.setSelectedItem(configuration.provider());model.setSelectedItem(configuration.model());input.setText(configuration.inputUsdPerMillion()==null?"":configuration.inputUsdPerMillion().toPlainString());output.setText(configuration.outputUsdPerMillion()==null?"":configuration.outputUsdPerMillion().toPlainString());manualPrices="manual".equals(configuration.priceSource())||(configuration.priceSource()==null&&configuration.inputUsdPerMillion()!=null);}
        if(Files.exists(directory.resolve("endpoint")))endpoint.setText(Files.readString(directory.resolve("endpoint")));
        endpoint.setEditable(!Files.exists(directory.resolve("identity")));
        if(Files.exists(directory.resolve("runner-name")))name.setText(Files.readString(directory.resolve("runner-name")));
        else if(Files.exists(directory.resolve("identity")))name.setText("Previously connected runner");
        name.setEditable(!Files.exists(directory.resolve("identity")));
        refreshSavedStatus();
        login.setSelected(configuration!=null&&configuration.startAtLogin());
        JPanel connectionFields=new JPanel(new GridLayout(0,2,12,12));
        field(connectionFields,"ForgeLoop address",endpoint);field(connectionFields,"Runner name",name);connectionFields.add(connect);connectionFields.add(fingerprint);
        connectionFields.add(connectionStatus);connectionFields.add(new JLabel("No enrollment token to copy."));
        connectionFields.add(reopen);connectionFields.add(cancelPairing);reopen.setEnabled(false);cancelPairing.setEnabled(false);
        JButton checkConnection=new JButton("Check saved connection");connectionFields.add(checkConnection);connectionFields.add(new JLabel("Heartbeat only - does not claim work"));
        checkConnection.addActionListener(e->background(()->{if(!Files.exists(directory.resolve("identity")))throw new IllegalStateException("Connect this runner first");client(URI.create(endpoint.getText().trim())).heartbeat(new RunnerIdentityStore().load(directory.resolve("identity")));log("Connection verified. No task was claimed and no provider call was made.");SwingUtilities.invokeLater(()->connectionStatus.setText("Connected - heartbeat verified"));}));
        JPanel prerequisitePanel=new JPanel(new GridLayout(0,2,8,8));
        prerequisitePanel.setBorder(BorderFactory.createTitledBorder("Before connecting: Git and Docker with Linux containers are required"));
        prerequisitePanel.add(new JLabel("Git"));prerequisitePanel.add(prerequisiteRow(gitPrerequisite,guideButton("Install Git",DesktopPrerequisites.gitGuide())));
        prerequisitePanel.add(new JLabel("Docker Engine"));prerequisitePanel.add(prerequisiteRow(dockerPrerequisite,guideButton("Install Docker",DesktopPrerequisites.dockerGuide())));
        JButton checkPrerequisites=new JButton("Check requirements");
        prerequisitePanel.add(checkPrerequisites);prerequisitePanel.add(new JLabel("Checks this computer only; no API calls"));
        checkPrerequisites.addActionListener(e->background(()->updatePrerequisites(DesktopPrerequisites.check())));
        JPanel connection=new JPanel(new BorderLayout(12,16));connection.add(prerequisitePanel,BorderLayout.NORTH);connection.add(connectionFields,BorderLayout.CENTER);
        reopen.addActionListener(e->{if(approvalPage!=null)try{Desktop.getDesktop().browse(approvalPage);}catch(Exception failure){log("Could not open your browser. Check your default browser settings.");}});
        cancelPairing.addActionListener(e->{pairingCancelled.set(true);cancelPairing.setEnabled(false);log("Cancelling connection. Please wait for the current request to finish.");});
        JPanel providerForm=new JPanel(new GridLayout(0,2,12,12));
        field(providerForm,"Provider",provider);field(providerForm,"Model (choose or enter an ID)",model);field(providerForm,"API key (only stored on this computer)",key);
        providerForm.add(new JLabel("Credential status"));providerForm.add(keyStatus);
        key.setToolTipText("Leave blank to keep the saved key for this provider. Enter a key only to replace it.");
        JButton checkKey=new JButton("Check saved key locally");providerForm.add(checkKey);providerForm.add(new JLabel("Checks storage, not API credit or key validity"));
        checkKey.addActionListener(e->{String selected=(String)provider.getSelectedItem();background(()->{new DesktopSecretStore(directory,selected).load();log("Saved key is readable from protected storage. No API request was made.");SwingUtilities.invokeLater(()->{if(selected.equals(provider.getSelectedItem()))keyStatus.setText("Saved key readable - no API request made");});});});
        JPanel prices=new JPanel(new GridLayout(0,2,12,12));field(prices,"Input USD / million tokens",input);field(prices,"Output USD / million tokens",output);prices.setVisible(manualPrices);
        JButton advanced=new JButton(manualPrices?"Use automatic prices":"Use manual prices");advanced.setToolTipText("Override public rates for custom models or account-specific pricing.");advanced.addActionListener(e->{manualPrices=!manualPrices;selectedQuote=null;priceLookupVersion.incrementAndGet();lookingUpPrice=false;prices.setVisible(manualPrices);advanced.setText(manualPrices?"Use automatic prices":"Use manual prices");if(!manualPrices)refreshPricing();else priceStatus.setText("Manual override: enter both prices, or leave both blank for N/A.");});
        providerForm.add(new JLabel("Price estimate"));providerForm.add(priceStatus);providerForm.add(new JLabel("Account-specific rates?"));providerForm.add(advanced);providerForm.add(login);providerForm.add(save);
        JPanel providerStep=new JPanel(new BorderLayout(12,12));providerStep.add(providerForm,BorderLayout.NORTH);providerStep.add(prices,BorderLayout.CENTER);
        JPanel controls=new JPanel(new GridLayout(0,2,12,12));controls.add(check);controls.add(new JLabel("Git and Docker are required; Java is bundled."));controls.add(start);controls.add(pause);
        JButton updates=new JButton("Check for updates");updates.addActionListener(e->background(this::checkUpdates));controls.add(updates);controls.add(new JLabel("View version and checksum; installer never launches here."));
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
        connect.addActionListener(e->pair());save.addActionListener(e->save());check.addActionListener(e->background(()->updatePrerequisites(DesktopPrerequisites.check())));
        start.addActionListener(e->{if(JOptionPane.showConfirmDialog(frame,"Start processing eligible issues? Model API calls can incur charges.","Start paid work",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)background(()->{if(configuration==null)throw new IllegalStateException("Save provider settings first");if(!Files.exists(directory.resolve("identity")))throw new IllegalStateException("Connect this runner first");startWorker();log("Runner started.");});});
        pause.addActionListener(e->background(()->{worker.pause();log("Pause requested. Current work will finish before the worker stops.");}));
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter(){@Override public void windowClosing(WindowEvent e){if(worker.running()||busy.get()){JOptionPane.showMessageDialog(frame,"Pause the runner and wait for current work/setup to finish before closing.");return;}System.exit(0);}});
        start.setEnabled(false);pause.setEnabled(false);
        statusTimer=new Timer(1000,e->{
            boolean enabled=!busy.get()&&!worker.running();
            connect.setEnabled(enabled&&!Files.exists(directory.resolve("identity")));save.setEnabled(enabled&&!lookingUpPrice);check.setEnabled(!busy.get());
            start.setEnabled(enabled&&configuration!=null&&Files.exists(directory.resolve("identity")));
            pause.setEnabled(!busy.get()&&worker.running()&&!worker.pausing());
            var snapshot=worker.status();
            status.setText(worker.pausing()?"Pausing - waiting for current work or request to finish":busy.get()&&!worker.running()?"Setup in progress - no paid work started":snapshot.label());
            status.setToolTipText(snapshot.lastContactMillis()==0?"No successful worker poll in this session":"Last successful control-plane poll: "+java.time.Instant.ofEpochMilli(snapshot.lastContactMillis()));
        });statusTimer.start();
        JTextField modelEditor=(JTextField)model.getEditor().getEditorComponent();
        Timer priceDebounce=new Timer(500,e->refreshPricing());priceDebounce.setRepeats(false);
        modelEditor.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){public void insertUpdate(javax.swing.event.DocumentEvent e){changed();}public void removeUpdate(javax.swing.event.DocumentEvent e){changed();}public void changedUpdate(javax.swing.event.DocumentEvent e){changed();}private void changed(){if(steps.getSelectedIndex()!=1)return;if(manualPrices){input.setText("");output.setText("");priceStatus.setText("Model changed — enter both account-specific rates, or leave blank for N/A.");}else queuePriceLookup(priceDebounce);}});
        provider.addActionListener(e->{model.setModel(new DefaultComboBoxModel<>("anthropic".equals(provider.getSelectedItem())?new String[]{"claude-sonnet-5"}:new String[]{""}));refreshSavedStatus();if(steps.getSelectedIndex()==1){if(manualPrices){input.setText("");output.setText("");priceStatus.setText("Provider changed — enter both account-specific rates, or leave blank for N/A.");}else queuePriceLookup(priceDebounce);}});
        steps.addChangeListener(e->{if(steps.getSelectedIndex()==1&&!manualPrices)refreshPricing();});
        frame.setMinimumSize(new Dimension(900,640));frame.pack();frame.setLocationByPlatform(true);frame.setVisible(true);
        if(autoStart&&configuration!=null&&configuration.startAtLogin())background(()->{startWorker();log("Started using your saved sign-in consent.");});
    }
    private static void field(JPanel panel,String label,JComponent component){JLabel text=new JLabel(label);text.setLabelFor(component);panel.add(text);panel.add(component);}
    /** Keep expanded pricing and controls reachable on small or scaled displays. */
    private static JPanel stretchStep(JPanel content){JPanel panel=new JPanel(new BorderLayout());panel.setBorder(BorderFactory.createEmptyBorder(20,12,12,12));panel.add(content,BorderLayout.CENTER);return panel;}
    private JButton guideButton(String label,URI guide){
        JButton button=new JButton(label);
        button.addActionListener(event->{try{Desktop.getDesktop().browse(guide);}catch(Exception failure){JOptionPane.showMessageDialog(frame,"Open this official guide in your browser:\n"+guide,"Installation guide",JOptionPane.INFORMATION_MESSAGE);}});
        return button;
    }
    private static JPanel prerequisiteRow(JLabel detail,JButton guide){
        JPanel row=new JPanel(new BorderLayout(8,0));row.add(detail,BorderLayout.CENTER);row.add(guide,BorderLayout.EAST);return row;
    }
    private void updatePrerequisites(DesktopPrerequisites.Report report){
        SwingUtilities.invokeLater(()->{
            gitPrerequisite.setText(wrapped(report.git().detail()));dockerPrerequisite.setText(wrapped(report.docker().detail()));
            log(report.summary());
        });
    }
    private static String wrapped(String text){return "<html><div style='width:390px'>"+text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")+"</div></html>";}
    private void checkUpdates()throws Exception{
        URI origin=URI.create(endpoint.getText().trim());
        DesktopReleaseCatalog.Release release=DesktopReleaseFeedClient.fetch(origin,DesktopReleaseCatalog.Target.current());
        SwingUtilities.invokeAndWait(()->showUpdateDialog(origin,release));
    }
    /** All Swing dialogs and clipboard actions stay on the event-dispatch thread. */
    private void showUpdateDialog(URI origin,DesktopReleaseCatalog.Release release){
        String installed=DesktopVersion.current();
        String comparison=installed.equals("development")?"This development build has no published installer version."
                :DesktopReleaseCatalog.compareVersions(installed,release.version())<0?"A newer version is available."
                :DesktopReleaseCatalog.compareVersions(installed,release.version())==0?"You are up to date."
                :"The installed package is newer than the latest version in the public feed.";
        boolean workActive=worker.running();
        String report="ForgeLoop Runner update check\n\n"
                +"Status: "+comparison+"\n"
                +"Installed version: "+installed+"\n"
                +"Latest version: "+release.version()+(release.preview()?" (preview)":" (stable)")+"\n"
                +"Platform package: "+release.filename()+"\n"
                +"SHA-256: "+release.sha256()+"\n"
                +"Release notes: "+release.releaseUrl()+"\n"
                +"Package source: "+release.packageUrl()+"\n\n"
                +"Compare the full SHA-256 after downloading. The desktop app will not download or launch an installer.\n"
                +(workActive?"Pause after current work and wait for the runner to stop before opening the installer page.\n"
                        :"To update, open the downloads page, pause work, wait for it to stop, close ForgeLoop Runner, then run the downloaded installer.\n")
                +"Runner identity, API keys, and settings stay in the private data folder.";
        JTextArea details=new JTextArea(report,16,76);details.setEditable(false);details.setLineWrap(true);details.setWrapStyleWord(true);details.setCaretPosition(0);
        Object[] options=workActive?new Object[]{"Copy SHA-256","Close"}:new Object[]{"Open downloads page","Copy SHA-256","Close"};
        int selected=JOptionPane.showOptionDialog(frame,new JScrollPane(details),"Runner updates",
                JOptionPane.DEFAULT_OPTION,JOptionPane.INFORMATION_MESSAGE,null,options,options[options.length-1]);
        if(!workActive&&selected==0){try{Desktop.getDesktop().browse(origin.resolve("/app/runner-downloads"));}catch(Exception failure){log("Could not open the downloads page. Use: "+origin.resolve("/app/runner-downloads"));}}
        else if((workActive&&selected==0)||(!workActive&&selected==1)){
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(release.sha256()),null);
            JOptionPane.showMessageDialog(frame,"SHA-256 copied. Compare all 64 characters.","Checksum copied",JOptionPane.INFORMATION_MESSAGE);
        }
    }
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
        background(()->{try{
            DesktopPrerequisites.Report prerequisites=DesktopPrerequisites.check();updatePrerequisites(prerequisites);
            if(!prerequisites.ready()){
                SwingUtilities.invokeLater(()->JOptionPane.showMessageDialog(frame,prerequisites.summary()+" Install or start the required tools, then select Check requirements.","Runner requirements",JOptionPane.WARNING_MESSAGE));
                return;
            }
            URI uri=URI.create(address);PairingRequest request=new PairingRequest();URI approval=request.approvalUri(uri,runnerName);approvalPage=approval;SwingUtilities.invokeLater(()->{fingerprint.setText("Match: "+request.fingerprint());connectionStatus.setText("Waiting for browser approval");reopen.setEnabled(true);cancelPairing.setEnabled(true);});Desktop.getDesktop().browse(approval);RunnerClient client=client(uri);
            RunnerIdentity identity=new DesktopPairing().await(()->client.exchangePairing(request.verifier()),pairingCancelled::get,Duration.ofMinutes(10));
            if(identity!=null){DesktopFiles.writeAtomic(directory.resolve("endpoint"),address.getBytes(java.nio.charset.StandardCharsets.UTF_8));DesktopFiles.writeAtomic(directory.resolve("runner-name"),runnerName.getBytes(java.nio.charset.StandardCharsets.UTF_8));new RunnerIdentityStore().save(directory.resolve("identity"),identity);DesktopFiles.protect(directory.resolve("identity"));SwingUtilities.invokeLater(()->{endpoint.setEditable(false);name.setEditable(false);refreshSavedStatus();steps.setSelectedIndex(1);});log("Connected. Save your provider settings, then explicitly start when ready.");return;}
            log(pairingCancelled.get()?"Connection cancelled. Click Connect in browser for a fresh request.":"Pairing timed out. Click Connect in browser for a fresh request; close the old browser tab.");
            }finally{approvalPage=null;SwingUtilities.invokeLater(()->{reopen.setEnabled(false);cancelPairing.setEnabled(false);fingerprint.setText(" ");refreshSavedStatus();});}});
    }
    private void save(){
        if(lookingUpPrice){JOptionPane.showMessageDialog(frame,"Wait for the public price lookup to finish, or choose manual prices.");return;}
        DesktopConfiguration next;
        try{
            BigDecimal inputRate=input.getText().isBlank()?null:new BigDecimal(input.getText().trim());
            BigDecimal outputRate=output.getText().isBlank()?null:new BigDecimal(output.getText().trim());
            String source=manualPrices?"manual":selectedQuote!=null?selectedQuote.source():null;
            String checked=manualPrices?null:selectedQuote!=null?selectedQuote.checkedAt():null;
            next=new DesktopConfiguration(endpoint.getText().trim(),(String)provider.getSelectedItem(),model.getEditor().getItem().toString().trim(),inputRate,outputRate,login.isSelected(),source,checked);
        }
        catch(Exception invalid){JOptionPane.showMessageDialog(frame,"Check the address, model, and prices. Leave both prices blank for an unpriced model, or enter both nonnegative rates.");return;}
        char[] password=key.getPassword();key.setText("");
        background(()->{try{if(Files.exists(directory.resolve("endpoint"))&&!Files.readString(directory.resolve("endpoint")).equals(next.endpoint()))throw new IllegalStateException("Address must match this runner's enrollment");var secrets=new DesktopSecretStore(directory,next.provider());if(password.length>0)secrets.save(new String(password));else secrets.load();if(next.startAtLogin()||(configuration!=null&&configuration.startAtLogin()))DesktopLoginStartup.configure(next.startAtLogin());DesktopFiles.writeAtomic(directory.resolve("config.json"),JSON.writeValueAsBytes(next));configuration=next;SwingUtilities.invokeLater(()->{refreshSavedStatus();steps.setSelectedIndex(2);});log("Settings saved. No paid provider call was made.");}finally{java.util.Arrays.fill(password,'\0');}});
    }
    /** Public catalog traffic contains only the selected model, never API keys or repository content. */
    private void refreshPricing(){
        if(manualPrices)return;
        String selectedProvider=(String)provider.getSelectedItem();
        String selectedModel=model.getEditor().getItem().toString().trim();
        long version=priceLookupVersion.incrementAndGet();
        lookingUpPrice=true;selectedQuote=null;input.setText("");output.setText("");
        priceStatus.setText("Checking public model prices…");
        Thread.ofVirtual().start(()->{
            try{
                var found=priceCatalog.find(selectedProvider,selectedModel);
                SwingUtilities.invokeLater(()->{
                    if(version!=priceLookupVersion.get()||manualPrices)return;
                    lookingUpPrice=false;
                    if(found.isEmpty()){priceStatus.setText("N/A — no catalog base rate; manual override is optional.");return;}
                    selectedQuote=found.get();
                    input.setText(selectedQuote.inputUsdPerMillion().stripTrailingZeros().toPlainString());
                    output.setText(selectedQuote.outputUsdPerMillion().stripTrailingZeros().toPlainString());
                    priceStatus.setText("Auto: $"+input.getText()+" / $"+output.getText()+" per 1M · checked "+selectedQuote.checkedAt().substring(0,10));
                    priceStatus.setToolTipText("Community catalog; published provider source: "+selectedQuote.source()+". Base text tokens only; estimates are not invoices.");
                });
            }catch(Exception unavailable){
                SwingUtilities.invokeLater(()->{if(version!=priceLookupVersion.get()||manualPrices)return;lookingUpPrice=false;priceStatus.setText("N/A — public price lookup unavailable. Manual override is optional.");});
            }
        });
    }
    /** Invalidate the previous model's quote as soon as selection changes, before debouncing network traffic. */
    private void queuePriceLookup(Timer debounce){
        priceLookupVersion.incrementAndGet();
        lookingUpPrice=true;
        selectedQuote=null;
        input.setText("");output.setText("");
        priceStatus.setText("Checking public model prices…");
        save.setEnabled(false);
        debounce.restart();
    }
    private void startWorker()throws Exception{if(!Files.exists(directory.resolve("identity")))throw new IllegalStateException("Connect this runner first");DesktopPrerequisites.requireReady();DesktopFiles.writeAtomic(directory.resolve("provider-policy.json"),JSON.writeValueAsBytes(configuration.policy()));worker.start(configuration,new DesktopSecretStore(directory,configuration.provider()).load(),this::log);}
    private void background(Work work){if(!busy.compareAndSet(false,true))return;Thread.ofVirtual().start(()->{try{work.run();}catch(Exception error){SwingUtilities.invokeLater(()->steps.setSelectedIndex(2));log("Action failed: "+(error instanceof IllegalArgumentException||error instanceof IllegalStateException?error.getMessage():error.getClass().getSimpleName()+"; check prerequisites and connectivity"));}finally{busy.set(false);}});}
    private void log(String message){SwingUtilities.invokeLater(()->{if(logs.getDocument().getLength()>24000)logs.setText("");logs.append(message+"\n");logs.setCaretPosition(logs.getDocument().getLength());});}
    @FunctionalInterface private interface Work{void run()throws Exception;}
    public static void main(String[] args)throws Exception{
        if(args.length==1&&args[0].equals("--self-test")){DesktopRuntimeCheck.verify();return;}
        if(args.length==1&&args[0].equals("--version")){System.out.println(DesktopVersion.current());return;}
        Path directory=DesktopFiles.directory();FileChannel channel=FileChannel.open(directory.resolve("desktop.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);FileLock lock=channel.tryLock();
        if(lock==null){channel.close();JOptionPane.showMessageDialog(null,"ForgeLoop Runner is already open.");return;}
        Runtime.getRuntime().addShutdownHook(new Thread(()->{try{lock.release();channel.close();}catch(Exception ignored){}}));
        DesktopTheme.install();SwingUtilities.invokeLater(()->{try{new DesktopRunner(directory,java.util.Arrays.asList(args).contains("--autostart"));}catch(Exception error){JOptionPane.showMessageDialog(null,"Cannot load runner settings. Check your private runner directory permissions.");System.exit(1);}});
    }
}
