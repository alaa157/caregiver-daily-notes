import { useState } from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ApiProvider } from '../../lib/api';
import { I18nProvider } from '../../i18n/I18nProvider';
import { messages } from '../../i18n/translations';
import { jsonResponse } from '../../tests/authFixtures';
import { ByokProvider } from './ByokContext';
import { clearAiPreferences, loadAiPreferences, saveAiPreferences } from './aiPreferences';
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

async function openDialog(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole('button', { name: 'Open settings' }));
  await waitFor(() => expect(screen.getByRole('combobox', { name: 'Provider' })).toHaveFocus());
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

describe('AI settings model picker', () => {
  it('shows catalog radios with no preselected model and the provider notice', async () => {
    const user = userEvent.setup();
    renderDialog();
    await openDialog(user);

    expect(screen.getByRole('radiogroup', { name: 'Model' })).toBeInTheDocument();
    expect(screen.getAllByRole('radio').some((radio) => (radio as HTMLInputElement).checked)).toBe(false);
    expect(screen.getByText('On the free tier, Google may use your prompts and responses to improve its products. Paid-tier requests are not used for this.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Test connection' })).toBeDisabled();
  });

  it('sends the selected catalog model in credentials.model', async () => {
    const user = userEvent.setup();
    const fetcher = vi.fn().mockResolvedValue(jsonResponse({ success: true }));
    vi.stubGlobal('fetch', fetcher);
    renderDialog();
    await openDialog(user);

    await user.click(screen.getByRole('radio', { name: /Gemini 3\.8 Flash/ }));
    await user.type(screen.getByLabelText('API key'), 'test-key');
    await user.click(screen.getByRole('button', { name: 'Test connection' }));

    await waitFor(() => expect(fetcher).toHaveBeenCalledTimes(1));
    const [url, init] = fetcher.mock.calls[0];
    expect(url).toBe('/api/ai/byok/connection-test');
    expect(JSON.parse((init as RequestInit).body as string)).toEqual({
      credentials: { providerId: 'GEMINI', model: 'gemini-3.8-flash', apiKey: 'test-key' },
    });
  });

  it('sends a valid custom model ID chosen through Other', async () => {
    const user = userEvent.setup();
    const fetcher = vi.fn().mockResolvedValue(jsonResponse({ success: true }));
    vi.stubGlobal('fetch', fetcher);
    renderDialog();
    await openDialog(user);

    await user.click(screen.getByRole('radio', { name: /Other/ }));
    await user.type(screen.getByLabelText('Model ID'), 'custom/model-1');
    await user.type(screen.getByLabelText('API key'), 'test-key');
    await user.click(screen.getByRole('button', { name: 'Test connection' }));

    await waitFor(() => expect(fetcher).toHaveBeenCalledTimes(1));
    const [, init] = fetcher.mock.calls[0];
    expect(JSON.parse((init as RequestInit).body as string)).toEqual({
      credentials: { providerId: 'GEMINI', model: 'custom/model-1', apiKey: 'test-key' },
    });
  });

  it('rejects an invalid custom model ID and disables the connection test', async () => {
    const user = userEvent.setup();
    const fetcher = vi.fn().mockResolvedValue(jsonResponse({ success: true }));
    vi.stubGlobal('fetch', fetcher);
    renderDialog();
    await openDialog(user);

    await user.click(screen.getByRole('radio', { name: /Other/ }));
    await user.type(screen.getByLabelText('Model ID'), 'bad model!');
    await user.type(screen.getByLabelText('API key'), 'test-key');

    expect(screen.getByText('Use only letters, numbers and . _ : / - (up to 128 characters).')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Test connection' })).toBeDisabled();
    expect(fetcher).not.toHaveBeenCalled();
  });

  it('changes the provider notice and clears the chosen model when the provider changes', async () => {
    const user = userEvent.setup();
    renderDialog();
    await openDialog(user);

    await user.click(screen.getByRole('radio', { name: /Gemini 3\.8 Flash/ }));
    await user.selectOptions(screen.getByRole('combobox', { name: 'Provider' }), 'OPENAI');

    expect(screen.queryByRole('radio', { name: /Gemini 3\.8 Flash/ })).not.toBeInTheDocument();
    expect(screen.getAllByRole('radio').some((radio) => (radio as HTMLInputElement).checked)).toBe(false);
    expect(screen.getByText('No free tier. Usage is billed to your OpenAI account.')).toBeInTheDocument();
  });

  it('keeps English and Arabic translation keys in parity', () => {
    expect(Object.keys(messages.ar).sort()).toEqual(Object.keys(messages.en).sort());
  });
});

describe('AI preferences storage', () => {
  const storageKey = 'caregiver-ai-preferences-v1';

  it('saves and loads only provider and model', () => {
    saveAiPreferences({ providerId: 'GEMINI', model: 'gemini-3.8-flash' });
    expect(localStorage.getItem(storageKey)).toBe('{"providerId":"GEMINI","model":"gemini-3.8-flash"}');
    expect(loadAiPreferences()).toEqual({ providerId: 'GEMINI', model: 'gemini-3.8-flash' });
    clearAiPreferences();
    expect(loadAiPreferences()).toBeNull();
  });

  it('ignores corrupt or unknown stored data', () => {
    localStorage.setItem(storageKey, '{not-json');
    expect(loadAiPreferences()).toBeNull();
    localStorage.setItem(storageKey, JSON.stringify({ providerId: 'UNKNOWN', model: 'gpt-6-luna' }));
    expect(loadAiPreferences()).toBeNull();
    localStorage.setItem(storageKey, JSON.stringify({ providerId: 'GEMINI', model: 'bad model!' }));
    expect(loadAiPreferences()).toBeNull();
    localStorage.setItem(storageKey, JSON.stringify({ providerId: 'GEMINI' }));
    expect(loadAiPreferences()).toBeNull();
  });

  it('does not throw when storage writes fail', () => {
    const setItem = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('denied'); });
    try {
      expect(() => saveAiPreferences({ providerId: 'OPENAI', model: 'gpt-6-luna' })).not.toThrow();
    } finally {
      setItem.mockRestore();
    }
  });
});

