import { useMemo, useState } from 'react';
import ActorSelector from '../../components/ActorSelector.jsx';
import AppShell from '../../components/AppShell.jsx';
import FieldErrors from '../../components/FieldErrors.jsx';
import { actors, lifecycleApi } from '../../api/lifecycleApi.js';
import { cachePlan, readWorkflow } from '../../api/workflowStorage.js';

const defaultForm = {
  seasonType: 'SPRING',
  targetTerritories: 'Northern Forest, Central Valley',
  timeWindows: [{ start: '2027-03-01T09:00:00', end: '2027-03-05T18:00:00' }],
  naturalPhenomenonParameters: [{ key: 'blooming', value: 'early' }],
  targetClimateMetrics: [{ key: 'temperature', value: '12' }, { key: 'humidity', value: '60' }]
};

function pairsToObject(pairs, numeric = false) {
  return pairs.reduce((result, pair) => {
    if (pair.key.trim()) {
      result[pair.key.trim()] = numeric ? Number(pair.value) : pair.value;
    }
    return result;
  }, {});
}

function planToForm(plan) {
  if (!plan) {
    return defaultForm;
  }
  return {
    seasonType: plan.seasonType ?? 'SPRING',
    targetTerritories: (plan.targetTerritories ?? []).join(', '),
    timeWindows: plan.timeWindows?.length ? plan.timeWindows : defaultForm.timeWindows,
    naturalPhenomenonParameters: Object.entries(plan.naturalPhenomenonParameters ?? { blooming: 'early' }).map(([key, value]) => ({ key, value })),
    targetClimateMetrics: Object.entries(plan.targetClimateMetrics ?? { temperature: 12 }).map(([key, value]) => ({ key, value: String(value) }))
  };
}

export default function SeasonPlanningPage() {
  const cached = useMemo(() => readWorkflow(), []);
  const cachedPlan = cached.latestPlan;
  const [actor, setActor] = useState(actors.planner);
  const [form, setForm] = useState(() => planToForm(cachedPlan));
  const [plan, setPlan] = useState(cachedPlan);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState(null);

  const editableCachedPlan = plan?.id && plan.status === 'DRAFT';

  function payload() {
    return {
      seasonType: form.seasonType,
      targetTerritories: form.targetTerritories.split(',').map((item) => item.trim()).filter(Boolean),
      timeWindows: form.timeWindows,
      naturalPhenomenonParameters: pairsToObject(form.naturalPhenomenonParameters),
      targetClimateMetrics: pairsToObject(form.targetClimateMetrics, true)
    };
  }

  function setPair(group, index, field, value) {
    setForm((current) => ({
      ...current,
      [group]: current[group].map((pair, pairIndex) => (pairIndex === index ? { ...pair, [field]: value } : pair))
    }));
  }

  async function runCommand(command, success) {
    setBusy(true);
    setError(null);
    setMessage('');
    try {
      const result = await command();
      setPlan(result);
      cachePlan(result);
      setMessage(success(result));
    } catch (commandError) {
      setError(commandError);
    } finally {
      setBusy(false);
    }
  }

  return (
    <AppShell title="Plan the season" activeStep={1} plan={plan}>
      <div className="content-grid two-col">
        <form className="card stack" onSubmit={(event) => event.preventDefault()}>
          <h4>SeasonPlan draft</h4>
          <ActorSelector actor={actor} onChange={setActor} options={[actors.planner]} />
          <label className="field">
            <span>Season type</span>
            <input value={form.seasonType} onChange={(event) => setForm({ ...form, seasonType: event.target.value })} />
          </label>
          <label className="field">
            <span>Target territories</span>
            <textarea value={form.targetTerritories} onChange={(event) => setForm({ ...form, targetTerritories: event.target.value })} />
          </label>
          <section className="stack">
            <h4>Time windows</h4>
            {form.timeWindows.map((window, index) => (
              <div className="mini-item wrap" key={`${window.start}-${index}`}>
                <label className="field">
                  <span>Start</span>
                  <input
                    value={window.start}
                    onChange={(event) =>
                      setForm({
                        ...form,
                        timeWindows: form.timeWindows.map((item, itemIndex) =>
                          itemIndex === index ? { ...item, start: event.target.value } : item
                        )
                      })
                    }
                  />
                </label>
                <label className="field">
                  <span>End</span>
                  <input
                    value={window.end}
                    onChange={(event) =>
                      setForm({
                        ...form,
                        timeWindows: form.timeWindows.map((item, itemIndex) =>
                          itemIndex === index ? { ...item, end: event.target.value } : item
                        )
                      })
                    }
                  />
                </label>
                {form.timeWindows.length > 1 && (
                  <button
                    type="button"
                    onClick={() => setForm({ ...form, timeWindows: form.timeWindows.filter((_, itemIndex) => itemIndex !== index) })}
                  >
                    Remove
                  </button>
                )}
              </div>
            ))}
            <button type="button" onClick={() => setForm({ ...form, timeWindows: [...form.timeWindows, { start: '', end: '' }] })}>
              Add time window
            </button>
          </section>
          <KeyValueEditor title="Natural phenomenon parameters" rows={form.naturalPhenomenonParameters} onChange={(index, field, value) => setPair('naturalPhenomenonParameters', index, field, value)} onAdd={() => setForm({ ...form, naturalPhenomenonParameters: [...form.naturalPhenomenonParameters, { key: '', value: '' }] })} />
          <KeyValueEditor title="Target climate metrics" rows={form.targetClimateMetrics} onChange={(index, field, value) => setPair('targetClimateMetrics', index, field, value)} onAdd={() => setForm({ ...form, targetClimateMetrics: [...form.targetClimateMetrics, { key: '', value: '' }] })} />
          <div className="button-row">
            <button className="primary" disabled={busy} onClick={() => runCommand(() => lifecycleApi.createPlan(payload(), actor), (result) => `Saved draft ${result.id}`)}>
              Save draft
            </button>
            {editableCachedPlan && (
              <button disabled={busy} onClick={() => runCommand(() => lifecycleApi.updatePlan(plan.id, payload(), actor), (result) => `Updated draft ${result.id}`)}>
                Update draft
              </button>
            )}
            <button disabled={busy || !plan?.id} onClick={() => runCommand(() => lifecycleApi.submitPlan(plan.id, actor), (result) => `Submitted plan ${result.id}`)}>
              Submit for approval
            </button>
          </div>
        </form>
        <aside className="stack">
          {message && <p className="note">{message}</p>}
          {error && <p className="note rose">{error.message}</p>}
          <FieldErrors errors={error?.errors} />
          <div className="card">
            <h4>Current response</h4>
            <p>Plan id: {plan?.id ?? 'none'}</p>
            <p>Status: {plan?.status ?? 'unsaved'}</p>
          </div>
        </aside>
      </div>
    </AppShell>
  );
}

function KeyValueEditor({ title, rows, onChange, onAdd }) {
  return (
    <section className="stack">
      <h4>{title}</h4>
      {rows.map((row, index) => (
        <div className="mini-item wrap" key={`${title}-${index}`}>
          <label className="field">
            <span>Key</span>
            <input value={row.key} onChange={(event) => onChange(index, 'key', event.target.value)} />
          </label>
          <label className="field">
            <span>Value</span>
            <input value={row.value} onChange={(event) => onChange(index, 'value', event.target.value)} />
          </label>
        </div>
      ))}
      <button type="button" onClick={onAdd}>
        Add row
      </button>
    </section>
  );
}
