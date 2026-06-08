const STORAGE_KEY = 'seasonPlan.workflow';

const emptyWorkflow = {
  latestPlanId: null,
  latestPlan: null,
  reservationsByPlan: {},
  tasksByPlan: {},
  assignmentsByTask: {}
};

export function readWorkflow() {
  try {
    return { ...emptyWorkflow, ...JSON.parse(sessionStorage.getItem(STORAGE_KEY) ?? '{}') };
  } catch {
    return { ...emptyWorkflow };
  }
}

export function writeWorkflow(nextWorkflow) {
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ ...emptyWorkflow, ...nextWorkflow }));
}

export function cachePlan(plan) {
  const workflow = readWorkflow();
  writeWorkflow({ ...workflow, latestPlanId: plan.id, latestPlan: plan });
}

export function cacheReservation(planId, reservation) {
  const workflow = readWorkflow();
  writeWorkflow({
    ...workflow,
    reservationsByPlan: { ...workflow.reservationsByPlan, [planId]: reservation }
  });
}

export function cacheTask(planId, task) {
  const workflow = readWorkflow();
  const tasks = workflow.tasksByPlan[planId] ?? [];
  const nextTasks = tasks.some((item) => item.id === task.id)
    ? tasks.map((item) => (item.id === task.id ? task : item))
    : [...tasks, task];
  writeWorkflow({ ...workflow, tasksByPlan: { ...workflow.tasksByPlan, [planId]: nextTasks } });
}

export function cacheAssignment(taskId, assignment) {
  const workflow = readWorkflow();
  const assignments = workflow.assignmentsByTask[taskId] ?? [];
  writeWorkflow({
    ...workflow,
    assignmentsByTask: { ...workflow.assignmentsByTask, [taskId]: [...assignments, assignment] }
  });
}
