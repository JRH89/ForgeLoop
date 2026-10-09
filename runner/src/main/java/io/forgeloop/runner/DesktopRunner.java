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
    private final DesktopPages steps=new DesktopPages();
    private final JPanel recoveryControls=DesktopLayout.stack(8);
    private final JComboBox<String> provider=new JComboBox<>(new String[]{"anthropic","openai","gemini"});
    private final JPasswordField key=new JPasswordField();
    private final JCheckBox login=new JCheckBox("Start work at sign-in");
    private final JTextArea logs=new JTextArea(9,65);
    private final JTextArea status=DesktopLayout.text("Not started. Setup does not spend API credits.");
    private final JTextArea notice=DesktopLayout.text("");
    private final JTextArea runnerState=DesktopLayout.text("Ready when you are");
    private final JTextArea runnerDetails=DesktopLayout.muted("Connect this computer and save a provider to begin.");
    private final JTextArea modelSummary=DesktopLayout.text("No provider saved");
    private final JTextArea connectionSummary=DesktopLayout.text("Not connected");
    private final JButton reconnect=new JButton("Reconnect to ForgeLoop");
    private final JTextArea fingerprint=DesktopLayout.text(" ");
    private final JTextArea connectionStatus=DesktopLayout.text("Not connected yet"),keyStatus=DesktopLayout.text("No saved provider settings");
    private final JTextArea gitPrerequisite=DesktopLayout.text("Not checked"),dockerPrerequisite=DesktopLayout.text("Not checked");
    private final JTextArea priceStatus=DesktopLayout.text("Open Provider to check public model pricing.");
    private final ModelPriceCatalog priceCatalog=new ModelPriceCatalog();
    private volatile ModelPriceCatalog.Quote selectedQuote;
    private volatile boolean manualPrices;
    private volatile boolean lookingUpPrice;
    private final java.util.concurrent.atomic.AtomicLong priceLookupVersion=new java.util.concurrent.atomic.AtomicLong();
    private final JButton reopen=new JButton("Reopen approval page"),cancelPairing=new JButton("Cancel connection");
    private volatile URI approvalPage;
    private final java.util.concurrent.atomic.AtomicBoolean pairingCancelled=new java.util.concurrent.atomic.AtomicBoolean();
    private final JButton connect=new JButton("Connect in browser"),save=new JButton("Save provider settings"),start=new JButton("Start runner"),pause=new JButton("Pause runner"),check=new JButton("Check Git and Docker");
    private final java.util.concurrent.atomic.AtomicBoolean busy=new java.util.concurrent.atomic.AtomicBoolean();
    private final JButton cancelDockerStartup=new JButton("Cancel Docker startup");
    private final java.util.concurrent.atomic.AtomicBoolean dockerStartupCancelled=new java.util.concurrent.atomic.AtomicBoolean();
    private final Object startupLock=new Object();
    private volatile boolean preparingDocker;
    private volatile String setupStatus="Setup in progress - no paid work started";
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
        JPanel connectionFields=DesktopLayout.stack(14);
        connectionFields.add(DesktopLayout.field("ForgeLoop address",endpoint,null));
        connectionFields.add(DesktopLayout.field("Runner name",name,null));
        connectionFields.add(connectionStatus);
        connectionFields.add(DesktopLayout.actions(connect));
        connectionFields.add(fingerprint);fingerprint.setVisible(false);
        connectionFields.add(DesktopLayout.muted("Approve this computer in your browser and compare the fingerprint."));
        connectionFields.add(DesktopLayout.actions(reopen,cancelPairing));reopen.setEnabled(false);cancelPairing.setEnabled(false);reopen.setVisible(false);cancelPairing.setVisible(false);
        JButton checkConnection=new JButton("Check saved connection");
        checkConnection.setEnabled(Files.exists(directory.resolve("identity")));
        reconnect.setEnabled(Files.exists(directory.resolve("identity")));
        connect.setEnabled(!Files.exists(directory.resolve("identity")));
        recoveryControls.add(DesktopLayout.actions(checkConnection,reconnect));
        recoveryControls.add(DesktopLayout.muted("Verify without claiming work. Reconnect keeps your provider key and settings."));
        recoveryControls.setVisible(Files.exists(directory.resolve("identity")));
        connectionFields.add(recoveryControls);
        reconnect.addActionListener(e->{
            if(worker.running()||busy.get())return;
            if(JOptionPane.showConfirmDialog(frame,"Reset this computer's saved connection and pair again? Your provider key and settings will be kept. The old identity is backed up locally.","Reconnect runner",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
            background(()->{DesktopConnectionRecovery.reset(directory);SwingUtilities.invokeLater(()->{endpoint.setEditable(true);name.setEditable(true);fingerprint.setText(" ");refreshSavedStatus();steps.setSelectedIndex(0);});log("Saved connection reset. Click Connect in browser to pair with your server.");});
        });
        checkConnection.addActionListener(e->background(()->{
            if(!Files.exists(directory.resolve("identity")))throw new IllegalStateException("Connect this runner first");
            try{client(URI.create(endpoint.getText().trim())).heartbeat(new RunnerIdentityStore().load(directory.resolve("identity")));log("Connection verified. No task was claimed and no provider call was made.");SwingUtilities.invokeLater(()->connectionStatus.setText("Connected - heartbeat verified"));}
            catch(Exception failure){SwingUtilities.invokeLater(()->{connectionStatus.setText("Connection could not be verified. Check the server address or reconnect.");steps.setSelectedIndex(0);});throw failure;}
        }));
        JPanel prerequisitePanel=DesktopLayout.stack(16);
        prerequisitePanel.add(DesktopLayout.field("Git",gitPrerequisite,null));
        prerequisitePanel.add(DesktopLayout.field("Docker Engine",dockerPrerequisite,null));
        JButton checkPrerequisites=new JButton("Check requirements");
        prerequisitePanel.add(DesktopLayout.actions(checkPrerequisites,guideButton("Install Git",DesktopPrerequisites.gitGuide()),guideButton("Install Docker",DesktopPrerequisites.dockerGuide())));
        prerequisitePanel.add(DesktopLayout.text("Checks this computer only; no API calls."));
        checkPrerequisites.addActionListener(e->background(()->updatePrerequisites(DesktopPrerequisites.check())));
        JPanel connection=DesktopLayout.page("Connect your runner","Link this computer to your ForgeLoop account.",
                DesktopLayout.section("Connection",null,connectionFields),
                DesktopLayout.section("This computer","Git and Docker with Linux containers are required. Java is bundled.",prerequisitePanel));
        reopen.addActionListener(e->{if(approvalPage!=null)try{Desktop.getDesktop().browse(approvalPage);}catch(Exception failure){log("Could not open your browser. Check your default browser settings.");}});
        cancelPairing.addActionListener(e->{pairingCancelled.set(true);cancelPairing.setEnabled(false);log("Cancelling connection. Please wait for the current request to finish.");});
        JPanel providerForm=DesktopLayout.stack(16);
        providerForm.add(DesktopLayout.field("Provider",provider,null));
        providerForm.add(DesktopLayout.field("Model (choose or enter an ID)",model,null));
        providerForm.add(DesktopLayout.field("API key",key,"Stored only on this computer. Leave blank to keep your saved key; enter a key to replace it."));
        providerForm.add(keyStatus);
        key.setToolTipText("Leave blank to keep the saved key for this provider. Enter a key only to replace it.");
        JButton checkKey=new JButton("Check saved key locally");providerForm.add(DesktopLayout.actions(checkKey));providerForm.add(DesktopLayout.text("Checks protected storage, not API credit or key validity."));
        checkKey.addActionListener(e->{String selected=(String)provider.getSelectedItem();background(()->{new DesktopSecretStore(directory,selected).load();log("Saved key is readable from protected storage. No API request was made.");SwingUtilities.invokeLater(()->{if(selected.equals(provider.getSelectedItem()))keyStatus.setText("Saved key readable - no API request made");});});});
        JPanel prices=DesktopLayout.stack(16);prices.add(DesktopLayout.field("Input USD / million tokens",input,null));prices.add(DesktopLayout.field("Output USD / million tokens",output,null));prices.setVisible(manualPrices);
        JButton advanced=new JButton(manualPrices?"Use automatic prices":"Use manual prices");advanced.setToolTipText("Override public rates for custom models or account-specific pricing.");advanced.addActionListener(e->{manualPrices=!manualPrices;selectedQuote=null;priceLookupVersion.incrementAndGet();lookingUpPrice=false;prices.setVisible(manualPrices);advanced.setText(manualPrices?"Use automatic prices":"Use manual prices");if(!manualPrices)refreshPricing();else priceStatus.setText("Manual override: enter both prices, or leave both blank for N/A.");});
        JPanel pricing=DesktopLayout.stack(16);pricing.add(priceStatus);pricing.add(DesktopLayout.actions(advanced));pricing.add(prices);
        JPanel consent=DesktopLayout.stack(12);consent.add(DesktopLayout.actions(login));consent.add(DesktopLayout.text("Optional: automatically process eligible work when you sign in. Model API calls can incur charges."));consent.add(DesktopLayout.actions(save));
        JPanel providerStep=DesktopLayout.page("Model & provider","Choose the model that will do the work. Credentials stay on this computer.",
                DesktopLayout.section("Provider settings",null,providerForm),
                DesktopLayout.section("Token pricing","Public base rates are looked up automatically.",pricing),
                DesktopLayout.section("Save your settings",null,consent));
        JPanel controls=DesktopLayout.stack(16);
        runnerState.setFont(runnerState.getFont().deriveFont(Font.BOLD,24f));
        controls.add(runnerState);controls.add(runnerDetails);controls.add(DesktopLayout.actions(start,pause));
        controls.add(DesktopLayout.muted("Starting processes eligible issues and can use API credits. Docker starts automatically if needed."));
        controls.add(DesktopLayout.actions(cancelDockerStartup));cancelDockerStartup.setVisible(false);
        cancelDockerStartup.setEnabled(false);
        cancelDockerStartup.addActionListener(e->{
            // Serialize cancellation with the final worker transition: never report cancellation after work starts.
            synchronized(startupLock){
                if(!preparingDocker)return;
                dockerStartupCancelled.set(true);cancelDockerStartup.setEnabled(false);
                setupStatus="Cancelling Docker startup - no paid work started";
                log("Cancelling Docker startup. The runner will remain stopped; Docker may remain running.");
            }
        });
        JPanel utilities=DesktopLayout.stack(12);utilities.add(DesktopLayout.actions(check));utilities.add(DesktopLayout.text("Git and Docker are required; Java is bundled."));
        JButton updates=new JButton("Check for updates");updates.addActionListener(e->background(this::checkUpdates));utilities.add(DesktopLayout.actions(updates));utilities.add(DesktopLayout.text("View version and checksum. The installer never launches here."));
        JButton diagnostics=new JButton("Export safe diagnostics");utilities.add(DesktopLayout.actions(diagnostics));utilities.add(DesktopLayout.text("No API keys, credentials, or task logs included."));
        diagnostics.addActionListener(e->{JFileChooser chooser=new JFileChooser();chooser.setSelectedFile(new java.io.File("forgeloop-diagnostics.txt"));if(chooser.showSaveDialog(frame)==JFileChooser.APPROVE_OPTION){Path target=chooser.getSelectedFile().toPath();if(Files.exists(target)&&JOptionPane.showConfirmDialog(frame,"Replace the existing diagnostics file?","Confirm replacement",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;String report=DesktopDiagnostics.summary(directory,configuration,worker.running());background(()->{DesktopFiles.writeAtomic(target.toAbsolutePath(),report.getBytes(java.nio.charset.StandardCharsets.UTF_8));log("Safe diagnostics exported. No secret material or raw logs were included.");});}});
        logs.setEditable(false);logs.setLineWrap(true);logs.setWrapStyleWord(true);
        logs.setFont(new Font(Font.MONOSPACED,Font.PLAIN,12));logs.setMargin(new Insets(16,16,16,16));
        JScrollPane logScroll=new JScrollPane(logs,JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        logScroll.setPreferredSize(new Dimension(1,340));
        JPanel overview=DesktopLayout.stack(14);
        overview.add(DesktopLayout.field("Connection",connectionSummary,null));
        overview.add(DesktopLayout.field("Model",modelSummary,"Change your model or API key in Provider."));
        JButton connectionLink=new JButton("Open connection settings");connectionLink.addActionListener(e->steps.setSelectedIndex(0));
        JButton providerLink=new JButton("Open provider settings");providerLink.addActionListener(e->steps.setSelectedIndex(1));
        overview.add(DesktopLayout.actions(connectionLink,providerLink));
        JPanel runStep=DesktopLayout.page("Runner","Your machine does the work. ForgeLoop coordinates it.",
                DesktopLayout.section("Execution",null,controls),DesktopLayout.section("Setup",null,overview));
        JButton clearLogs=new JButton("Clear activity view");clearLogs.addActionListener(e->logs.setText(""));
        JPanel activity=DesktopLayout.stack(12);activity.add(logScroll);activity.add(DesktopLayout.actions(clearLogs));
        JPanel activityStep=DesktopLayout.page("Activity","Connection, setup, and execution messages for this session.",
                DesktopLayout.section("Session log",null,activity),DesktopLayout.section("Maintenance",null,utilities));
        steps.addPage(DesktopLayout.scroll(connection));steps.addPage(DesktopLayout.scroll(providerStep));steps.addPage(DesktopLayout.scroll(runStep));steps.addPage(DesktopLayout.scroll(activityStep));
        if(Files.exists(directory.resolve("identity")))steps.setSelectedIndex(configuration==null?1:2);
        status.setForeground(DesktopTheme.MUTED);
        JPanel footerState=DesktopLayout.stack(6);footerState.add(status);footerState.add(notice);
        notice.setForeground(new Color(0xF3B36B));notice.setVisible(false);
        frame.setContentPane(new DesktopShell(steps,footerState));
        DesktopTheme.primary(connect);DesktopTheme.primary(save);DesktopTheme.primary(start);
        pause.setToolTipText("Finish current work, then stop claiming tasks.");
        runnerState.setText("Runner stopped");
        if(configuration!=null)log("Saved settings restored. Your API key stays hidden; leave its field blank to keep it.");
        if(Files.exists(directory.resolve("identity")))log("Saved connection restored. Verify the connection if your server has changed.");
        connect.addActionListener(e->pair());save.addActionListener(e->save());check.addActionListener(e->background(()->updatePrerequisites(DesktopPrerequisites.check())));
        start.addActionListener(e->{if(JOptionPane.showConfirmDialog(frame,"Start processing eligible issues? ForgeLoop will try to start local Docker if needed. Model API calls can incur charges once the runner starts.","Start paid work",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)background(()->{startWorker();log("Runner started.");});});
        pause.addActionListener(e->background(()->{worker.pause();log("Pause requested. Current work will finish before the worker stops.");}));
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter(){@Override public void windowClosing(WindowEvent e){if(worker.running()||busy.get()){JOptionPane.showMessageDialog(frame,"Pause the runner and wait for current work/setup to finish before closing.");return;}System.exit(0);}});
        start.setEnabled(false);pause.setEnabled(false);
        statusTimer=new Timer(1000,e->{
            boolean enabled=!busy.get()&&!worker.running();
            connect.setEnabled(enabled&&!Files.exists(directory.resolve("identity")));save.setEnabled(enabled&&!lookingUpPrice);check.setEnabled(!busy.get());
            reconnect.setEnabled(enabled&&Files.exists(directory.resolve("identity")));
            checkConnection.setEnabled(enabled&&Files.exists(directory.resolve("identity")));
            start.setEnabled(enabled&&configuration!=null&&configuration.endpoint().equals(endpoint.getText().trim())&&Files.exists(directory.resolve("identity")));
            pause.setEnabled(!busy.get()&&worker.running()&&!worker.pausing());
            cancelDockerStartup.setEnabled(busy.get()&&preparingDocker&&!dockerStartupCancelled.get());
            cancelDockerStartup.setVisible(preparingDocker);
            var snapshot=worker.status();
            status.setText(worker.pausing()?"Pausing - waiting for current work or request to finish":busy.get()&&!worker.running()?setupStatus:snapshot.label());
            runnerState.setText(worker.pausing()?"Finishing current work":busy.get()?"Preparing runner":worker.running()?snapshot.label():"Runner stopped");
            runnerDetails.setText(worker.running()?"Follow execution messages in Activity.":"Start when your connection and provider are ready.");
            status.setToolTipText(snapshot.lastContactMillis()==0?"No successful worker poll in this session":"Last successful control-plane poll: "+java.time.Instant.ofEpochMilli(snapshot.lastContactMillis()));
        });statusTimer.start();
        JTextField modelEditor=(JTextField)model.getEditor().getEditorComponent();
        Timer priceDebounce=new Timer(500,e->refreshPricing());priceDebounce.setRepeats(false);
        modelEditor.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){public void insertUpdate(javax.swing.event.DocumentEvent e){changed();}public void removeUpdate(javax.swing.event.DocumentEvent e){changed();}public void changedUpdate(javax.swing.event.DocumentEvent e){changed();}private void changed(){if(steps.getSelectedIndex()!=1)return;if(manualPrices){input.setText("");output.setText("");priceStatus.setText("Model changed — enter both account-specific rates, or leave blank for N/A.");}else queuePriceLookup(priceDebounce);}});
        provider.addActionListener(e->{model.setModel(new DefaultComboBoxModel<>("anthropic".equals(provider.getSelectedItem())?new String[]{"claude-sonnet-5"}:new String[]{""}));refreshSavedStatus();if(steps.getSelectedIndex()==1){if(manualPrices){input.setText("");output.setText("");priceStatus.setText("Provider changed — enter both account-specific rates, or leave blank for N/A.");}else queuePriceLookup(priceDebounce);}});
        steps.addChangeListener(e->{if(steps.getSelectedIndex()==1&&!manualPrices)refreshPricing();});
        // Logical screen dimensions shrink on HiDPI displays; keep the first window above the taskbar.
        GraphicsConfiguration display=frame.getGraphicsConfiguration();Rectangle screen=display.getBounds();Insets systemInsets=Toolkit.getDefaultToolkit().getScreenInsets(display);
        int usableWidth=screen.width-systemInsets.left-systemInsets.right,usableHeight=screen.height-systemInsets.top-systemInsets.bottom;
        frame.setMinimumSize(new Dimension(Math.min(640,usableWidth),Math.min(560,usableHeight)));
        frame.setSize(Math.min(1040,usableWidth),Math.min(780,usableHeight));frame.setLocationByPlatform(true);frame.setVisible(true);
        if(autoStart&&configuration!=null&&configuration.startAtLogin())background(()->{startWorker();log("Started using your saved sign-in consent.");});
    }
    private JButton guideButton(String label,URI guide){
        JButton button=new JButton(label);
        button.addActionListener(event->{try{Desktop.getDesktop().browse(guide);}catch(Exception failure){JOptionPane.showMessageDialog(frame,"Open this official guide in your browser:\n"+guide,"Installation guide",JOptionPane.INFORMATION_MESSAGE);}});
        return button;
    }
    private void updatePrerequisites(DesktopPrerequisites.Report report){
        SwingUtilities.invokeLater(()->{
            gitPrerequisite.setText(report.git().detail());dockerPrerequisite.setText(report.docker().detail());
            log(report.summary());
        });
    }
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
    /** UI verification may dispose an idle window without terminating its test JVM. */
    void disposeIdle(){if(worker.running()||busy.get())throw new IllegalStateException("Cannot dispose active runner");statusTimer.stop();frame.dispose();}
    JFrame window(){return frame;}
    private RunnerClient client(URI uri){return new RunnerClient(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build(),uri);}
    /** Never decrypt credentials just to render the UI; distinguish configured from verified. */
    private void refreshSavedStatus(){
        connectionStatus.setText(Files.exists(directory.resolve("identity"))?"Saved connection — not yet verified this session":"Not connected yet");
        connectionSummary.setText(Files.exists(directory.resolve("identity"))?"Saved connection — verify after a server change":"Not connected");
        recoveryControls.setVisible(Files.exists(directory.resolve("identity")));
        modelSummary.setText(configuration==null?"No provider saved":configuration.provider()+" / "+configuration.model());
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
            URI uri=URI.create(address);PairingRequest request=new PairingRequest();URI approval=request.approvalUri(uri,runnerName);approvalPage=approval;SwingUtilities.invokeLater(()->{fingerprint.setText("Match: "+request.fingerprint());fingerprint.setVisible(true);connectionStatus.setText("Waiting for browser approval");reopen.setEnabled(true);cancelPairing.setEnabled(true);reopen.setVisible(true);cancelPairing.setVisible(true);});Desktop.getDesktop().browse(approval);RunnerClient client=client(uri);
            RunnerIdentity identity=new DesktopPairing().await(()->client.exchangePairing(request.verifier()),pairingCancelled::get,Duration.ofMinutes(10));
            if(identity!=null){DesktopFiles.writeAtomic(directory.resolve("endpoint"),address.getBytes(java.nio.charset.StandardCharsets.UTF_8));DesktopFiles.writeAtomic(directory.resolve("runner-name"),runnerName.getBytes(java.nio.charset.StandardCharsets.UTF_8));new RunnerIdentityStore().save(directory.resolve("identity"),identity);DesktopFiles.protect(directory.resolve("identity"));SwingUtilities.invokeLater(()->{endpoint.setEditable(false);name.setEditable(false);refreshSavedStatus();steps.setSelectedIndex(1);});log("Connected. Save your provider settings, then explicitly start when ready.");return;}
            log(pairingCancelled.get()?"Connection cancelled. Click Connect in browser for a fresh request.":"Pairing timed out. Click Connect in browser for a fresh request; close the old browser tab.");
            }finally{approvalPage=null;SwingUtilities.invokeLater(()->{reopen.setEnabled(false);cancelPairing.setEnabled(false);reopen.setVisible(false);cancelPairing.setVisible(false);fingerprint.setText(" ");fingerprint.setVisible(false);refreshSavedStatus();});}});
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
    /** Manual start and previously consented sign-in startup share the same readiness/cancellation boundary. */
    private void startWorker()throws Exception{
        if(configuration==null)throw new IllegalStateException("Save provider settings first");
        if(!Files.exists(directory.resolve("identity")))throw new IllegalStateException("Connect this runner first");
        if(!configuration.endpoint().equals(Files.readString(directory.resolve("endpoint")).trim()))throw new IllegalStateException("Save provider settings for the new connection before starting");
        synchronized(startupLock){dockerStartupCancelled.set(false);preparingDocker=true;}
        try{
            var prepared=DesktopDockerStartup.prepareForWorker(message->{setupStatus=message;log(message);},dockerStartupCancelled::get);
            var report=prepared.prerequisites();
            updatePrerequisites(report);
            if(!report.ready())throw new IllegalStateException(report.summary());
            // Load local secrets before the transition, leaving Cancel responsive if the OS vault is slow.
            var secret=new DesktopSecretStore(directory,configuration.provider()).load();
            synchronized(startupLock){
                if(dockerStartupCancelled.get())throw new IllegalStateException("Docker startup cancelled. The runner was not started.");
                DesktopFiles.writeAtomic(directory.resolve("provider-policy.json"),JSON.writeValueAsBytes(configuration.policy()));
                worker.start(configuration,secret,this::log,prepared.dockerEnvironment());
                preparingDocker=false;
            }
        }finally{preparingDocker=false;}
    }
    private void background(Work work){
        if(!busy.compareAndSet(false,true))return;
        setupStatus="Setup in progress - no paid work started";
        SwingUtilities.invokeLater(()->notice.setVisible(false));
        Thread.ofVirtual().start(()->{
            try{work.run();}
            catch(Exception error){
                String message="Action failed: "+(error instanceof IllegalArgumentException||error instanceof IllegalStateException?error.getMessage():error.getClass().getSimpleName()+"; check prerequisites and connectivity");
                log(message);
                // Keep failures visible on the current page instead of sending users to another tab.
                SwingUtilities.invokeLater(()->{notice.setText(message);notice.setVisible(true);frame.revalidate();});
            }finally{busy.set(false);}
        });
    }
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
