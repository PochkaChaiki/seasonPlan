import '@testing-library/jest-dom/vitest';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import SeasonPlanningPage from './SeasonPlanningPage.jsx';

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/season-plans/new']}>
      <Routes>
        <Route path="/season-plans/new" element={<SeasonPlanningPage />} />
      </Routes>
    </MemoryRouter>
  );
}

describe('SeasonPlanningPage', () => {
  afterEach(() => cleanup());

  beforeEach(() => {
    sessionStorage.clear();
    vi.restoreAllMocks();
  });

  it('saves a valid draft through POST with planner actor headers', async () => {
    const user = userEvent.setup();
    const requests = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation((url, options) => {
      requests.push({ url: String(url), options });
      return Promise.resolve(
        Response.json({ id: 'plan-1', seasonType: 'SPRING', targetTerritories: ['Northern Forest'], status: 'DRAFT' })
      );
    });

    renderPage();
    await user.clear(screen.getByLabelText(/Target territories/i));
    await user.type(screen.getByLabelText(/Target territories/i), 'Northern Forest');
    await user.click(screen.getByRole('button', { name: 'Save draft' }));

    expect(await screen.findByText(/Saved draft plan-1/i)).toBeInTheDocument();
    expect(requests[0].url).toBe('http://localhost:8080/season-plans');
    expect(requests[0].options.method).toBe('POST');
    expect(requests[0].options.headers['X-Actor-Role']).toBe('FAIRY_PLANNER');
    expect(JSON.parse(requests[0].options.body).seasonType).toBe('SPRING');
  });

  it('displays backend field errors', async () => {
    const user = userEvent.setup();
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      Response.json({ message: 'validation failed', errors: { seasonType: ['season type is required'] } }, { status: 400 })
    );

    renderPage();
    await user.click(screen.getByRole('button', { name: 'Save draft' }));

    expect(await screen.findByText('validation failed')).toBeInTheDocument();
    expect(screen.getByText('season type is required')).toBeInTheDocument();
  });

  it('submits a saved draft and displays SUBMITTED status', async () => {
    const user = userEvent.setup();
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(Response.json({ id: 'plan-1', seasonType: 'SPRING', status: 'DRAFT' }))
      .mockResolvedValueOnce(Response.json({ id: 'plan-1', seasonType: 'SPRING', status: 'SUBMITTED' }));

    renderPage();
    await user.click(screen.getByRole('button', { name: 'Save draft' }));
    await user.click(await screen.findByRole('button', { name: 'Submit for approval' }));

    await waitFor(() => expect(screen.getByText(/Submitted plan plan-1/i)).toBeInTheDocument());
    expect(screen.getByText('SUBMITTED')).toBeInTheDocument();
  });
});
