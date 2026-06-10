export default function ReadinessSummary({ readiness }) {
  if (!readiness) {
    return <p className="note">Готовность еще не загружена.</p>;
  }

  const entries = Object.entries(readiness.errors ?? {});
  if (readiness.ready || entries.length === 0) {
    return <p className="note">Готовность: да. Ошибок проверки не получено.</p>;
  }

  return (
    <div className="note warn">
      <strong>Проверка готовности</strong>
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
