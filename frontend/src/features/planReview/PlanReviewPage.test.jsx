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
      .mockResolvedValueOnce(Response.json({ ready: false, errors: { pollenReservation: ['confirmed pollen reservation is required'] } }))
      .mockResolvedValueOnce(Response.json([]));

    renderPage();

    expect(await screen.findByText('Northern Forest')).toBeInTheDocument();
    expect(screen.getByText('confirmed pollen reservation is required')).toBeInTheDocument();
    expect(screen.getByText(/История пуста/i)).toBeInTheDocument();
  });

  it('requires verification before approval and verifies a submitted plan', async () => {
    const user = userEvent.setup();
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(Response.json(plan))
      .mockResolvedValueOnce(Response.json({ ready: false, errors: {} }))
      .mockResolvedValueOnce(Response.json([]))
      .mockResolvedValueOnce(Response.json({ ...plan, status: 'VERIFIED' }))
      .mockResolvedValueOnce(Response.json([]));
    renderPage();
    expect(await screen.findByRole('button', { name: 'Утвердить план' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Проверить план' }));
    expect(await screen.findByText(/VERIFIED/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Утвердить план' })).toBeEnabled();
    expect(fetchMock.mock.calls[3][0]).toContain('/season-plans/plan-1/verify');
  });

  it('shows review history and submits a typed approval condition', async () => {
    const user = userEvent.setup();
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(Response.json({ ...plan, status: 'VERIFIED' }))
      .mockResolvedValueOnce(Response.json({ ready: false, errors: {} }))
      .mockResolvedValueOnce(Response.json([{ action: 'SEASON_PLAN_VERIFIED', actorId: 'chief', createdAt: '2026-01-01T00:00:00Z', comment: null }]))
      .mockResolvedValueOnce(Response.json({ ...plan, status: 'APPROVED' }))
      .mockResolvedValueOnce(Response.json([]));
    renderPage();
    expect(await screen.findByText('SEASON_PLAN_VERIFIED')).toBeInTheDocument();
    await user.type(screen.getByLabelText('Ключ условия'), 'weather-evidence');
    expect(screen.getByRole('button', { name: 'Утвердить план' })).toBeDisabled();
    await user.type(screen.getByLabelText('Название условия'), 'Weather evidence');
    await user.type(screen.getByLabelText('Требуемое подтверждение'), 'Calibration record');
    await user.selectOptions(screen.getByLabelText('Роль владельца'), 'TALENT_COORDINATOR');
    await user.click(screen.getByRole('button', { name: 'Добавить условие' }));
    await user.click(screen.getByRole('button', { name: 'Утвердить план' }));
    await screen.findByText(/План plan-1 утвержден/i);
    const payload = JSON.parse(fetchMock.mock.calls[3][1].body);
    expect(payload.conditions).toEqual([{ key: 'weather-evidence', displayText: 'Weather evidence', evidenceRequirement: 'Calibration record', ownerRole: 'TALENT_COORDINATOR' }]);
  });

  it('refreshes readiness after conditional approval', async () => {
    const user = userEvent.setup();
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(Response.json({ ...plan, status: 'VERIFIED' }))
      .mockResolvedValueOnce(Response.json({ ready: true, errors: {} }))
      .mockResolvedValueOnce(Response.json([]))
      .mockResolvedValueOnce(Response.json({ ...plan, status: 'APPROVED', approvalConditions: [{ key: 'weather', displayText: 'Weather', evidenceRequirement: 'proof', ownerRole: 'TALENT_COORDINATOR' }] }))
      .mockResolvedValueOnce(Response.json([]))
      .mockResolvedValueOnce(Response.json({ ready: false, errors: { weather: ['approval condition evidence is not verified'] } }));
    renderPage();
    expect(await screen.findByText(/Готовность: да/i)).toBeInTheDocument();
    await user.type(screen.getByLabelText('Ключ условия'), 'weather');
    await user.type(screen.getByLabelText('Название условия'), 'Weather');
    await user.type(screen.getByLabelText('Требуемое подтверждение'), 'proof');
    await user.click(screen.getByRole('button', { name: 'Добавить условие' }));
    await user.click(screen.getByRole('button', { name: 'Утвердить план' }));
    expect(await screen.findByText('approval condition evidence is not verified')).toBeInTheDocument();
    expect(screen.queryByText(/Готовность: да/i)).not.toBeInTheDocument();
  });

  it('does not retain a ready indication if readiness refresh fails', async () => {
    const user = userEvent.setup();
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(Response.json({ ...plan, status: 'VERIFIED' }))
      .mockResolvedValueOnce(Response.json({ ready: true, errors: {} }))
      .mockResolvedValueOnce(Response.json([]))
      .mockResolvedValueOnce(Response.json({ ...plan, status: 'APPROVED' }))
      .mockResolvedValueOnce(Response.json([]))
      .mockResolvedValueOnce(Response.json({ message: 'readiness unavailable' }, { status: 503 }));
    renderPage();
    expect(await screen.findByText(/Готовность: да/i)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Утвердить план' }));
    expect(await screen.findByText(/Не удалось обновить готовность/i)).toBeInTheDocument();
    expect(screen.queryByText(/Готовность: да/i)).not.toBeInTheDocument();
    expect(screen.getByText(/План plan-1 утвержден/i)).toBeInTheDocument();
  });

  it('approves with a comment and displays APPROVED status', async () => {
    const user = userEvent.setup();
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(Response.json({ ...plan, status: 'VERIFIED' }))
      .mockResolvedValueOnce(Response.json({ ready: true, errors: {} }))
      .mockResolvedValueOnce(Response.json([]))
      .mockResolvedValueOnce(Response.json({ ...plan, status: 'APPROVED', approvalComment: 'approved for launch' }))
      .mockResolvedValueOnce(Response.json([]));

    renderPage();
    await user.type(await screen.findByLabelText(/Комментарий к решению/i), 'approved for launch');
    await user.click(screen.getByRole('button', { name: 'Утвердить план' }));

    expect(await screen.findByText(/APPROVED/)).toBeInTheDocument();
    expect(screen.getByText('approved for launch')).toBeInTheDocument();
  });

  it('reports motivated return when approval encounters a rejected reservation', async () => {
    const user = userEvent.setup();
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(Response.json({ ...plan, status: 'VERIFIED' }))
      .mockResolvedValueOnce(Response.json({ ready: false, errors: {} }))
      .mockResolvedValueOnce(Response.json([]))
      .mockResolvedValueOnce(Response.json({ ...plan, status: 'DRAFT', approvalComment: 'Pollen reservation rejected: stock' }))
      .mockResolvedValueOnce(Response.json([]));
    renderPage();
    await user.click(await screen.findByRole('button', { name: 'Утвердить план' }));
    expect(await screen.findByText(/План plan-1 возвращен на доработку/i)).toBeInTheDocument();
    expect(screen.getByText(/Pollen reservation rejected: stock/)).toBeInTheDocument();
  });

  it('returns with a comment and displays DRAFT status', async () => {
    const user = userEvent.setup();
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(Response.json(plan))
      .mockResolvedValueOnce(Response.json({ ready: false, errors: {} }))
      .mockResolvedValueOnce(Response.json([]))
      .mockResolvedValueOnce(Response.json({ ...plan, status: 'DRAFT', approvalComment: 'revise windows' }))
      .mockResolvedValueOnce(Response.json([]));

    renderPage();
    await user.type(await screen.findByLabelText(/Комментарий к решению/i), 'revise windows');
    await user.click(screen.getByRole('button', { name: 'Вернуть на доработку' }));

    expect(await screen.findByText(/План plan-1 возвращен на доработку/i)).toBeInTheDocument();
    expect(screen.getByText(/DRAFT/)).toBeInTheDocument();
  });

  it('shows backend approval blocking errors', async () => {
    const user = userEvent.setup();
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(Response.json({ ...plan, status: 'VERIFIED' }))
      .mockResolvedValueOnce(Response.json({ ready: false, errors: {} }))
      .mockResolvedValueOnce(Response.json([]))
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

  it('keeps a readable draft visible but disables guardian decisions until it is submitted', async () => {
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(Response.json({ ...plan, status: 'DRAFT' }))
      .mockResolvedValueOnce(Response.json({ ready: false, errors: {} }))
      .mockResolvedValueOnce(Response.json([]));

    renderPageAs({ role: 'CHIEF_GUARDIAN', id: 'guardian-1' });

    expect(await screen.findByText('Northern Forest')).toBeInTheDocument();
    expect(screen.getByText(/планировщик должен отправить план на согласование/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Утвердить план' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Вернуть на доработку' })).toBeDisabled();
  });

  it('shows review history to the planner who created the plan', async () => {
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(Response.json({ ...plan, createdBy: 'planner-1' }))
      .mockResolvedValueOnce(Response.json({ ready: false, errors: {} }))
      .mockResolvedValueOnce(Response.json([{ id: 'audit-1', action: 'SEASON_PLAN_SUBMITTED', actorId: 'planner-1', createdAt: '2026-01-01T00:00:00Z' }]));
    renderPageAs({ role: 'FAIRY_PLANNER', id: 'planner-1' });
    expect(await screen.findByText('SEASON_PLAN_SUBMITTED')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Утвердить план' })).not.toBeInTheDocument();
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
