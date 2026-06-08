export default function ReadinessSummary({ readiness }) {
  if (!readiness) {
    return <p className="note">Readiness has not been loaded.</p>;
  }

  const entries = Object.entries(readiness.errors ?? {});
  if (readiness.ready || entries.length === 0) {
    return <p className="note">Readiness: ready. No validation errors were reported.</p>;
  }

  return (
    <div className="note warn">
      <strong>Readiness validation summary</strong>
      {entries.map(([field, messages]) => (
        <div key={field} className="error-group">
          <span>{field}</span>
          <ul>
            {messages.map((message) => (
              <li key={message}>{message}</li>
            ))}
          </ul>
        </div>
      ))}
    </div>
  );
}
