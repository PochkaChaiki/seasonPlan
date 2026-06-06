import '@testing-library/jest-dom/vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import App from './App.jsx';

describe('App', () => {
  it('renders the required top-level screens', () => {
    render(
      <MemoryRouter>
        <App />
      </MemoryRouter>
    );

    expect(screen.getByRole('heading', { name: 'SeasonPlan' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Dashboard' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'SeasonPlan details' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Task list' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'AuditLog viewer' })).toBeInTheDocument();
  });
});
