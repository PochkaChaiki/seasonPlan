import { Link, NavLink } from 'react-router-dom';
import StatusPill from './StatusPill.jsx';

export default function AppShell({ title, subtitle, activeStep, plan, children }) {
  const steps = [
    { label: '1. Season planning', path: '/season-plans/new' },
    { label: '2. Review and approval', path: plan?.id ? `/season-plans/${plan.id}/review` : null },
    { label: '3. Preparation for launch', path: plan?.id ? `/season-plans/${plan.id}/preparation` : null }
  ];

  return (
    <div className="page-shell">
      <header className="site-header">
        <div>
          <h1>SeasonPlan lifecycle</h1>
          <p>{subtitle ?? 'First lifecycle slice for planning, approval, and preparation.'}</p>
        </div>
        <Link className="back-link" to="/">
          Back to dashboard
        </Link>
      </header>
      <section className="screen">
        <aside className="sidebar">
          <div className="brand">
            <h2>Lifecycle steps</h2>
            <p>Only the first 3 use cases are active in this frontend slice.</p>
          </div>
          <nav className="menu" aria-label="Lifecycle">
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
            <span className="menu-item inactive">Later use cases inactive</span>
          </nav>
        </aside>
        <main className="main">
          <div className="topbar">
            <div>
              <h3>{title}</h3>
              <p>Plan: {plan?.id ?? 'not saved yet'}</p>
            </div>
            <div className="pill-row">
              <StatusPill status={plan?.status} />
              {plan?.id && (
                <>
                  <Link className="back-link" to={`/season-plans/${plan.id}/review`}>
                    Review cached plan
                  </Link>
                  <Link className="back-link" to={`/season-plans/${plan.id}/preparation`}>
                    Prepare cached plan
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
