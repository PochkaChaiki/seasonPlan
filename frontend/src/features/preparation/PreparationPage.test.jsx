import '@testing-library/jest-dom/vitest';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import PreparationPage from './PreparationPage.jsx';

const plan = { id: 'plan-1', seasonType: 'SPRING', status: 'APPROVED', targetTerritories: ['Northern Forest'] };

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/season-plans/plan-1/preparation']}>
      <Routes>
        <Route path="/season-plans/:id/preparation" element={<PreparationPage />} />
      </Routes>
    </MemoryRouter>
  );
}

function mockInitialLoad() {
  return vi
    .spyOn(globalThis, 'fetch')
    .mockResolvedValueOnce(Response.json(plan))
    .mockResolvedValueOnce(Response.json({ ready: false, errors: { task: ['mandatory task is required'] } }));
}

describe('PreparationPage', () => {
  afterEach(() => cleanup());

  beforeEach(() => {
    sessionStorage.clear();
    vi.restoreAllMocks();
  });

  it('requests and confirms a pollen reservation', async () => {
    const user = userEvent.setup();
    mockInitialLoad()
      .mockResolvedValueOnce(Response.json({ id: 'reservation-1', seasonPlanId: 'plan-1', requestedAmount: 100, status: 'PENDING' }))
      .mockResolvedValueOnce(Response.json({ ready: false, errors: {} }))
      .mockResolvedValueOnce(
        Response.json({ id: 'reservation-1', seasonPlanId: 'plan-1', requestedAmount: 100, confirmedAmount: 100, status: 'CONFIRMED' })
      )
      .mockResolvedValueOnce(Response.json({ ready: false, errors: {} }));

    renderPage();
    await user.click(await screen.findByRole('button', { name: 'Request pollen reservation' }));
    expect(await screen.findByText('PENDING')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Confirm reservation' }));
    expect(await screen.findByText('CONFIRMED')).toBeInTheDocument();
  });

  it('displays pollen limit errors when confirmation is rejected', async () => {
    const user = userEvent.setup();
    sessionStorage.setItem(
      'seasonPlan.workflow',
      JSON.stringify({ reservationsByPlan: { 'plan-1': { id: 'reservation-1', requestedAmount: 100, status: 'PENDING' } } })
    );
    mockInitialLoad().mockResolvedValueOnce(
      Response.json({ message: 'validation failed', errors: { confirmedAmount: ['pollen limit exceeded'] } }, { status: 400 })
    );

    renderPage();
    await user.click(await screen.findByRole('button', { name: 'Confirm reservation' }));

    expect(await screen.findByText('pollen limit exceeded')).toBeInTheDocument();
  });

  it('creates a task and assigns a compatible fairy', async () => {
    const user = userEvent.setup();
    mockInitialLoad()
      .mockResolvedValueOnce(
        Response.json({ id: 'task-1', seasonPlanId: 'plan-1', title: 'Launch blooming', requiredTalent: 'SPRING_WEATHER', status: 'READY' })
      )
      .mockResolvedValueOnce(Response.json({ ready: false, errors: {} }))
      .mockResolvedValueOnce(
        Response.json({ id: 'assignment-1', magicTaskId: 'task-1', fairyId: 'fairy-1', talent: 'SPRING_WEATHER', status: 'CONFIRMED' })
      )
      .mockResolvedValueOnce(Response.json({ ready: false, errors: {} }));

    renderPage();
    await user.click(await screen.findByRole('button', { name: 'Create task' }));
    expect(await screen.findByText('Launch blooming')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Assign fairy' }));
    expect(await screen.findByText('fairy-1')).toBeInTheDocument();
    expect(screen.getByText('CONFIRMED')).toBeInTheDocument();
  });

  it('shows assignment schedule and talent errors', async () => {
    const user = userEvent.setup();
    sessionStorage.setItem(
      'seasonPlan.workflow',
      JSON.stringify({ tasksByPlan: { 'plan-1': [{ id: 'task-1', title: 'Launch blooming', status: 'READY' }] } })
    );
    mockInitialLoad().mockResolvedValueOnce(
      Response.json({ message: 'validation failed', errors: { assignment: ['fairy has schedule conflict'] } }, { status: 400 })
    );

    renderPage();
    await user.click(await screen.findByRole('button', { name: 'Assign fairy' }));

    expect(await screen.findByText('fairy has schedule conflict')).toBeInTheDocument();
  });

  it('displays readiness errors and marks ritual ready after success', async () => {
    const user = userEvent.setup();
    mockInitialLoad()
      .mockResolvedValueOnce(Response.json({ ...plan, status: 'RITUAL_READY' }))
      .mockResolvedValueOnce(Response.json({ ready: true, errors: {} }));

    renderPage();

    expect(await screen.findByText('mandatory task is required')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Mark ritual ready' }));
    expect(await screen.findByText(/RITUAL_READY/)).toBeInTheDocument();
  });
});
