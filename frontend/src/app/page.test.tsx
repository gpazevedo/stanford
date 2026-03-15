// src/app/page.test.tsx
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';

vi.mock('@/lib/api', () => ({
  searchCourses: vi.fn().mockResolvedValue([
    { courseId: 'CS229', title: 'Machine Learning', units: '3-4',
      canApply: true, missingPrereqs: [], applied: false },
  ]),
  applyToCourse: vi.fn().mockResolvedValue(undefined),
}));

vi.mock('@/lib/auth', () => ({ getCurrentJwt: vi.fn().mockResolvedValue('tok') }));
vi.mock('next/link', () => ({
  default: ({ href, children, ...props }: { href: string; children: React.ReactNode; [key: string]: unknown }) =>
    <a href={href} {...props}>{children as React.ReactNode}</a>
}));

import SearchPage from './page';
import { searchCourses, applyToCourse } from '@/lib/api';

describe('SearchPage', () => {
  it('renders search bar initially', () => {
    render(<SearchPage />);
    expect(screen.getByRole('searchbox')).toBeInTheDocument();
  });

  it('shows results after search', async () => {
    const user = userEvent.setup();
    render(<SearchPage />);

    await user.type(screen.getByRole('searchbox'), 'machine learning');
    await user.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() =>
      expect(screen.getByText('Machine Learning')).toBeInTheDocument());
    expect(searchCourses).toHaveBeenCalledWith('machine learning', 10);
  });

  it('calls applyToCourse when Apply is clicked', async () => {
    const user = userEvent.setup();
    render(<SearchPage />);

    await user.type(screen.getByRole('searchbox'), 'ml');
    await user.click(screen.getByRole('button', { name: /search/i }));
    await screen.findByText('Machine Learning');

    await user.click(screen.getByRole('button', { name: /apply/i }));

    expect(applyToCourse).toHaveBeenCalledWith('CS229');
  });
});
