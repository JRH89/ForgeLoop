import { useEffect, useState, type Dispatch, type SetStateAction } from 'react';
import { Archive, Recycle, Trash2 } from 'lucide-react';
import { archiveRun, deleteRun, loadRun, loadRunOperations, loadRuns, RUN_PAGE_SIZE, type FeatureRun, type OperatorSession, type RepositoryConnection, type RunOperations } from '../api';
import ConsolePageHeader from '../ConsolePageHeader';
import NewRun from './NewRun';
import RunDetail from './RunDetail';
import { terminal, costSummary } from './presentation';

type RunFilter = "All" | "Running" | "Succeeded" | "Failed";

function runCategory(state: string): Exclude<RunFilter, "All"> {
  if (["READY_FOR_REVIEW", "COMPLETE"].includes(state)) return "Succeeded";
  if (["BLOCKED", "FAILED", "REJECTED", "CANCELLED"].includes(state)) return "Failed";
  return "Running";
}

function displayedRunState(run: FeatureRun): string {
  return run.publication?.pullRequestState === "MERGED" ? "COMPLETE" : run.state;
}

function runProgress(run: FeatureRun): number {
  if (["READY_FOR_REVIEW", "COMPLETE"].includes(displayedRunState(run))) return 100;
  if (!run.tasks.length) return 0;
  return Math.round((run.tasks.filter((task) => task.state === "VERIFIED").length / run.tasks.length) * 100);
}

function relativeTime(value: string): string {
  const elapsed = Math.max(0, Date.now() - new Date(value).getTime());
  if (!Number.isFinite(elapsed)) return "—";
  const minutes = Math.floor(elapsed / 60_000);
  if (minutes < 1) return "just now";
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  return `${Math.floor(hours / 24)}d ago`;
}

