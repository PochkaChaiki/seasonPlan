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

function mockPlanList(plans = []) {
  return vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(Response.json(plans));
}

describe('SeasonPlanningPage', () => {
  afterEach(() => cleanup());

  beforeEach(() => {
    sessionStorage.clear();
    vi.restoreAllMocks();
  });

  it('saves a valid draft through the session-authenticated API', async () => {
    const user = userEvent.setup();
    const requests = [];
    mockPlanList().mockImplementation((url, options) => {
      requests.push({ url: String(url), options });
      return Promise.resolve(
        Response.json({ id: 'plan-1', seasonType: 'SPRING', targetTerritories: ['Northern Forest'], status: 'DRAFT' })
      );
    });

    renderPage();
    await user.clear(screen.getByLabelText(/Целевые территории/i));
    await user.type(screen.getByLabelText(/Целевые территории/i), 'Northern Forest');
    await user.click(screen.getByRole('button', { name: 'Сохранить новый черновик' }));

    expect(await screen.findByText(/Сохранен черновик plan-1/i)).toBeInTheDocument();
    expect(requests[0].url).toBe('/api/season-plans');
    expect(requests[0].options.method).toBe('POST');
    expect(requests[0].options.headers['X-Actor-Role']).toBeUndefined();
    expect(JSON.parse(requests[0].options.body).seasonType).toBe('SPRING');
  });

  it('displays backend field errors', async () => {
    const user = userEvent.setup();
    mockPlanList().mockResolvedValueOnce(
      Response.json({ message: 'validation failed', errors: { seasonType: ['season type is required'] } }, { status: 400 })
    );

    renderPage();
    await user.click(screen.getByRole('button', { name: 'Сохранить новый черновик' }));

    expect(await screen.findByText('validation failed')).toBeInTheDocument();
    expect(screen.getByText('season type is required')).toBeInTheDocument();
  });

  it('submits a saved draft and displays SUBMITTED status', async () => {
    const user = userEvent.setup();
    mockPlanList()
      .mockResolvedValueOnce(Response.json({ id: 'plan-1', seasonType: 'SPRING', status: 'DRAFT' }))
      .mockResolvedValueOnce(Response.json({ id: 'plan-1', seasonType: 'SPRING', status: 'SUBMITTED' }));

    renderPage();
    await user.click(screen.getByRole('button', { name: 'Сохранить новый черновик' }));
    await user.click(await screen.findByRole('button', { name: 'Отправить на согласование' }));

    await waitFor(() => expect(screen.getByText(/План plan-1 отправлен на согласование/i)).toBeInTheDocument());
    expect(screen.getAllByText(/SUBMITTED/).length).toBeGreaterThan(0);
  });

  it('allows creating a new draft while a cached plan is already in progress', async () => {
    const user = userEvent.setup();
    const requests = [];
    sessionStorage.setItem(
      'seasonPlan.workflow',
      JSON.stringify({ latestPlanId: 'plan-active', latestPlan: { id: 'plan-active', status: 'APPROVED' } })
    );
    mockPlanList([{ id: 'plan-active', seasonType: 'SPRING', status: 'APPROVED', targetTerritories: [] }]).mockImplementation((url, options) => {
      requests.push({ url: String(url), options });
      return Promise.resolve(Response.json({ id: 'plan-next', seasonType: 'SPRING', status: 'DRAFT' }));
    });

    renderPage();
    expect(screen.getByText(/Можно создать новый черновик/i)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Сохранить новый черновик' }));

    expect(await screen.findByText(/Сохранен черновик plan-next/i)).toBeInTheDocument();
    expect(requests[0].options.method).toBe('POST');
    expect(requests[0].url).toBe('/api/season-plans');
  });

  it('requires choosing season type from available options', async () => {
    mockPlanList();

    renderPage();

    const seasonType = await screen.findByLabelText('season-type');
    expect(seasonType.tagName).toBe('SELECT');
    expect(Array.from(seasonType.options).map((option) => option.value)).toEqual(['SPRING', 'SUMMER', 'AUTUMN', 'WINTER']);
  });

  it('loads a chosen existing draft into the planning form', async () => {
    const user = userEvent.setup();
    mockPlanList([
      { id: 'plan-summer', seasonType: 'SUMMER', status: 'DRAFT', targetTerritories: ['South Field'] },
      { id: 'plan-spring', seasonType: 'SPRING', status: 'DRAFT', targetTerritories: ['Northern Forest'] }
    ]).mockResolvedValueOnce(
      Response.json({
        id: 'plan-spring',
        seasonType: 'SPRING',
        status: 'DRAFT',
        targetTerritories: ['Northern Forest'],
        timeWindows: [{ start: '2027-03-01T09:00:00', end: '2027-03-05T18:00:00' }],
        naturalPhenomenonParameters: { blooming: 'late' },
        targetClimateMetrics: { temperature: 10 }
      })
    );

    renderPage();

    await user.selectOptions(await screen.findByLabelText('existing-plan'), 'plan-spring');
    await user.click(screen.getByRole('button', { name: 'Load selected plan' }));

    expect(await screen.findByText('Loaded plan plan-spring')).toBeInTheDocument();
    expect(screen.getByLabelText('season-type')).toHaveValue('SPRING');
    expect(screen.getByDisplayValue('Northern Forest')).toBeInTheDocument();
  });

  it('hides planning and existing-plan blocks from the talent coordinator observer role', async () => {
    mockPlanList([
      { id: 'plan-draft', seasonType: 'SPRING', status: 'DRAFT', targetTerritories: ['Northern Forest'] },
      { id: 'plan-submitted', seasonType: 'SUMMER', status: 'SUBMITTED', targetTerritories: ['South Field'] }
    ]);

    render(
      <MemoryRouter initialEntries={['/season-plans/new']}>
        <Routes>
          <Route path="/season-plans/new" element={<SeasonPlanningPage currentActor={{ role: 'TALENT_COORDINATOR', id: 'coordinator-1' }} />} />
        </Routes>
      </MemoryRouter>
    );

    expect((await screen.findAllByText(/Планирование сезона/i)).length).toBeGreaterThan(0);
    expect(screen.queryByText(/Planning controls are available only to FAIRY_PLANNER/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/Existing SeasonPlans/i)).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Сохранить новый черновик/i })).not.toBeInTheDocument();
    expect(screen.queryByText(/plan-draft/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/plan-submitted/i)).not.toBeInTheDocument();
  });
});
