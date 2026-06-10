import { useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';
import ActorSelector from '../../components/ActorSelector.jsx';
import AppShell from '../../components/AppShell.jsx';
import FieldErrors from '../../components/FieldErrors.jsx';
import LoadingState from '../../components/LoadingState.jsx';
import ReadinessSummary from '../../components/ReadinessSummary.jsx';
import { actors, lifecycleApi } from '../../api/lifecycleApi.js';
import { cacheAssignment, cachePlan, cacheReservation, cacheTask, readWorkflow } from '../../api/workflowStorage.js';

const talentOptions = [
  'SPRING_WEATHER',
  'FROST_CONTROL',
  'LIGHT_MAGIC',
  'WATER_FLOW',
  'GARDEN_CARE'
];

const assignmentStatusOptions = ['PROPOSED', 'CONFIRMED'];
const assignmentRoleOptions = ['LEAD', 'SUPPORT', 'BACKUP'];
// Temporary candidate catalog until the backend exposes a fairy lookup endpoint.
// Schedule availability is still enforced by backend assignment validation.
const fairyOptionsByTalent = {
  SPRING_WEATHER: ['fairy-spring', 'fairy-bloom'],
  FROST_CONTROL: ['fairy-north', 'fairy-frost'],
  LIGHT_MAGIC: ['fairy-lumen', 'fairy-glow'],
  WATER_FLOW: ['fairy-river', 'fairy-rain'],
  GARDEN_CARE: ['fairy-grove', 'fairy-root']
};

const defaultTaskForm = {
  title: 'Запуск цветения',
  description: 'Подготовить раннюю последовательность цветения',
  requiredTalent: 'SPRING_WEATHER',
  start: '2027-03-02T09:00:00',
  end: '2027-03-02T12:00:00',
  status: 'READY'
};

const defaultAssignmentForm = {
  taskId: '',
  fairyId: 'fairy-spring',
  role: 'LEAD',
  talent: 'SPRING_WEATHER',
  status: 'CONFIRMED'
};

function compatibleFairiesForTalent(talent) {
  return fairyOptionsByTalent[talent] ?? [];
}

export default function PreparationPage() {
  const { id } = useParams();
  const workflow = useMemo(() => readWorkflow(), []);
  const [plan, setPlan] = useState(null);
  const [readiness, setReadiness] = useState(null);
  const [reservation, setReservation] = useState(workflow.reservationsByPlan[id]);
  const [tasks, setTasks] = useState(workflow.tasksByPlan[id] ?? []);
  const [assignmentsByTask, setAssignmentsByTask] = useState(workflow.assignmentsByTask ?? {});
  const [pollenActor, setPollenActor] = useState(actors.pollen);
  const [taskActor, setTaskActor] = useState(actors.coordinator);
  const [pollenForm, setPollenForm] = useState({ requestedAmount: '100', confirmedAmount: '100', comment: 'в пределах лимита' });
  const [taskForm, setTaskForm] = useState(defaultTaskForm);
  const [assignmentForm, setAssignmentForm] = useState(defaultAssignmentForm);
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

  async function refreshReadiness() {
    const nextReadiness = await lifecycleApi.getReadiness(id);
    setReadiness(nextReadiness);
  }

  async function runCommand(command, onSuccess) {
    setBusy(true);
    setMessage('');
    setError(null);
    try {
      const result = await command();
      await onSuccess(result);
    } catch (commandError) {
      setError(commandError);
    } finally {
      setBusy(false);
    }
  }

  function taskPayload() {
    return {
      title: taskForm.title,
      description: taskForm.description,
      requiredTalent: taskForm.requiredTalent,
      timeWindow: { start: taskForm.start, end: taskForm.end },
      status: taskForm.status
    };
  }

  function assignmentPayload() {
    const fairyOptions = compatibleFairyOptions();
    return {
      fairyId: fairyOptions.includes(assignmentForm.fairyId) ? assignmentForm.fairyId : fairyOptions[0] ?? assignmentForm.fairyId,
      role: assignmentForm.role,
      talent: assignmentForm.talent,
      status: assignmentForm.status
    };
  }

  function selectedTask() {
    return tasks.find((task) => task.id === assignmentForm.taskId) ?? tasks[0];
  }

  function selectedTaskTalent() {
    return selectedTask()?.requiredTalent ?? assignmentForm.talent;
  }

  function compatibleFairyOptions() {
    return compatibleFairiesForTalent(selectedTaskTalent());
  }

  if (busy && !plan) {
    return (
      <AppShell title="Подготовка сезона к запуску" activeStep={3}>
        <LoadingState label="Загружаем состояние подготовки..." />
      </AppShell>
    );
  }

  return (
    <AppShell title="Подготовка сезона к запуску" activeStep={3} plan={plan}>
      <div className="content-grid two-col">
        <section className="stack">
          <div className="card stack">
            <h4>Резерв пыльцы</h4>
            <ActorSelector actor={pollenActor} onChange={setPollenActor} options={[actors.pollen]} />
            <div className="field-grid">
              <label className="field">
                <span>Запрошенный объем</span>
                <input value={pollenForm.requestedAmount} onChange={(event) => setPollenForm({ ...pollenForm, requestedAmount: event.target.value })} />
              </label>
              <label className="field">
                <span>Подтвержденный объем</span>
                <input value={pollenForm.confirmedAmount} onChange={(event) => setPollenForm({ ...pollenForm, confirmedAmount: event.target.value })} />
              </label>
              <label className="field">
                <span>Комментарий по пыльце</span>
                <input value={pollenForm.comment} onChange={(event) => setPollenForm({ ...pollenForm, comment: event.target.value })} />
              </label>
            </div>
            <div className="button-row">
              <button
                className="primary"
                disabled={busy}
                onClick={() =>
                  runCommand(() => lifecycleApi.requestReservation(id, { requestedAmount: Number(pollenForm.requestedAmount) }, pollenActor), async (result) => {
                    setReservation(result);
                    cacheReservation(id, result);
                    setMessage(`Резерв пыльцы ${result.id} запрошен`);
                    await refreshReadiness();
                  })
                }
              >
                Запросить резерв
              </button>
              <button
                disabled={busy || !reservation?.id}
                onClick={() =>
                  runCommand(
                    () =>
                      lifecycleApi.confirmReservation(
                        reservation.id,
                        { confirmedAmount: Number(pollenForm.confirmedAmount), comment: pollenForm.comment },
                        pollenActor
                      ),
                    async (result) => {
                      setReservation(result);
                      cacheReservation(id, result);
                      setMessage(`Резерв пыльцы ${result.id} подтвержден`);
                      await refreshReadiness();
                    }
                  )
                }
              >
                Подтвердить резерв
              </button>
              <button
                disabled={busy || !reservation?.id}
                onClick={() =>
                  runCommand(() => lifecycleApi.rejectReservation(reservation.id, { comment: pollenForm.comment }, pollenActor), async (result) => {
                    setReservation(result);
                    cacheReservation(id, result);
                    setMessage(`Резерв пыльцы ${result.id} отклонен`);
                    await refreshReadiness();
                  })
                }
              >
                Отклонить резерв
              </button>
            </div>
            <ObjectSummary object={reservation} empty="В этой сессии еще нет резерва." />
          </div>

          <div className="card stack">
            <h4>MagicTask</h4>
            <ActorSelector actor={taskActor} onChange={setTaskActor} options={[actors.coordinator]} />
            <div className="field-grid">
              <label className="field">
                <span>Название задачи</span>
                <input value={taskForm.title} onChange={(event) => setTaskForm({ ...taskForm, title: event.target.value })} />
              </label>
              <label className="field">
                <span>Требуемый талант</span>
                <select value={taskForm.requiredTalent} onChange={(event) => setTaskForm({ ...taskForm, requiredTalent: event.target.value })}>
                  {talentOptions.map((talent) => (
                    <option key={talent} value={talent}>{talent}</option>
                  ))}
                </select>
              </label>
              <label className="field">
                <span>Описание задачи</span>
                <textarea value={taskForm.description} onChange={(event) => setTaskForm({ ...taskForm, description: event.target.value })} />
              </label>
              <label className="field">
                <span>Статус задачи</span>
                <select value={taskForm.status} onChange={(event) => setTaskForm({ ...taskForm, status: event.target.value })}>
                  <option value="READY">READY</option>
                  <option value="TODO">TODO</option>
                  <option value="BLOCKED">BLOCKED</option>
                </select>
              </label>
              <label className="field">
                <span>Начало задачи</span>
                <input value={taskForm.start} onChange={(event) => setTaskForm({ ...taskForm, start: event.target.value })} />
              </label>
              <label className="field">
                <span>Окончание задачи</span>
                <input value={taskForm.end} onChange={(event) => setTaskForm({ ...taskForm, end: event.target.value })} />
              </label>
            </div>
            <div className="button-row">
              <button
                className="primary"
                disabled={busy}
                onClick={() =>
                  runCommand(() => lifecycleApi.createTask(id, taskPayload(), taskActor), async (result) => {
                    const nextTasks = [...tasks, result];
                    setTasks(nextTasks);
                    setAssignmentForm((current) => {
                      const nextTalent = result.requiredTalent ?? current.talent;
                      return {
                        ...current,
                        taskId: result.id,
                        talent: nextTalent,
                        fairyId: compatibleFairiesForTalent(nextTalent)[0] ?? current.fairyId
                      };
                    });
                    cacheTask(id, result);
                    setMessage(`Задача ${result.id} создана`);
                    await refreshReadiness();
                  })
                }
              >
                Создать задачу
              </button>
              <button
                disabled={busy || !selectedTask()?.id}
                onClick={() =>
                  runCommand(() => lifecycleApi.updateTask(selectedTask().id, taskPayload(), taskActor), async (result) => {
                    setTasks(tasks.map((task) => (task.id === result.id ? result : task)));
                    setAssignmentForm((current) => {
                      if ((current.taskId || selectedTask()?.id) !== result.id) {
                        return current;
                      }
                      const nextTalent = result.requiredTalent ?? current.talent;
                      return {
                        ...current,
                        talent: nextTalent,
                        fairyId: compatibleFairiesForTalent(nextTalent)[0] ?? current.fairyId
                      };
                    });
                    cacheTask(id, result);
                    setMessage(`Задача ${result.id} обновлена`);
                    await refreshReadiness();
                  })
                }
              >
                Обновить выбранную задачу
              </button>
            </div>
          </div>

          <div className="card stack">
            <h4>FairyAssignment</h4>
            <div className="field-grid">
              <label className="field">
                <span>Задача для назначения</span>
                <select
                  aria-label="assignment-task"
                  value={assignmentForm.taskId || selectedTask()?.id || ''}
                  onChange={(event) => {
                    const task = tasks.find((item) => item.id === event.target.value);
                    const nextTalent = task?.requiredTalent ?? assignmentForm.talent;
                    setAssignmentForm({
                      ...assignmentForm,
                      taskId: event.target.value,
                      talent: nextTalent,
                      fairyId: compatibleFairiesForTalent(nextTalent)[0] ?? assignmentForm.fairyId
                    });
                  }}
                >
                  {tasks.length === 0 ? <option value="">Нет задач в сессии</option> : null}
                  {tasks.map((task) => (
                    <option key={task.id} value={task.id}>{task.title} ({task.id})</option>
                  ))}
                </select>
              </label>
              <label className="field">
                <span>Фея</span>
                <select
                  aria-label="assignment-fairy"
                  value={compatibleFairyOptions().includes(assignmentForm.fairyId) ? assignmentForm.fairyId : compatibleFairyOptions()[0] ?? ''}
                  onChange={(event) => setAssignmentForm({ ...assignmentForm, fairyId: event.target.value })}
                >
                  {compatibleFairyOptions().map((fairyId) => (
                    <option key={fairyId} value={fairyId}>{fairyId}</option>
                  ))}
                </select>
              </label>
              <label className="field">
                <span>Роль в назначении</span>
                <select aria-label="assignment-role" value={assignmentForm.role} onChange={(event) => setAssignmentForm({ ...assignmentForm, role: event.target.value })}>
                  {assignmentRoleOptions.map((role) => (
                    <option key={role} value={role}>{role}</option>
                  ))}
                </select>
              </label>
              <label className="field">
                <span>Талант назначения</span>
                <select value={assignmentForm.talent} onChange={(event) => setAssignmentForm({ ...assignmentForm, talent: event.target.value })}>
                  {talentOptions.map((talent) => (
                    <option key={talent} value={talent}>{talent}</option>
                  ))}
                </select>
              </label>
              <label className="field">
                <span>Статус назначения</span>
                <select aria-label="assignment-status" value={assignmentForm.status} onChange={(event) => setAssignmentForm({ ...assignmentForm, status: event.target.value })}>
                  {assignmentStatusOptions.map((status) => (
                    <option key={status} value={status}>{status}</option>
                  ))}
                </select>
              </label>
            </div>
            <button
              className="primary"
              disabled={busy || !selectedTask()?.id}
              onClick={() =>
                runCommand(() => lifecycleApi.assignFairy(selectedTask().id, assignmentPayload(), taskActor), async (result) => {
                  const next = { ...assignmentsByTask, [selectedTask().id]: [...(assignmentsByTask[selectedTask().id] ?? []), result] };
                  setAssignmentsByTask(next);
                  cacheAssignment(selectedTask().id, result);
                  setMessage(`Назначение ${result.id} подтверждено`);
                  await refreshReadiness();
                })
              }
            >
              Назначить фею
            </button>
          </div>

          <SessionLists tasks={tasks} assignmentsByTask={assignmentsByTask} />
        </section>

        <aside className="stack">
          <div className="card stack">
            <h4>Готовность к RitualSession</h4>
            <ReadinessSummary readiness={readiness} />
            <ChecklistControls
              checklist={plan?.readinessChecklist ?? []}
              busy={busy}
              onComplete={(key) =>
                runCommand(() => lifecycleApi.completeReadinessChecklistItem(id, key, taskActor), async (result) => {
                  setPlan(result);
                  cachePlan(result);
                  setMessage(`Checklist item ${key} complete`);
                  await refreshReadiness();
                })
              }
            />
            <button
              className="primary"
              disabled={busy}
              onClick={() =>
                runCommand(() => lifecycleApi.markRitualReady(id, taskActor), async (result) => {
                  setPlan(result);
                  cachePlan(result);
                  setMessage(`План ${result.id} готов к RitualSession`);
                  await refreshReadiness();
                })
              }
            >
              Подтвердить готовность
            </button>
          </div>
          <p className="note">
            Резервы, задачи и назначения показаны только для текущей сессии, потому что текущий API не предоставляет list endpoints.
          </p>
          <p className="note">Доступность фей API не раскрывает. Конфликты расписания показываются только из ошибок действия назначения.</p>
          {message && <p className="note">{message}</p>}
          {error && <p className="note rose">{error.message}</p>}
          <FieldErrors errors={error?.errors} />
        </aside>
      </div>
    </AppShell>
  );
}

function ObjectSummary({ object, empty }) {
  if (!object) {
    return <p className="note">{empty}</p>;
  }
  return (
    <div className="mini-list">
      {Object.entries(object).map(([key, value]) => (
        <div className="mini-item" key={key}>
          <span>{key}</span>
          <strong>{String(value)}</strong>
        </div>
      ))}
    </div>
  );
}

function ChecklistControls({ checklist, busy, onComplete }) {
  if (checklist.length === 0) {
    return <p className="note">No readiness checklist items returned for this plan.</p>;
  }
  return (
    <div className="mini-list">
      {checklist.map((item) => (
        <div className="mini-item" key={item.key}>
          <span>{item.key}: {item.complete ? 'complete' : 'incomplete'}</span>
          <button disabled={busy || item.complete} onClick={() => onComplete(item.key)}>
            Complete {item.key}
          </button>
        </div>
      ))}
    </div>
  );
}

function SessionLists({ tasks, assignmentsByTask }) {
  return (
    <div className="card stack">
      <h4>Задачи и состав команды</h4>
      <p className="note">Списки задач и назначений являются сессионными, не авторитетными данными backend.</p>
      {tasks.length === 0 ? (
        <p className="note">В этой сессии еще нет задач.</p>
      ) : (
        <table className="table">
          <thead>
            <tr>
              <th>Задача</th>
              <th>Талант</th>
              <th>Срок</th>
              <th>Статус</th>
              <th>Назначения</th>
            </tr>
          </thead>
          <tbody>
            {tasks.map((task) => (
              <tr key={task.id}>
                <td>{task.title}</td>
                <td>{task.requiredTalent}</td>
                <td>{task.timeWindow ? `${task.timeWindow.start} - ${task.timeWindow.end}` : '-'}</td>
                <td>{task.status}</td>
                <td>
                  {(assignmentsByTask[task.id] ?? []).map((assignment) => (
                    <span className="assignment-chip" key={assignment.id}>
                      <span>{assignment.fairyId}</span>
                      <strong>{assignment.status}</strong>
                    </span>
                  ))}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
