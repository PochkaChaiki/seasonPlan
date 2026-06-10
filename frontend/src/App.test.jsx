import '@testing-library/jest-dom/vitest';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App.jsx';

function renderApp(route = '/') {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <App />
    </MemoryRouter>
  );
}

describe('App navigation', () => {
  afterEach(() => cleanup());

  beforeEach(() => {
    sessionStorage.clear();
    vi.restoreAllMocks();
  });

  it('renders the first 3 lifecycle use-case cards on the dashboard', () => {
    renderApp();

    expect(screen.getByRole('heading', { name: 'SeasonPlan lifecycle' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Планирование сезона/i })).toBeInTheDocument();
    expect(screen.getByText(/Согласование сезонного плана/i)).toBeInTheDocument();
    expect(screen.getByText(/Подготовка сезона к запуску/i)).toBeInTheDocument();
    expect(screen.getByText(/Создайте черновик/i)).toBeInTheDocument();
  });

  it('does not expose later lifecycle use cases as active routes', () => {
    renderApp('/incidents');

    expect(screen.getByRole('heading', { name: 'Этап неактивен' })).toBeInTheDocument();
    expect(screen.getByText(/Поздние этапы жизненного цикла/i)).toBeInTheDocument();
  });

  it('navigates to planning, review, and preparation routes from cached workflow state', async () => {
    const user = userEvent.setup();
    sessionStorage.setItem(
      'seasonPlan.workflow',
      JSON.stringify({ latestPlanId: 'plan-1', latestPlan: { id: 'plan-1', status: 'SUBMITTED' } })
    );

    vi.spyOn(globalThis, 'fetch').mockImplementation((url) => {
      if (String(url).endsWith('/season-plans/plan-1')) {
        return Promise.resolve(Response.json({ id: 'plan-1', seasonType: 'SPRING', status: 'SUBMITTED' }));
      }
      if (String(url).endsWith('/season-plans/plan-1/readiness')) {
        return Promise.resolve(Response.json({ ready: false, errors: {} }));
      }
      return Promise.reject(new Error(`unexpected request: ${url}`));
    });

    renderApp();
    await user.click(screen.getByRole('link', { name: /Планирование сезона/i }));
    expect(screen.getByRole('heading', { name: 'Планирование сезона' })).toBeInTheDocument();

    await user.click(screen.getByRole('link', { name: 'Согласование' }));
    expect(await screen.findByRole('heading', { name: 'Согласование сезонного плана' })).toBeInTheDocument();

    await user.click(screen.getByRole('link', { name: 'Подготовка' }));
    expect(await screen.findByRole('heading', { name: 'Подготовка сезона к запуску' })).toBeInTheDocument();
  });
});
