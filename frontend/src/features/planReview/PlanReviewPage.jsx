import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import ActorSelector from '../../components/ActorSelector.jsx';
import AppShell from '../../components/AppShell.jsx';
import FieldErrors from '../../components/FieldErrors.jsx';
import LoadingState from '../../components/LoadingState.jsx';
import ReadinessSummary from '../../components/ReadinessSummary.jsx';
import { actors, lifecycleApi } from '../../api/lifecycleApi.js';
import { cachePlan } from '../../api/workflowStorage.js';

export default function PlanReviewPage() {
  const { id } = useParams();
  const [actor, setActor] = useState(actors.guardian);
  const [plan, setPlan] = useState(null);
  const [readiness, setReadiness] = useState(null);
  const [comment, setComment] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState(null);

  useEffect(() => {
    let active = true;
    setBusy(true);
    Promise.all([lifecycleApi.getPlan(id), lifecycleApi.getReadiness(id)])
      .then(([loadedPlan, loadedReadiness]) => {
        if (active) {
          setPlan(loadedPlan);
          setReadiness(loadedReadiness);
          cachePlan(loadedPlan);
        }
      })
      .catch((loadError) => active && setError(loadError))
      .finally(() => active && setBusy(false));
    return () => {
      active = false;
    };
  }, [id]);

  async function decide(action) {
    setBusy(true);
    setMessage('');
    setError(null);
    try {
      const result =
        action === 'approve'
          ? await lifecycleApi.approvePlan(id, { comment }, actor)
          : await lifecycleApi.returnPlan(id, { comment }, actor);
      setPlan(result);
      cachePlan(result);
      setComment('');
      setMessage(action === 'approve' ? `Approved plan ${result.id}` : `Returned plan ${result.id}`);
    } catch (decisionError) {
      setError(decisionError);
    } finally {
      setBusy(false);
    }
  }

  if (busy && !plan) {
    return (
      <AppShell title="Review and approve" activeStep={2}>
        <LoadingState label="Loading plan and readiness..." />
      </AppShell>
    );
  }

  return (
    <AppShell title="Review and approve" activeStep={2} plan={plan}>
      <div className="content-grid two-col">
        <section className="card stack">
          <h4>Plan summary</h4>
          <SummaryRows plan={plan} />
          <ReadinessSummary readiness={readiness} />
          <p className="note">Audit history is not exposed by the current API.</p>
          <p className="note">Standalone plan validation is not exposed; action errors and readiness are shown as available feedback.</p>
        </section>
        <aside className="card stack">
          <h4>Decision</h4>
          <ActorSelector actor={actor} onChange={setActor} options={[actors.guardian]} />
          <label className="field">
            <span>Decision comment</span>
            <textarea value={comment} onChange={(event) => setComment(event.target.value)} />
          </label>
          <div className="button-row">
            <button className="primary" disabled={busy} onClick={() => decide('approve')}>
              Approve plan
            </button>
            <button disabled={busy} onClick={() => decide('return')}>
              Return for revision
            </button>
          </div>
          {message && <p className="note">{message}</p>}
          {error && <p className="note rose">{error.message}</p>}
          <FieldErrors errors={error?.errors} />
        </aside>
      </div>
    </AppShell>
  );
}

function SummaryRows({ plan }) {
  if (!plan) {
    return <p className="note rose">Plan could not be loaded.</p>;
  }

  return (
    <div className="mini-list">
      <div className="mini-item">
        <span>Season type</span>
        <strong>{plan.seasonType}</strong>
      </div>
      <div className="mini-item">
        <span>Territories</span>
        <strong>{(plan.targetTerritories ?? []).join(', ')}</strong>
      </div>
      <div className="mini-item">
        <span>Windows</span>
        <strong>{(plan.timeWindows ?? []).map((window) => `${window.start} to ${window.end}`).join(', ')}</strong>
      </div>
      <div className="mini-item">
        <span>Climate metrics</span>
        <strong>{Object.entries(plan.targetClimateMetrics ?? {}).map(([key, value]) => `${key}: ${value}`).join(', ')}</strong>
      </div>
      <div className="mini-item">
        <span>Natural parameters</span>
        <strong>{Object.entries(plan.naturalPhenomenonParameters ?? {}).map(([key, value]) => `${key}: ${value}`).join(', ')}</strong>
      </div>
      <div className="mini-item">
        <span>Approval comment</span>
        <strong>{plan.approvalComment ?? 'none'}</strong>
      </div>
    </div>
  );
}
