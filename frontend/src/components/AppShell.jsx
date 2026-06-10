import { Link, NavLink } from 'react-router-dom';
import StatusPill from './StatusPill.jsx';

export default function AppShell({ title, subtitle, activeStep, plan, children }) {
  const steps = [
    { label: '1. Планирование сезона', path: '/season-plans/new' },
    { label: '2. Согласование сезонного плана', path: plan?.id ? `/season-plans/${plan.id}/review` : null },
    { label: '3. Подготовка сезона к запуску', path: plan?.id ? `/season-plans/${plan.id}/preparation` : null }
  ];

  return (
    <div className="page-shell">
      <header className="site-header">
        <div>
          <h1>SeasonPlan lifecycle</h1>
          <p>{subtitle ?? 'Первые три этапа жизненного цикла: планирование, согласование и подготовка.'}</p>
        </div>
        <Link className="back-link" to="/">
          К списку этапов
        </Link>
      </header>
      <section className="screen">
        <aside className="sidebar">
          <div className="brand">
            <h2>Season Flow</h2>
            <p>Основные этапы сезонного цикла</p>
          </div>
          <nav className="menu" aria-label="Жизненный цикл">
            {steps.map((step, index) =>
              step.path ? (
                <NavLink
                  key={step.label}
                  to={step.path}
                  className={({ isActive }) => `menu-item ${isActive || activeStep === index + 1 ? 'active' : ''}`}
                >
                  {step.label}
                </NavLink>
              ) : (
                <span key={step.label} className={`menu-item ${activeStep === index + 1 ? 'active' : ''}`}>
                  {step.label}
                </span>
              )
            )}
            <span className="menu-item inactive">Следующие этапы пока неактивны</span>
          </nav>
        </aside>
        <main className="main">
          <div className="topbar">
            <div>
              <h3>{title}</h3>
              <p>SeasonPlan: {plan?.id ?? 'еще не сохранен'}</p>
            </div>
            <div className="pill-row">
              <StatusPill status={plan?.status} />
              {plan?.id && (
                <>
                  <Link className="back-link" to={`/season-plans/${plan.id}/review`}>
                    Согласование
                  </Link>
                  <Link className="back-link" to={`/season-plans/${plan.id}/preparation`}>
                    Подготовка
                  </Link>
                </>
              )}
            </div>
          </div>
          {children}
        </main>
      </section>
    </div>
  );
}
