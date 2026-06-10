import { useEffect, useMemo, useState } from 'react';
import ActorSelector from '../../components/ActorSelector.jsx';
import AppShell from '../../components/AppShell.jsx';
import FieldErrors from '../../components/FieldErrors.jsx';
import { actors, lifecycleApi } from '../../api/lifecycleApi.js';
import { cachePlan, cachePlans, readWorkflow } from '../../api/workflowStorage.js';

const seasonTypeOptions = ['SPRING', 'SUMMER', 'AUTUMN', 'WINTER'];

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
  const initialPlan = cachedPlan?.status === 'DRAFT' ? cachedPlan : null;
  const cachedPlans = useMemo(() => Object.values(cached.plansById ?? {}), [cached.plansById]);
  const [actor, setActor] = useState(actors.planner);
  const [form, setForm] = useState(() => planToForm(initialPlan));
  const [plan, setPlan] = useState(initialPlan);
  const [availablePlans, setAvailablePlans] = useState(cachedPlans);
  const [selectedPlanId, setSelectedPlanId] = useState(initialPlan?.id ?? cachedPlan?.id ?? '');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState(null);

  const editableCachedPlan = plan?.id && plan.status === 'DRAFT';

  useEffect(() => {
    let active = true;
    lifecycleApi.listPlans()
      .then((plans) => {
        if (!active) {
          return;
        }
        setAvailablePlans(plans);
        cachePlans(plans);
        setSelectedPlanId((current) => current || plans[0]?.id || '');
      })
      .catch(() => {
        // Keep the cached session list available if the backend list endpoint is temporarily unreachable.
      });
    return () => {
      active = false;
    };
  }, []);

  function startNewDraft() {
    setForm(defaultForm);
    setPlan(null);
    setSelectedPlanId('');
    setMessage('Открыта форма нового черновика. Последний план в сессии не изменится до успешного сохранения.');
    setError(null);
  }

  function loadCachedDraft() {
    setForm(planToForm(cachedPlan));
    setPlan(cachedPlan);
    setSelectedPlanId(cachedPlan.id);
    setMessage(`Загружен черновик ${cachedPlan.id} из текущей сессии.`);
    setError(null);
  }

  function payload() {
    return {
      seasonType: form.seasonType,
      targetTerritories: form.targetTerritories.split(',').map((item) => item.trim()).filter(Boolean),
      timeWindows: form.timeWindows,
      naturalPhenomenonParameters: pairsToObject(form.naturalPhenomenonParameters),
      targetClimateMetrics: pairsToObject(form.targetClimateMetrics, true)
    };
  }

  async function loadSelectedPlan() {
    if (!selectedPlanId) {
      return;
    }
    setBusy(true);
    setError(null);
    setMessage('');
    try {
      const loadedPlan = await lifecycleApi.getPlan(selectedPlanId);
      setPlan(loadedPlan);
      setForm(planToForm(loadedPlan));
      cachePlan(loadedPlan);
      setAvailablePlans((current) =>
        current.some((item) => item.id === loadedPlan.id)
          ? current.map((item) => (item.id === loadedPlan.id ? loadedPlan : item))
          : [loadedPlan, ...current]
      );
      setMessage(`Loaded plan ${loadedPlan.id}`);
    } catch (loadError) {
      setError(loadError);
    } finally {
      setBusy(false);
    }
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
      setSelectedPlanId(result.id);
      setAvailablePlans((current) =>
        current.some((item) => item.id === result.id)
          ? current.map((item) => (item.id === result.id ? result : item))
          : [result, ...current]
      );
      cachePlan(result);
      setMessage(success(result));
    } catch (commandError) {
      setError(commandError);
    } finally {
      setBusy(false);
    }
  }

  return (
    <AppShell title="Планирование сезона" activeStep={1} plan={plan ?? cachedPlan}>
      <div className="content-grid two-col">
        <form className="card stack" onSubmit={(event) => event.preventDefault()}>
          <h4>{plan?.id ? 'Черновик SeasonPlan' : 'Новый черновик SeasonPlan'}</h4>
          <ActorSelector actor={actor} onChange={setActor} options={[actors.planner]} />
          <div className="field-grid">
          <label className="field">
            <span>Тип сезона</span>
            <select aria-label="season-type" value={form.seasonType} onChange={(event) => setForm({ ...form, seasonType: event.target.value })}>
              {seasonTypeOptions.map((seasonType) => (
                <option key={seasonType} value={seasonType}>{seasonType}</option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>Целевые территории</span>
            <textarea value={form.targetTerritories} onChange={(event) => setForm({ ...form, targetTerritories: event.target.value })} />
          </label>
          </div>
          <section className="stack">
            <h4>Окна проведения</h4>
            {form.timeWindows.map((window, index) => (
              <div className="mini-item wrap" key={`${window.start}-${index}`}>
                <label className="field">
                  <span>Начало</span>
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
                  <span>Окончание</span>
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
                    Удалить
                  </button>
                )}
              </div>
            ))}
            <button type="button" onClick={() => setForm({ ...form, timeWindows: [...form.timeWindows, { start: '', end: '' }] })}>
              Добавить окно
            </button>
          </section>
          <KeyValueEditor title="Параметры природных явлений" rows={form.naturalPhenomenonParameters} onChange={(index, field, value) => setPair('naturalPhenomenonParameters', index, field, value)} onAdd={() => setForm({ ...form, naturalPhenomenonParameters: [...form.naturalPhenomenonParameters, { key: '', value: '' }] })} />
          <KeyValueEditor title="Целевые климатические метрики" rows={form.targetClimateMetrics} onChange={(index, field, value) => setPair('targetClimateMetrics', index, field, value)} onAdd={() => setForm({ ...form, targetClimateMetrics: [...form.targetClimateMetrics, { key: '', value: '' }] })} />
          <div className="button-row">
            <button className="primary" disabled={busy} onClick={() => runCommand(() => lifecycleApi.createPlan(payload(), actor), (result) => `Сохранен черновик ${result.id}`)}>
              Сохранить новый черновик
            </button>
            {editableCachedPlan && (
              <button disabled={busy} onClick={() => runCommand(() => lifecycleApi.updatePlan(plan.id, payload(), actor), (result) => `Обновлен черновик ${result.id}`)}>
                Обновить текущий черновик
              </button>
            )}
            <button disabled={busy || !plan?.id} onClick={() => runCommand(() => lifecycleApi.submitPlan(plan.id, actor), (result) => `План ${result.id} отправлен на согласование`)}>
              Отправить на согласование
            </button>
            <button type="button" disabled={busy} onClick={startNewDraft}>
              Начать новый черновик
            </button>
          </div>
        </form>
        <aside className="stack">
          {message && <p className="note">{message}</p>}
          {error && <p className="note rose">{error.message}</p>}
          <FieldErrors errors={error?.errors} />
          <div className="card stack">
            <h4>Existing SeasonPlans</h4>
            {availablePlans.length > 0 ? (
              <>
                <label className="field">
                  <span>Choose plan</span>
                  <select aria-label="existing-plan" value={selectedPlanId} onChange={(event) => setSelectedPlanId(event.target.value)}>
                    {availablePlans.map((item) => (
                      <option key={item.id} value={item.id}>
                        {item.seasonType} - {item.status} - {item.targetTerritories?.join(', ') || item.id}
                      </option>
                    ))}
                  </select>
                </label>
                <button type="button" disabled={busy || !selectedPlanId} onClick={loadSelectedPlan}>
                  Load selected plan
                </button>
              </>
            ) : (
              <p className="note">No existing plans were returned by the backend yet.</p>
            )}
          </div>
          <div className="card">
            <h4>Текущий результат формы</h4>
            <p>Plan id: {plan?.id ?? 'еще не сохранен'}</p>
            <p>Статус: {plan?.status ?? 'не сохранен'}</p>
          </div>
          <div className="card stack">
            <h4>Последний план в сессии</h4>
            {cachedPlan ? (
              <>
                <div className="mini-list">
                  <div className="mini-item"><span>Plan id</span><strong>{cachedPlan.id}</strong></div>
                  <div className="mini-item"><span>Статус</span><strong>{cachedPlan.status}</strong></div>
                </div>
                {cachedPlan.status === 'DRAFT' && (
                  <button type="button" disabled={busy} onClick={loadCachedDraft}>
                    Загрузить этот черновик
                  </button>
                )}
                <p className="note">Можно создать новый черновик, даже если последний план еще выполняется или ожидает подготовки.</p>
              </>
            ) : (
              <p className="note">В этой сессии пока нет сохраненного плана.</p>
            )}
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
            <span>Ключ</span>
            <input value={row.key} onChange={(event) => onChange(index, 'key', event.target.value)} />
          </label>
          <label className="field">
            <span>Значение</span>
            <input value={row.value} onChange={(event) => onChange(index, 'value', event.target.value)} />
          </label>
        </div>
      ))}
      <button type="button" onClick={onAdd}>
        Добавить строку
      </button>
    </section>
  );
}
