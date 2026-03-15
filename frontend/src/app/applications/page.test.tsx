// src/app/applications/page.test.tsx
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';

vi.mock('@/lib/api', () => ({
  getApplications:    vi.fn().mockResolvedValue([
    { courseId: 'CS229', title: 'Machine Learning', units: '3-4',
      canApply: false, missingPrereqs: [], applied: true },
  ]),
  withdrawApplication: vi.fn().mockResolvedValue(undefined),
}));
vi.mock('@/lib/auth', () => ({ getCurrentJwt: vi.fn().mockResolvedValue('tok') }));
vi.mock('next/link', () => ({
  default: ({ href, children, ...props }: { href: string; children: React.ReactNode; [key: string]: unknown }) =>
    <a href={href} {...props}>{children as React.ReactNode}</a>
}));

import ApplicationsPage from './page';
import { getApplications, withdrawApplication } from '@/lib/api';

beforeEach(() => {
  vi.mocked(getApplications).mockResolvedValue([
    { courseId: 'CS229', title: 'Machine Learning', units: '3-4',
      canApply: false, missingPrereqs: [], applied: true },
  ]);
  vi.mocked(withdrawApplication).mockResolvedValue(undefined);
});

describe('ApplicationsPage', () => {
  it('lists applied courses', async () => {
    render(<ApplicationsPage />);
    await screen.findByText('Machine Learning');
    expect(screen.getByText(/3-4 units/i)).toBeInTheDocument();
  });

  it('removes course from list after withdrawal', async () => {
    const user = userEvent.setup();
    render(<ApplicationsPage />);
    await screen.findByText('Machine Learning');

    await user.click(screen.getByRole('button', { name: /withdraw/i }));
    await user.click(screen.getByRole('button', { name: /confirm/i }));

    await waitFor(() =>
      expect(screen.queryByText('Machine Learning')).not.toBeInTheDocument());
    expect(withdrawApplication).toHaveBeenCalledWith('CS229');
  });

  it('shows empty state when no applications', async () => {
    const { getApplications } = await import('@/lib/api');
    vi.mocked(getApplications).mockResolvedValueOnce([]);
    render(<ApplicationsPage />);
    await screen.findByText(/no applications/i);
  });
});
