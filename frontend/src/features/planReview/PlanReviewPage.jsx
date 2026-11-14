import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import AppShell from '../../components/AppShell.jsx';
import FieldErrors from '../../components/FieldErrors.jsx';
import LoadingState from '../../components/LoadingState.jsx';
import ReadinessSummary from '../../components/ReadinessSummary.jsx';
import { lifecycleApi } from '../../api/lifecycleApi.js';
import { cachePlan } from '../../api/workflowStorage.js';

export default function PlanReviewPage({ currentActor }) {
  const { id } = useParams();
  const actor = currentActor;
  const canDecide = !actor || actor?.roles?.includes('CHIEF_GUARDIAN') || actor?.role === 'CHIEF_GUARDIAN';
  const [plan, setPlan] = useState(null);
  const [readiness, setReadiness] = useState(null);
  const [readinessError, setReadinessError] = useState('');
  const [comment, setComment] = useState('');
  const [history, setHistory] = useState([]);
  const [historyError, setHistoryError] = useState('');
  const [conditions, setConditions] = useState([]);
  const [condition, setCondition] = useState({ key: '', displayText: '', evidenceRequirement: '', ownerRole: 'TALENT_COORDINATOR' });
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState(null);
  const canVerify = canDecide && plan?.status === 'SUBMITTED';
  const canApprove = canDecide && plan?.status === 'VERIFIED';
  const canReturn = canVerify || canApprove;
  const canViewOwnHistory = !canDecide && (actor?.roles?.includes('FAIRY_PLANNER') || actor?.role === 'FAIRY_PLANNER')
    && Boolean(actor?.id && plan?.createdBy === actor.id);
  const hasUnaddedCondition = [condition.key, condition.displayText, condition.evidenceRequirement].some((value) => value.trim());

  useEffect(() => {
    let active = true;
    setBusy(true);
    Promise.all([lifecycleApi.getPlan(id), lifecycleApi.getReadiness(id),
      canDecide ? lifecycleApi.getReviewHistory(id) : Promise.resolve([])])
      .then(([loadedPlan, loadedReadiness, loadedHistory]) => {
        if (active) {
          setPlan(loadedPlan);
          setReadiness(loadedReadiness);
          setHistory(loadedHistory);
          cachePlan(loadedPlan);
        }
      })
      .catch((loadError) => active && setError(loadError))
      .finally(() => active && setBusy(false));
    return () => {
      active = false;
    };
  }, [id, canDecide]);

  useEffect(() => {
    if (!canViewOwnHistory) return undefined;
    let active = true;
    lifecycleApi.getReviewHistory(id)
      .then((entries) => { if (active) setHistory(entries); })
      .catch(() => { if (active) setHistoryError('Не удалось загрузить историю аудита'); });
    return () => { active = false; };
  }, [id, canViewOwnHistory]);

  async function decide(action) {
    setBusy(true);
    setMessage('');
    setError(null);
    try {
      const result =
        action === 'verify'
          ? await lifecycleApi.verifyPlan(id)
          : action === 'approve'
            ? await lifecycleApi.approvePlan(id, { comment, conditions }, actor)
            : await lifecycleApi.returnPlan(id, { comment }, actor);
      setPlan(result);
      cachePlan(result);
      setComment('');
      setConditions([]);
      setReadiness(null);
      setReadinessError('');
      lifecycleApi.getReviewHistory(id).then(setHistory).catch(() => setHistoryError('Не удалось обновить историю аудита'));
      setMessage(action === 'verify' ? `План ${result.id} проверен` : result.status === 'APPROVED' ? `План ${result.id} утвержден` : `План ${result.id} возвращен на доработку`);
      try {
        setReadiness(await lifecycleApi.getReadiness(id));
      } catch {
        setReadinessError('Не удалось обновить готовность');
      }
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
      {canViewOwnHistory && <section className="card stack"><AuditHistory history={history} error={historyError} /></section>}
      {canDecide && (
      <div className="content-grid two-col">
        <section className="card stack">
          <h4>Сводка плана</h4>
          <SummaryRows plan={plan} />
          <ReadinessSummary readiness={readiness} />
          {readinessError && <p className="note rose" role="alert">{readinessError}</p>}
          <AuditHistory history={history} error={historyError} />
          <p className="note">Отдельного endpoint для валидации плана нет; показываем ошибки действий и ответ готовности.</p>
        </section>
        {canDecide ? (
        <aside className="card stack">
          <h4>Решение</h4>
          {!['SUBMITTED', 'VERIFIED'].includes(plan?.status) && (
            <p className="note">Планировщик должен отправить план на согласование. Решение доступно только для плана в статусе SUBMITTED или VERIFIED.</p>
          )}
          <label className="field">
            <span>Комментарий к решению</span>
            <textarea value={comment} onChange={(event) => setComment(event.target.value)} />
          </label>
          {canApprove && <fieldset className="stack">
            <legend>Условия утверждения</legend>
            <label className="field">Ключ условия<input value={condition.key} onChange={(event) => setCondition({ ...condition, key: event.target.value })} /></label>
            <label className="field">Название условия<input value={condition.displayText} onChange={(event) => setCondition({ ...condition, displayText: event.target.value })} /></label>
            <label className="field">Требуемое подтверждение<input value={condition.evidenceRequirement} onChange={(event) => setCondition({ ...condition, evidenceRequirement: event.target.value })} /></label>
            <label className="field">Роль владельца<select value={condition.ownerRole} onChange={(event) => setCondition({ ...condition, ownerRole: event.target.value })}>
              {['TALENT_COORDINATOR', 'POLLEN_GUARDIAN', 'CHIEF_GUARDIAN', 'FAIRY_PLANNER', 'CLIMATE_BALANCER', 'MAGIC_ANOMALY_CONTROLLER', 'FAIRY_PARTICIPANT'].map((role) => <option key={role} value={role}>{role}</option>)}
            </select></label>
            <button type="button" disabled={busy || !condition.key.trim() || !condition.displayText.trim() || !condition.evidenceRequirement.trim() || conditions.some((item) => item.key === condition.key)} onClick={() => {
              setConditions([...conditions, condition]);
              setCondition({ key: '', displayText: '', evidenceRequirement: '', ownerRole: 'TALENT_COORDINATOR' });
            }}>Добавить условие</button>
            <ul>{conditions.map((item) => <li key={item.key}>{item.key} — {item.displayText} — {item.evidenceRequirement} — {item.ownerRole} <button type="button" onClick={() => setConditions(conditions.filter((entry) => entry.key !== item.key))}>Удалить</button></li>)}</ul>
          </fieldset>}
          <div className="button-row">
            <button disabled={busy || !canVerify} onClick={() => decide('verify')}>
              Проверить план
            </button>
            <button className="primary" disabled={busy || !canApprove || hasUnaddedCondition} onClick={() => decide('approve')}>
              Утвердить план
            </button>
            <button disabled={busy || !canReturn || !comment.trim()} onClick={() => decide('return')}>
              Вернуть на доработку
            </button>
          </div>
          {message && <p className="note">{message}</p>}
          {error && <p className="note rose">{error.message}</p>}
          <FieldErrors errors={error?.errors} />
        </aside>
        ) : null}
      </div>
      )}
    </AppShell>
  );
}

function AuditHistory({ history, error }) {
  return <>
    <h4>История аудита</h4>
    {error && <p className="note rose">{error}</p>}
    {history.length === 0 ? <p>История пуста</p> : <ul>{history.map((entry) => (
      <li key={entry.id ?? `${entry.action}-${entry.createdAt}`}><strong>{entry.action}</strong> — {entry.actorId} — {entry.createdAt}{entry.comment ? ` — ${entry.comment}` : ''}</li>
    ))}</ul>}
  </>;
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
        <span>Условия утверждения</span>
        <strong>{(plan.approvalConditions ?? []).map((item) => `${item.key}: ${item.displayText} — ${item.evidenceRequirement} (${item.ownerRole})`).join('; ') || 'нет'}</strong>
      </div>
      <div className="mini-item">
        <span>Комментарий согласования</span>
        <strong>{plan.approvalComment ?? 'нет'}</strong>
      </div>
    </div>
  );
}
