// src/components/CourseCard.test.tsx
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CourseCard } from './CourseCard';
import type { CourseSearchResult } from '@/types/api';

vi.mock('next/link', () => ({
  default: ({ href, children, ...props }: { href: string; children: React.ReactNode; [key: string]: unknown }) =>
    <a href={href} {...props}>{children}</a>
}));

const eligible: CourseSearchResult = {
  courseId: 'CS229', title: 'Machine Learning', units: '3-4',
  canApply: true, missingPrereqs: [], applied: false,
};
const applied: CourseSearchResult = {
  courseId: 'CS106B', title: 'Programming Abstractions', units: '4',
  canApply: false, missingPrereqs: [], applied: true,
};
const ineligible: CourseSearchResult = {
  courseId: 'CS231N', title: 'Deep Learning for Vision', units: '3',
  canApply: false, missingPrereqs: ['CS229', 'CS109'], applied: false,
};

describe('CourseCard', () => {
  it('shows title and units', () => {
    render(<CourseCard course={eligible} onApply={vi.fn()} />);
    expect(screen.getByText('Machine Learning')).toBeInTheDocument();
    expect(screen.getByText(/3-4 units/i)).toBeInTheDocument();
  });

  it('shows Apply button when eligible and not applied', async () => {
    const user = userEvent.setup();
    const onApply = vi.fn();
    render(<CourseCard course={eligible} onApply={onApply} />);

    await user.click(screen.getByRole('button', { name: /apply/i }));
    expect(onApply).toHaveBeenCalledWith('CS229');
  });

  it('shows Applied badge (no Apply button) when already applied', () => {
    render(<CourseCard course={applied} onApply={vi.fn()} />);
    expect(screen.getByText('Applied')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^apply$/i })).not.toBeInTheDocument();
  });

  it('shows eligibility badge when ineligible and no Apply button', () => {
    render(<CourseCard course={ineligible} onApply={vi.fn()} />);
    expect(screen.getByText(/missing 2 prereqs/i)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /apply/i })).not.toBeInTheDocument();
  });

  it('links to course detail page', () => {
    render(<CourseCard course={eligible} onApply={vi.fn()} />);
    const link = screen.getByRole('link');
    expect(link).toHaveAttribute('href', '/courses/CS229');
  });
});
