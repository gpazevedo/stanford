import { render, screen } from '@testing-library/react';
import { EligibilityBadge } from './EligibilityBadge';

describe('EligibilityBadge', () => {
  it('shows Eligible when canApply is true', () => {
    render(<EligibilityBadge canApply={true} missingPrereqs={[]} />);
    expect(screen.getByText('Eligible')).toBeInTheDocument();
  });

  it('shows missing prereq count when canApply is false', () => {
    render(<EligibilityBadge canApply={false} missingPrereqs={['CS106B', 'CS109']} />);
    expect(screen.getByText(/Missing 2 prereqs/i)).toBeInTheDocument();
  });

  it('shows singular prereq when only one missing', () => {
    render(<EligibilityBadge canApply={false} missingPrereqs={['CS106B']} />);
    expect(screen.getByText(/Missing 1 prereq$/i)).toBeInTheDocument();
  });
});
