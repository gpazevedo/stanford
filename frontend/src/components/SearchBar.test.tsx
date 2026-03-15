import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SearchBar } from './SearchBar';
import { describe, it, expect, vi } from 'vitest';

describe('SearchBar', () => {
  it('calls onSearch with query when form is submitted', async () => {
    const user = userEvent.setup();
    const onSearch = vi.fn();
    render(<SearchBar onSearch={onSearch} loading={false} />);

    await user.type(screen.getByRole('searchbox'), 'machine learning');
    await user.click(screen.getByRole('button', { name: /search/i }));

    expect(onSearch).toHaveBeenCalledWith('machine learning');
  });

  it('calls onSearch when Enter is pressed', async () => {
    const user = userEvent.setup();
    const onSearch = vi.fn();
    render(<SearchBar onSearch={onSearch} loading={false} />);

    await user.type(screen.getByRole('searchbox'), 'deep learning{Enter}');

    expect(onSearch).toHaveBeenCalledWith('deep learning');
  });

  it('disables button while loading', () => {
    render(<SearchBar onSearch={vi.fn()} loading={true} />);
    expect(screen.getByRole('button', { name: /searching/i })).toBeDisabled();
  });

  it('does not call onSearch with empty query', async () => {
    const user = userEvent.setup();
    const onSearch = vi.fn();
    render(<SearchBar onSearch={onSearch} loading={false} />);

    await user.click(screen.getByRole('button', { name: /search/i }));

    expect(onSearch).not.toHaveBeenCalled();
  });
});
