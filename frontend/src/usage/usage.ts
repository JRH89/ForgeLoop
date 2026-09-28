import type { FeatureRun, ProviderActivity } from '../api';

export type UsageBucket = { name: string; knownMicros: number; priced: number; unpriced: number; tokens: number };
export type ActivityCostRow = { id: string; activityType: string; repository: string; provider: string; model: string; recordedAt: string; usage: UsageBucket };
export const usd = (micros: number) => new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', minimumFractionDigits: 2, maximumFractionDigits: micros > 0 && micros < 10_000 ? 6 : 2 }).format(micros / 1_000_000);

/** Attribute all provider spend to its recording day, not the run or repository creation date. */
export function usageSummary(runs: FeatureRun[], days: number, repository: string, now = Date.now(), activities: ProviderActivity[] = []) {
  const end = new Date(now); end.setUTCHours(0, 0, 0, 0); end.setUTCDate(end.getUTCDate() + 1);
  const start = new Date(end); start.setUTCDate(start.getUTCDate() - days);
  const daily = new Map<string, UsageBucket>();
  for (let day = start.getTime(); day < end.getTime(); day += 86_400_000) {
    const name = new Date(day).toISOString().slice(0, 10);
    daily.set(name, emptyBucket(name));
  }
  const models = new Map<string, UsageBucket>();
  const harnesses = new Map<string, UsageBucket>();
  const activityTypes = new Map<string, UsageBucket>();
  const rows: Array<{ run: FeatureRun; usage: UsageBucket }> = [];
  const activityRows: ActivityCostRow[] = [];
  const total = emptyBucket('Total');

  const ensure = (map: Map<string, UsageBucket>, name: string) => {
    if (!map.has(name)) map.set(name, emptyBucket(name));
    return map.get(name)!;
  };
  const add = (bucket: UsageBucket, inputTokens: number, outputTokens: number, estimatedCostMicros: number, costKnown: boolean) => {
    if (costKnown) { bucket.knownMicros += estimatedCostMicros; bucket.priced++; }
    else bucket.unpriced++;
    bucket.tokens += inputTokens + outputTokens;
  };
  const addAttribution = (activityType: string, provider: string, model: string, recordedAt: string,
                          inputTokens: number, outputTokens: number, estimatedCostMicros: number, costKnown: boolean) => {
    const at = Date.parse(recordedAt);
    if (!Number.isFinite(at) || at < start.getTime() || at >= end.getTime()) return false;
    add(ensure(activityTypes, activityType), inputTokens, outputTokens, estimatedCostMicros, costKnown);
    add(ensure(models, `${provider} / ${model}`), inputTokens, outputTokens, estimatedCostMicros, costKnown);
    add(total, inputTokens, outputTokens, estimatedCostMicros, costKnown);
    add(daily.get(new Date(at).toISOString().slice(0, 10))!, inputTokens, outputTokens, estimatedCostMicros, costKnown);
    return true;
  };

  for (const run of runs.filter(item => !repository || item.repository === repository)) {
    const usage = emptyBucket(run.id);
    for (const attempt of run.tasks.flatMap(task => task.providerAttempts ?? [])) {
      const at = Date.parse(attempt.recordedAt);
      if (!Number.isFinite(at) || at < start.getTime() || at >= end.getTime()) continue;
      add(ensure(harnesses, run.harnessProfile || 'Unspecified'), attempt.inputTokens, attempt.outputTokens, attempt.estimatedCostMicros, attempt.costKnown);
      add(usage, attempt.inputTokens, attempt.outputTokens, attempt.estimatedCostMicros, attempt.costKnown);
      addAttribution('RUN', attempt.provider, attempt.model, attempt.recordedAt, attempt.inputTokens, attempt.outputTokens, attempt.estimatedCostMicros, attempt.costKnown);
    }
    const created = Date.parse(run.createdAt);
    if (usage.priced + usage.unpriced > 0 || (created >= start.getTime() && created < end.getTime())) rows.push({ run, usage });
  }

  for (const activity of activities) {
    if (repository && activity.repository !== repository) continue;
    const included = addAttribution(activity.activityType, activity.provider, activity.model, activity.recordedAt,
      activity.inputTokens, activity.outputTokens, activity.estimatedCostMicros, activity.costKnown);
    if (!included) continue;
    activityRows.push({
      id: activity.id,
      activityType: activity.activityType,
      repository: activity.repository,
      provider: activity.provider,
      model: activity.model,
      recordedAt: activity.recordedAt,
      usage: {
        name: activity.id,
        knownMicros: activity.costKnown ? activity.estimatedCostMicros : 0,
        priced: activity.costKnown ? 1 : 0,
        unpriced: activity.costKnown ? 0 : 1,
        tokens: activity.inputTokens + activity.outputTokens,
      },
    });
  }

  return {
    total,
    daily: [...daily.values()],
    models: [...models.values()].sort((a, b) => b.knownMicros - a.knownMicros),
    harnesses: [...harnesses.values()].sort((a, b) => b.knownMicros - a.knownMicros),
    activityTypes: [...activityTypes.values()].sort((a, b) => b.knownMicros - a.knownMicros),
    rows: rows.sort((a, b) => b.usage.knownMicros - a.usage.knownMicros),
    activities: activityRows.sort((a, b) => Date.parse(b.recordedAt) - Date.parse(a.recordedAt)),
  };
}

function emptyBucket(name: string): UsageBucket {
  return { name, knownMicros: 0, priced: 0, unpriced: 0, tokens: 0 };
}
