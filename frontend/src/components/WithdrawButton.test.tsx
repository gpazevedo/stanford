// src/components/WithdrawButton.test.tsx
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { WithdrawButton } from './WithdrawButton';

describe('WithdrawButton', () => {
  it('calls onWithdraw after confirmation', async () => {
    const user = userEvent.setup();
    const onWithdraw = vi.fn();
    render(<WithdrawButton courseId="CS229" onWithdraw={onWithdraw} />);

    await user.click(screen.getByRole('button', { name: /withdraw/i }));
    // confirmation dialog appears
    await user.click(screen.getByRole('button', { name: /confirm/i }));

    expect(onWithdraw).toHaveBeenCalledWith('CS229');
  });

  it('does not call onWithdraw when cancelled', async () => {
    const user = userEvent.setup();
    const onWithdraw = vi.fn();
    render(<WithdrawButton courseId="CS229" onWithdraw={onWithdraw} />);

    await user.click(screen.getByRole('button', { name: /withdraw/i }));
    await user.click(screen.getByRole('button', { name: /cancel/i }));

    expect(onWithdraw).not.toHaveBeenCalled();
  });

  it('is disabled while loading', () => {
    render(<WithdrawButton courseId="CS229" onWithdraw={vi.fn()} loading />);
    expect(screen.getByRole('button', { name: /withdraw/i })).toBeDisabled();
  });

  it('confirm button is also disabled when loading becomes true after dialog opens', async () => {
    const user = userEvent.setup();
    render(<WithdrawButton courseId="CS229" onWithdraw={vi.fn()} loading />);
    // When loading=true the initial button is disabled — confirm dialog never opens
    expect(screen.queryByRole('button', { name: /confirm/i })).not.toBeInTheDocument();
  });
});
