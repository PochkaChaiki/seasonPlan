import { useState } from 'react';
import { useAuth } from './AuthContext.jsx';
import { PASSWORD_HELP, validateNewPassword } from './passwordPolicy.js';

export default function ProfileSecurityPage() {
  const { user, changeOwnCredentials } = useAuth();
  const [username, setUsername] = useState(user.username);
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const submit = async (event) => {
    event.preventDefault(); setError('');
    if (!currentPassword || !username.trim()) { setError('Заполните все поля.'); return; }
    const passwordError = validateNewPassword(newPassword);
    if (passwordError) { setError(passwordError); return; }
    setBusy(true);
    try {
      await changeOwnCredentials({ currentPassword, username: username.trim(), newPassword });
    } catch (reason) {
      setCurrentPassword(''); setNewPassword('');
      setError(reason.message ?? 'Не удалось изменить учетные данные.');
    } finally { setBusy(false); }
  };
  return <main className="page-shell"><section className="card compact">
    <h1>Профиль и безопасность</h1>
    <p className="note">После сохранения текущий сеанс завершится, и потребуется новый вход.</p>
    <form className="stack" onSubmit={submit} noValidate>
      <label className="field"><span>Текущий пароль</span><input aria-label="Текущий пароль" type="password" autoComplete="current-password" value={currentPassword} onChange={(event) => setCurrentPassword(event.target.value)} /></label>
      <label className="field"><span>Имя пользователя</span><input aria-label="Имя пользователя" autoComplete="username" value={username} onChange={(event) => setUsername(event.target.value)} /></label>
      <label className="field"><span>Новый пароль</span><input aria-label="Новый пароль" type="password" autoComplete="new-password" value={newPassword} onChange={(event) => setNewPassword(event.target.value)} /><small>{PASSWORD_HELP}</small></label>
      {error && <p role="alert" className="field-errors">{error}</p>}
      <button className="primary" type="submit" disabled={busy}>{busy ? 'Сохранение…' : 'Сохранить учетные данные'}</button>
    </form>
  </section></main>;
}
