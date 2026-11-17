import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { FormEvent } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { I18nProvider } from '../i18n/I18nProvider';
import { Button } from './Button';
import { StatusAnnouncerProvider } from './StatusAnnouncer';
import { EmptyState, ErrorState, FieldError, LoadingState } from './Feedback';

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

  it('writes loading and empty messages into the persistent live region', async () => {
    const renderFeedback = (empty: boolean) => (
      <StatusAnnouncerProvider><I18nProvider>
        {empty
          ? <EmptyState title="No recipients yet" description="Add a recipient to continue." />
          : <LoadingState label="Loading recipient details" />}
      </I18nProvider></StatusAnnouncerProvider>
    );
    const view = render(renderFeedback(false));
    const status = screen.getByRole('status');
    const decoration = view.container.querySelector('.feedback-icon');

    expect(status).toHaveAttribute('aria-live', 'polite');
    expect(status).toBeEmptyDOMElement();
    expect(screen.getByText('Loading recipient details')).toBeVisible();
    await waitFor(() => expect(status).toHaveTextContent('Loading recipient details'));
    expect(decoration).toHaveAttribute('aria-hidden', 'true');
    expect(decoration?.querySelector('.spinner')).not.toBeNull();

    view.rerender(renderFeedback(true));
    expect(screen.getByRole('status')).toBe(status);
    expect(screen.getByText('No recipients yet')).toBeVisible();
    await waitFor(() => expect(status).toHaveTextContent('No recipients yet. Add a recipient to continue.'));
  });

  it('announces errors as alerts', () => {
    render(
      <I18nProvider>
        <ErrorState title="Unable to load records" description="Check your connection." onRetry={() => {}} />
      </I18nProvider>,
    );
    const alert = screen.getByRole('alert');
    const retry = screen.getByRole('button', { name: 'Retry' });

    expect(alert).toBeVisible();
    expect(screen.getByText('Unable to load records')).toBeVisible();
    expect(screen.getByText('Check your connection.')).toBeVisible();
    expect(retry).toHaveAttribute('type', 'button');
    expect(retry).toHaveClass('button--secondary');
    expect(retry.querySelector('svg')).toHaveAttribute('aria-hidden', 'true');
  });

  it('associates field error text with its id', () => {
    render(
      <I18nProvider>
        <label htmlFor="email">Email</label>
        <input id="email" aria-describedby="email-error" />
        <FieldError id="email-error" message="Enter a valid email address." />
      </I18nProvider>,
    );
    const input = screen.getByRole('textbox', { name: 'Email' });
    const error = screen.getByRole('alert');

    expect(error).toHaveAttribute('id', 'email-error');
    expect(error).toHaveTextContent('Enter a valid email address.');
    expect(error).toBeVisible();
    expect(error.querySelector('svg')).toHaveAttribute('aria-hidden', 'true');
    expect(input).toHaveAttribute('aria-describedby', 'email-error');
    expect(document.getElementById(input.getAttribute('aria-describedby')!)).toBe(error);
  });

  it('keeps retry button keyboard accessible', async () => {
    const user = userEvent.setup();
    const retry = vi.fn();
    const submit = vi.fn((event: FormEvent<HTMLFormElement>) => event.preventDefault());
    render(<I18nProvider><form onSubmit={submit}><ErrorState onRetry={retry} /></form></I18nProvider>);
    const button = screen.getByRole('button', { name: 'Retry' });

    await user.tab();
    expect(button).toHaveFocus();
    await user.keyboard('{Enter}');
    expect(retry).toHaveBeenCalledTimes(1);
    expect(submit).not.toHaveBeenCalled();
    await user.keyboard(' ');
    expect(retry).toHaveBeenCalledTimes(2);
    expect(submit).not.toHaveBeenCalled();
  });

  it('preserves native button types and disabled behavior', async () => {
    const user = userEvent.setup();
    const action = vi.fn();
    const submit = vi.fn((event: FormEvent<HTMLFormElement>) => event.preventDefault());
    render(
      <form onSubmit={submit}>
        <Button disabled onClick={action}>Unavailable</Button>
        <Button type="submit">Submit</Button>
        <Button type="reset">Reset</Button>
      </form>,
    );
    const disabled = screen.getByRole('button', { name: 'Unavailable' });

    expect(disabled).toHaveAttribute('type', 'button');
    expect(screen.getByRole('button', { name: 'Submit' })).toHaveAttribute('type', 'submit');
    expect(screen.getByRole('button', { name: 'Reset' })).toHaveAttribute('type', 'reset');
    expect(disabled).toBeDisabled();
    await user.click(disabled);
    expect(action).not.toHaveBeenCalled();
    expect(submit).not.toHaveBeenCalled();
  });
});
