// src/components/PrereqList.test.tsx
import { render, screen } from '@testing-library/react';
import { PrereqList } from './PrereqList';
import type { PrereqStatus } from '@/types/api';

const prereqs: PrereqStatus[] = [
  { courseId: 'CS106B', title: 'Programming Abstractions', met: true },
  { courseId: 'CS109',  title: 'Probability',              met: false },
];

describe('PrereqList', () => {
  it('renders each prerequisite with title', () => {
    render(<PrereqList prerequisites={prereqs} />);
    expect(screen.getByText('Programming Abstractions')).toBeInTheDocument();
    expect(screen.getByText('Probability')).toBeInTheDocument();
  });

  it('shows checkmark for met prerequisites', () => {
    render(<PrereqList prerequisites={prereqs} />);
    expect(screen.getByText('Programming Abstractions').closest('li'))
      .toHaveTextContent('✓');
  });

  it('shows cross for unmet prerequisites', () => {
    render(<PrereqList prerequisites={prereqs} />);
    expect(screen.getByText('Probability').closest('li'))
      .toHaveTextContent('✗');
  });

  it('renders nothing when list is empty', () => {
    const { container } = render(<PrereqList prerequisites={[]} />);
    expect(container.firstChild).toBeNull();
  });
});
