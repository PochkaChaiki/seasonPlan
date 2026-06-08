export default function ActorSelector({ actor, onChange, options }) {
  return (
    <label className="field compact">
      <span>Actor context</span>
      <select
        value={`${actor.role}|${actor.id}`}
        onChange={(event) => {
          const [role, id] = event.target.value.split('|');
          onChange({ role, id });
        }}
      >
        {options.map((option) => (
          <option key={`${option.role}|${option.id}`} value={`${option.role}|${option.id}`}>
            {option.role} ({option.id})
          </option>
        ))}
      </select>
    </label>
  );
}
