import { useState } from 'react';
import { useAuth } from './AuthContext.jsx';
import { PASSWORD_HELP, validateNewPassword } from './passwordPolicy.js';

const USER_ROLES = [
  ['FAIRY_PLANNER', 'Планировщик сезона'],
  ['CHIEF_GUARDIAN', 'Главный хранитель'],
  ['TALENT_COORDINATOR', 'Координатор талантов'],
  ['POLLEN_GUARDIAN', 'Хранитель пыльцы'],
  ['CLIMATE_BALANCER', 'Балансировщик климата'],
  ['MAGIC_ANOMALY_CONTROLLER', 'Контролер магических аномалий'],
  ['FAIRY_PARTICIPANT', 'Участник-фея']
];

const emptyForm = { username: '', displayName: '', password: '', roles: [] };

function messages(errors, field) {
  return errors[field]?.map((message) => <li key={message}>{message}</li>);
}

export default function UserRegistrationPage() {
  const { createUser } = useAuth();
  const [form, setForm] = useState(emptyForm);
  const [errors, setErrors] = useState({});
  const [globalError, setGlobalError] = useState('');
  const [success, setSuccess] = useState('');
  const [busy, setBusy] = useState(false);

  const update = (field, value) => setForm((current) => ({ ...current, [field]: value }));
  const toggleRole = (role) => update('roles', form.roles.includes(role) ? form.roles.filter((item) => item !== role) : [...form.roles, role]);
  const validate = () => {
    const next = {};
    if (!form.username.trim()) next.username = ['Введите имя пользователя.'];
    if (!form.displayName.trim()) next.displayName = ['Введите отображаемое имя.'];
    const passwordError = validateNewPassword(form.password);
    if (passwordError) next.password = [passwordError];
    if (!form.roles.length) next.roles = ['Выберите хотя бы одну роль.'];
    return next;
  };
  const submit = async (event) => {
    event.preventDefault();
    setSuccess(''); setGlobalError('');
    const validationErrors = validate();
    if (Object.keys(validationErrors).length) { setErrors(validationErrors); return; }
    setBusy(true); setErrors({});
    try {
      await createUser({ ...form, username: form.username.trim(), displayName: form.displayName.trim() });
      setForm(emptyForm);
      setSuccess('Пользователь создан. Передайте учетные данные безопасным способом.');
    } catch (reason) {
      setForm((current) => ({ ...current, password: '' }));
      setErrors(reason.errors ?? {});
      setGlobalError(reason.message ?? 'Не удалось создать пользователя.');
    } finally { setBusy(false); }
  };

  return <main className="page-shell"><section className="card compact">
    <h1>Регистрация пользователя</h1>
    <p className="note">Создайте учетную запись и назначьте роли. Пароль не сохраняется в браузере.</p>
    {success && <p role="status" className="note">{success}</p>}
    {globalError && <p role="alert" className="field-errors">{globalError}</p>}
    <form className="stack" onSubmit={submit} noValidate>
      <label className="field"><span>Имя пользователя</span><input aria-label="Имя пользователя" autoComplete="username" value={form.username} onChange={(event) => update('username', event.target.value)} />{errors.username && <ul>{messages(errors, 'username')}</ul>}</label>
      <label className="field"><span>Отображаемое имя</span><input aria-label="Отображаемое имя" autoComplete="name" value={form.displayName} onChange={(event) => update('displayName', event.target.value)} />{errors.displayName && <ul>{messages(errors, 'displayName')}</ul>}</label>
      <label className="field"><span>Пароль</span><input aria-label="Пароль" type="password" autoComplete="new-password" value={form.password} onChange={(event) => update('password', event.target.value)} />{errors.password && <ul>{messages(errors, 'password')}</ul>}<small>{PASSWORD_HELP}</small></label>
      <fieldset className="role-checkboxes"><legend>Роли</legend>{USER_ROLES.map(([role, label]) => <label key={role}><input type="checkbox" checked={form.roles.includes(role)} onChange={() => toggleRole(role)} />{label}</label>)}{errors.roles && <ul>{messages(errors, 'roles')}</ul>}</fieldset>
      <button className="primary" type="submit" disabled={busy}>{busy ? 'Создание…' : 'Создать пользователя'}</button>
    </form>
  </section></main>;
}
