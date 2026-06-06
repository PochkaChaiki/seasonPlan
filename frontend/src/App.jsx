import { Link, Route, Routes } from 'react-router-dom';

const screens = [
  { path: '/', label: 'Dashboard' },
  { path: '/season-plans', label: 'SeasonPlan details' },
  { path: '/tasks', label: 'Task list' },
  { path: '/incidents', label: 'Incident journal' },
  { path: '/climate-metrics', label: 'ClimateMetric monitoring' },
  { path: '/audit-log', label: 'AuditLog viewer' },
  { path: '/reports', label: 'SeasonReport viewer/export' }
];

function PlaceholderPage({ title }) {
  return (
    <section>
      <h2>{title}</h2>
      <p>This screen will be implemented through a tested vertical slice.</p>
    </section>
  );
}

export default function App() {
  return (
    <main className="app-shell">
      <h1>SeasonPlan</h1>
      <nav aria-label="Primary">
        {screens.map((screen) => (
          <Link key={screen.path} to={screen.path}>
            {screen.label}
          </Link>
        ))}
      </nav>
      <Routes>
        {screens.map((screen) => (
          <Route
            key={screen.path}
            path={screen.path}
            element={<PlaceholderPage title={screen.label} />}
          />
        ))}
      </Routes>
    </main>
  );
}

