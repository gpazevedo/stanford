// src/app/profile/page.test.tsx
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';

vi.mock('@/lib/api', () => ({
  getCompletedCourses:   vi.fn().mockResolvedValue({ courseIds: ['CS106B'] }),
  updateCompletedCourses: vi.fn().mockResolvedValue({ courseIds: ['CS106B', 'CS109'] }),
}));
vi.mock('@/lib/auth', () => ({ getCurrentJwt: vi.fn().mockResolvedValue('tok') }));

import ProfilePage from './page';
import { updateCompletedCourses } from '@/lib/api';

describe('ProfilePage', () => {
  it('shows current completed courses', async () => {
    render(<ProfilePage />);
    await screen.findByText('CS106B');
  });

  it('adds a course via the input', async () => {
    const user = userEvent.setup();
    render(<ProfilePage />);
    await screen.findByText('CS106B');

    await user.type(screen.getByPlaceholderText(/course id/i), 'CS109');
    await user.click(screen.getByRole('button', { name: /add/i }));

    await waitFor(() =>
      expect(updateCompletedCourses).toHaveBeenCalledWith(
        expect.arrayContaining(['CS106B', 'CS109'])));
  });

  it('removes a course by clicking its remove button', async () => {
    const user = userEvent.setup();
    render(<ProfilePage />);
    await screen.findByText('CS106B');

    await user.click(screen.getByRole('button', { name: /remove CS106B/i }));

    await waitFor(() =>
      expect(updateCompletedCourses).toHaveBeenCalledWith([]));
  });
});
