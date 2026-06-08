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
    expect(screen.getByRole('link', { name: /Season planning/i })).toBeInTheDocument();
    expect(screen.getByText(/Review and approval/i)).toBeInTheDocument();
    expect(screen.getByText(/Preparation for launch/i)).toBeInTheDocument();
    expect(screen.getByText(/Create a draft plan to begin/i)).toBeInTheDocument();
  });

  it('does not expose later lifecycle use cases as active routes', () => {
    renderApp('/incidents');

    expect(screen.getByRole('heading', { name: 'Workflow not active' })).toBeInTheDocument();
    expect(screen.getByText(/Later lifecycle use cases are intentionally inactive/i)).toBeInTheDocument();
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
    await user.click(screen.getByRole('link', { name: /Season planning/i }));
    expect(screen.getByRole('heading', { name: 'Plan the season' })).toBeInTheDocument();

    await user.click(screen.getByRole('link', { name: /Review cached plan/i }));
    expect(await screen.findByRole('heading', { name: 'Review and approve' })).toBeInTheDocument();

    await user.click(screen.getByRole('link', { name: /Prepare cached plan/i }));
    expect(await screen.findByRole('heading', { name: 'Preparation for launch' })).toBeInTheDocument();
  });
});
