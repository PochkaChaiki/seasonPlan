export default function FieldErrors({ errors }) {
  if (!errors || Object.keys(errors).length === 0) {
    return null;
  }

  return (
    <div className="field-errors" role="alert">
      {Object.entries(errors).map(([field, messages]) => (
        <div key={field}>
          <strong>{field}</strong>
          <ul>
            {(Array.isArray(messages) ? messages : [messages]).map((message) => (
              <li key={message}>{message}</li>
            ))}
          </ul>
        </div>
      ))}
    </div>
  );
}
