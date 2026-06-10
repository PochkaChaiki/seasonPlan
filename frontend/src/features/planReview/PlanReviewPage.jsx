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
      setMessage(action === 'approve' ? `План ${result.id} утвержден` : `План ${result.id} возвращен на доработку`);
    } catch (decisionError) {
      setError(decisionError);
    } finally {
      setBusy(false);
    }
  }

  if (busy && !plan) {
    return (
      <AppShell title="Согласование сезонного плана" activeStep={2}>
        <LoadingState label="Загружаем план и готовность..." />
      </AppShell>
    );
  }

  return (
    <AppShell title="Согласование сезонного плана" activeStep={2} plan={plan}>
      <div className="content-grid two-col">
        <section className="card stack">
          <h4>Сводка плана</h4>
          <SummaryRows plan={plan} />
          <ReadinessSummary readiness={readiness} />
          <p className="note">История аудита не раскрыта текущим API.</p>
          <p className="note">Отдельного endpoint для валидации плана нет; показываем ошибки действий и ответ готовности.</p>
        </section>
        <aside className="card stack">
          <h4>Решение</h4>
          <ActorSelector actor={actor} onChange={setActor} options={[actors.guardian]} />
          <label className="field">
            <span>Комментарий к решению</span>
            <textarea value={comment} onChange={(event) => setComment(event.target.value)} />
          </label>
          <div className="button-row">
            <button className="primary" disabled={busy} onClick={() => decide('approve')}>
              Утвердить план
            </button>
            <button disabled={busy} onClick={() => decide('return')}>
              Вернуть на доработку
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
    return <p className="note rose">План не удалось загрузить.</p>;
  }

  return (
    <div className="mini-list">
      <div className="mini-item">
        <span>Тип сезона</span>
        <strong>{plan.seasonType}</strong>
      </div>
      <div className="mini-item">
        <span>Территории</span>
        <strong>{(plan.targetTerritories ?? []).join(', ')}</strong>
      </div>
      <div className="mini-item">
        <span>Окна проведения</span>
        <strong>{(plan.timeWindows ?? []).map((window) => `${window.start} - ${window.end}`).join(', ')}</strong>
      </div>
      <div className="mini-item">
        <span>Климатические метрики</span>
        <strong>{Object.entries(plan.targetClimateMetrics ?? {}).map(([key, value]) => `${key}: ${value}`).join(', ')}</strong>
      </div>
      <div className="mini-item">
        <span>Природные параметры</span>
        <strong>{Object.entries(plan.naturalPhenomenonParameters ?? {}).map(([key, value]) => `${key}: ${value}`).join(', ')}</strong>
      </div>
      <div className="mini-item">
        <span>Комментарий согласования</span>
        <strong>{plan.approvalComment ?? 'нет'}</strong>
      </div>
    </div>
  );
}
