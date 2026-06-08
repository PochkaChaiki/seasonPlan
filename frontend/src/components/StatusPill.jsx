export default function StatusPill({ status }) {
  if (!status) {
    return null;
  }
  const warning = ['PENDING', 'BLOCKED', 'TODO', 'REJECTED'].includes(status);
  return <span className={`pill ${warning ? 'warn' : 'accent'}`}>{status}</span>;
}
