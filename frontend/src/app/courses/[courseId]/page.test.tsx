// src/app/courses/[courseId]/page.test.tsx
import { render, screen } from '@testing-library/react';
import { vi, beforeEach } from 'vitest';
import { Suspense } from 'react';
import type { CourseDetail } from '@/types/api';

vi.mock('@/lib/api', () => ({
  getCourse:      vi.fn(),
  applyToCourse:  vi.fn().mockResolvedValue(undefined),
}));
vi.mock('@/lib/auth', () => ({ getCurrentJwt: vi.fn().mockResolvedValue('tok') }));

import CourseDetailPage from './page';
import { getCourse } from '@/lib/api';

const course: CourseDetail = {
  courseId: 'CS231N',
  title: 'Deep Learning for Vision',
  description: 'CNNs for visual recognition.',
  units: '3',
  quarter: 'Spring 2024',
  instructors: ['Fei-Fei Li'],
  prereqNote: 'CS229 and CS109',
  prerequisites: [
    { courseId: 'CS229', title: 'Machine Learning', met: true },
    { courseId: 'CS109', title: 'Probability',      met: false },
  ],
  canApply: false,
  applied: false,
};

beforeEach(() => {
  vi.mocked(getCourse).mockResolvedValue(course);
});

function renderPage() {
  return render(
    <Suspense fallback={<p>Loading</p>}>
      <CourseDetailPage params={Promise.resolve({ courseId: 'CS231N' })} />
    </Suspense>
  );
}

describe('CourseDetailPage', () => {
  it('shows title and description', async () => {
    renderPage();
    await screen.findByText('Deep Learning for Vision');
    expect(screen.getByText(/CNNs for visual recognition/i)).toBeInTheDocument();
  });

  it('renders prerequisite list with met/unmet status', async () => {
    renderPage();
    await screen.findByText('Machine Learning');
    const mlItem = screen.getByText('Machine Learning').closest('li')!;
    const probItem = screen.getByText('Probability').closest('li')!;
    expect(mlItem).toHaveTextContent('✓');
    expect(probItem).toHaveTextContent('✗');
  });

  it('shows blocked message when canApply is false', async () => {
    renderPage();
    await screen.findByText('Deep Learning for Vision');
    expect(screen.getByText(/prerequisites not met/i)).toBeInTheDocument();
  });

  it('shows prereqNote as informational text', async () => {
    renderPage();
    await screen.findByText(/CS229 and CS109/);
  });

  it('shows Apply button when canApply is true and not applied', async () => {
    const { getCourse } = await import('@/lib/api');
    vi.mocked(getCourse).mockResolvedValueOnce({ ...course, canApply: true, applied: false });
    render(<CourseDetailPage params={Promise.resolve({ courseId: 'CS231N' })} />);
    await screen.findByText('Deep Learning for Vision');
    expect(screen.getByRole('button', { name: /apply/i })).toBeInTheDocument();
  });

  it('shows Applied badge when already applied', async () => {
    const { getCourse } = await import('@/lib/api');
    vi.mocked(getCourse).mockResolvedValueOnce({ ...course, canApply: false, applied: true });
    render(<CourseDetailPage params={Promise.resolve({ courseId: 'CS231N' })} />);
    await screen.findByText('Deep Learning for Vision');
    expect(screen.getByText('Applied')).toBeInTheDocument();
  });
});