describe('AI preferences in dialog', () => {
  const storageKey = 'caregiver-ai-preferences-v1';

  it('preselects a saved provider and catalog model on open', async () => {
    const user = userEvent.setup();
    saveAiPreferences({ providerId: 'OPENAI', model: 'gpt-6-luna' });
    renderDialog();
    await openDialog(user);

    await waitFor(() => expect(screen.getByLabelText('Provider')).toHaveValue('OPENAI'));
    await waitFor(() => expect(screen.getByRole('radio', { name: /GPT-6 Luna/ })).toBeChecked());
  });

  it('shows a saved non-catalog model under Other', async () => {
    const user = userEvent.setup();
    saveAiPreferences({ providerId: 'OPEN_ROUTER', model: 'custom/free-model' });
    renderDialog();
    await openDialog(user);

    await waitFor(() => expect(screen.getByLabelText('Provider')).toHaveValue('OPEN_ROUTER'));
    await waitFor(() => expect(screen.getByRole('radio', { name: /Other/ })).toBeChecked());
    expect(screen.getByLabelText('Model ID')).toHaveValue('custom/free-model');
  });

  it('saves provider and model after a successful test without storing the key', async () => {
    const user = userEvent.setup();
    const fetcher = vi.fn().mockResolvedValue(jsonResponse({ success: true }));
    vi.stubGlobal('fetch', fetcher);
    renderDialog();
    await openDialog(user);

    await user.selectOptions(screen.getByLabelText('Provider'), 'OPENAI');
    await user.click(screen.getByRole('radio', { name: /GPT-6 Luna/ }));
    await user.type(screen.getByLabelText('API key'), 'test-key');
    await user.click(screen.getByRole('button', { name: 'Test connection' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Connection test succeeded');
    expect(screen.getByLabelText('API key')).toHaveValue('');
    expect(localStorage.getItem(storageKey)).toBe('{"providerId":"OPENAI","model":"gpt-6-luna"}');
    expect(JSON.stringify(localStorage)).not.toContain('test-key');
    expect(JSON.stringify(sessionStorage)).not.toContain('test-key');

    await user.click(screen.getByRole('button', { name: 'Close' }));
    await user.click(screen.getByRole('button', { name: 'Open settings' }));
    await waitFor(() => expect(screen.getByLabelText('Provider')).toHaveValue('OPENAI'));
    await waitFor(() => expect(screen.getByRole('radio', { name: /GPT-6 Luna/ })).toBeChecked());
  });

  it('still completes a successful test when preference storage throws', async () => {
    const user = userEvent.setup();
    const fetcher = vi.fn().mockResolvedValue(jsonResponse({ success: true }));
    vi.stubGlobal('fetch', fetcher);
    const setItem = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('denied'); });
    try {
      renderDialog();
      await openDialog(user);
      await user.click(screen.getByRole('radio', { name: /Gemini 3\.8 Flash/ }));
      await user.type(screen.getByLabelText('API key'), 'test-key');
      await user.click(screen.getByRole('button', { name: 'Test connection' }));

      expect(await screen.findByRole('status')).toHaveTextContent('Connection test succeeded');
      expect(screen.getByLabelText('API key')).toHaveValue('');
    } finally {
      setItem.mockRestore();
    }
  });

  it('enables Remove key only when credentials or a key entry exist', async () => {
    const user = userEvent.setup();
    renderDialog();
    await openDialog(user);

    const remove = screen.getByRole('button', { name: 'Remove key' });
    expect(remove).toBeDisabled();
    await user.type(screen.getByLabelText('API key'), 'draft-key');
    expect(remove).toBeEnabled();
    await user.clear(screen.getByLabelText('API key'));
    expect(remove).toBeDisabled();
  });

  it('clears credentials and key while keeping saved preferences', async () => {
    const user = userEvent.setup();
    const fetcher = vi.fn().mockResolvedValue(jsonResponse({ success: true }));
    vi.stubGlobal('fetch', fetcher);
    renderDialog();
    await openDialog(user);

    await user.click(screen.getByRole('radio', { name: /Gemini 3\.8 Flash/ }));
    await user.type(screen.getByLabelText('API key'), 'test-key');
    await user.click(screen.getByRole('button', { name: 'Test connection' }));
    expect(await screen.findByRole('status')).toHaveTextContent('Connection test succeeded');

    await user.click(screen.getByRole('button', { name: 'Remove key' }));
    expect(screen.getByText('No verified credentials are active.')).toBeVisible();
    expect(screen.getByLabelText('API key')).toHaveValue('');
    expect(loadAiPreferences()).toEqual({ providerId: 'GEMINI', model: 'gemini-3.8-flash' });
    expect(screen.getByRole('radio', { name: /Gemini 3\.8 Flash/ })).toBeChecked();
  });

  it('shows key memory and data sent notices', async () => {
    const user = userEvent.setup();
    renderDialog();
    await openDialog(user);

    expect(screen.getByText("Your key is kept only in this browser tab's memory. You will need to enter it again after you reload or close the tab.")).toBeVisible();
    expect(screen.getByText('When you generate a summary, the note text for that care recipient is sent to the provider you chose.')).toBeVisible();
  });
});
