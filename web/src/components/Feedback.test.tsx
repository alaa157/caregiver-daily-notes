import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { FormEvent } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { I18nProvider } from '../i18n/I18nProvider';
import { ErrorState } from './Feedback';

describe('error feedback', () => {
  it('retries through its callback without submitting a surrounding form', async () => {
    const user = userEvent.setup();
    const retry = vi.fn();
    const submit = vi.fn((event: FormEvent<HTMLFormElement>) => event.preventDefault());
    render(<I18nProvider><form onSubmit={submit}><ErrorState onRetry={retry} /></form></I18nProvider>);
    const button = screen.getByRole('button', { name: 'Retry' });

    expect(button).toHaveAttribute('type', 'button');
    await user.click(button);

    expect(retry).toHaveBeenCalledTimes(1);
    expect(submit).not.toHaveBeenCalled();
  });
  it('renders a custom error message without a retry control when no callback is supplied', () => {
    render(<I18nProvider><ErrorState title="Connection unavailable" description="Please try later." /></I18nProvider>);

    expect(screen.getByRole('alert')).toHaveTextContent('Connection unavailable');
    expect(screen.getByRole('alert')).toHaveTextContent('Please try later.');
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });
});
