const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api';
let csrfToken = null;
let csrfHeaderName = 'X-XSRF-TOKEN';

export function setCsrfToken(token, headerName = 'X-XSRF-TOKEN') {
  csrfToken = token;
  csrfHeaderName = headerName;
}

export async function apiRequest(path, { method = 'GET', body } = {}) {
  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (method !== 'GET' && method !== 'HEAD' && csrfToken) headers[csrfHeaderName] = csrfToken;
  let response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method, headers, credentials: 'include', body: body === undefined ? undefined : JSON.stringify(body)
    });
  } catch {
    throw { message: 'Network error. Check that the backend API is reachable.', errors: {} };
  }
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw { status: response.status, code: data.code, message: data.message ?? `Request failed with status ${response.status}`, errors: data.errors ?? {} };
  return data;
}

export const lifecycleApi = {
  createPlan: (payload) => apiRequest('/season-plans', { method: 'POST', body: payload }),
  updatePlan: (id, payload) => apiRequest(`/season-plans/${id}`, { method: 'PATCH', body: payload }),
  submitPlan: (id) => apiRequest(`/season-plans/${id}/submit`, { method: 'POST' }),
  approvePlan: (id, payload) => apiRequest(`/season-plans/${id}/approve`, { method: 'POST', body: payload }),
  returnPlan: (id, payload) => apiRequest(`/season-plans/${id}/return`, { method: 'POST', body: payload }),
  listPlans: () => apiRequest('/season-plans'), getPlan: (id) => apiRequest(`/season-plans/${id}`),
  getReadiness: (id) => apiRequest(`/season-plans/${id}/readiness`), listTasks: (id) => apiRequest(`/season-plans/${id}/tasks`),
  requestReservation: (id, payload) => apiRequest(`/season-plans/${id}/pollen-reservations`, { method: 'POST', body: payload }),
  confirmReservation: (id, payload) => apiRequest(`/pollen-reservations/${id}/confirm`, { method: 'POST', body: payload }),
  rejectReservation: (id, payload) => apiRequest(`/pollen-reservations/${id}/reject`, { method: 'POST', body: payload }),
  createTask: (id, payload) => apiRequest(`/season-plans/${id}/tasks`, { method: 'POST', body: payload }),
  updateTask: (id, payload) => apiRequest(`/tasks/${id}`, { method: 'PATCH', body: payload }),
  listAssignments: (id) => apiRequest(`/tasks/${id}/assignments`),
  assignFairy: (id, payload) => apiRequest(`/tasks/${id}/assignments`, { method: 'POST', body: payload }),
  completeReadinessChecklistItem: (id, key) => apiRequest(`/season-plans/${id}/readiness-checklist/${encodeURIComponent(key)}/complete`, { method: 'POST' }),
  markRitualReady: (id) => apiRequest(`/season-plans/${id}/mark-ritual-ready`, { method: 'POST' })
};
