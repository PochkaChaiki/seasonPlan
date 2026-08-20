import { useState } from 'react';
import { useAuth } from './AuthContext.jsx';

export default function LoginPage({ notice }) {
  const { login } = useAuth(); 
  const [username, setUsername] = useState(''); 
  const [password, setPassword] = useState(''); 
  const [error, setError] = useState('');
  const submit = async (event) => { 
    event.preventDefault(); 
    setError(''); 
    try { 
      await login(username, password); 
    } catch (reason) { 
      setError(reason.message); 
    } 
  };
  return <main className="page-shell">
    <section className="card">
      <h1>Вход в систему</h1>
      {notice && <p role="status" className="note">{notice}</p>}
      <form onSubmit={submit}>
        <label>Имя пользователя<input value={username} onChange={(event) => setUsername(event.target.value)} autoComplete="username" required /></label>
        <label>Пароль<input type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="current-password" required /></label>
        {error && <p role="alert">{error}</p>}<button className="primary" type="submit">Войти</button>
      </form>
    </section>
  </main>;
}
