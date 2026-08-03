import '@testing-library/jest-dom/vitest';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import PlanReviewPage from './PlanReviewPage.jsx';

const plan = {
  id: 'plan-1',
  seasonType: 'SPRING',
  targetTerritories: ['Northern Forest'],
  timeWindows: [{ start: '2027-03-01T09:00:00', end: '2027-03-05T18:00:00' }],
  targetClimateMetrics: { temperature: 12 },
  naturalPhenomenonParameters: { blooming: 'early' },
  status: 'SUBMITTED'
};

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/season-plans/plan-1/review']}>
      <Routes>
        <Route path="/season-plans/:id/review" element={<PlanReviewPage />} />
      </Routes>
    </MemoryRouter>
  );
}

function renderPageAs(actor) {
  return render(
    <MemoryRouter initialEntries={['/season-plans/plan-1/review']}>
      <Routes>
        <Route path="/season-plans/:id/review" element={<PlanReviewPage currentActor={actor} />} />
      </Routes>
    </MemoryRouter>
  );
}

describe('PlanReviewPage', () => {
  afterEach(() => cleanup());

  beforeEach(() => {
    sessionStorage.clear();
    vi.restoreAllMocks();
  });

  it('loads and displays plan details and readiness', async () => {
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(Response.json(plan))
      .mockResolvedValueOnce(Response.json({ ready: false, errors: { pollenReservation: ['confirmed pollen reservation is required'] } }));

    renderPage();

    expect(await screen.findByText('Northern Forest')).toBeInTheDocument();
    expect(screen.getByText('confirmed pollen reservation is required')).toBeInTheDocument();
    expect(screen.getByText(/История аудита не раскрыта/i)).toBeInTheDocument();
  });

  it('approves with a comment and displays APPROVED status', async () => {
    const user = userEvent.setup();
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(Response.json(plan))
      .mockResolvedValueOnce(Response.json({ ready: true, errors: {} }))
      .mockResolvedValueOnce(Response.json({ ...plan, status: 'APPROVED', approvalComment: 'approved for launch' }));

    renderPage();
    await user.type(await screen.findByLabelText(/Комментарий к решению/i), 'approved for launch');
    await user.click(screen.getByRole('button', { name: 'Утвердить план' }));

    expect(await screen.findByText(/APPROVED/)).toBeInTheDocument();
    expect(screen.getByText('approved for launch')).toBeInTheDocument();
  });

  it('returns with a comment and displays DRAFT status', async () => {
    const user = userEvent.setup();
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(Response.json(plan))
      .mockResolvedValueOnce(Response.json({ ready: false, errors: {} }))
      .mockResolvedValueOnce(Response.json({ ...plan, status: 'DRAFT', approvalComment: 'revise windows' }));

    renderPage();
    await user.type(await screen.findByLabelText(/Комментарий к решению/i), 'revise windows');
    await user.click(screen.getByRole('button', { name: 'Вернуть на доработку' }));

    expect(await screen.findByText(/План plan-1 возвращен на доработку/i)).toBeInTheDocument();
    expect(screen.getByText(/DRAFT/)).toBeInTheDocument();
  });

  it('shows backend approval blocking errors', async () => {
    const user = userEvent.setup();
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(Response.json(plan))
      .mockResolvedValueOnce(Response.json({ ready: false, errors: {} }))
      .mockResolvedValueOnce(
        Response.json(
          { message: 'validation failed', errors: { pollenReservation: ['rejected pollen reservation blocks approval'] } },
          { status: 400 }
        )
      );

    renderPage();
    await user.click(await screen.findByRole('button', { name: 'Утвердить план' }));

    expect(await screen.findByText('rejected pollen reservation blocks approval')).toBeInTheDocument();
  });

  it('hides review summary and decision blocks for non-guardian roles', async () => {
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(Response.json(plan))
      .mockResolvedValueOnce(Response.json({ ready: false, errors: {} }));

    renderPageAs({ role: 'FAIRY_PLANNER', id: 'planner-1' });

    expect((await screen.findAllByText(/Согласование сезонного плана/i)).length).toBeGreaterThan(0);
    expect(screen.queryByText('Northern Forest')).not.toBeInTheDocument();
    expect(screen.queryByText(/Review decisions are available only to CHIEF_GUARDIAN/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/Сводка плана/i)).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'РЈС‚РІРµСЂРґРёС‚СЊ РїР»Р°РЅ' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Р’РµСЂРЅСѓС‚СЊ РЅР° РґРѕСЂР°Р±РѕС‚РєСѓ' })).not.toBeInTheDocument();
  });
});
