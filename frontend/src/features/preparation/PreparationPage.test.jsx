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

function renderPageAs(actor) {
  return render(
    <MemoryRouter initialEntries={['/season-plans/plan-1/preparation']}>
      <Routes>
        <Route path="/season-plans/:id/preparation" element={<PreparationPage currentActor={actor} />} />
      </Routes>
    </MemoryRouter>
  );
}

function mockInitialLoad() {
  return vi
    .spyOn(globalThis, 'fetch')
    .mockResolvedValueOnce(Response.json(plan))
    .mockResolvedValueOnce(Response.json({ ready: false, errors: { task: ['mandatory task is required'] } }))
    .mockResolvedValueOnce(Response.json([]));
}

function mockInitialLoadWithTasks(tasks, assignmentsByTask = {}) {
  const fetchMock = vi
    .spyOn(globalThis, 'fetch')
    .mockResolvedValueOnce(Response.json(plan))
    .mockResolvedValueOnce(Response.json({ ready: false, errors: { task: ['mandatory task is required'] } }))
    .mockResolvedValueOnce(Response.json(tasks));

  tasks.forEach((task) => {
    fetchMock.mockResolvedValueOnce(Response.json(assignmentsByTask[task.id] ?? []));
  });

  return fetchMock;
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
    await user.click(await screen.findByRole('button', { name: 'Запросить резерв' }));
    expect((await screen.findAllByText('PENDING')).length).toBeGreaterThan(0);

    await user.click(screen.getByRole('button', { name: 'Подтвердить резерв' }));
    expect((await screen.findAllByText('CONFIRMED')).length).toBeGreaterThan(0);
  });

  it('loads persisted tasks and fairy assignments for the opened season plan', async () => {
    mockInitialLoadWithTasks(
      [
        {
          id: 'task-from-db',
          seasonPlanId: 'plan-1',
          title: 'Loaded from database',
          requiredTalent: 'SPRING_WEATHER',
          timeWindow: { start: '2027-03-02T09:00:00', end: '2027-03-02T12:00:00' },
          status: 'READY'
        }
      ],
      {
        'task-from-db': [
          {
            id: 'assignment-from-db',
            magicTaskId: 'task-from-db',
            fairyId: 'fairy-spring',
            role: 'LEAD',
            talent: 'SPRING_WEATHER',
            status: 'CONFIRMED'
          }
        ]
      }
    );

    renderPage();

    expect(await screen.findByText('Loaded from database')).toBeInTheDocument();
    expect((await screen.findAllByText('fairy-spring')).length).toBeGreaterThan(0);
  });

  it('keeps backend tasks visible when assignment loading fails', async () => {
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(Response.json(plan))
      .mockResolvedValueOnce(Response.json({ ready: false, errors: {} }))
      .mockResolvedValueOnce(
        Response.json([
          {
            id: 'task-from-db',
            seasonPlanId: 'plan-1',
            title: 'Task survives assignment failure',
            requiredTalent: 'SPRING_WEATHER',
            timeWindow: { start: '2027-03-02T09:00:00', end: '2027-03-02T12:00:00' },
            status: 'READY'
          }
        ])
      )
      .mockResolvedValueOnce(Response.json({ message: 'bad gateway' }, { status: 502 }));

    renderPage();

    expect(await screen.findByText('Task survives assignment failure')).toBeInTheDocument();
    expect(await screen.findByText('bad gateway')).toBeInTheDocument();
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
    await user.click(await screen.findByRole('button', { name: 'Подтвердить резерв' }));

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
    await user.click(await screen.findByRole('button', { name: 'Создать задачу' }));
    expect(await screen.findByText('Launch blooming')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Назначить фею' }));
    expect(await screen.findByText('fairy-1')).toBeInTheDocument();
    expect(screen.getAllByText('CONFIRMED').length).toBeGreaterThan(0);
  });

  it('offers only backend-supported assignment statuses and compatible fairy options', async () => {
    const user = userEvent.setup();
    mockInitialLoadWithTasks([{ id: 'task-1', title: 'Launch blooming', requiredTalent: 'SPRING_WEATHER', status: 'READY' }]);

    renderPage();

    const status = await screen.findByLabelText('assignment-status');
    expect(Array.from(status.options).map((option) => option.value)).toEqual(['PROPOSED', 'CONFIRMED']);

    await user.selectOptions(screen.getByLabelText('assignment-task'), 'task-1');
    const fairy = screen.getByLabelText('assignment-fairy');
    expect(Array.from(fairy.options).map((option) => option.value)).toEqual(['fairy-spring', 'fairy-bloom']);

    const role = screen.getByLabelText('assignment-role');
    expect(Array.from(role.options).map((option) => option.value)).toEqual(['LEAD', 'SUPPORT', 'BACKUP']);
  });

  it('sends manually entered task and assignment values', async () => {
    const user = userEvent.setup();
    const requests = [];
    mockInitialLoad()
      .mockImplementation((url, options) => {
        requests.push({ url: String(url), options });
        if (String(url).endsWith('/tasks') && options?.method === 'POST') {
          return Promise.resolve(
            Response.json({
              id: 'task-1',
              seasonPlanId: 'plan-1',
              title: 'Проверить северные сады',
              requiredTalent: 'FROST_CONTROL',
              status: 'READY'
            })
          );
        }
        if (String(url).endsWith('/readiness')) {
          return Promise.resolve(Response.json({ ready: false, errors: {} }));
        }
        if (String(url).endsWith('/assignments')) {
          return Promise.resolve(
            Response.json({ id: 'assignment-1', magicTaskId: 'task-1', fairyId: 'fairy-north', talent: 'FROST_CONTROL', status: 'CONFIRMED' })
          );
        }
        return Promise.reject(new Error(`unexpected request: ${url}`));
      });

    renderPage();

    await user.clear(await screen.findByLabelText(/Название задачи/i));
    await user.type(screen.getByLabelText(/Название задачи/i), 'Проверить северные сады');
    await user.clear(screen.getByLabelText(/Описание задачи/i));
    await user.type(screen.getByLabelText(/Описание задачи/i), 'Осмотреть участки перед запуском');
    await user.selectOptions(screen.getByLabelText(/Требуемый талант/i), 'FROST_CONTROL');
    await user.selectOptions(screen.getByLabelText(/Талант назначения/i), 'FROST_CONTROL');

    await user.click(screen.getByRole('button', { name: 'Создать задачу' }));
    expect(await screen.findByLabelText('assignment-fairy')).toHaveValue('fairy-north');
    await user.click(await screen.findByRole('button', { name: 'Назначить фею' }));

    const createTask = requests.find((request) => request.url.endsWith('/tasks'));
    expect(JSON.parse(createTask.options.body)).toMatchObject({
      title: 'Проверить северные сады',
      description: 'Осмотреть участки перед запуском',
      requiredTalent: 'FROST_CONTROL'
    });

    const assignFairy = requests.find((request) => request.url.endsWith('/assignments'));
    expect(JSON.parse(assignFairy.options.body)).toMatchObject({ fairyId: 'fairy-north', talent: 'FROST_CONTROL' });
  });

  it('shows assignment schedule and talent errors', async () => {
    const user = userEvent.setup();
    mockInitialLoadWithTasks([{ id: 'task-1', title: 'Launch blooming', requiredTalent: 'SPRING_WEATHER', status: 'READY' }]).mockResolvedValueOnce(
      Response.json({ message: 'validation failed', errors: { assignment: ['fairy has schedule conflict'] } }, { status: 400 })
    );

    renderPage();
    await user.click(await screen.findByRole('button', { name: 'Назначить фею' }));

    expect(await screen.findByText('fairy has schedule conflict')).toBeInTheDocument();
  });

  it('displays readiness errors and marks ritual ready after success', async () => {
    const user = userEvent.setup();
    mockInitialLoad()
      .mockResolvedValueOnce(Response.json({ ...plan, status: 'RITUAL_READY' }))
      .mockResolvedValueOnce(Response.json({ ready: true, errors: {} }));

    renderPage();

    expect(await screen.findByText('mandatory task is required')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Подтвердить готовность' }));
    expect(await screen.findByText(/RITUAL_READY/)).toBeInTheDocument();
  });

  it('completes readiness checklist items through the backend', async () => {
    const user = userEvent.setup();
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(
        Response.json({
          ...plan,
          readinessChecklist: [
            { key: 'calendar-reviewed', complete: false },
            { key: 'roles-reviewed', complete: false }
          ]
        })
      )
      .mockResolvedValueOnce(Response.json({ ready: false, errors: { readinessChecklist: ['all readiness checklist items must be complete'] } }))
      .mockResolvedValueOnce(Response.json([]))
      .mockResolvedValueOnce(
        Response.json({
          ...plan,
          readinessChecklist: [
            { key: 'calendar-reviewed', complete: true },
            { key: 'roles-reviewed', complete: false }
          ]
        })
      )
      .mockResolvedValueOnce(Response.json({ ready: false, errors: { readinessChecklist: ['all readiness checklist items must be complete'] } }));

    renderPage();

    await user.click(await screen.findByRole('button', { name: /calendar-reviewed/i }));

    expect(await screen.findByText(/calendar-reviewed: complete/i)).toBeInTheDocument();
  });

  it('shows only pollen controls to pollen guardian observers', async () => {
    mockInitialLoad();

    renderPageAs({ role: 'POLLEN_GUARDIAN', id: 'pollen-1' });

    expect(await screen.findByRole('button', { name: /Запросить резерв/i })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Создать задачу/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Подтвердить готовность/i })).not.toBeInTheDocument();
    expect(screen.queryByText(/Task and readiness controls are available only to TALENT_COORDINATOR/i)).not.toBeInTheDocument();
  });

  it('does not render unauthorized task and readiness blocks for pollen guardian observers', async () => {
    mockInitialLoad();

    renderPageAs({ role: 'POLLEN_GUARDIAN', id: 'pollen-1' });

    expect((await screen.findAllByRole('button', { name: /резерв/i })).length).toBeGreaterThan(0);
    expect(screen.queryByText(/Task and readiness controls are available only to TALENT_COORDINATOR/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/MagicTask/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/FairyAssignment/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/RitualSession/i)).not.toBeInTheDocument();
  });
});
