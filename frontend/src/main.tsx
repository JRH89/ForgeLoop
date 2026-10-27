import { SignOutButton } from './SignOutButton';
import RunsPage from './runs/RunsPage';
import { terminal } from './runs/presentation';
import ConsolePageHeader from './ConsolePageHeader';
import {
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { createRoot } from "react-dom/client";
import OnboardingPage from './onboarding/OnboardingPage';
import UsagePage from './usage/UsagePage';
import AccountPage from './account/AccountPage';
import './console-navigation.css';
import './support/console-link.css';
import { BarChart3, GitBranch, ListChecks, Menu, X, SlidersHorizontal, Users, UserRound, MessageCircle } from "lucide-react";
import GuideNavigation from "./GuideNavigation";
import {
  configureOrganizationPolicy,
  createHarnessDefinition,
  createLocalMcpConfiguration,
  loadOperator,
  loadMyOrganizations,
  loadRunAnalytics,
  loadPlatformConfiguration,
  loadRepositoryConnections,
  loadRuns,
  selectOrganization,
  type FeatureRun,
  type OperatorSession,
  type OrganizationMember,
  type RepositoryConnection,
  type RunAnalytics,
  type PlatformConfiguration,
} from "./api";
import { brandIcon as favicon } from './brand';
import UserGuidePage from "./UserGuidePage";
import RunnerSetup from "./RunnerSetup";
import RunnerPairingPage from "./RunnerPairingPage";
import RunnerDownloads from "./RunnerDownloads";
import TeamPage from "./TeamPage";
import RepositoryScans from "./repositories/RepositoryScans";
import RepositoryDisclosure from "./repositories/RepositoryDisclosure";
import IssueChatPage from "./issue-chat/IssueChatPage";
import { isRunnerPairingRoute } from "./desktopRoute";
import IntakeSettings from "./IntakeSettings";
import RunRecordSettings from "./RunRecordSettings";
import RepositoryBudgetSettings from "./RepositoryBudgetSettings";
import SetupBanner from "./SetupBanner";
import "./styles.css";
import "./dashboard-responsive.css";
import "./repository-settings.css";
import "./run-detail.css";

function RepositoryPage({ items, operator, onSettings }: { items: RepositoryConnection[]; operator:OperatorSession; onSettings:()=>void }) {
  return (
    <>
      <section className="panel connected-repositories">
        <ConsolePageHeader eyebrow="Repository access" title="Repositories" description="Manage connected repositories and discover work to propose." actions={<a className="primary" href="/api/github/app/install">Install GitHub App</a>}/>
        {items.length ? (
          items.map((item) => (
            <RepositoryDisclosure key={`${operator.subject}:${operator.organizationId}:${item.id}`} storageKey={`forgeloop:repository-expanded:${encodeURIComponent(operator.subject)}:${encodeURIComponent(operator.organizationId)}:${encodeURIComponent(item.repository)}`} heading={<>
                <div className="repository-identity">
                  <span className="repository-identity-icon" aria-hidden="true"><GitBranch size={18}/></span>
                  <div>
                    <h3>{item.repository}</h3>
                    <p>{item.defaultBranch} · {item.issueLabel} · {item.requiredGates.join(", ")}</p>
                  </div>
                </div>
                <span className="status complete">Policy v{item.policyRevision}</span>
              </>}>
              <p>Run limit: ${item.maxBudgetUsd} USD · Intake: {item.requireAssignee ? 'assignment required' : item.requireLabel === false ? 'label bypassed' : 'label only'} · Detailed records: {item.runRecord ? 'enabled' : 'disabled'}</p>
              <button className="secondary" onClick={onSettings}>Manage repository settings</button>
              <RepositoryScans repository={item.repository} isAdmin={operator.role==='ADMIN'}/>
            </RepositoryDisclosure>
          ))
        ) : (
          <p className="empty">No repositories are authorized yet.</p>
        )}
      </section>
    </>
  );
}

function ConfigurationPage({operator, repositories, onSaved}:{operator:OperatorSession; repositories:RepositoryConnection[]; onSaved:(item:RepositoryConnection)=>void}) {
  const [configuration,setConfiguration]=useState<PlatformConfiguration>();
  const [error,setError]=useState("");
  const [busy,setBusy]=useState("");
  const [harness,setHarness]=useState({name:"",description:"",allowedRoles:"PLANNER,BACKEND,FRONTEND,INDEPENDENT_TEST,INTEGRATION,REVIEW,VERIFICATION",defaultAttemptBudget:2});
  const [mcp,setMcp]=useState({name:"",command:"",arguments:"",allowedRoles:"PLANNER,BACKEND,FRONTEND,REVIEW",contextTool:"",toolArguments:"{}"});
  useEffect(()=>{void loadPlatformConfiguration().then(setConfiguration).catch(reason=>setError(reason instanceof Error?reason.message:"Unable to load configuration"));},[]);
  const admin=operator.role==="ADMIN";
  const csv=(value:string)=>value.split(",").map(item=>item.trim()).filter(Boolean);
  async function perform(label:string,work:()=>Promise<unknown>){setBusy(label);setError("");try{await work();setConfiguration(await loadPlatformConfiguration());}catch(reason){setError(reason instanceof Error?reason.message:"Configuration update failed");}finally{setBusy("");}}
  if(!configuration)return <p className="loading">Loading execution policy…</p>;
  return <>
    <ConsolePageHeader eyebrow="Execution governance" title="Settings" description="Manage spending limits, delivery rules, and repository configuration." actions={<span className="revision">Policy revision {configuration.policy.revision}</span>}/>
    {error&&<p role="alert">{error}</p>}
    <RunnerSetup operator={operator}/>
    <section className="panel policy-card"><div><p className="eyebrow">Organization boundary</p><h2>Execution policy</h2><p>Hard limits applied before work enters the runner queue.</p></div><form onSubmit={event=>{event.preventDefault();const form=new FormData(event.currentTarget);void perform("policy",()=>configureOrganizationPolicy({maxRunBudgetUsd:Number(form.get("budget")),maxParallelTasks:Number(form.get("parallel")),allowedProviders:csv(String(form.get("providers"))),requireHumanApproval:form.get("approval")==="on",autoMergeEnabled:form.get("autoMerge")==="on"}));}}><label>Organization per-run ceiling (USD)<input name="budget" type="number" min="1" step="1" defaultValue={configuration.policy.maxRunBudgetUsd} disabled={!admin}/></label><label>Parallel task ceiling<input name="parallel" type="number" min="1" max="16" defaultValue={configuration.policy.maxParallelTasks} disabled={!admin}/></label><label>Allowed providers<input name="providers" defaultValue={configuration.policy.allowedProviders.join(", ")} disabled={!admin}/></label><label className="check"><input name="approval" type="checkbox" defaultChecked={configuration.policy.requireHumanApproval} disabled={!admin}/> Require human approval before delivery</label><label className="check"><input name="autoMerge" type="checkbox" defaultChecked={configuration.policy.autoMergeEnabled} disabled={!admin}/> Auto-merge the verified commit after all GitHub checks pass</label>{admin&&<button className="primary compact" disabled={!!busy}>Save policy</button>}</form></section>
    <section className="panel"><h2>Repository settings</h2><p>Each repository has one run limit, bounded by the organization ceiling above. New manual and issue-triggered runs use that limit; existing runs keep their captured budget.</p>{repositories.length ? repositories.map(item => <article className="repository-card" key={item.id}><h3>{item.repository}</h3><div className="repository-settings-grid"><RepositoryBudgetSettings item={item} organizationLimit={configuration.policy.maxRunBudgetUsd} editable={admin} onSaved={onSaved}/><IntakeSettings item={item} editable={admin} onSaved={onSaved}/><RunRecordSettings item={item} editable={admin} onSaved={onSaved}/></div></article>) : <p>No connected repositories yet.</p>}</section>
    <details className="panel"><summary>Advanced harness &amp; MCP configuration</summary>
    <div className="config-columns"><section className="panel"><div className="section-heading"><div><p className="eyebrow">Reusable orchestration</p><h2>Harness definitions</h2></div><span className="count">{configuration.harnesses.length}</span></div>{configuration.harnesses.map(item=><article className="definition" key={item.id}><div><b>{item.name}</b><span className={`status ${item.enabled?"complete":"failed"}`}>{item.enabled?"ACTIVE":"DISABLED"}</span></div><p>{item.description}</p><small>{item.allowedRoles.join(" · ")} · {item.defaultAttemptBudget} attempts · v{item.revision}</small></article>)}{admin&&<form className="subform" onSubmit={event=>{event.preventDefault();void perform("harness",()=>createHarnessDefinition({...harness,allowedRoles:csv(harness.allowedRoles)}));}}><h3>Add harness</h3><input aria-label="Harness name" placeholder="BUG_FIX" value={harness.name} onChange={event=>setHarness({...harness,name:event.target.value.toUpperCase()})} required/><textarea aria-label="Harness description" placeholder="Describe the bounded workflow" value={harness.description} onChange={event=>setHarness({...harness,description:event.target.value})} required/><input aria-label="Harness roles" value={harness.allowedRoles} onChange={event=>setHarness({...harness,allowedRoles:event.target.value})} required/><input aria-label="Default attempt budget" type="number" min="1" max="10" value={harness.defaultAttemptBudget} onChange={event=>setHarness({...harness,defaultAttemptBudget:Number(event.target.value)})}/><button className="secondary" disabled={!!busy}>Create harness</button></form>}</section>
    <section className="panel"><div className="section-heading"><div><p className="eyebrow">Runner-local context</p><h2>MCP routes</h2></div><span className="count">{configuration.mcp.length}</span></div>{configuration.mcp.length?configuration.mcp.map(item=><article className="definition" key={item.id}><div><b>{item.name}</b><span className={`status ${item.enabled?"complete":"failed"}`}>{item.enabled?"ROUTED":"DISABLED"}</span></div><p><code>{item.command} {item.arguments.join(" ")}</code></p><small>{item.contextTool} · {item.allowedRoles.join(" · ")} · v{item.revision}</small></article>):<p className="empty">No local MCP context routes configured.</p>}{admin&&<form className="subform" onSubmit={event=>{event.preventDefault();try{JSON.parse(mcp.toolArguments);}catch{setError("MCP tool arguments must be valid JSON");return;}void perform("mcp",()=>createLocalMcpConfiguration({...mcp,arguments:csv(mcp.arguments),allowedRoles:csv(mcp.allowedRoles)}));}}><h3>Add local route</h3><input aria-label="MCP route name" placeholder="repository-context" value={mcp.name} onChange={event=>setMcp({...mcp,name:event.target.value})} required/><input aria-label="MCP command" placeholder="node" value={mcp.command} onChange={event=>setMcp({...mcp,command:event.target.value})} required/><input aria-label="MCP command arguments" placeholder="server.js, --stdio" value={mcp.arguments} onChange={event=>setMcp({...mcp,arguments:event.target.value})}/><input aria-label="MCP allowed roles" value={mcp.allowedRoles} onChange={event=>setMcp({...mcp,allowedRoles:event.target.value})} required/><input aria-label="MCP context tool" placeholder="repository.get_context" value={mcp.contextTool} onChange={event=>setMcp({...mcp,contextTool:event.target.value})} required/><textarea aria-label="MCP tool arguments" value={mcp.toolArguments} onChange={event=>setMcp({...mcp,toolArguments:event.target.value})} required/><button className="secondary" disabled={!!busy}>Create route</button></form>}</section></div>
    </details>
  </>;
}

function App() {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [mobile, setMobile] = useState(() => window.matchMedia?.('(max-width: 760px)').matches ?? false);
  const menuButton = useRef<HTMLButtonElement>(null);
  const menuPanel = useRef<HTMLElement>(null);
  useEffect(() => {
    const media = window.matchMedia?.('(max-width: 760px)');
    const resize = () => { setMobile(media?.matches ?? false); setMobileMenuOpen(false); };
    media?.addEventListener('change', resize);
    return () => media?.removeEventListener('change', resize);
  }, []);
  useEffect(() => {
    if (!mobileMenuOpen) return;
    const escape = (event: KeyboardEvent) => { if (event.key === 'Escape') { setMobileMenuOpen(false); menuButton.current?.focus(); } };
    const outside = (event: PointerEvent) => { if (!menuPanel.current?.contains(event.target as Node) && !menuButton.current?.contains(event.target as Node)) setMobileMenuOpen(false); };
    document.addEventListener('keydown', escape); document.addEventListener('pointerdown', outside);
    return () => { document.removeEventListener('keydown', escape); document.removeEventListener('pointerdown', outside); };
  }, [mobileMenuOpen]);
  type ConsolePage = "Runs" | "IssueChat" | "Repositories" | "Analytics" | "Configuration" | "Guide" | "GettingStarted" | "Team" | "Account";
  const locationPage = (): ConsolePage => { const route = new URLSearchParams(window.location.hash.slice(1)); return route.has('account') ? 'Account' : route.has('chat') ? 'IssueChat' : 'Runs'; };
  const [page, setPage] = useState<ConsolePage>(locationPage);
  const navigate = (next: ConsolePage) => {
    window.history.pushState(null, '', next === 'Account' ? '/app#account=1' : next === 'IssueChat' ? '/app#chat' : '/app');
    setPage(next);
  };
  useEffect(() => {
    const syncRoute = () => setPage(locationPage());
    window.addEventListener('popstate', syncRoute);
    window.addEventListener('hashchange', syncRoute);
    return () => { window.removeEventListener('popstate', syncRoute); window.removeEventListener('hashchange', syncRoute); };
  }, []);
  const [guideTarget, setGuideTarget] = useState<{ id: string }>();
  useEffect(() => {
    if (page !== 'Guide' || !guideTarget) return;
    // Wait for React to mount the guide before moving keyboard focus and scrolling.
    const section = document.getElementById(guideTarget.id);
    if (section) {
      section.tabIndex = -1;
      section.focus({ preventScroll: true });
      section.scrollIntoView({ block: 'start' });
    }
  }, [page, guideTarget]);
  const [repositories, setRepositories] = useState<RepositoryConnection[]>([]);
  const [runs, setRuns] = useState<FeatureRun[]>([]);
  const [operator, setOperator] = useState<OperatorSession>();
  const [workspaces, setWorkspaces] = useState<OrganizationMember[]>([]);
  const [analytics,setAnalytics]=useState<RunAnalytics>();
  const [error, setError] = useState("");
  useEffect(() => {
    void Promise.all([loadRepositoryConnections(), loadRuns(), loadOperator(),loadRunAnalytics(),loadMyOrganizations()])
      .then(([connected, loaded, current,metrics,available]) => {
        setRepositories(connected);
        setRuns(loaded);
        setOperator(current);
        setAnalytics(metrics);
        setWorkspaces(available);
      })
      .catch((reason) =>
        setError(
          reason instanceof Error ? reason.message : "Unable to load ForgeLoop",
        ),
      );
  }, []);
  const summary = useMemo(
    () => `${analytics?.activeRuns ?? runs.filter((run) => !terminal.has(run.state)).length} active`,
    [runs, analytics],
  );
  const switchWorkspace = (organizationId: string) => {
    void selectOrganization(organizationId).then(() => window.location.reload()).catch(reason => setError(reason instanceof Error ? reason.message : String(reason)));
  };
  return (
    <main className="console-app">
      <header>
        <div>
          <a className="brand" href="/">
            <img src={favicon} alt="" />
            <strong>ForgeLoop</strong>
          </a>
          <span>Evidence-backed software delivery</span>
        </div>
        <div className="session">
          <i />
          {summary}
          {operator && (
            <span>
              {operator.role.toLowerCase()} · {workspaces.find(workspace => workspace.organizationId === operator.organizationId)?.organizationName ?? operator.organizationId}
            </span>
          )}
          {operator && workspaces.length > 1 && <select className="workspace-switch desktop-workspace" aria-label="Workspace" value={operator.organizationId} onChange={event => switchWorkspace(event.target.value)}>{workspaces.map(workspace => <option value={workspace.organizationId} key={workspace.id}>{workspace.organizationName} · {workspace.role.toLowerCase()}</option>)}</select>}
          <SignOutButton className="desktop-signout"/>
          <button ref={menuButton} className="console-menu-toggle" aria-label={mobileMenuOpen ? 'Close navigation' : 'Open navigation'} aria-expanded={mobileMenuOpen} aria-controls="console-navigation" onClick={() => setMobileMenuOpen(value => !value)}>{mobileMenuOpen ? <X aria-hidden="true" size={22}/> : <Menu aria-hidden="true" size={22}/>}</button>
        </div>
      </header>
      <div className="shell">
        <aside ref={menuPanel} id="console-navigation" className={mobileMenuOpen ? 'console-sidebar menu-open' : 'console-sidebar'} inert={mobile && !mobileMenuOpen} aria-hidden={mobile && !mobileMenuOpen ? true : undefined} onClick={() => { if (mobile) { setMobileMenuOpen(false); menuButton.current?.focus(); } }}>
          <nav aria-label="ForgeLoop navigation">
            {operator && workspaces.length > 1 && <select className="workspace-switch mobile-workspace" aria-label="Workspace" value={operator.organizationId} onClick={event => event.stopPropagation()} onChange={event => switchWorkspace(event.target.value)}>{workspaces.map(workspace => <option value={workspace.organizationId} key={workspace.id}>{workspace.organizationName} · {workspace.role.toLowerCase()}</option>)}</select>}
            <button
              className={page === "Runs" ? "active" : ""}
              onClick={() => navigate("Runs")}
            >
              <span className="nav-icon" aria-hidden="true"><ListChecks size={17} strokeWidth={1.9} /></span> Runs
            </button>
            <button className={page === "IssueChat" ? "active" : ""} onClick={() => navigate("IssueChat")}><span className="nav-icon" aria-hidden="true"><MessageCircle size={17} strokeWidth={1.9} /></span> Issue chat</button>
            <button className={page === "Analytics" ? "active" : ""} onClick={() => navigate("Analytics")}><span className="nav-icon" aria-hidden="true"><BarChart3 size={17} strokeWidth={1.9} /></span> Usage &amp; costs</button>
            <button
              className={page === "Repositories" ? "active" : ""}
              onClick={() => navigate("Repositories")}
            >
              <span className="nav-icon" aria-hidden="true"><GitBranch size={17} strokeWidth={1.9} /></span> Repositories
            </button>
            <button className={page === "Configuration" ? "active" : ""} onClick={() => navigate("Configuration")}><span className="nav-icon" aria-hidden="true"><SlidersHorizontal size={17} strokeWidth={1.9} /></span> Settings</button>
            <GuideNavigation active={page === 'Guide'} onSelect={id => { setGuideTarget({ id }); navigate('Guide'); }}/>
            {operator?.role === 'ADMIN' && <button className={page === 'Team' ? 'active' : ''} onClick={() => navigate('Team')}><span className="nav-icon" aria-hidden="true"><Users size={17} strokeWidth={1.9}/></span> Team</button>}
            <button className={`account-console-link ${page === 'Account' ? 'active' : ''}`} onClick={() => navigate('Account')}><span className="nav-icon" aria-hidden="true"><UserRound size={17} strokeWidth={1.9}/></span> Account</button>
            <SignOutButton className="mobile-signout"/>
          </nav>
          <p className="sidebar-note">
            Repository code and commands execute only on an enrolled customer
            runner.
          </p>
        </aside>
        <div className="content">
          {error ? (
            <p role="alert">{error}</p>
          ) : !operator ? (
            <p className="loading">Loading operator workspace…</p>
          ) : page === "Runs" ? (
            <><SetupBanner key={`${operator.subject}:${operator.organizationId}`} operator={operator} onOpen={() => navigate("GettingStarted")}/><RunsPage
              repositories={repositories}
              runs={runs}
              operator={operator}
              setRuns={setRuns}
            /></>
          ) : page === "GettingStarted" ? (
            <OnboardingPage operator={operator} navigate={setPage} />
          ) : page === "IssueChat" ? (
            <IssueChatPage repositories={repositories} operator={operator} onRepositories={() => navigate('Repositories')}/>
          ) : page === "Repositories" ? (
            <RepositoryPage items={repositories} operator={operator} onSettings={() => navigate("Configuration")}/>
          ) : page === "Configuration" ? (
            <ConfigurationPage operator={operator} repositories={repositories} onSaved={updated=>setRepositories(current=>current.map(item=>item.id===updated.id?updated:item))}/>
          ) : page === "Team" ? (
            operator.role === 'ADMIN' ? <TeamPage operator={operator}/> : <p role="alert">Administrator access is required.</p>
          ) : page === "Account" ? (
            <AccountPage operator={operator} workspaces={workspaces} onUsage={() => navigate('Analytics')} onWorkspaceSettings={() => navigate('Configuration')}/>
          ) : page === "Guide" ? (
            <UserGuidePage />
          ) : (
            <UsagePage initialAnalytics={analytics} onSettings={() => navigate('Configuration')}/>
          )}
        </div>
      </div>
    </main>
  );
}

if (import.meta.env.MODE !== "test") {
  const root = document.getElementById("root");
  if (!root) throw new Error("ForgeLoop root element is missing");
  const pairing = isRunnerPairingRoute(window.location.pathname, window.location.hash);
  if(pairing && window.location.pathname !== '/app/runner-connect') window.history.replaceState(null, '', `/app/runner-connect${window.location.hash}`);
  createRoot(root).render(window.location.pathname==='/app/runner-downloads'?<RunnerDownloads/>:pairing?<RunnerPairingPage/>:<App />);
}
export default App;
