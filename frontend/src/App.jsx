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
          <p>Frontend workflow for the first three lifecycle use cases.</p>
        </div>
      </header>
      <section className="catalog-grid">
        <Link className="catalog-card" to="/season-plans/new">
          <h2>Season planning</h2>
          <p>Create, update, and submit a SeasonPlan draft through the backend API.</p>
          <span className="card-tag">Use case 1</span>
        </Link>
        <Link className={`catalog-card ${planId ? '' : 'disabled-card'}`} to={planId ? `/season-plans/${planId}/review` : '/season-plans/new'}>
          <h2>Review and approval</h2>
          <p>{planId ? 'Review cached plan and approve or return it.' : 'A saved draft is required before review.'}</p>
          <span className="card-tag">{planId ? 'Use case 2' : 'Needs plan'}</span>
        </Link>
        <Link
          className={`catalog-card ${planId ? '' : 'disabled-card'}`}
          to={planId ? `/season-plans/${planId}/preparation` : '/season-plans/new'}
        >
          <h2>Preparation for launch</h2>
          <p>{planId ? 'Prepare pollen reservation, tasks, assignments, and readiness.' : 'A saved draft is required before preparation.'}</p>
          <span className="card-tag">{planId ? 'Use case 3' : 'Needs plan'}</span>
        </Link>
      </section>

      <section className="card dashboard-card">
        <h2>Current cached plan</h2>
        {planId ? (
          <div className="button-row">
            <span className="pill accent">{workflow.latestPlan?.status ?? 'cached'}</span>
            <span className="pill">Plan {planId}</span>
            <Link className="back-link" to={`/season-plans/${planId}/review`}>
              Review cached plan
            </Link>
            <Link className="back-link" to={`/season-plans/${planId}/preparation`}>
              Prepare cached plan
            </Link>
          </div>
        ) : (
          <p className="note">Create a draft plan to begin. The backend has no list endpoint, so workflow continuity starts after a real command succeeds.</p>
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
          <h1>Workflow not active</h1>
          <p>Later lifecycle use cases are intentionally inactive in this frontend slice.</p>
        </div>
        <Link className="back-link" to="/">
          Back to dashboard
        </Link>
      </header>
      <p className="note">Ritual launch, active monitoring, incidents, corrections, completion, reports, and archive are out of scope.</p>
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
