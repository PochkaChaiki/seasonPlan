const STORAGE_KEY = 'seasonPlan.pollenRequestIntents';

function scope(actor, planId) {
  return JSON.stringify([actor?.id ?? actor?.username ?? 'legacy-session', planId]);
}

function readIntents() {
  // Corrupt or unavailable storage must stop dispatch, not create a replacement key.
  const intents = JSON.parse(sessionStorage.getItem(STORAGE_KEY) ?? '{}');
  if (!intents || typeof intents !== 'object' || Array.isArray(intents)) {
    throw new Error('Не удалось прочитать сохраненный запрос резерва');
  }
  return intents;
}

export function preparePollenRequest(actor, planId, requestedAmount) {
  const intents = readIntents();
  const key = scope(actor, planId);
  const existing = intents[key];
  if (Object.hasOwn(intents, key)) {
    if (!existing || typeof existing.requestId !== 'string' || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(existing.requestId) || existing.requestedAmount !== requestedAmount) {
      throw new Error('Предыдущий запрос резерва не завершен. Повторите исходный объем.');
    }
    return { requestId: existing.requestId, requestedAmount: existing.requestedAmount };
  }
  const intent = { requestId: crypto.randomUUID(), requestedAmount };
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ ...intents, [key]: intent }));
  return intent;
}

export function completePollenRequest(actor, planId, requestId) {
  const intents = readIntents();
  const key = scope(actor, planId);
  if (intents[key]?.requestId === requestId) {
    delete intents[key];
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(intents));
  }
}
