import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { apiRequest, setCsrfToken } from '../api/lifecycleApi.js';

const AuthContext = createContext(null);
const base = import.meta.env.VITE_API_BASE_URL ?? '/api';

async function csrf() {
  const response = await fetch(`${base}/auth/csrf`, { credentials: 'include' });
  if (!response.ok) throw new Error('Unable to initialize CSRF protection');
  const token = await response.json();
  setCsrfToken(token.token, token.headerName);
  return token;
}

export function AuthProvider({ children }) {
  const [state, setState] = useState({ loading: true, user: null, reauthenticationNotice: '' });
  const restore = async () => {
    try {
      await csrf();
      const response = await fetch(`${base}/auth/me`, { credentials: 'include' });
      setState({ loading: false, user: response.ok ? await response.json() : null, reauthenticationNotice: '' });
    } catch { setState({ loading: false, user: null, reauthenticationNotice: '' }); }
  };
  useEffect(() => { restore(); }, []);
  const value = useMemo(() => ({ ...state,
    login: async (username, password) => {
      const token = await csrf();
      const response = await fetch(`${base}/auth/login`, { method: 'POST', credentials: 'include',
        headers: { 
          'Content-Type': 'application/x-www-form-urlencoded', 
          [token.headerName]: token.token 
        },
        body: new URLSearchParams({ username, password }) });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) 
        throw new Error(data.message ?? 'Invalid credentials');
      setState({ loading: false, user: data, reauthenticationNotice: '' });
      await csrf();
    },
    logout: async () => {
      const token = await csrf();
      await fetch(
        `${base}/auth/logout`, 
        { 
          method: 'POST', 
          credentials: 'include', 
          headers: {
             [token.headerName]: token.token 
            } 
        }
      );
      setCsrfToken(null); setState({ loading: false, user: null, reauthenticationNotice: '' });
    },
    createUser: (payload) => apiRequest('/admin/users', { method: 'POST', body: payload }),
    changeOwnCredentials: async (payload) => {
      await apiRequest('/auth/me/credentials', { method: 'PUT', body: payload });
      setCsrfToken(null);
      setState({ loading: false, user: null, reauthenticationNotice: 'Учетные данные изменены. Войдите снова с новыми данными.' });
    }
  }), [state]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
export function useAuth() { return useContext(AuthContext); }
