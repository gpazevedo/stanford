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

  it('confirm and cancel buttons are disabled when loading is true during confirmation', async () => {
    const user = userEvent.setup();
    // Start not loading, open the confirm dialog
    const { rerender } = render(<WithdrawButton courseId="CS229" onWithdraw={vi.fn()} loading={false} />);
    await user.click(screen.getByRole('button', { name: /withdraw/i }));
    // Now loading becomes true (e.g. withdrawal started elsewhere)
    rerender(<WithdrawButton courseId="CS229" onWithdraw={vi.fn()} loading={true} />);
    expect(screen.getByRole('button', { name: /confirm/i })).toBeDisabled();
    expect(screen.getByRole('button', { name: /cancel/i })).toBeDisabled();
  });
});
