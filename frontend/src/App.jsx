import { Link, Navigate, Route, Routes, useParams } from 'react-router-dom';
import { readWorkflow } from './api/workflowStorage.js';
import PreparationPage from './features/preparation/PreparationPage.jsx';
import PlanReviewPage from './features/planReview/PlanReviewPage.jsx';
import SeasonPlanningPage from './features/seasonPlanning/SeasonPlanningPage.jsx';
import './styles/global.css';

function Dashboard() {
  const workflow = readWorkflow();
  const planId = workflow.latestPlanId;

  return (
    <main className="page-shell">
      <header className="site-header">
        <div>
          <h1>SeasonPlan lifecycle</h1>
          <p>Клиент для первых трех этапов жизненного цикла сезона.</p>
        </div>
      </header>
      <section className="catalog-grid">
        <Link className="catalog-card" to="/season-plans/new">
          <h2>Планирование сезона</h2>
          <p>Создайте новый черновик или отредактируйте последний черновик через backend API.</p>
          <span className="card-tag">UC 1</span>
        </Link>
        <Link className={`catalog-card ${planId ? '' : 'disabled-card'}`} to={planId ? `/season-plans/${planId}/review` : '/season-plans/new'}>
          <h2>Согласование сезонного плана</h2>
          <p>{planId ? 'Откройте последний план из текущей сессии, утвердите его или верните на доработку.' : 'Для согласования нужен сохраненный план.'}</p>
          <span className="card-tag">{planId ? 'UC 2' : 'Нужен план'}</span>
        </Link>
        <Link
          className={`catalog-card ${planId ? '' : 'disabled-card'}`}
          to={planId ? `/season-plans/${planId}/preparation` : '/season-plans/new'}
        >
          <h2>Подготовка сезона к запуску</h2>
          <p>{planId ? 'Настройте резерв пыльцы, задачи, назначения и готовность.' : 'Для подготовки нужен сохраненный план.'}</p>
          <span className="card-tag">{planId ? 'UC 3' : 'Нужен план'}</span>
        </Link>
      </section>

      <section className="card dashboard-card">
        <h2>Последний план в текущей сессии</h2>
        {planId ? (
          <div className="button-row">
            <span className="pill accent">{workflow.latestPlan?.status ?? 'cached'}</span>
            <span className="pill">Plan {planId}</span>
            <Link className="back-link" to={`/season-plans/${planId}/review`}>
              Открыть согласование
            </Link>
            <Link className="back-link" to={`/season-plans/${planId}/preparation`}>
              Открыть подготовку
            </Link>
          </div>
        ) : (
          <p className="note">Создайте черновик, чтобы начать. У backend нет list endpoint, поэтому сессия запоминает только результаты успешных команд.</p>
        )}
      </section>
    </main>
  );
}

function PlanRedirect() {
  const { id } = useParams();
  const workflow = readWorkflow();
  const status = workflow.latestPlan?.id === id ? workflow.latestPlan.status : null;
  const target = ['APPROVED', 'RITUAL_READY'].includes(status) ? 'preparation' : 'review';
  return <Navigate to={`/season-plans/${id}/${target}`} replace />;
}

function InactiveWorkflow() {
  return (
    <main className="page-shell">
      <header className="site-header">
        <div>
          <h1>Этап неактивен</h1>
          <p>Поздние этапы жизненного цикла намеренно не включены в этот frontend slice.</p>
        </div>
        <Link className="back-link" to="/">
          К списку этапов
        </Link>
      </header>
      <p className="note">Запуск RitualSession, мониторинг, инциденты, корректировки, завершение, отчеты и архив пока вне объема реализации.</p>
    </main>
  );
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Dashboard />} />
      <Route path="/season-plans/new" element={<SeasonPlanningPage />} />
      <Route path="/season-plans/:id" element={<PlanRedirect />} />
      <Route path="/season-plans/:id/review" element={<PlanReviewPage />} />
      <Route path="/season-plans/:id/preparation" element={<PreparationPage />} />
      <Route path="/incidents" element={<InactiveWorkflow />} />
      <Route path="/climate-metrics" element={<InactiveWorkflow />} />
      <Route path="/audit-log" element={<InactiveWorkflow />} />
      <Route path="/reports" element={<InactiveWorkflow />} />
      <Route path="*" element={<InactiveWorkflow />} />
    </Routes>
  );
}
