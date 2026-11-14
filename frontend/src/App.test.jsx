import '@testing-library/jest-dom/vitest';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App.jsx';

function renderApp(route = '/') { return render(<MemoryRouter initialEntries={[route]}><App /></MemoryRouter>); }

describe('session authentication', () => {
  afterEach(() => cleanup());
  beforeEach(() => { sessionStorage.clear(); vi.restoreAllMocks(); });

  it('shows the login screen when session restoration returns 401', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((url) => Promise.resolve(
      String(url).endsWith('/auth/csrf') ? Response.json({ token: 'csrf', headerName: 'X-XSRF-TOKEN' }) : new Response('', { status: 401 })
    ));
    renderApp();
    expect(await screen.findByRole('heading', { name: 'Вход в систему' })).toBeInTheDocument();
  });

  it('restores the server session and sends no actor simulator headers', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch').mockImplementation((url) => Promise.resolve(
      String(url).endsWith('/auth/csrf') ? Response.json({ token: 'csrf', headerName: 'X-XSRF-TOKEN' }) :
        Response.json({ id: '00000000-0000-0000-0000-000000000001', username: 'planner', displayName: 'Planner', roles: ['FAIRY_PLANNER'] })
    ));
    renderApp();
    expect(await screen.findByRole('heading', { name: 'SeasonPlan lifecycle' })).toBeInTheDocument();
    expect(screen.getByText(/Planner/)).toBeInTheDocument();
    expect(fetch.mock.calls.every(([, options]) => !options?.headers?.['X-Actor-Id'] && !options?.headers?.['X-Actor-Role'])).toBe(true);
  });
});

describe('persisted season plan access', () => {
  afterEach(() => cleanup());
  beforeEach(() => { sessionStorage.clear(); vi.restoreAllMocks(); });

  const persistedPlan = {
    id: '11111111-1111-1111-1111-111111111111',
    seasonType: 'SPRING',
    targetTerritories: ['Northern Forest'],
    timeWindows: [],
    targetClimateMetrics: {},
    naturalPhenomenonParameters: {},
    readinessChecklist: []
  };

  it('shows own in-app notifications as escaped text with plan links and refreshes', async () => {
    const planner = { id: '00000000-0000-0000-0000-000000000001', username: 'planner', displayName: 'Planner', roles: ['FAIRY_PLANNER'] };
    let requests = 0;
    const fetch = mockAuthenticatedSession(planner, (path) => {
      if (path.endsWith('/notifications')) {
        requests++;
        return Response.json([{ eventId: 'event-1', seasonPlanId: persistedPlan.id, eventType: 'SEASON_PLAN_RETURNED',
          message: requests === 1 ? '<img src=x onerror=alert(1)>' : 'revised', occurredAt: '2026-01-01T00:00:00Z' }]);
      }
      if (path.endsWith('/season-plans')) return Response.json([]);
      return new Response('', { status: 404 });
    });
    const user = userEvent.setup();
    renderApp();
    expect(await screen.findByText('<img src=x onerror=alert(1)>')).toBeInTheDocument();
    expect(document.querySelector('img')).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: `Открыть план ${persistedPlan.id}` })).toHaveAttribute('href', `/season-plans/${persistedPlan.id}`);
    await user.click(screen.getByRole('button', { name: 'Обновить уведомления' }));
    expect(await screen.findByText('revised')).toBeInTheDocument();
    expect(fetch.mock.calls.filter(([url]) => String(url).endsWith('/notifications'))).toHaveLength(2);
  });

  it('reports notification fetch failures and allows refresh', async () => {
    const planner = { id: 'planner', username: 'planner', displayName: 'Planner', roles: ['FAIRY_PLANNER'] };
    let requests = 0;
    mockAuthenticatedSession(planner, (path) => {
      if (path.endsWith('/notifications')) return ++requests === 1
        ? Response.json({ message: 'Notifications unavailable' }, { status: 503 }) : Response.json([]);
      return Response.json([]);
    });
    const user = userEvent.setup();
    renderApp();
    expect(await screen.findByRole('alert')).toHaveTextContent('Notifications unavailable');
    await user.click(screen.getByRole('button', { name: 'Обновить уведомления' }));
    expect(await screen.findByText('Уведомлений пока нет.')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('lets a chief guardian open a submitted plan returned by the backend with an empty browser cache', async () => {
    const guardian = {
      id: '22222222-2222-2222-2222-222222222222', username: 'guardian', displayName: 'Guardian',
      roles: ['CHIEF_GUARDIAN'], administrator: false
    };
    const plan = { ...persistedPlan, status: 'SUBMITTED' };
    const fetch = mockAuthenticatedSession(guardian, (path) => {
      if (path.endsWith('/season-plans')) return Response.json([plan]);
      if (path.endsWith(`/season-plans/${plan.id}`)) return Response.json(plan);
      if (path.endsWith(`/season-plans/${plan.id}/readiness`)) return Response.json({ ready: false, errors: {} });
      if (path.endsWith(`/season-plans/${plan.id}/review-history`)) return Response.json([]);
      return new Response('', { status: 404 });
    });
    const user = userEvent.setup();

    renderApp();
    const openReview = await screen.findByRole('link', { name: `Open review for plan ${plan.id}` });
    expect(openReview).toHaveAttribute('href', `/season-plans/${plan.id}/review`);
    await user.click(openReview);

    expect(await screen.findByRole('button', { name: 'Проверить план' })).toBeEnabled();
    expect(screen.getByRole('button', { name: 'Утвердить план' })).toBeDisabled();
    expect(fetch.mock.calls.some(([url]) => String(url).endsWith('/season-plans'))).toBe(true);
  });

  it('lets a preparation role open an approved plan returned by the backend with an empty browser cache', async () => {
    const coordinator = {
      id: '33333333-3333-3333-3333-333333333333', username: 'coordinator', displayName: 'Coordinator',
      roles: ['TALENT_COORDINATOR'], administrator: false
    };
    const plan = { ...persistedPlan, status: 'APPROVED' };
    mockAuthenticatedSession(coordinator, (path) => {
      if (path.endsWith('/season-plans')) return Response.json([plan]);
      if (path.endsWith(`/season-plans/${plan.id}`)) return Response.json(plan);
      if (path.endsWith(`/season-plans/${plan.id}/readiness`)) return Response.json({ ready: false, errors: {} });
      if (path.endsWith(`/season-plans/${plan.id}/review-history`)) return Response.json([]);
      if (path.endsWith(`/season-plans/${plan.id}/tasks`)) return Response.json([]);
      return new Response('', { status: 404 });
    });

    renderApp();
    const openPreparation = await screen.findByRole('link', { name: `Open preparation for plan ${plan.id}` });
    expect(openPreparation).toHaveAttribute('href', `/season-plans/${plan.id}/preparation`);
  });
});

const admin = {
  id: '00000000-0000-0000-0000-000000000001', username: 'admin', displayName: 'Администратор',
  roles: ['FAIRY_PLANNER'], administrator: true
};

function mockAuthenticatedSession(user, handler = () => Response.json({})) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation((url, options = {}) => {
    const path = String(url);
    if (path.endsWith('/auth/csrf')) return Promise.resolve(Response.json({ token: 'csrf', headerName: 'X-XSRF-TOKEN' }));
    if (path.endsWith('/auth/me')) return Promise.resolve(Response.json(user));
    return Promise.resolve(handler(path, options));
  });
}

