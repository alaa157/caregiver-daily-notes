import { useState } from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { ApiProvider } from '../../lib/api';
import { I18nProvider } from '../../i18n/I18nProvider';
import { ByokProvider } from './ByokContext';
import { AiSettingsDialog } from './AiSettingsDialog';

function DialogFixture() {
  const [open, setOpen] = useState(false);
  return <>
    <button type="button" onClick={() => setOpen(true)}>Open settings</button>
    <AiSettingsDialog open={open} onClose={() => setOpen(false)} />
  </>;
}

function renderDialog() {
  return render(<I18nProvider><ApiProvider config={{ apiBaseUrl: '' }}><ByokProvider><DialogFixture /></ByokProvider></ApiProvider></I18nProvider>);
}

describe('AI settings dialog accessibility', () => {
  it('opens a named modal and moves focus into it', async () => {
    const user = userEvent.setup();
    renderDialog();
    const trigger = screen.getByRole('button', { name: 'Open settings' });
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    await user.tab();
    expect(trigger).toHaveFocus();
    await user.click(trigger);

    const dialog = screen.getByRole('dialog', { name: 'AI provider settings' });
    expect(dialog).toHaveAttribute('aria-modal', 'true');
    expect(dialog).toHaveAttribute('open');
    await waitFor(() => expect(screen.getByRole('combobox', { name: 'Provider' })).toHaveFocus());
  });

  it('keeps keyboard focus inside the modal at both tab boundaries', async () => {
    const user = userEvent.setup();
    renderDialog();
    await user.click(screen.getByRole('button', { name: 'Open settings' }));
    const provider = screen.getByRole('combobox', { name: 'Provider' });
    const close = screen.getByRole('button', { name: 'Close' });
    await waitFor(() => expect(provider).toHaveFocus());

    await user.tab({ shift: true });
    expect(close).toHaveFocus();
    await user.tab();
    expect(provider).toHaveFocus();

    await user.tab({ shift: true });
    expect(close).toHaveFocus();
  });

  it('closes on Escape and restores focus to the trigger', async () => {
    const user = userEvent.setup();
    renderDialog();
    const trigger = screen.getByRole('button', { name: 'Open settings' });
    await user.click(trigger);
    await waitFor(() => expect(screen.getByRole('combobox', { name: 'Provider' })).toHaveFocus());

    await user.keyboard('{Escape}');

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
  });
});
