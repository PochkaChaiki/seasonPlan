import { beforeEach, describe, expect, it } from 'vitest';
import { completePollenRequest, preparePollenRequest } from './pollenRequestIntent.js';

const actor = { id: 'guardian-1' };
const storageKey = 'seasonPlan.pollenRequestIntents';

describe('pollenRequestIntent', () => {
  beforeEach(() => sessionStorage.clear());

  it('persists before return and scopes uncertain intents by actor and plan', () => {
    const original = preparePollenRequest(actor, 'plan-1', 100);
    expect(JSON.parse(sessionStorage.getItem(storageKey))[JSON.stringify(['guardian-1', 'plan-1'])]).toEqual(original);
    expect(preparePollenRequest(actor, 'plan-1', 100)).toEqual(original);
    expect(preparePollenRequest({ id: 'guardian-2' }, 'plan-1', 100).requestId).not.toBe(original.requestId);
    expect(preparePollenRequest(actor, 'plan-2', 100).requestId).not.toBe(original.requestId);
  });

  it('only clears the known completed intent, permitting a deliberate new request', () => {
    const original = preparePollenRequest(actor, 'plan-1', 100);
    completePollenRequest(actor, 'plan-1', 'different-request');
    expect(preparePollenRequest(actor, 'plan-1', 100)).toEqual(original);
    completePollenRequest(actor, 'plan-1', original.requestId);
    expect(preparePollenRequest(actor, 'plan-1', 100).requestId).not.toBe(original.requestId);
  });

  it.each(['{broken', 'null', '[]', '{"[\\"guardian-1\\",\\"plan-1\\"]":null}', '{"[\\"guardian-1\\",\\"plan-1\\"]":{"requestId":"","requestedAmount":100}}'])('fails closed on corrupt storage: %s', (stored) => {
    sessionStorage.setItem(storageKey, stored);
    expect(() => preparePollenRequest(actor, 'plan-1', 100)).toThrow();
    expect(sessionStorage.getItem(storageKey)).toBe(stored);
  });
});
