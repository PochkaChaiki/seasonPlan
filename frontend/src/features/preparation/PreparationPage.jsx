import { useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';
import ActorSelector from '../../components/ActorSelector.jsx';
import AppShell from '../../components/AppShell.jsx';
import FieldErrors from '../../components/FieldErrors.jsx';
import LoadingState from '../../components/LoadingState.jsx';
import ReadinessSummary from '../../components/ReadinessSummary.jsx';
import { actors, lifecycleApi } from '../../api/lifecycleApi.js';
import { cacheAssignment, cachePlan, cacheReservation, cacheTask, readWorkflow } from '../../api/workflowStorage.js';

const defaultTask = {
  title: 'Launch blooming',
  description: 'Prepare early blooming sequence',
  requiredTalent: 'SPRING_WEATHER',
  timeWindow: { start: '2027-03-02T09:00:00', end: '2027-03-02T12:00:00' },
  status: 'READY'
};

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

  function firstTask() {
    return tasks[0];
  }

  if (busy && !plan) {
    return (
      <AppShell title="Preparation for launch" activeStep={3}>
        <LoadingState label="Loading preparation state..." />
      </AppShell>
    );
  }

  return (
    <AppShell title="Preparation for launch" activeStep={3} plan={plan}>
      <div className="content-grid two-col">
        <section className="stack">
          <div className="card stack">
            <h4>Pollen reservation</h4>
            <ActorSelector actor={pollenActor} onChange={setPollenActor} options={[actors.pollen]} />
            <div className="button-row">
              <button
                className="primary"
                disabled={busy}
                onClick={() =>
                  runCommand(() => lifecycleApi.requestReservation(id, { requestedAmount: 100 }, pollenActor), async (result) => {
                    setReservation(result);
                    cacheReservation(id, result);
                    setMessage(`Pollen reservation ${result.id} requested`);
                    await refreshReadiness();
                  })
                }
              >
                Request pollen reservation
              </button>
              <button
                disabled={busy || !reservation?.id}
                onClick={() =>
                  runCommand(
                    () => lifecycleApi.confirmReservation(reservation.id, { confirmedAmount: 100, comment: 'within limit' }, pollenActor),
                    async (result) => {
                      setReservation(result);
                      cacheReservation(id, result);
                      setMessage(`Pollen reservation ${result.id} confirmed`);
                      await refreshReadiness();
                    }
                  )
                }
              >
                Confirm reservation
              </button>
              <button
                disabled={busy || !reservation?.id}
                onClick={() =>
                  runCommand(() => lifecycleApi.rejectReservation(reservation.id, { comment: 'not available' }, pollenActor), async (result) => {
                    setReservation(result);
                    cacheReservation(id, result);
                    setMessage(`Pollen reservation ${result.id} rejected`);
                    await refreshReadiness();
                  })
                }
              >
                Reject reservation
              </button>
            </div>
            <ObjectSummary object={reservation} empty="No session reservation yet." />
          </div>

          <div className="card stack">
            <h4>MagicTask and FairyAssignment</h4>
            <ActorSelector actor={taskActor} onChange={setTaskActor} options={[actors.coordinator]} />
            <div className="button-row">
              <button
                className="primary"
                disabled={busy}
                onClick={() =>
                  runCommand(() => lifecycleApi.createTask(id, defaultTask, taskActor), async (result) => {
                    const nextTasks = [...tasks, result];
                    setTasks(nextTasks);
                    cacheTask(id, result);
                    setMessage(`Task ${result.id} created`);
                    await refreshReadiness();
                  })
                }
              >
                Create task
              </button>
              <button
                disabled={busy || !firstTask()?.id}
                onClick={() =>
                  runCommand(() => lifecycleApi.updateTask(firstTask().id, defaultTask, taskActor), async (result) => {
                    setTasks(tasks.map((task) => (task.id === result.id ? result : task)));
                    cacheTask(id, result);
                    setMessage(`Task ${result.id} updated`);
                    await refreshReadiness();
                  })
                }
              >
                Update task
              </button>
              <button
                disabled={busy || !firstTask()?.id}
                onClick={() =>
                  runCommand(
                    () =>
                      lifecycleApi.assignFairy(
                        firstTask().id,
                        { fairyId: 'fairy-1', role: 'LEAD', talent: 'SPRING_WEATHER', status: 'CONFIRMED' },
                        taskActor
                      ),
                    async (result) => {
                      const next = { ...assignmentsByTask, [firstTask().id]: [...(assignmentsByTask[firstTask().id] ?? []), result] };
                      setAssignmentsByTask(next);
                      cacheAssignment(firstTask().id, result);
                      setMessage(`Assignment ${result.id} confirmed`);
                      await refreshReadiness();
                    }
                  )
                }
              >
                Assign fairy
              </button>
            </div>
            <SessionLists tasks={tasks} assignmentsByTask={assignmentsByTask} />
          </div>
        </section>

        <aside className="stack">
          <div className="card stack">
            <h4>Readiness</h4>
            <ReadinessSummary readiness={readiness} />
            <button
              className="primary"
              disabled={busy}
              onClick={() =>
                runCommand(() => lifecycleApi.markRitualReady(id, taskActor), async (result) => {
                  setPlan(result);
                  cachePlan(result);
                  setMessage(`Plan ${result.id} marked ritual ready`);
                  await refreshReadiness();
                })
              }
            >
              Mark ritual ready
            </button>
          </div>
          <p className="note">
            Reservations, tasks, and assignments are session-only lists because the current API does not expose list endpoints.
          </p>
          <p className="note">Fairy availability is not exposed by the current API. Assignment action errors are shown when returned.</p>
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

function SessionLists({ tasks, assignmentsByTask }) {
  return (
    <div className="stack">
      <p className="note">Session-only tasks and assignments.</p>
      {tasks.length === 0 ? (
        <p className="note">No session tasks yet.</p>
      ) : (
        <table className="table">
          <thead>
            <tr>
              <th>Task</th>
              <th>Status</th>
              <th>Assignments</th>
            </tr>
          </thead>
          <tbody>
            {tasks.map((task) => (
              <tr key={task.id}>
                <td>{task.title}</td>
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