describe('authentication administration', () => {
  afterEach(() => cleanup());
  beforeEach(() => { sessionStorage.clear(); vi.restoreAllMocks(); });

  it('shows the administration link only to an administrator and sends user registration with CSRF', async () => {
    const fetch = mockAuthenticatedSession(admin, (path) => path.endsWith('/admin/users')
      ? new Response(JSON.stringify({ id: 'new-user', username: 'fairy' }), { status: 201, headers: { 'Content-Type': 'application/json' } })
      : new Response('', { status: 404 }));
    const user = userEvent.setup();
    renderApp();
    await user.click(await screen.findByRole('link', { name: 'Управление пользователями' }));
    await user.type(screen.getByLabelText('Имя пользователя'), 'fairy');
    await user.type(screen.getByLabelText('Отображаемое имя'), 'Новая фея');
    await user.type(screen.getByLabelText('Пароль'), 'Secret-password-123');
    await user.click(screen.getByLabelText('Планировщик сезона'));
    await user.click(screen.getByRole('button', { name: 'Создать пользователя' }));

    await screen.findByText('Пользователь создан. Передайте учетные данные безопасным способом.');
    const call = fetch.mock.calls.find(([url, options]) => String(url).endsWith('/admin/users') && options.method === 'POST');
    expect(call[1].headers['X-XSRF-TOKEN']).toBe('csrf');
    expect(JSON.parse(call[1].body)).toEqual({ username: 'fairy', displayName: 'Новая фея', password: 'Secret-password-123', roles: ['FAIRY_PLANNER'] });
  });

  it('hides administrator navigation and denies the registration route to an ordinary user', async () => {
    mockAuthenticatedSession({ ...admin, administrator: false });
    renderApp('/admin/users');
    expect(await screen.findByRole('heading', { name: 'Доступ запрещен' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Управление пользователями' })).not.toBeInTheDocument();
  });

  it('validates registration roles, renders backend field errors, and clears its password after failure', async () => {
    mockAuthenticatedSession(admin, (path) => path.endsWith('/admin/users')
      ? new Response(JSON.stringify({ message: 'Проверьте введенные данные', errors: { username: ['Имя уже занято'] } }), { status: 400, headers: { 'Content-Type': 'application/json' } })
      : new Response('', { status: 404 }));
    const user = userEvent.setup();
    renderApp('/admin/users');
    await screen.findByRole('heading', { name: 'Регистрация пользователя' });
    await user.click(screen.getByRole('button', { name: 'Создать пользователя' }));
    expect(await screen.findByText('Выберите хотя бы одну роль.')).toBeInTheDocument();
    await user.type(screen.getByLabelText('Имя пользователя'), 'fairy');
    await user.type(screen.getByLabelText('Отображаемое имя'), 'Новая фея');
    await user.type(screen.getByLabelText('Пароль'), 'Secret-password-123');
    await user.click(screen.getByLabelText('Планировщик сезона'));
    await user.click(screen.getByRole('button', { name: 'Создать пользователя' }));
    expect(await screen.findByText('Имя уже занято')).toBeInTheDocument();
    expect(screen.getByLabelText('Пароль')).toHaveValue('');
  });

  it('changes own credentials with CSRF and returns to login after session invalidation', async () => {
    const fetch = mockAuthenticatedSession(admin, (path) => path.endsWith('/auth/me/credentials')
      ? new Response(null, { status: 204 }) : new Response('', { status: 404 }));
    const user = userEvent.setup();
    renderApp('/profile/security');
    await screen.findByRole('heading', { name: 'Профиль и безопасность' });
    await user.type(screen.getByLabelText('Текущий пароль'), 'Old-secret');
    await user.clear(screen.getByLabelText('Имя пользователя'));
    await user.type(screen.getByLabelText('Имя пользователя'), 'new-admin');
    await user.type(screen.getByLabelText('Новый пароль'), 'New-secret-123');
    await user.click(screen.getByRole('button', { name: 'Сохранить учетные данные' }));

    expect(await screen.findByRole('heading', { name: 'Вход в систему' })).toBeInTheDocument();
    expect(screen.getByText('Учетные данные изменены. Войдите снова с новыми данными.')).toBeInTheDocument();
    await waitFor(() => {
      const call = fetch.mock.calls.find(([url, options]) => String(url).endsWith('/auth/me/credentials') && options.method === 'PUT');
      expect(call[1].headers['X-XSRF-TOKEN']).toBe('csrf');
      expect(JSON.parse(call[1].body)).toEqual({ currentPassword: 'Old-secret', username: 'new-admin', newPassword: 'New-secret-123' });
    });
  });

  it('rejects a password shorter than 12 characters in both forms without sending a command', async () => {
    const registrationFetch = mockAuthenticatedSession(admin);
    const user = userEvent.setup();
    renderApp('/admin/users');
    await screen.findByRole('heading', { name: 'Регистрация пользователя' });
    await user.type(screen.getByLabelText('Имя пользователя'), 'fairy');
    await user.type(screen.getByLabelText('Отображаемое имя'), 'Новая фея');
    await user.type(screen.getByLabelText('Пароль'), 'короткий');
    await user.click(screen.getByLabelText('Планировщик сезона'));
    await user.click(screen.getByRole('button', { name: 'Создать пользователя' }));
    expect(await screen.findByText('Пароль должен содержать не менее 12 символов и не более 72 байт UTF-8.')).toBeInTheDocument();
    expect(registrationFetch.mock.calls.some(([url, options]) => String(url).endsWith('/admin/users') && options.method === 'POST')).toBe(false);

    cleanup();
    vi.restoreAllMocks();
    const credentialFetch = mockAuthenticatedSession(admin);
    renderApp('/profile/security');
    await screen.findByRole('heading', { name: 'Профиль и безопасность' });
    await user.type(screen.getByLabelText('Текущий пароль'), 'Old-secret');
    await user.type(screen.getByLabelText('Новый пароль'), 'короткий');
    await user.click(screen.getByRole('button', { name: 'Сохранить учетные данные' }));
    expect(await screen.findByText('Пароль должен содержать не менее 12 символов и не более 72 байт UTF-8.')).toBeInTheDocument();
    expect(credentialFetch.mock.calls.some(([url, options]) => String(url).endsWith('/auth/me/credentials') && options.method === 'PUT')).toBe(false);
  });

  it('rejects a new password exceeding 72 UTF-8 bytes without sending a command', async () => {
    const fetch = mockAuthenticatedSession(admin);
    const user = userEvent.setup();
    renderApp('/profile/security');
    await screen.findByRole('heading', { name: 'Профиль и безопасность' });
    await user.type(screen.getByLabelText('Текущий пароль'), 'Old-secret');
    await user.type(screen.getByLabelText('Новый пароль'), 'я'.repeat(37));
    await user.click(screen.getByRole('button', { name: 'Сохранить учетные данные' }));
    expect(await screen.findByText('Пароль должен содержать не менее 12 символов и не более 72 байт UTF-8.')).toBeInTheDocument();
    expect(fetch.mock.calls.some(([url, options]) => String(url).endsWith('/auth/me/credentials') && options.method === 'PUT')).toBe(false);
  });
});
