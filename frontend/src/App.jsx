import { Link, Navigate, Route, Routes, useParams } from 'react-router-dom';
import { readWorkflow } from './api/workflowStorage.js';
import { AuthProvider, useAuth } from './auth/AuthContext.jsx';
import LoginPage from './auth/LoginPage.jsx';
import ProfileSecurityPage from './auth/ProfileSecurityPage.jsx';
import UserRegistrationPage from './auth/UserRegistrationPage.jsx';
import PreparationPage from './features/preparation/PreparationPage.jsx';
import PlanReviewPage from './features/planReview/PlanReviewPage.jsx';
import SeasonPlanningPage from './features/seasonPlanning/SeasonPlanningPage.jsx';
import './styles/global.css';

function Dashboard({ currentActor }) {
  const workflow = readWorkflow();
  const planId = workflow.latestPlanId;
  const currentStatus = workflow.latestPlan?.status;
  const canPlan = currentActor.roles.includes('FAIRY_PLANNER');
  const draftHidden = currentStatus === 'DRAFT' && !canPlan;
  const canOpenPlan = Boolean(planId && !draftHidden);

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
        <Link className={`catalog-card ${canOpenPlan ? '' : 'disabled-card'}`} to={canOpenPlan ? `/season-plans/${planId}/review` : '/'}>
          <h2>Согласование сезонного плана</h2>
          <p>
            {draftHidden
              ? 'DRAFT plans are hidden from this role until planning is complete.'
              : planId
                ? 'Откройте последний план из текущей сессии, утвердите его или верните на доработку.'
                : 'Для согласования нужен сохраненный план.'}
          </p>
          <span className="card-tag">{canOpenPlan ? 'UC 2' : 'Observer'}</span>
        </Link>
        <Link className={`catalog-card ${canOpenPlan ? '' : 'disabled-card'}`} to={canOpenPlan ? `/season-plans/${planId}/preparation` : '/'}>
          <h2>Подготовка сезона к запуску</h2>
          <p>
            {draftHidden
              ? 'DRAFT plans are hidden from this role until planning is complete.'
              : planId
                ? 'Настройте резерв пыльцы, задачи, назначения и готовность.'
                : 'Для подготовки нужен сохраненный план.'}
          </p>
          <span className="card-tag">{canOpenPlan ? 'UC 3' : 'Observer'}</span>
        </Link>
      </section>

      <section className="card dashboard-card">
        <h2>Последний план в текущей сессии</h2>
        {planId ? (
          <div className="button-row">
            <span className="pill accent">{workflow.latestPlan?.status ?? 'cached'}</span>
            <span className="pill">Plan {planId}</span>
            {canOpenPlan && (
              <>
                <Link className="back-link" to={`/season-plans/${planId}/review`}>
                  Открыть согласование
                </Link>
                <Link className="back-link" to={`/season-plans/${planId}/preparation`}>
                  Открыть подготовку
                </Link>
              </>
            )}
            {draftHidden && <span className="note">DRAFT plans are hidden from this role until planning is complete.</span>}
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