export default function RunsPage({
  repositories,
  runs,
  operator,
  setRuns,
}: {
  repositories: RepositoryConnection[];
  runs: FeatureRun[];
  operator: OperatorSession;
  setRuns: Dispatch<SetStateAction<FeatureRun[]>>;
}) {
  const [selected, setSelected] = useState<string | undefined>(() => new URLSearchParams(window.location.hash.slice(1)).get("run") || undefined);
  const [operations, setOperations] = useState<RunOperations>({
    evidence: [],
    reviews: [],
    artifacts: [],
    events: [],
    escalations: [],
    audit: [],
  });
  const [creating, setCreating] = useState(false);
  const [archived,setArchived]=useState(false);
  const [page,setPage]=useState(0);
  const [pageLoading,setPageLoading]=useState(false);
  const [refreshError,setRefreshError]=useState('');
  const [lastUpdated,setLastUpdated]=useState('');
  const [archiveBusy,setArchiveBusy]=useState('');
  const [filter, setFilter] = useState<RunFilter>("All");
  const run = runs.find((item) => item.id === selected);
  const filters: RunFilter[] = ["All", "Running", "Succeeded", "Failed"];
  const queueRuns = runs.filter(item=>Boolean(item.archived)===archived);
  const visibleRuns = queueRuns.filter((item) => filter === "All" || runCategory(displayedRunState(item)) === filter);
  async function refresh() {
    if (!selected) return;
    const [updated, detail] = await Promise.all([
      loadRun(selected),
      loadRunOperations(selected),
    ]);
    setRuns((current) =>
      current.map((item) => (item.id === updated.id ? updated : item)),
    );
    setOperations(detail);
  }
  useEffect(() => {
    let stopped=false;
    let timer:number;
    let failures=0;
    setOperations({evidence:[],reviews:[],artifacts:[],events:[],escalations:[],audit:[]});
    // Serialized polling prevents overlapping requests and stale responses after selection changes.
    async function poll() {
      try {
        const [updated,detail]=await Promise.all([loadRuns(page, archived), selected?loadRunOperations(selected):Promise.resolve(undefined)]);
        if(stopped)return;
        setRuns(updated);
        if(detail)setOperations(detail);
        setRefreshError('');setLastUpdated(new Date().toLocaleTimeString());failures=0;
      } catch(reason) { if(!stopped){failures++;setRefreshError(reason instanceof Error?reason.message:'Live updates unavailable');} }
      finally { if(!stopped)timer=window.setTimeout(()=>void poll(), document.hidden?10000:Math.min(15000,2000*2**failures)); }
    }
    setPageLoading(true);
    void poll().finally(()=>{if(!stopped)setPageLoading(false);});
    return()=>{stopped=true;window.clearTimeout(timer);};
  }, [selected, page, archived]);
  async function archive(item:FeatureRun) {
    if(!window.confirm(item.archived?'Restore this run to the intake queue?':'Archive this run? Evidence and GitHub history will be retained.'))return;
    setArchiveBusy(item.id);
    try { const updated=await archiveRun(item.id,!item.archived);setRuns(current=>current.map(run=>run.id===updated.id?updated:run));if(selected===item.id)setSelected(undefined); }
    catch(reason){setRefreshError(reason instanceof Error?reason.message:'Archive failed');}
    finally{setArchiveBusy('');}
  }
  async function deleteArchived(item:FeatureRun) {
    const confirmation=`Permanently delete archived run ${item.sourceRef}? Its ForgeLoop run, task, and evidence records will be removed. The audit log and GitHub activity remain; uploaded artifact bytes follow their configured retention. A later eligible GitHub issue event may create a fresh run.`;
    if(!window.confirm(confirmation))return;
    setArchiveBusy(item.id);
    try {
      if(!await deleteRun(item.id))throw new Error('Run was not deleted');
      setRuns(current=>current.filter(run=>run.id!==item.id));
      if(selected===item.id)setSelected(undefined);
    } catch(reason) { setRefreshError(reason instanceof Error?reason.message:'Delete failed'); }
    finally { setArchiveBusy(''); }
  }
  if (creating)
    return (
      <>
        <button className="back" onClick={() => setCreating(false)}>
          ← Back to runs
        </button>
        <NewRun
          repositories={repositories}
          onAdded={(added) => {
            setRuns((current) => [added, ...current]);
            setSelected(added.id);
            setPage(0);
            setArchived(false);
            setCreating(false);
          }}
        />
      </>
    );
  return (
    <>
      <ConsolePageHeader eyebrow="Software delivery" title="Runs" description="Track issues through verified delivery." actions={operator.role !== "VIEWER" && <button className="primary" onClick={() => setCreating(true)}>+ New Run</button>}/>
      <section className="panel runs-dashboard">
        <p role="status">{refreshError?`Updates interrupted: ${refreshError}. Retrying automatically.`:`Live updates every 2 seconds${lastUpdated?` · Updated ${lastUpdated}`:''}`}</p>
        <label className="check"><input type="checkbox" checked={archived} onChange={event=>{setArchived(event.target.checked);setPage(0);setSelected(undefined);}}/> Show archived runs</label>
        <h2 className="visually-hidden">Intake queue</h2>
        <div className="run-filters" role="group" aria-label="Filter runs">
          {filters.map((item) => (
            <button key={item} className={filter === item ? "active" : ""} aria-pressed={filter === item} onClick={() => setFilter(item)}>
              {item}{" "}<span>{item === "All" ? queueRuns.length : queueRuns.filter((runItem) => runCategory(displayedRunState(runItem)) === item).length}</span>
            </button>
          ))}
        </div>
        {visibleRuns.length ? (
          <div className="runs-table-wrap">
            <table className="runs-table">
              <thead><tr><th scope="col">Run</th><th scope="col">Repository</th><th scope="col">Status</th><th scope="col">Progress</th><th scope="col">Cost</th><th scope="col">Started</th><th scope="col">Queue</th></tr></thead>
              <tbody>
                {visibleRuns.map((item) => {
                  const progressPercent = runProgress(item);
                  return (
                    <tr key={item.id} className={selected === item.id ? "selected" : ""} tabIndex={0} aria-label={`Open run ${item.title}`} onClick={() => setSelected(item.id)} onKeyDown={(event) => { if (event.key === "Enter" || event.key === " ") { event.preventDefault(); setSelected(item.id); } }}>
                      <td><b>{item.sourceRef}</b><small title={item.title}>{item.title}</small></td>
                      <td data-label="Repository">{item.repository}</td>
                      <td data-label="Status"><span className={`status ${displayedRunState(item).toLowerCase()}`}>{displayedRunState(item).replaceAll("_", " ")}</span></td>
                      <td data-label="Progress"><div className="run-progress"><span><i style={{ width: `${progressPercent}%` }} /></span><small>{progressPercent}%</small></div></td>
                      <td data-label="Cost" title={costSummary(item)}>{costSummary(item, true)}</td>
                      <td data-label="Started"><time dateTime={item.createdAt}>{relativeTime(item.createdAt)}</time></td>
                      <td data-label="Queue">{operator.role!=='VIEWER'&&(item.archived||terminal.has(displayedRunState(item)))?item.archived?<div className="queue-actions">
                        <button className="queue-icon-button" aria-label={`Restore ${item.sourceRef}`} title="Restore run to intake queue" disabled={archiveBusy===item.id} onKeyDown={event=>event.stopPropagation()} onClick={event=>{event.stopPropagation();void archive(item);}}><Recycle size={16} aria-hidden="true"/></button>
                        <button className="queue-icon-button danger" aria-label={`Delete ${item.sourceRef}`} title="Permanently delete archived run" disabled={archiveBusy===item.id} onKeyDown={event=>event.stopPropagation()} onClick={event=>{event.stopPropagation();void deleteArchived(item);}}><Trash2 size={16} aria-hidden="true"/></button>
                      </div>:<button className="queue-action-button" disabled={archiveBusy===item.id} onKeyDown={event=>event.stopPropagation()} onClick={event=>{event.stopPropagation();void archive(item);}}><Archive size={14} aria-hidden="true"/>Archive</button>:<small>Cancel active work before archiving</small>}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        ) : runs.length ? (
          <p className="empty">No {filter.toLowerCase()} runs.</p>
        ) : (
          <p className="empty">GitHub issues carrying the configured intake label will appear here automatically.</p>
        )}
        <nav aria-label="Run history pages" className="queue-actions">
          <button className="secondary" disabled={page===0||pageLoading} onClick={()=>{setPage(value=>value-1);setSelected(undefined);}}>Previous page</button>
          <span>Page {page+1} · Filters apply to this page</span>
          <button className="secondary" disabled={runs.length<RUN_PAGE_SIZE||pageLoading} onClick={()=>{setPage(value=>value+1);setSelected(undefined);}}>Next page</button>
        </nav>
      </section>
      {run && <RunDetail run={run} operations={operations} operator={operator} onRefresh={refresh} />}
    </>
  );
}
