export default function StatusPill({ status }) {
  if (!status) {
    return null;
  }
  const labels = {
    DRAFT: 'Черновик',
    SUBMITTED: 'На согласовании',
    VERIFIED: 'Проверен',
    APPROVED: 'Утвержден',
    RITUAL_READY: 'Готов к RitualSession',
    ACTIVE: 'Активен',
    COMPLETED: 'Завершен',
    ARCHIVED: 'В архиве',
    PENDING: 'Ожидает',
    CONFIRMED: 'Подтверждено',
    REJECTED: 'Отклонено',
    READY: 'Готово',
    BLOCKED: 'Заблокировано',
    TODO: 'Нужно сделать'
  };
  const warning = ['PENDING', 'BLOCKED', 'TODO', 'REJECTED'].includes(status);
  return <span className={`pill ${warning ? 'warn' : 'accent'}`}>{labels[status] ? `${labels[status]} (${status})` : status}</span>;
}
