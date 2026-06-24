import { actorOptions } from '../api/lifecycleApi.js';

export default function RoleObserverBox({ actor, onChange, plan }) {
  return (
    <aside className="role-observer" aria-label="Role observation context">
      <div>
        <h2>Role view</h2>
        <p>Available screens and controls follow the selected role.</p>
      </div>
      <label className="field compact">
        <span>Observe as role</span>
        <select
          aria-label="Observe as role"
          value={actor.role}
          onChange={(event) => {
            const nextActor = actorOptions.find((option) => option.role === event.target.value);
            if (nextActor) {
              onChange(nextActor);
            }
          }}
        >
          {actorOptions.map((option) => (
            <option key={option.role} value={option.role}>
              {option.role}
            </option>
          ))}
        </select>
      </label>
      <div className="role-status">
        <span>Current SeasonPlan</span>
        <strong>{plan?.status ?? 'NO_PLAN'}</strong>
      </div>
    </aside>
  );
}
