import { useEffect, useState } from 'react';
import { Link, Navigate, Route, Routes, useParams } from 'react-router-dom';
import { lifecycleApi } from './api/lifecycleApi.js';
import { cachePlans, readWorkflow } from './api/workflowStorage.js';
import { AuthProvider, useAuth } from './auth/AuthContext.jsx';
import LoginPage from './auth/LoginPage.jsx';
import ProfileSecurityPage from './auth/ProfileSecurityPage.jsx';
import UserRegistrationPage from './auth/UserRegistrationPage.jsx';
import PreparationPage from './features/preparation/PreparationPage.jsx';
import PlanReviewPage from './features/planReview/PlanReviewPage.jsx';
import SeasonPlanningPage from './features/seasonPlanning/SeasonPlanningPage.jsx';
import './styles/global.css';

function Dashboard({ currentActor }) {
  const [plans, setPlans] = useState([]);
  const [loadingPlans, setLoadingPlans] = useState(true);
  const [planLoadError, setPlanLoadError] = useState('');
  const roles = currentActor.roles ?? [];
  const canPlan = roles.includes('FAIRY_PLANNER');
  const canReview = roles.includes('CHIEF_GUARDIAN');
  const canPrepare = roles.includes('TALENT_COORDINATOR') || roles.includes('POLLEN_GUARDIAN');
  const primaryPlan = plans[0];

  useEffect(() => {
    let active = true;
    lifecycleApi.listPlans()
      .then((loadedPlans) => {
        if (!active) return;
        setPlans(loadedPlans);
        cachePlans(loadedPlans);
        setPlanLoadError('');
      })
      .catch((error) => {
        if (active) setPlanLoadError(error.message ?? 'Не удалось загрузить планы сезона.');
      })
      .finally(() => {
        if (active) setLoadingPlans(false);
      });
    return () => { active = false; };
  }, []);

  return (
    <main className="page-shell">
      <header className="site-header">
        <div>
          <h1>SeasonPlan lifecycle</h1>
          <p>Клиент для первых трех этапов жизненного цикла сезона.</p>
        </div>
      </header>
      <section className="catalog-grid">
        {canPlan ? (
          <Link className="catalog-card" to="/season-plans/new">
            <h2>Планирование сезона</h2>
            <p>Создайте новый черновик или отредактируйте последний черновик через backend API.</p>
            <span className="card-tag">UC 1</span>
          </Link>
        ) : (
          <article className="catalog-card disabled-card">
            <h2>Планирование сезона</h2>
            <p>Planning controls are available only to FAIRY_PLANNER.</p>
            <span className="card-tag">Observer</span>
          </article>
        )}
        {canReview && primaryPlan ? (
        <Link className="catalog-card" to={`/season-plans/${primaryPlan.id}/review`}>
          <h2>Согласование сезонного плана</h2>
          <p>Откройте план, проверьте его и примите решение после отправки на согласование.</p>
          <span className="card-tag">UC 2</span>
        </Link>
        ) : (
        <article className="catalog-card disabled-card">
          <h2>Согласование сезонного плана</h2>
          <p>{canReview ? 'Нет сохраненных планов.' : 'Review controls are available only to CHIEF_GUARDIAN.'}</p>
          <span className="card-tag">Observer</span>
        </article>
        )}
        {canPrepare && primaryPlan ? (
        <Link className="catalog-card" to={`/season-plans/${primaryPlan.id}/preparation`}>
          <h2>Подготовка сезона к запуску</h2>
          <p>Настройте резерв пыльцы, задачи, назначения и готовность.</p>
          <span className="card-tag">UC 3</span>
        </Link>
        ) : (
        <article className="catalog-card disabled-card">
          <h2>Подготовка сезона к запуску</h2>
          <p>{canPrepare ? 'Нет сохраненных планов.' : 'Preparation controls are available to TALENT_COORDINATOR and POLLEN_GUARDIAN.'}</p>
          <span className="card-tag">Observer</span>
        </article>
        )}
      </section>

      <section className="card dashboard-card">
        <h2>Доступные планы сезона</h2>
        {loadingPlans && plans.length === 0 ? <p className="note">Загрузка планов…</p> : null}
        {planLoadError ? <p className="note rose">{planLoadError}</p> : null}
        {plans.length > 0 ? (
          <div className="mini-list">
            {plans.map((plan) => (
              <div className="mini-item wrap" key={plan.id}>
                <span>{plan.seasonType ?? 'SeasonPlan'} · {plan.id}</span>
                <strong>{plan.status}</strong>
                <div className="button-row">
                  {canReview && (
                    <Link className="back-link" aria-label={`Open review for plan ${plan.id}`} to={`/season-plans/${plan.id}/review`}>
                      Открыть согласование
                    </Link>
                  )}
                  {canPrepare && (
                    <Link className="back-link" aria-label={`Open preparation for plan ${plan.id}`} to={`/season-plans/${plan.id}/preparation`}>
                      Открыть подготовку
                    </Link>
                  )}
                  {!canReview && !canPrepare && <span className="note">План доступен только для просмотра статуса.</span>}
                </div>
              </div>
            ))}
          </div>
        ) : (
          !loadingPlans && <p className="note">Сохраненных планов пока нет.</p>
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

function AccessDenied() {
  return <main className="page-shell"><section className="card compact">
    <h1>Доступ запрещен</h1>
    <p className="note">У вас нет прав для управления пользователями.</p>
    <Link className="back-link" to="/">На главную</Link>
  </section></main>;
}

function AdminRoute({ user }) {
  return user.administrator ? <UserRegistrationPage /> : <AccessDenied />;
}

function AuthenticatedApp() {
  const { loading, user, logout, reauthenticationNotice } = useAuth();
  if (loading) return <main className="page-shell"><p>Загрузка сеанса…</p></main>;
  if (!user) return <LoginPage notice={reauthenticationNotice} />;
  return (
    <>
      <header className="auth-bar">
        <span>{user.displayName} ({user.roles.join(', ')})</span>
        <nav aria-label="Учетная запись" className="auth-nav">
          <Link to="/profile/security">Профиль и безопасность</Link>
          {user.administrator && <Link to="/admin/users">Управление пользователями</Link>}
          <button onClick={logout}>Выйти</button>
        </nav>
      </header>
      <Routes>
        <Route path="/" element={<Dashboard currentActor={user} />} />
        <Route path="/season-plans/new" element={<SeasonPlanningPage currentActor={user} />} />
        <Route path="/season-plans/:id" element={<PlanRedirect />} />
        <Route path="/season-plans/:id/review" element={<PlanReviewPage currentActor={user} />} />
        <Route path="/season-plans/:id/preparation" element={<PreparationPage currentActor={user} />} />
        <Route path="/incidents" element={<InactiveWorkflow />} />
        <Route path="/climate-metrics" element={<InactiveWorkflow />} />
        <Route path="/audit-log" element={<InactiveWorkflow />} />
        <Route path="/reports" element={<InactiveWorkflow />} />
        <Route path="/profile/security" element={<ProfileSecurityPage />} />
        <Route path="/admin/users" element={<AdminRoute user={user} />} />
        <Route path="*" element={<InactiveWorkflow />} />
      </Routes>
    </>
  );
}

export default function App() { return <AuthProvider><AuthenticatedApp /></AuthProvider>; }
