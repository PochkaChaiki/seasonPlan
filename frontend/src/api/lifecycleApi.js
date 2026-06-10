const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

async function request(path, { method = 'GET', body, actor } = {}) {
  const headers = { Accept: 'application/json' };

  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }

  if (actor) {
    headers['X-Actor-Role'] = actor.role;
    headers['X-Actor-Id'] = actor.id;
  }

  let response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body)
    });
  } catch {
    throw { message: 'Network error. Check that the backend API is reachable.', errors: {} };
  }

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw {
      message: data.message ?? `Request failed with status ${response.status}`,
      errors: data.errors ?? {}
    };
  }
  return data;
}

export const actors = {
  planner: { role: 'FAIRY_PLANNER', id: 'planner-1' },
  guardian: { role: 'CHIEF_GUARDIAN', id: 'guardian-1' },
  coordinator: { role: 'TALENT_COORDINATOR', id: 'coordinator-1' },
  pollen: { role: 'POLLEN_GUARDIAN', id: 'pollen-1' }
};

export const lifecycleApi = {
  createPlan: (payload, actor) => request('/season-plans', { method: 'POST', body: payload, actor }),
  updatePlan: (id, payload, actor) => request(`/season-plans/${id}`, { method: 'PATCH', body: payload, actor }),
  submitPlan: (id, actor) => request(`/season-plans/${id}/submit`, { method: 'POST', actor }),
  approvePlan: (id, payload, actor) => request(`/season-plans/${id}/approve`, { method: 'POST', body: payload, actor }),
  returnPlan: (id, payload, actor) => request(`/season-plans/${id}/return`, { method: 'POST', body: payload, actor }),
  listPlans: () => request('/season-plans'),
  getPlan: (id) => request(`/season-plans/${id}`),
  getReadiness: (id) => request(`/season-plans/${id}/readiness`),
  requestReservation: (id, payload, actor) =>
    request(`/season-plans/${id}/pollen-reservations`, { method: 'POST', body: payload, actor }),
  confirmReservation: (id, payload, actor) =>
    request(`/pollen-reservations/${id}/confirm`, { method: 'POST', body: payload, actor }),
  rejectReservation: (id, payload, actor) =>
    request(`/pollen-reservations/${id}/reject`, { method: 'POST', body: payload, actor }),
  createTask: (id, payload, actor) => request(`/season-plans/${id}/tasks`, { method: 'POST', body: payload, actor }),
  updateTask: (id, payload, actor) => request(`/tasks/${id}`, { method: 'PATCH', body: payload, actor }),
  assignFairy: (id, payload, actor) => request(`/tasks/${id}/assignments`, { method: 'POST', body: payload, actor }),
  completeReadinessChecklistItem: (id, key, actor) =>
    request(`/season-plans/${id}/readiness-checklist/${encodeURIComponent(key)}/complete`, { method: 'POST', actor }),
  markRitualReady: (id, actor) => request(`/season-plans/${id}/mark-ritual-ready`, { method: 'POST', actor })
};
