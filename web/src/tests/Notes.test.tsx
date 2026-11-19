import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { App } from '../app/App';
import { AuthProvider } from '../features/auth/AuthProvider';
import { NoteTimelineRow } from '../features/notes/NoteTimelineRow';
import { I18nProvider } from '../i18n/I18nProvider';
import { ApiProvider } from '../lib/api';
import { CARE_DATA_CHANGED_EVENT } from '../lib/careDataEvents';
import { jsonResponse, testToken } from './authFixtures';
import { addendumFixture, noteDetailFixture, noteFixture, noteListFixture, noteObservations, structuredObservations } from './noteFixtures';
import { observationFields, type FieldCorrection } from '../api/notes';
import type { NoteObservations } from '../api/observation';
import { ObservationValues } from '../features/notes/ObservationValues';
import { recipientFixture } from './recipientFixtures';

const root = '/app/notes';
const detail = `${root}/${noteFixture.id}`;
function renderNotes(path: string, fetcher: ReturnType<typeof vi.fn>, language = 'en', authenticated = true) {
  vi.stubGlobal('fetch', fetcher);
  localStorage.setItem('caregiver-language', language);
  if (authenticated) sessionStorage.setItem('caregiver-auth-session-v1', JSON.stringify({ token: testToken(), baseUrl: '' }));
  return render(<I18nProvider><ApiProvider config={{ apiBaseUrl: '' }}><AuthProvider><MemoryRouter initialEntries={[path]}><App /></MemoryRouter></AuthProvider></ApiProvider></I18nProvider>);
}
function dataFetcher(overrides?: (path: string, init: RequestInit) => Response | Promise<Response> | undefined) {
  return vi.fn().mockImplementation((path: string, init: RequestInit) => {
    const override = overrides?.(path, init);
    if (override) return Promise.resolve(override);
    if (path.startsWith('/api/recipients?')) return Promise.resolve(jsonResponse(path.includes('status=ARCHIVED') ? [] : [recipientFixture]));
    if (path === '/api/recipients') return Promise.resolve(jsonResponse([recipientFixture]));
    if (path.startsWith('/api/recipients/')) return Promise.resolve(jsonResponse(recipientFixture));
    return Promise.resolve(jsonResponse(path.endsWith('/addenda') ? [] : path.endsWith('/corrections') ? correctionHistoryFixture() : path === '/api/notes' ? noteListFixture : path.startsWith('/api/notes/') && !path.endsWith('/addenda') && !path.endsWith('/corrections') ? noteDetailFixture : noteListFixture));
  });
}
function correctionHistoryFixture(events: unknown[] = []) {
  const effectiveObservations = Object.fromEntries(observationFields.map((field) => {
    const observation = noteFixture.observations[field];
    return [field, { state: observation.state, value: observation.value ?? null, rawValue: observation.rawValue ?? null,
      lastOperation: null, eventId: null, actorId: null, updatedAt: noteFixture.createdAt }];
  }));
  return { originalNote: { ...noteListFixture, correctionCount: events.length }, events, effectiveObservations };
}
function correctionResponse(corrections: FieldCorrection[] = []) {
  let effectiveObservations = correctionHistoryFixture().effectiveObservations as Record<string, Record<string, unknown>>;
  const actorId = 'f5555555-5555-4555-8555-555555555555';
  const events = corrections.map((correction, index) => {
    const previousEffective = effectiveObservations[correction.field];
    const eventId = `e5555555-5555-4555-8555-${String(index + 1).padStart(12, '0')}`;
    const state = correction.operation === 'SET' ? 'RECORDED'
      : correction.operation === 'UNKNOWN' ? 'UNKNOWN' : 'NOT_RECORDED';
    const effectiveAfter = { state, value: correction.operation === 'SET' ? correction.value : null, rawValue: null,
      lastOperation: correction.operation, eventId, actorId, updatedAt: '2026-10-18T12:00:00Z' };
    effectiveObservations = { ...effectiveObservations, [correction.field]: effectiveAfter };
    return { id: eventId, noteId: noteFixture.id, sequenceNo: index + 1, createdAt: '2026-10-18T12:00:00Z', actorId,
      field: correction.field, operation: correction.operation,
      value: correction.operation === 'SET' ? correction.value : null, previousEffective, effectiveAfter };
  });
  return { originalNote: { ...noteFixture, correctionCount: events.length }, events, effectiveObservations };
}
async function metadata() {
  await userEvent.selectOptions(await screen.findByLabelText('Care recipient'), recipientFixture.id);
  fireEvent.change(screen.getByLabelText('Observation date'), { target: { value: noteFixture.date } });
}
async function setRecorded(label: string) {
  await setObservationState(label, 'RECORDED');
}
async function setObservationState(label: string, state: 'RECORDED' | 'UNKNOWN' | 'NOT_RECORDED' | 'NOT_APPLICABLE') {
  const stateText = {
    RECORDED: 'Recorded',
    UNKNOWN: 'Unknown',
    NOT_RECORDED: 'Not recorded',
    NOT_APPLICABLE: 'Not applicable',
  }[state];
  const group = screen.getByRole('group', { name: `${label} — Observation status` });
  await userEvent.click(within(group).getByText(stateText, { exact: true }));
}
async function selectMedicationChip(name: 'Taken' | 'Missed' | 'Unknown') {
  const group = screen.getByRole('radiogroup', { name: 'Medication taken' });
  await userEvent.click(within(group).getByRole('radio', { name }));
}
async function selectVerificationChip(name: 'Verified' | 'Unverified') {
  const group = screen.getByRole('radiogroup', { name: 'Medication report verified' });
  await userEvent.click(within(group).getByRole('radio', { name }));
}
async function selectFallChip(name: 'Yes' | 'No' | 'Unknown') {
  const group = screen.getByRole('radiogroup', { name: 'Fall reported' });
  await userEvent.click(within(group).getByRole('radio', { name }));
}
async function selectMoodChip(name: string) {
  const group = screen.getByRole('radiogroup', { name: 'Mood' });
  await userEvent.click(within(group).getByRole('radio', { name }));
}
async function selectAppetiteChip(name: string) {
  const group = screen.getByRole('radiogroup', { name: 'Appetite' });
  await userEvent.click(within(group).getByRole('radio', { name }));
}
async function selectSleepChip(name: string) {
  const group = screen.getByRole('radiogroup', { name: 'Sleep' });
  await userEvent.click(within(group).getByRole('radio', { name }));
}
async function selectMobilityChip(name: string) {
  const group = screen.getByRole('radiogroup', { name: 'Mobility' });
  await userEvent.click(within(group).getByRole('radio', { name }));
}
async function selectPainScoreChip(name: string) {
  const group = screen.getByRole('radiogroup', { name: 'Pain score (0–10)' });
  await userEvent.click(within(group).getByRole('radio', { name }));
}
function mockTimelineDimensions(body: HTMLElement, initialHeight = 0) {
  let scrollHeight = initialHeight;
  let clientHeight = initialHeight;
  body.style.lineHeight = '20px';
  body.style.fontSize = '10px';
  Object.defineProperty(body, 'scrollHeight', { configurable: true, get: () => scrollHeight });
  Object.defineProperty(body, 'clientHeight', { configurable: true, get: () => clientHeight });
  return {
    set(scroll: number, client: number) {
      scrollHeight = scroll;
      clientHeight = client;
    },
  };
}

class ControlledResizeObserver implements ResizeObserver {
  static instances: ControlledResizeObserver[] = [];
  disconnected = false;

  constructor(private readonly callback: ResizeObserverCallback) {
    ControlledResizeObserver.instances.push(this);
  }

  observe() {}
  unobserve() {}
  disconnect() { this.disconnected = true; }
  notify() { this.callback([], this); }
}

describe('daily-note creation and immutable corrections', () => {
  it('opens note creation from the app shell as a prefilled modal drawer and closes accessibly', async () => {
    // Warm the lazy note-page module; otherwise its cold import outlasts the
    // wait for the dialog to appear.
    await import('../features/notes/NotesPages');
    renderNotes('/app', dataFetcher());
    await waitFor(() => expect(document.querySelector('.app-create-note')).not.toBeNull());
    const trigger = document.querySelector<HTMLAnchorElement>('.app-create-note');
    expect(trigger).not.toBeNull();
    await userEvent.click(trigger!);
    const dialog = await screen.findByRole('dialog', { name: 'Record a daily note' });
    expect(dialog).toBeVisible();
    expect(document.querySelector('.app-layout')).toHaveAttribute('aria-hidden', 'true');
    expect(screen.getByLabelText('Observation date')).toHaveValue(new Date().toISOString().slice(0, 10));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add details' }));
    fireEvent.change(within(dialog).getByRole('textbox', { name: 'Free-text observations' }), { target: { value: 'Mixed العربية and English' } });
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel' }));
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    confirm.mockReturnValue(true);
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    confirm.mockRestore();
    await waitFor(() => expect(document.querySelector('.app-layout')).not.toHaveAttribute('aria-hidden', 'true'));
  });

  it('keeps the notes deep link as a dialog and restores the selected prefill', async () => {
    renderNotes(`/app/notes/new?recipientId=${recipientFixture.id}&date=${noteFixture.date}`, dataFetcher());
    const dialog = await screen.findByRole('dialog', { name: 'Record a daily note' });
    expect(within(dialog).getByLabelText('Care recipient')).toHaveValue(recipientFixture.id);
    expect(within(dialog).getByLabelText('Observation date')).toHaveValue(noteFixture.date);
  });

  it('groups note fields with fieldset legends', async () => {
    renderNotes(`${root}/new`, dataFetcher());

    const details = await screen.findByRole('group', { name: 'Note details' });
    const observations = screen.getByRole('group', { name: 'Observations' });
    expect(details).toBeVisible();
    expect(observations).toBeVisible();
    for (const legend of ['Daily wellbeing', 'Medication', 'Pain and safety', 'Additional notes']) {
      expect(within(observations).getByRole('group', { name: legend })).toBeVisible();
    }
    expect(within(details).getByLabelText('Care recipient')).toBeInTheDocument();
    expect(within(details).getByLabelText('Observation date')).toBeInTheDocument();
    expect(within(screen.getByRole('group', { name: 'Daily wellbeing' })).getByRole('radiogroup', { name: 'Mobility' })).toBeInTheDocument();
    expect(within(screen.getByRole('group', { name: 'Medication' })).getByRole('radiogroup', { name: 'Medication report verified' })).toBeInTheDocument();
    expect(within(screen.getByRole('group', { name: 'Pain and safety' })).getByRole('radiogroup', { name: 'Fall reported' })).toBeInTheDocument();
    expect(within(screen.getByRole('group', { name: 'Additional notes' }))
      .getByRole('button', { name: 'Add details' })).toBeInTheDocument();
  });

  it('keeps state choices keyboard accessible and exposes the selected state', async () => {
    renderNotes(`${detail}/addenda/new`, dataFetcher());
    await screen.findByRole('form', { name: 'Add a correction' });
    await userEvent.click(screen.getByRole('checkbox', { name: 'Correct this field: Free-text observations' }));
    const group = await screen.findByRole('group', { name: 'Free-text observations — Observation status' });
    await userEvent.click(within(group).getByText('Recorded', { exact: true }));
    const recorded = within(group).getByRole('radio', { name: 'Free-text observations — Recorded' });
    recorded.focus();
    expect(recorded).toBeChecked();
    await userEvent.keyboard('{ArrowDown}');
    expect(within(group).getByRole('radio', { name: 'Free-text observations — Unknown' })).toBeChecked();
    expect(recorded).not.toBeChecked();
    expect(within(group).getByText('Unknown', { exact: true })).toBeVisible();
  });

  it('submits recorded zero and false without coercion', async () => {
    const fetcher = dataFetcher();
    renderNotes(`${root}/new`, fetcher);
    await metadata();
    await selectPainScoreChip('0');
    await selectFallChip('No');
    await userEvent.click(screen.getByRole('button', { name: 'Save original note' }));
    await screen.findByText('Original note saved.');

    const posted = fetcher.mock.calls.find(([path, init]) => path === '/api/notes' && init.method === 'POST');
    const observations = JSON.parse(posted?.[1].body as string).observations as Record<string, { state: string; value?: unknown }>;
    expect(observations.painScore).toEqual({ state: 'RECORDED', value: 0 });
    expect(typeof observations.painScore.value).toBe('number');
    expect(observations.fallReported).toEqual({ state: 'RECORDED', value: false });
    expect(typeof observations.fallReported.value).toBe('boolean');
  });

  it('submits unknown without a typed value', async () => {
    let submitted: Record<string, unknown> | null = null;
    const fetcher = dataFetcher((path, init) => {
      if (path === '/api/notes' && init.method === 'POST') {
        submitted = JSON.parse(init.body as string);
        return jsonResponse(noteListFixture, 201);
      }
    });
    renderNotes(`${root}/new`, fetcher);
    await metadata();
    await selectPainScoreChip('Unknown');
    await userEvent.click(screen.getByRole('button', { name: 'Save original note' }));
    await screen.findByText('Original note saved.');

    expect(submitted).toMatchObject({ observations: { painScore: { state: 'UNKNOWN', value: null } } });
  });

  it('submits not recorded without a typed value', async () => {
    let submitted: Record<string, unknown> | null = null;
    const fetcher = dataFetcher((path, init) => {
      if (path === '/api/notes' && init.method === 'POST') {
        submitted = JSON.parse(init.body as string);
        return jsonResponse(noteListFixture, 201);
      }
    });
    renderNotes(`${root}/new`, fetcher);
    await metadata();
    await userEvent.click(screen.getByRole('button', { name: 'Save original note' }));
    await screen.findByText('Original note saved.');

    expect(submitted).toMatchObject({ observations: { fallReported: { state: 'NOT_RECORDED', value: null } } });
  });

  it('preserves medication verification independently from medication observation state', async () => {
    let submitted: Record<string, unknown> | null = null;
    const fetcher = dataFetcher((path, init) => {
      if (path === '/api/notes' && init.method === 'POST') {
        submitted = JSON.parse(init.body as string);
        return jsonResponse(noteListFixture, 201);
      }
    });
    renderNotes(`${root}/new`, fetcher);
    await metadata();
    await selectMedicationChip('Unknown');
    await selectVerificationChip('Verified');
    await userEvent.click(screen.getByRole('button', { name: 'Save original note' }));
    await screen.findByText('Original note saved.');

    expect(submitted).toMatchObject({
      observations: { medicationStatus: { state: 'UNKNOWN', value: null } },
      medicationVerification: 'VERIFIED',
    });
  });

  it('maps cleared free text back to not recorded', async () => {
    let submitted: Record<string, unknown> | null = null;
    const fetcher = dataFetcher((path, init) => {
      if (path === '/api/notes' && init.method === 'POST') {
        submitted = JSON.parse(init.body as string);
        return jsonResponse(noteListFixture, 201);
      }
    });
    renderNotes(`${root}/new`, fetcher);
    await metadata();
    await userEvent.click(screen.getByRole('button', { name: 'Add details' }));
    const textarea = screen.getByRole('textbox', { name: 'Free-text observations' });
    await userEvent.type(textarea, 'x');
    await userEvent.clear(textarea);
    await userEvent.click(screen.getByRole('button', { name: 'Save original note' }));

    expect(submitted).toMatchObject({ observations: { freeText: { state: 'NOT_RECORDED', value: null } } });
  });
  it('submits a stale typed value as null after selecting unknown', async () => {
    let submitted: Record<string, unknown> | null = null;
    const fetcher = dataFetcher((path, init) => {
      if (path === '/api/notes' && init.method === 'POST') {
        submitted = JSON.parse(init.body as string);
        return jsonResponse(noteListFixture, 201);
      }
    });
    renderNotes(`${root}/new`, fetcher);
    await metadata();
    await selectMoodChip('Calm');
    await selectMoodChip('Unknown');
    await userEvent.click(screen.getByRole('button', { name: 'Save original note' }));
    await screen.findByText('Original note saved.');
    expect(submitted).toMatchObject({ observations: { mood: { state: 'UNKNOWN', value: null } } });
  });

  it('does not submit twice while save is pending', async () => {
    let resolve!: (response: Response) => void;
    const fetcher = dataFetcher((path, init) => {
      if (path === '/api/notes' && init.method === 'POST') {
        return new Promise<Response>((done) => { resolve = done; });
      }
    });
    renderNotes(`${root}/new`, fetcher);
    await metadata();
    const form = screen.getByRole('form', { name: 'Record a daily note' });
    fireEvent.submit(form);
    fireEvent.submit(form);
    expect(fetcher.mock.calls.filter(([path, init]) => path === '/api/notes' && init.method === 'POST')).toHaveLength(1);
    expect(screen.getByRole('button', { name: 'Saving…' })).toBeDisabled();
    resolve(jsonResponse(noteListFixture, 201));
    expect(await screen.findByText('Original note saved.')).toBeInTheDocument();
  });

  it('does not offer legacy ambiguous as a state when creating a note', async () => {
    renderNotes(`${detail}/addenda/new`, dataFetcher());
    await screen.findByRole('form', { name: 'Add a correction' });
    await userEvent.click(screen.getByRole('checkbox', { name: 'Correct this field: Free-text observations' }));
    const group = await screen.findByRole('group', { name: 'Free-text observations — Observation status' });
    expect(within(group).queryByRole('radio', { name: 'Free-text observations — Legacy ambiguous' })).not.toBeInTheDocument();
  });

  it('announces save success and save failure separately', async () => {
    const successView = renderNotes(`${root}/new`, dataFetcher((path, init) =>
      path === '/api/notes' && init.method === 'POST' ? jsonResponse(noteListFixture, 201) : undefined));
    await metadata();
    await userEvent.click(screen.getByRole('button', { name: 'Save original note' }));
    expect((await screen.findAllByRole('status')).some((el) => el.textContent === 'Original note saved.')).toBe(true);
    successView.unmount();

    renderNotes(`${root}/new`, dataFetcher((path, init) =>
      path === '/api/notes' && init.method === 'POST' ? jsonResponse({ message: 'private server detail' }, 500) : undefined));
    await metadata();
    await userEvent.click(screen.getByRole('button', { name: 'Add details' }));
    const text = screen.getByRole('textbox', { name: 'Free-text observations' });
    fireEvent.change(text, { target: { value: 'Keep this entry for a retry.' } });
    await userEvent.click(screen.getByRole('button', { name: 'Save original note' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Could not confirm that the save completed');
    expect(screen.queryByText('Original note saved.')).not.toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Free-text observations' })).toHaveValue('Keep this entry for a retry.');
    expect(screen.getByRole('button', { name: 'Retry the same save' })).toBeEnabled();
    expect(screen.queryByText('private server detail')).not.toBeInTheDocument();
  });

  it('expands a note body longer than four lines', async () => {
    const text = 'Line one\nLine two\nLine three\nLine four\nLine five';
    render(<I18nProvider><NoteTimelineRow entryLabel="Original note" dateLabel="Oct 18, 2026"
      body={<p className="note-verbatim">{text}</p>} /></I18nProvider>);
    const row = screen.getByRole('article', { name: 'Original note' });
    const body = within(row).getByTestId('note-timeline-body');
    mockTimelineDimensions(body, 81);
    fireEvent(window, new Event('resize'));

    const showMore = await within(row).findByRole('button', { name: 'Show more' });
    expect(body).toHaveAttribute('data-collapsed', 'true');
    showMore.focus();
    await userEvent.keyboard('{Enter}');
    expect(showMore).toHaveAttribute('aria-expanded', 'true');
    expect(body).toHaveAttribute('data-collapsed', 'false');
    expect(body.textContent).toBe(text);
    await userEvent.keyboard(' ');
    expect(within(row).getByRole('button', { name: 'Show more' })).toHaveAttribute('aria-expanded', 'false');
    expect(body).toHaveAttribute('data-collapsed', 'true');
  });

  it('does not show expansion when the body is exactly four rendered lines', () => {
    render(<I18nProvider><NoteTimelineRow entryLabel="Original note" dateLabel="Oct 18, 2026" body={<p>Four lines.</p>} /></I18nProvider>);
    const body = screen.getByTestId('note-timeline-body');
    mockTimelineDimensions(body, 80);
    fireEvent(window, new Event('resize'));

    expect(body).toHaveAttribute('data-collapsed', 'false');
    expect(screen.queryByRole('button', { name: 'Show more' })).not.toBeInTheDocument();
  });

  it('does not show expansion when the body is below four rendered lines', () => {
    render(<I18nProvider><NoteTimelineRow entryLabel="Original note" dateLabel="Oct 18, 2026" body={<p>Two lines.</p>} /></I18nProvider>);
    const body = screen.getByTestId('note-timeline-body');
    mockTimelineDimensions(body, 40);
    fireEvent(window, new Event('resize'));

    expect(body).toHaveAttribute('data-collapsed', 'false');
    expect(screen.queryByRole('button', { name: 'Show more' })).not.toBeInTheDocument();
  });

  it('shows expansion when content grows beyond four lines after render', async () => {
    ControlledResizeObserver.instances = [];
    vi.stubGlobal('ResizeObserver', ControlledResizeObserver);
    const { unmount } = render(<I18nProvider><NoteTimelineRow entryLabel="Original note" dateLabel="Oct 18, 2026" body={<p>Changing content.</p>} /></I18nProvider>);
    const body = screen.getByTestId('note-timeline-body');
    const dimensions = mockTimelineDimensions(body, 80);
    expect(screen.queryByRole('button', { name: 'Show more' })).not.toBeInTheDocument();

    dimensions.set(81, 80);
    act(() => ControlledResizeObserver.instances[0].notify());
    expect(await screen.findByRole('button', { name: 'Show more' })).toHaveAttribute('aria-expanded', 'false');
    expect(body).toHaveAttribute('data-collapsed', 'true');

    unmount();
    expect(ControlledResizeObserver.instances[0].disconnected).toBe(true);
  });

  it('removes expansion when content fits again and resets expanded state', async () => {
    ControlledResizeObserver.instances = [];
    vi.stubGlobal('ResizeObserver', ControlledResizeObserver);
    const { unmount } = render(<I18nProvider><NoteTimelineRow entryLabel="Original note" dateLabel="Oct 18, 2026" body={<p>Changing content.</p>} /></I18nProvider>);
    const body = screen.getByTestId('note-timeline-body');
    const dimensions = mockTimelineDimensions(body, 81);
    act(() => ControlledResizeObserver.instances[0].notify());

    await userEvent.click(await screen.findByRole('button', { name: 'Show more' }));
    expect(screen.getByRole('button', { name: 'Show less' })).toHaveAttribute('aria-expanded', 'true');

    dimensions.set(80, 80);
    act(() => ControlledResizeObserver.instances[0].notify());
    expect(screen.queryByRole('button', { name: 'Show more' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Show less' })).not.toBeInTheDocument();
    expect(body).toHaveAttribute('data-collapsed', 'false');

    dimensions.set(81, 80);
    act(() => ControlledResizeObserver.instances[0].notify());
    expect(await screen.findByRole('button', { name: 'Show more' })).toHaveAttribute('aria-expanded', 'false');
    expect(body).toHaveAttribute('data-collapsed', 'true');
    unmount();
    expect(ControlledResizeObserver.instances[0].disconnected).toBe(true);
  });

  it('does not show expansion for short note text', () => {
    render(<I18nProvider><NoteTimelineRow entryLabel="Original note" dateLabel="Oct 18, 2026"
      body={<p>Short note text.</p>} /></I18nProvider>);

    expect(screen.getByText('Short note text.')).toBeVisible();
    expect(screen.queryByRole('button', { name: 'Show more' })).not.toBeInTheDocument();
  });

  it('keeps correction provenance visible when body is collapsed', async () => {
    const longText = Array.from({ length: 10 }, (_, index) => `تصحيح ${index + 1} — mixed English`).join('\n');
    const longNote = {
      ...noteFixture,
      freeText: longText,
      observations: { ...noteFixture.observations, freeText: { state: 'RECORDED' as const, value: longText } },
    };
    const longAddendum = {
      ...addendumFixture,
      freeText: longText,
      observations: { ...addendumFixture.observations, freeText: { state: 'RECORDED' as const, value: longText } },
    };
    const corrections = correctionResponse([
      { field: 'mood', operation: 'SET', value: 'calm' },
      { field: 'painScore', operation: 'SET', value: 3 },
    ]);
    corrections.originalNote = { ...corrections.originalNote, observations: longNote.observations };
    corrections.effectiveObservations.freeText = {
      ...corrections.effectiveObservations.freeText,
      value: longText,
    };
    const fetcher = dataFetcher((path) => {
      if (path === `/api/notes/${noteFixture.id}`) return jsonResponse({ ...noteDetailFixture, ...longNote, addenda: [] });
      if (path.endsWith('/addenda')) return jsonResponse([longAddendum]);
      if (path.endsWith('/corrections')) return jsonResponse(corrections);
      return undefined;
    });
    renderNotes(detail, fetcher);

    const addendum = await screen.findByRole('article', { name: 'Add a correction 1' });
    const body = within(addendum).getByTestId('note-timeline-body');
    mockTimelineDimensions(body, 81);
    fireEvent(window, new Event('resize'));
    await within(addendum).findByRole('button', { name: 'Show more' });
    expect(body).toHaveAttribute('data-collapsed', 'true');
    expect(within(addendum).getByText('Source ID:')).toBeVisible();
    expect(within(addendum).getByText(addendumFixture.id)).toBeVisible();
    expect(within(addendum).getByRole('time')).toHaveAttribute('dateTime', addendumFixture.createdAt);

    const correctionRows = screen.getAllByRole('article', { name: /Correction event/ });
    expect(correctionRows).toHaveLength(2);
    expect(correctionRows.map((row) => row.querySelector('.note-timeline-row__label')?.textContent))
      .toEqual(['Correction event 1 · Mood', 'Correction event 2 · Pain score (0–10)']);
    expect(within(correctionRows[0]).getByText('Recorded by caregiver:')).toBeVisible();
    expect(within(correctionRows[0]).getByText(corrections.events[0].actorId)).toBeVisible();
    expect(within(correctionRows[0]).getByText(corrections.events[0].id)).toBeVisible();
    expect(within(correctionRows[0]).getByText('Previous effective value')).toBeVisible();
    expect(within(correctionRows[0]).getByText('New effective value')).toBeVisible();
    const eventsBefore = correctionRows.map((row) => row.querySelector('.note-timeline-row__label')?.textContent);
    await userEvent.click(within(addendum).getByRole('button', { name: 'Show more' }));
    expect(body).toHaveAttribute('data-collapsed', 'false');
    expect(screen.getAllByRole('article', { name: /Correction event/ })
      .map((row) => row.querySelector('.note-timeline-row__label')?.textContent)).toEqual(eventsBefore);
  });
  it('shows the original note when correction history is unavailable', async () => {
    const fetcher = dataFetcher((path) => path.endsWith('/corrections') ? jsonResponse({}, 500) : undefined);
    renderNotes(detail, fetcher);

    const original = await screen.findByRole('article', { name: 'Original note — read only' });
    expect(within(original).getByText('Unknown')).toBeVisible();
    expect(await screen.findByText('Could not load this information. Try again.')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Retry' })).toBeEnabled();
    expect(screen.queryByText('No structured corrections yet.')).not.toBeInTheDocument();
  });

  it('retries correction history without hiding the original note', async () => {
    let failed = true;
    const fetcher = dataFetcher((path) => {
      if (path.endsWith('/corrections') && failed) { failed = false; return jsonResponse({}, 500); }
      return undefined;
    });
    renderNotes(detail, fetcher);
    await screen.findByRole('article', { name: 'Original note — read only' });
    const errors = await screen.findAllByRole('alert');
    const correctionError = errors.find((element) => element.textContent?.includes('Could not load this information'));
    expect(correctionError).toBeDefined();
    await userEvent.click(within(correctionError as HTMLElement).getByRole('button', { name: 'Retry' }));
    expect(await screen.findByText('No structured corrections yet.')).toBeVisible();
    expect(screen.getByRole('article', { name: 'Original note — read only' })).toBeVisible();
  });

  it('renders correction events in sequence order', async () => {
    const corrections = correctionResponse([
      { field: 'mood', operation: 'SET', value: 'calm' },
      { field: 'painScore', operation: 'SET', value: 3 },
    ]);
    const fetcher = dataFetcher((path) => path.endsWith('/corrections') ? jsonResponse(corrections) : undefined);
    renderNotes(detail, fetcher);
    const events = await screen.findAllByRole('article', { name: /Correction event/ });
    expect(events.map((event) => event.querySelector('.note-timeline-row__label')?.textContent))
      .toEqual(['Correction event 1 · Mood', 'Correction event 2 · Pain score (0–10)']);
    expect(within(events[0]).getByText(corrections.events[0].id)).toBeVisible();
    expect(within(events[0]).getByRole('time')).toHaveAttribute('dateTime', corrections.events[0].createdAt);
  });

  it('links effective values to the source correction event', async () => {
    const corrections = correctionResponse([{ field: 'mood', operation: 'SET', value: 'calm' }]);
    const eventId = corrections.events[0].id;
    corrections.effectiveObservations.mood.eventId = eventId;
    corrections.effectiveObservations.mood.actorId = corrections.events[0].actorId;
    corrections.effectiveObservations.mood.lastOperation = 'SET';
    corrections.effectiveObservations.mood.updatedAt = corrections.events[0].createdAt;
    const fetcher = dataFetcher((path) => path.endsWith('/corrections') ? jsonResponse(corrections) : undefined);
    renderNotes(detail, fetcher);
    const effectiveRegion = await screen.findByRole('region', { name: 'Current effective value' });
    expect(within(effectiveRegion).getByText('calm')).toBeVisible();
    expect(screen.getByRole('link', { name: `Correction event: ${eventId}` })).toHaveAttribute(
      'href', `#correction-event-${noteFixture.id}-${eventId}`,
    );
    expect(document.getElementById(`correction-event-${noteFixture.id}-${eventId}`)).toBeInTheDocument();
  });

  it('renders addenda and correction events in one chronological sequence', async () => {
    const corrections = correctionResponse([
      { field: 'mood', operation: 'SET', value: 'calm' },
      { field: 'painScore', operation: 'SET', value: 3 },
    ]);
    corrections.originalNote = { ...corrections.originalNote, createdAt: '2026-10-18T09:00:00Z' };
    Object.values(corrections.effectiveObservations).forEach((observation) => {
      if (observation.eventId === null) observation.updatedAt = corrections.originalNote.createdAt;
    });
    corrections.events[0].createdAt = '2026-10-18T10:00:00Z';
    corrections.events[0].previousEffective.updatedAt = corrections.originalNote.createdAt;
    corrections.events[0].effectiveAfter.updatedAt = '2026-10-18T10:00:00Z';
    corrections.events[1].createdAt = '2026-10-18T12:00:00Z';
    corrections.events[1].previousEffective.updatedAt = corrections.originalNote.createdAt;
    corrections.events[1].effectiveAfter.updatedAt = '2026-10-18T12:00:00Z';
    const firstAddendum = { ...addendumFixture, id: 'e5555555-5555-4555-8555-555555555551', createdAt: '2026-10-18T11:00:00Z' };
    const secondAddendum = { ...addendumFixture, id: 'e5555555-5555-4555-8555-555555555552', createdAt: '2026-10-18T13:00:00Z' };
    const fetcher = dataFetcher((path) => {
      if (path.endsWith('/addenda')) return jsonResponse([secondAddendum, firstAddendum]);
      if (path.endsWith('/corrections')) return jsonResponse(corrections);
      return undefined;
    });
    renderNotes(detail, fetcher);

    await screen.findByRole('article', { name: /Correction event 2/ });
    const changes = screen.getAllByRole('article', { name: /^(Add a correction|Correction event)/ });
    expect(changes.map((entry) => entry.querySelector('.note-timeline-row__label')?.textContent)).toEqual([
      'Correction event 1 · Mood',
      'Add a correction 1',
      'Correction event 2 · Pain score (0–10)',
      'Add a correction 2',
    ]);
    expect(changes[0].compareDocumentPosition(changes[1]) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(changes[1].compareDocumentPosition(changes[2]) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(changes[2].compareDocumentPosition(changes[3]) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(within(changes[0]).getByText(corrections.events[0].id)).toBeVisible();
    expect(within(changes[2]).getByText(corrections.events[1].id)).toBeVisible();
  });
  it('orders equal timestamps by correction sequence and deterministic addendum ID', async () => {
    const corrections = correctionResponse([
      { field: 'mood', operation: 'SET', value: 'calm' },
      { field: 'painScore', operation: 'SET', value: 3 },
    ]);
    const timestamp = addendumFixture.createdAt;
    corrections.originalNote = { ...corrections.originalNote, createdAt: timestamp };
    Object.values(corrections.effectiveObservations).forEach((observation) => {
      if (observation.eventId === null) observation.updatedAt = timestamp;
    });
    corrections.events.forEach((event) => {
      event.createdAt = timestamp;
      event.previousEffective.updatedAt = timestamp;
      event.effectiveAfter.updatedAt = timestamp;
    });
    const sequenceOne = { ...corrections.events[0], id: 'e5555555-5555-4555-8555-000000000002',
      effectiveAfter: { ...corrections.events[0].effectiveAfter } };
    const sequenceTwo = { ...corrections.events[1], id: 'e5555555-5555-4555-8555-000000000001',
      previousEffective: { ...corrections.events[1].previousEffective },
      effectiveAfter: { ...corrections.events[1].effectiveAfter } };
    corrections.events = [sequenceOne, sequenceTwo];
    sequenceOne.effectiveAfter.eventId = sequenceOne.id;
    sequenceTwo.effectiveAfter.eventId = sequenceTwo.id;
    corrections.effectiveObservations.mood = sequenceOne.effectiveAfter;
    corrections.effectiveObservations.painScore = sequenceTwo.effectiveAfter;
    const addenda = [
      { ...addendumFixture, id: 'e5555555-5555-4555-8555-000000000004', createdAt: timestamp },
      { ...addendumFixture, id: 'e5555555-5555-4555-8555-000000000003', createdAt: timestamp },
    ];
    const fetcher = dataFetcher((path) => path.endsWith('/addenda') ? jsonResponse(addenda)
      : path.endsWith('/corrections') ? jsonResponse(corrections) : undefined);
    renderNotes(detail, fetcher);
    await waitFor(() => expect(screen.queryByRole('status', { name: 'Loading note data…' })).toBeNull());
    await screen.findByRole('article', { name: 'Correction event 2 · Pain score (0–10)' });
    const rows = await screen.findAllByRole('article', { name: /^(Add a correction|Correction event)/ });
    expect(rows.map((row) => row.querySelector('.note-timeline-row__label')?.textContent)).toEqual([
      'Correction event 1 · Mood', 'Correction event 2 · Pain score (0–10)', 'Add a correction 1', 'Add a correction 2',
    ]);
    expect(within(rows[0]).getByText(sequenceOne.id)).toBeVisible();
    expect(within(rows[1]).getByText(sequenceTwo.id)).toBeVisible();
    expect(within(rows[2]).getByText(addenda[1].id)).toBeVisible();
    expect(within(rows[3]).getByText(addenda[0].id)).toBeVisible();
  });
  it('opens a note detail using the backend NoteDetailResponse shape', async () => {
    const fetcher = dataFetcher();
    renderNotes(detail, fetcher);
    expect(await screen.findByRole('article', { name: 'Original note — read only' })).toBeVisible();
    expect(screen.getByText('Locked — original note, read-only')).toBeVisible();
    const originalPane = screen.getByRole('region', { name: 'Original note — read only' });
    const correctionPane = screen.getByRole('heading', { name: 'Correction timeline' }).closest('.note-surface');
    expect(originalPane.compareDocumentPosition(correctionPane as HTMLElement) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(originalPane.querySelectorAll('input, textarea, select, button')).toHaveLength(0);
    expect(fetcher.mock.calls.some(([path]) => path === `/api/notes/${noteFixture.id}`)).toBe(true);
  });

  it('keeps structured corrections available when addenda fail', async () => {
    const corrections = correctionResponse([{ field: 'mood', operation: 'SET', value: 'calm' }]);
    const fetcher = dataFetcher((path) => {
      if (path.endsWith('/addenda')) return jsonResponse({}, 500);
      if (path.endsWith('/corrections')) return jsonResponse(corrections);
      return undefined;
    });
    renderNotes(detail, fetcher);

    expect(await screen.findByRole('article', { name: 'Correction event 1 · Mood' })).toBeVisible();
    expect(screen.getByRole('article', { name: 'Original note — read only' })).toBeVisible();
    expect(screen.getByRole('button', { name: 'Retry' })).toBeEnabled();
  });

  it('keeps addenda available when correction history fails', async () => {
    const fetcher = dataFetcher((path) => {
      if (path.endsWith('/addenda')) return jsonResponse([addendumFixture]);
      if (path.endsWith('/corrections')) return jsonResponse({}, 500);
      return undefined;
    });
    renderNotes(detail, fetcher);
    expect(await screen.findByRole('article', { name: 'Original note — read only' })).toBeVisible();
    expect(await screen.findByRole('article', { name: 'Add a correction 1' })).toBeVisible();
    expect(await screen.findByText('Could not load this information. Try again.')).toBeVisible();
  });

  it('keeps original and legacy addendum values distinct in chronological order', async () => {
    const older = { ...addendumFixture, id: 'e5555555-5555-4555-8555-555555555551', createdAt: '2026-10-18T12:00:00Z',
      providedFields: ['mood', ...addendumFixture.providedFields],
      observations: { ...addendumFixture.observations, mood: { state: 'LEGACY_AMBIGUOUS' as const, value: null, rawValue: 'legacy mood' } } };
    const newer = { ...addendumFixture, id: 'e5555555-5555-4555-8555-555555555552', createdAt: '2026-10-19T12:00:00Z' };
    const fetcher = dataFetcher((path) => path.endsWith('/addenda') ? jsonResponse([newer, older]) : undefined);
    renderNotes(detail, fetcher);
    const original = await screen.findByRole('article', { name: 'Original note — read only' });
    const addenda = await screen.findAllByRole('article', { name: /Add a correction/ });
    expect(within(original).getByText('6 hours')).toBeVisible();
    expect(addenda.map((item) => item.querySelector('.note-timeline-row__label')?.textContent))
      .toEqual(['Add a correction 1', 'Add a correction 2']);
    expect(within(addenda[0]).getByText('Legacy (ambiguous)')).toBeVisible();
    expect(addenda[0].compareDocumentPosition(addenda[1]) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });


  it('protects direct note/addendum URLs before any API request', async () => {
    const fetcher = vi.fn();
    renderNotes(`${detail}/addenda/new`, fetcher, 'en', false);
    expect(await screen.findByRole('heading', { name: 'Caregiver sign in' })).toBeInTheDocument();
    expect(fetcher).not.toHaveBeenCalled();
  });
  it('handles loading and the empty-recipient state', async () => {
    let resolve!: (response: Response) => void;
    renderNotes(`${root}/new`, vi.fn().mockImplementation(() => new Promise<Response>((done) => { resolve = done; })));
    expect(await screen.findByText('Loading note data…')).toBeInTheDocument();
    resolve(jsonResponse([]));
    expect(await screen.findByText('No care recipients available.')).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: 'Care recipients' }).at(-1)).toHaveAttribute('href', '/app/recipients');
  });
  it('retries a loading error without exposing backend details', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(jsonResponse({ message: 'private diagnostics' }, 500)).mockResolvedValueOnce(jsonResponse([recipientFixture]));
    renderNotes(`${root}/new`, fetcher);
    expect(await screen.findByRole('alert')).not.toHaveTextContent('private diagnostics');
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }));
    expect(await screen.findByLabelText('Care recipient')).toBeInTheDocument();
  });
  it('prefills recipient and calendar date from a workflow return URL', async () => {
    const fetcher = dataFetcher();
    renderNotes(`${root}/new?recipientId=${recipientFixture.id}&date=${noteFixture.date}`, fetcher);
    expect(await screen.findByLabelText('Care recipient')).toHaveValue(recipientFixture.id);
    expect(screen.getByLabelText('Observation date')).toHaveValue(noteFixture.date);
    expect(fetcher.mock.calls.filter(([, init]) => init.method === 'POST')).toHaveLength(0);
  });
  it('validates required metadata, focuses its first field, and makes no write', async () => {
    const fetcher = dataFetcher();
    renderNotes(`${root}/new`, fetcher);
    await screen.findByLabelText('Care recipient');
    fireEvent.change(screen.getByLabelText('Observation date'), { target: { value: '' } });
    await userEvent.click(screen.getByRole('button', { name: 'Save original note' }));
    expect(screen.getByLabelText('Care recipient')).toHaveFocus();
    expect(screen.getByLabelText('Observation date')).toHaveAttribute('aria-invalid', 'true');
    expect(fetcher.mock.calls.filter(([, init]) => init.method !== 'GET')).toHaveLength(0);
  });
  it('submits state/value envelopes and mixed free text unchanged, then shows a read-only original', async () => {
    let saved: Record<string, unknown> | null = null;
    const fetcher = dataFetcher((path, init) => {
      if (path === '/api/notes' && init.method === 'POST') { saved = JSON.parse(init.body as string); return jsonResponse(noteListFixture, 201); }
    });
    renderNotes(`${root}/new`, fetcher);
    await metadata();
    await selectMoodChip('Calm');
    await selectAppetiteChip('Poor');
    await selectSleepChip('Did not sleep');
    await selectMobilityChip('Independent');
    await selectMedicationChip('Unknown');
    await selectVerificationChip('Unverified');
    await selectPainScoreChip('0');
    await selectFallChip('No');
    await userEvent.click(screen.getByRole('button', { name: 'Add details' }));
    fireEvent.change(screen.getByRole('textbox', { name: 'Free-text observations' }), { target: { value: noteObservations.freeText } });
    await userEvent.click(screen.getByRole('button', { name: 'Save original note' }));
    expect(await screen.findByText('Original note saved.')).toHaveFocus();
    expect(saved).toEqual({
      recipientId: recipientFixture.id,
      date: noteFixture.date,
      observations: {
        mood: { state: 'RECORDED', value: 'CALM' },
        appetite: { state: 'RECORDED', value: 'POOR' },
        sleep: { state: 'RECORDED', value: 'DID_NOT_SLEEP' },
        mobility: { state: 'RECORDED', value: 'INDEPENDENT' },
        medicationStatus: { state: 'UNKNOWN', value: null },
        painScore: { state: 'RECORDED', value: 0 },
        fallReported: { state: 'RECORDED', value: false },
        freeText: { state: 'RECORDED', value: noteObservations.freeText },
      },
      medicationVerification: 'UNVERIFIED',
    });
    expect(screen.getByRole('article', { name: 'Original note — read only' })).toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    expect(screen.getByText((_, element) => element?.classList.contains('obs-display-value') === true
      && element.textContent?.includes('غير متأكد من الدواء') === true)?.textContent).toBe(noteObservations.freeText);
    expect(screen.getByRole('article', { name: 'Original note — read only' })).toBeVisible();
    expect(document.querySelector('script')).not.toBeInTheDocument();
  }, 10000);
  it('blocks duplicate clicks and safely reuses the identical payload/key after a lost response', async () => {
    const posts: RequestInit[] = [];
    let reject!: (error: unknown) => void;
    const fetcher = dataFetcher((path, init) => {
      if (path === '/api/notes' && init.method === 'POST') {
        posts.push(init);
        return posts.length === 1 ? new Promise<Response>((_, fail) => { reject = fail; }) : jsonResponse(noteListFixture, 201);
      }
    });
    renderNotes(`${root}/new`, fetcher);
    await metadata();
    const form = screen.getByRole('form', { name: 'Record a daily note' });
    fireEvent.submit(form);
    fireEvent.submit(form);
    expect(posts).toHaveLength(1);
    expect(screen.getByRole('button', { name: 'Saving…' })).toBeDisabled();
    reject(new TypeError('response lost'));
    expect(await screen.findByRole('alert')).toHaveTextContent(/could not confirm/i);
    expect(screen.getByRole('button', { name: 'Add details' })).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'Retry the same save' }));
    await screen.findByText('Original note saved.');
    expect(posts).toHaveLength(2);
    expect(posts[1].body).toBe(posts[0].body);
    expect(new Headers(posts[1].headers).get('Idempotency-Key')).toBe(new Headers(posts[0].headers).get('Idempotency-Key'));
  });
  it('keeps values editable after explicit backend validation failure and reuses the key for an unchanged retry', async () => {
    const posts: RequestInit[] = [];
    const fetcher = dataFetcher((path, init) => {
      if (path === '/api/notes' && init.method === 'POST') {
        posts.push(init);
        return jsonResponse(posts.length === 1 ? { message: 'private validation' } : noteListFixture, posts.length === 1 ? 400 : 201);
      }
    });
    renderNotes(`${root}/new`, fetcher);
    await metadata();
    await userEvent.click(screen.getByRole('button', { name: 'Add details' }));
    fireEvent.change(screen.getByRole('textbox', { name: 'Free-text observations' }), { target: { value: 'القيمة الأصلية' } });
    await userEvent.click(screen.getByRole('button', { name: 'Save original note' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Review the entered values');
    await waitFor(() => expect(screen.getByRole('textbox', { name: 'Free-text observations' })).toBeEnabled());
    expect(screen.getByRole('textbox', { name: 'Free-text observations' })).toBeEnabled();
    expect(screen.getByRole('textbox', { name: 'Free-text observations' })).toHaveValue('القيمة الأصلية');
    expect(screen.queryByText('private validation')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Save original note' }));
    await screen.findByText('Original note saved.');
    expect(posts).toHaveLength(2);
    expect(posts[1].body).toBe(posts[0].body);
    expect(new Headers(posts[1].headers).get('Idempotency-Key')).toBe(new Headers(posts[0].headers).get('Idempotency-Key'));
  });
  it('opens the existing note after a recipient/date conflict without another POST', async () => {
    const fetcher = dataFetcher((path, init) => path === '/api/notes' && init.method === 'POST' ? jsonResponse({ code: 'NOTE_CONFLICT' }, 409) : undefined);
    renderNotes(`${root}/new`, fetcher);
    await metadata();
    await userEvent.click(screen.getByRole('button', { name: 'Save original note' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('conflicts with an existing record');
    await userEvent.click(screen.getByRole('button', { name: 'Open note for this date' }));
    await screen.findByRole('heading', { name: 'Daily note details' });
    await screen.findByRole('heading', { name: 'Original note — read only' });
    expect(fetcher.mock.calls.filter(([, init]) => init.method === 'POST')).toHaveLength(1);
  });
  it('can open an inactive recipient’s existing note but cannot create one', async () => {
    const inactiveRecipient = {
      ...recipientFixture,
      active: false,
      lifecycleStatus: 'ARCHIVED' as const,
      archivedAt: '2026-10-15T10:00:00Z',
      archivedBy: 'b1111111-1111-4111-8111-111111111111',
    };
    renderNotes(`${root}/new`, dataFetcher((path) => {
      if (path.startsWith('/api/recipients/')) return jsonResponse(inactiveRecipient);
      if (path.startsWith('/api/recipients')) return jsonResponse([inactiveRecipient]);
    }));
    await metadata();
    expect(screen.getByRole('button', { name: 'Save original note' })).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'Open note for this date' }));
    expect(await screen.findByRole('heading', { name: 'Original note — read only' })).toBeInTheDocument();
  });
  it('reports a missing note on lookup and retains the selected metadata', async () => {
    renderNotes(`${root}/new`, dataFetcher((path) => path.includes('/by-recipient/') ? jsonResponse({}, 404) : undefined));
    await metadata();
    await userEvent.click(screen.getByRole('button', { name: 'Open note for this date' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('No note was found');
    expect(screen.getByLabelText('Observation date')).toHaveValue(noteFixture.date);
  });
  it('records only selected correction fields and leaves the original immutable', async () => {
    const writes: RequestInit[] = [];
    const careDataChanged = vi.fn();
    window.addEventListener(CARE_DATA_CHANGED_EVENT, careDataChanged);
    let appended = false;
    const fetcher = dataFetcher((path, init) => {
      if (path.endsWith('/addenda') && init.method === 'POST') { appended = true; writes.push(init); return jsonResponse(addendumFixture, 201); }
      if (path.endsWith('/addenda')) return jsonResponse(appended ? [addendumFixture] : []);
    });
    renderNotes(`${detail}/addenda/new`, fetcher);
    const form = await screen.findByRole('form', { name: 'Add a correction' });
    await userEvent.click(within(form).getByRole('button', { name: 'Save addendum' }));
    expect(screen.getByRole('checkbox', { name: 'Correct this field: Mood' })).toHaveFocus();
    expect(writes).toHaveLength(0);
    await userEvent.click(screen.getByRole('checkbox', { name: 'Correct this field: Medication taken' }));
    await userEvent.click(within(screen.getByRole('radiogroup', { name: 'Medication taken' })).getByRole('radio', { name: 'Missed' }));
    await userEvent.click(screen.getByRole('checkbox', { name: 'Correct this field: Medication report verified' }));
    await userEvent.selectOptions(screen.getByLabelText('Medication report verified', { exact: true }), 'UNVERIFIED');
    await userEvent.click(screen.getByRole('checkbox', { name: 'Correct this field: Free-text observations' }));
    await setRecorded('Free-text observations');
    fireEvent.change(screen.getByRole('textbox', { name: 'Free-text observations' }), { target: { value: addendumFixture.freeText } });
    await userEvent.click(screen.getByRole('button', { name: 'Save addendum' }));
    expect(await screen.findByText('Addendum saved. The original note is unchanged.')).toHaveFocus();
    expect(careDataChanged).toHaveBeenCalledTimes(1);
    window.removeEventListener(CARE_DATA_CHANGED_EVENT, careDataChanged);
    expect(JSON.parse(writes[0].body as string)).toEqual({
      observations: {
        medicationStatus: { state: 'RECORDED', value: 'MISSED' },
        freeText: { state: 'RECORDED', value: addendumFixture.freeText },
      },
      medicationVerification: 'UNVERIFIED',
    });
    const original = screen.getByRole('article', { name: 'Original note — read only' });
    expect(within(original).getByText('Unknown')).toBeInTheDocument();
    expect(screen.getByText('Missed')).toBeInTheDocument();
    expect(fetcher.mock.calls.some(([, init]) => init.method === 'PUT' || init.method === 'PATCH' || init.method === 'DELETE')).toBe(false);
  });
  it('creates explicit typed correction operations and keeps the original note visible', async () => {
    let posted: Record<string, unknown> | null = null;
    let storedHistory: unknown = correctionHistoryFixture();
    const fetcher = dataFetcher((path, init) => {
      if (path.endsWith('/corrections') && init.method === 'POST') {
        const submitted = JSON.parse(init.body as string) as { corrections: FieldCorrection[] };
        posted = submitted as Record<string, unknown>;
        storedHistory = correctionResponse(submitted.corrections);
        return jsonResponse(storedHistory, 201);
      }
      if (path.endsWith('/corrections')) return jsonResponse(storedHistory);
    });
    renderNotes(detail, fetcher);
    await screen.findByRole('heading', { name: 'Original note — read only' });
    await userEvent.click(await screen.findByRole('button', { name: 'Add structured correction' }));
    const mood = within(screen.getByRole('group', { name: 'Mood' }));
    await userEvent.selectOptions(mood.getByLabelText('Correction operation'), 'SET');
    fireEvent.change(mood.getByLabelText('New value'), { target: { value: '' } });
    const pain = within(screen.getByRole('group', { name: 'Pain score (0–10)' }));
    await userEvent.selectOptions(pain.getByLabelText('Correction operation'), 'SET');
    fireEvent.change(pain.getByLabelText('New value'), { target: { value: '0' } });
    const fall = within(screen.getByRole('group', { name: 'Fall reported' }));
    await userEvent.selectOptions(fall.getByLabelText('Correction operation'), 'SET');
    await userEvent.selectOptions(fall.getByLabelText('New value'), 'false');
    await userEvent.selectOptions(within(screen.getByRole('group', { name: 'Appetite' })).getByLabelText('Correction operation'), 'UNKNOWN');
    await userEvent.selectOptions(within(screen.getByRole('group', { name: 'Sleep' })).getByLabelText('Correction operation'), 'CLEAR');
    await userEvent.selectOptions(within(screen.getByRole('group', { name: 'Mobility' })).getByLabelText('Correction operation'), 'NOT_RECORDED');
    await userEvent.click(screen.getByRole('button', { name: 'Save corrections' }));
    await screen.findByText('Structured corrections saved. The original note is unchanged.');
    expect(posted).toEqual({ corrections: [
      { field: 'mood', operation: 'SET', value: '' }, { field: 'appetite', operation: 'UNKNOWN' },
      { field: 'sleep', operation: 'CLEAR' }, { field: 'mobility', operation: 'NOT_RECORDED' },
      { field: 'painScore', operation: 'SET', value: 0 }, { field: 'fallReported', operation: 'SET', value: false },
    ] });
    const original = screen.getByRole('article', { name: 'Original note — read only' });
    expect(within(original).getByText('6 hours')).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Correction event 1 · Mood' })).toBeInTheDocument();
    expect(await screen.findByText('Set to Recorded as empty')).toBeVisible();
    expect(fetcher.mock.calls.some(([, init]) => ['PUT', 'PATCH', 'DELETE'].includes(init.method ?? 'GET'))).toBe(false);
    expect(fetcher.mock.calls.filter(([, init]) => init.method === 'POST')).toHaveLength(1);
  });
  it('localizes the correction editor and keeps Arabic RTL layout', async () => {
    renderNotes(detail, dataFetcher(), 'ar');
    await screen.findByRole('heading', { name: 'الملاحظة الأصلية — للقراءة فقط' });
    await userEvent.click(screen.getByRole('button', { name: 'إضافة تصحيح منظّم' }));
    expect(document.documentElement.dir).toBe('rtl');
    expect(screen.getByRole('form', { name: 'محرّر التصحيح المنظّم' })).toBeInTheDocument();
    expect(screen.getAllByLabelText('عملية التصحيح')).toHaveLength(8);
  });
  it('validates SET values before writing and keeps zero valid', async () => {
    const fetcher = dataFetcher();
    renderNotes(detail, fetcher);
    await screen.findByRole('heading', { name: 'Original note — read only' });
    await userEvent.click(await screen.findByRole('button', { name: 'Add structured correction' }));
    const pain = within(screen.getByRole('group', { name: 'Pain score (0–10)' }));
    await userEvent.selectOptions(pain.getByLabelText('Correction operation'), 'SET');
    fireEvent.change(pain.getByLabelText('New value'), { target: { value: '11' } });
    await userEvent.click(screen.getByRole('button', { name: 'Save corrections' }));
    expect(await screen.findByText('Enter a whole number from 0 to 10.')).toBeInTheDocument();
    expect(fetcher.mock.calls.some(([path, init]) => path.endsWith('/corrections') && init.method === 'POST')).toBe(false);
    fireEvent.change(pain.getByLabelText('New value'), { target: { value: '0' } });
    expect(pain.getByLabelText('New value')).toHaveValue(0);
  });
  it('blocks duplicate submissions and safely retries the same correction batch', async () => {
    const posts: RequestInit[] = [];
    let reject!: (error: unknown) => void;
    const fetcher = dataFetcher((path, init) => {
      if (path.endsWith('/corrections') && init.method === 'POST') {
        posts.push(init);
        return posts.length === 1 ? new Promise<Response>((_, fail) => { reject = fail; }) : jsonResponse(correctionResponse(), 201);
      }
    });
    renderNotes(detail, fetcher);
    await screen.findByRole('heading', { name: 'Original note — read only' });
    await userEvent.click(await screen.findByRole('button', { name: 'Add structured correction' }));
    const mood = within(screen.getByRole('group', { name: 'Mood' }));
    await userEvent.selectOptions(mood.getByLabelText('Correction operation'), 'SET');
    fireEvent.change(mood.getByLabelText('New value'), { target: { value: '' } });
    const form = screen.getByRole('form', { name: 'Structured correction editor' });
    fireEvent.submit(form);
    fireEvent.submit(form);
    expect(posts).toHaveLength(1);
    expect(screen.getByRole('button', { name: 'Saving corrections…' })).toBeDisabled();
    reject(new TypeError('response lost'));
    expect(await screen.findByRole('alert')).toHaveTextContent('Corrections could not be saved');
    await userEvent.click(screen.getByRole('button', { name: 'Save corrections' }));
    await screen.findByText('Structured corrections saved. The original note is unchanged.');
    expect(posts).toHaveLength(2);
    expect(posts[1].body).toBe(posts[0].body);
    expect(new Headers(posts[1].headers).get('Idempotency-Key')).toBe(new Headers(posts[0].headers).get('Idempotency-Key'));
  });
  it('requires a chip choice for a ticked chip field', async () => {
    const fetcher = dataFetcher();
    renderNotes(`${detail}/addenda/new`, fetcher);
    await screen.findByRole('form', { name: 'Add a correction' });
    await userEvent.click(screen.getByRole('checkbox', { name: 'Correct this field: Medication taken' }));
    await userEvent.click(screen.getByRole('button', { name: 'Save addendum' }));
    expect(screen.getByRole('alert')).toHaveTextContent('Choose one option for this field.');
    expect(document.getElementById('note-medicationStatus-state')).toHaveFocus();
    expect(fetcher.mock.calls.some(([path, init]) => path.endsWith('/addenda') && init.method === 'POST')).toBe(false);
  });
  it('renders Arabic/RTL and mixed text safely without altering its content', async () => {
    renderNotes(detail, dataFetcher(), 'ar');
    await screen.findByRole('heading', { name: 'الملاحظة الأصلية — للقراءة فقط' });
    expect(document.documentElement).toHaveAttribute('dir', 'rtl');
    const text = screen.getByText(/غير متأكد من الدواء/);
    expect(text).toHaveAttribute('dir', 'auto');
    expect(text.textContent).toBe(noteObservations.freeText);
    expect(screen.getByText('غير معروف')).toBeInTheDocument();
  });
  it('handles unavailable note details generically', async () => {
    renderNotes(detail, vi.fn().mockResolvedValue(jsonResponse({ message: 'owner private' }, 404)));
    expect(await screen.findByRole('alert')).toHaveTextContent(/not available/i);
    expect(screen.queryByText('owner private')).not.toBeInTheDocument();
  });
  it('cancels abandoned requests and does not publish a late successful save', async () => {
    let signal!: AbortSignal;
    let resolve!: (value: Response) => void;
    const fetcher = dataFetcher((path, init) => {
      if (path === '/api/notes' && init.method === 'POST') { signal = init.signal!; return new Promise<Response>((done) => { resolve = done; }); }
    });
    const view = renderNotes(`${root}/new`, fetcher);
    await metadata();
    fireEvent.submit(screen.getByRole('form', { name: 'Record a daily note' }));
    view.unmount();
    expect(signal.aborted).toBe(true);
    resolve(jsonResponse(noteListFixture, 201));
    await waitFor(() => expect(fetcher.mock.calls.filter(([, init]) => init.method === 'POST')).toHaveLength(1));
  });
  it('creates explicit correction events without changing the canonical original observation states', async () => {
    let posted: FieldCorrection[] = [];
    let storedHistory: unknown = correctionHistoryFixture();
    const fetcher = dataFetcher((path, init) => {
      if (path.endsWith('/corrections') && init.method === 'POST') {
        posted = JSON.parse(init.body as string).corrections as FieldCorrection[];
        storedHistory = correctionResponse(posted);
        return jsonResponse(storedHistory, 201);
      }
      if (path.endsWith('/corrections')) return jsonResponse(storedHistory);
    });
    renderNotes(detail, fetcher);
    const original = await screen.findByRole('article', { name: 'Original note — read only' });
    await userEvent.click(screen.getByRole('button', { name: 'Add structured correction' }));
    const mood = within(screen.getByRole('group', { name: 'Mood' }));
    await userEvent.selectOptions(mood.getByLabelText('Correction operation'), 'SET');
    fireEvent.change(mood.getByLabelText('New value'), { target: { value: '' } });
    const pain = within(screen.getByRole('group', { name: 'Pain score (0–10)' }));
    await userEvent.selectOptions(pain.getByLabelText('Correction operation'), 'SET');
    fireEvent.change(pain.getByLabelText('New value'), { target: { value: '0' } });
    const fall = within(screen.getByRole('group', { name: 'Fall reported' }));
    await userEvent.selectOptions(fall.getByLabelText('Correction operation'), 'SET');
    await userEvent.selectOptions(fall.getByLabelText('New value'), 'false');
    const appetite = within(screen.getByRole('group', { name: 'Appetite' }));
    await userEvent.selectOptions(appetite.getByLabelText('Correction operation'), 'UNKNOWN');
    expect(appetite.queryByLabelText('New value')).not.toBeInTheDocument();
    await userEvent.selectOptions(within(screen.getByRole('group', { name: 'Sleep' })).getByLabelText('Correction operation'), 'CLEAR');
    await userEvent.selectOptions(within(screen.getByRole('group', { name: 'Mobility' })).getByLabelText('Correction operation'), 'NOT_RECORDED');
    await userEvent.click(screen.getByRole('button', { name: 'Save corrections' }));
    expect(await screen.findByText('Structured corrections saved. The original note is unchanged.')).toHaveFocus();
    expect(posted).toEqual([
      { field: 'mood', operation: 'SET', value: '' }, { field: 'appetite', operation: 'UNKNOWN' },
      { field: 'sleep', operation: 'CLEAR' }, { field: 'mobility', operation: 'NOT_RECORDED' },
      { field: 'painScore', operation: 'SET', value: 0 }, { field: 'fallReported', operation: 'SET', value: false },
    ]);
    expect(within(original).getByText('6 hours')).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Correction event 1 · Mood' })).toBeInTheDocument();
    expect(screen.getByText('Set to Recorded as empty')).toBeInTheDocument();
  });
  it('localizes the structured correction editor in Arabic RTL', async () => {
    renderNotes(detail, dataFetcher(), 'ar');
    await screen.findByRole('heading', { name: 'الملاحظة الأصلية — للقراءة فقط' });
    await userEvent.click(screen.getByRole('button', { name: 'إضافة تصحيح منظّم' }));
    expect(document.documentElement.dir).toBe('rtl');
    expect(screen.getByRole('form', { name: 'محرّر التصحيح المنظّم' })).toBeInTheDocument();
    expect(screen.getAllByLabelText('عملية التصحيح')).toHaveLength(8);
  });
  it('blocks duplicate correction submissions and safely retries with the same key', async () => {
    const posts: RequestInit[] = [];
    let reject!: (error: unknown) => void;
    const fetcher = dataFetcher((path, init) => {
      if (path.endsWith('/corrections') && init.method === 'POST') {
        posts.push(init);
        return posts.length === 1 ? new Promise<Response>((_, fail) => { reject = fail; })
          : jsonResponse(correctionResponse(JSON.parse(init.body as string).corrections), 201);
      }
    });
    renderNotes(detail, fetcher);
    await screen.findByRole('heading', { name: 'Original note — read only' });
    await userEvent.click(screen.getByRole('button', { name: 'Add structured correction' }));
    const mood = within(screen.getByRole('group', { name: 'Mood' }));
    await userEvent.selectOptions(mood.getByLabelText('Correction operation'), 'SET');
    fireEvent.change(mood.getByLabelText('New value'), { target: { value: '' } });
    const form = screen.getByRole('form', { name: 'Structured correction editor' });
    fireEvent.submit(form);
    fireEvent.submit(form);
    expect(posts).toHaveLength(1);
    expect(screen.getByRole('button', { name: 'Saving corrections…' })).toBeDisabled();
    reject(new TypeError('response lost'));
    expect(await screen.findByRole('alert')).toHaveTextContent('Corrections could not be saved');
    await userEvent.click(screen.getByRole('button', { name: 'Save corrections' }));
    await screen.findByText('Structured corrections saved. The original note is unchanged.');
    expect(posts).toHaveLength(2);
    expect(posts[1].body).toBe(posts[0].body);
    expect(new Headers(posts[1].headers).get('Idempotency-Key')).toBe(new Headers(posts[0].headers).get('Idempotency-Key'));
  });
});

describe('not-recorded rows in the saved note view', () => {
  const notRecorded = { state: 'NOT_RECORDED' as const };

  function observationsWith(patch: Partial<NoteObservations>): NoteObservations {
    return { ...structuredObservations, ...patch };
  }

  async function renderOriginalWith(observations: NoteObservations) {
    const fetcher = dataFetcher((path) =>
      path === `/api/notes/${noteFixture.id}`
        ? jsonResponse({ ...noteDetailFixture, observations })
        : undefined);
    renderNotes(detail, fetcher);
    return screen.findByRole('article', { name: 'Original note — read only' });
  }

  it('hides a NOT_RECORDED row but keeps RECORDED rows', async () => {
    const original = await renderOriginalWith(observationsWith({
      appetite: notRecorded,
    }));
    expect(within(original).getByText('Mood')).toBeVisible();
    expect(within(original).queryByText('Appetite')).not.toBeInTheDocument();
  });

  it('keeps an UNKNOWN row with the Unknown label', async () => {
    const original = await renderOriginalWith(observationsWith({
      mood: { state: 'UNKNOWN' },
      medicationStatus: { state: 'RECORDED', value: 'TAKEN' },
    }));
    const row = within(original).getByText('Mood').closest('.note-field-row') as HTMLElement;
    expect(within(row).getByText('Unknown')).toBeVisible();
    expect(row.querySelector('.obs-display-state')).toHaveAttribute('data-state', 'UNKNOWN');
  });

  it('shows notesNothingRecorded when every field is NOT_RECORDED', async () => {
    const allHidden = Object.fromEntries(
      observationFields.map((field) => [field, notRecorded]),
    ) as unknown as NoteObservations;
    const original = await renderOriginalWith({
      ...allHidden,
      medicationVerification: 'NOT_ASSERTED',
    });
    expect(await within(original).findByText('Nothing recorded for this note.')).toBeVisible();
    expect(original.querySelectorAll('dt')).toHaveLength(0);
    expect(within(original).queryByText('Mood')).not.toBeInTheDocument();
    expect(within(original).queryByText('Medication report verified')).not.toBeInTheDocument();
  });

  it('keeps a LEGACY_AMBIGUOUS row', async () => {
    const original = await renderOriginalWith(observationsWith({
      sleep: { state: 'LEGACY_AMBIGUOUS', value: null, rawValue: 'old sleep value' },
    }));
    expect(within(original).getByText('Legacy (ambiguous)')).toBeVisible();
    expect(within(original).getByText('old sleep value')).toBeVisible();
  });

  it('renders NOT_RECORDED rows in correction mode', () => {
    render(
      <I18nProvider>
        <ObservationValues
          correction
          providedFields={['appetite']}
          values={observationsWith({ appetite: notRecorded })}
        />
      </I18nProvider>,
    );
    expect(screen.getByText('Appetite')).toBeVisible();
    expect(screen.getByText('Not recorded')).toBeVisible();
  });

  it('still shows an explicit NOT_RECORDED correction event in the timeline', async () => {
    const corrections = correctionResponse([
      { field: 'mood', operation: 'NOT_RECORDED' },
    ]);
    const fetcher = dataFetcher((path) => path.endsWith('/corrections') ? jsonResponse(corrections) : undefined);
    renderNotes(detail, fetcher);
    const events = await screen.findAllByRole('article', { name: /Correction event/ });
    expect(events).toHaveLength(1);
    expect(events[0].querySelector('.note-timeline-row__label')?.textContent)
      .toBe('Correction event 1 · Mood');
    expect(within(events[0]).getByText(corrections.events[0].id)).toBeVisible();
  });
});

describe('medication status chips on the create form', () => {
  async function createWithChip(chip: 'Taken' | 'Missed' | 'Unknown', deselect = false) {
    let submitted: Record<string, unknown> | null = null;
    const fetcher = dataFetcher((path, init) => {
      if (path === '/api/notes' && init.method === 'POST') {
        submitted = JSON.parse(init.body as string);
        return jsonResponse(noteListFixture, 201);
      }
    });
    renderNotes(`${root}/new`, fetcher);
    await metadata();
    await selectMedicationChip(chip);
    if (deselect) await selectMedicationChip(chip);
    await userEvent.click(screen.getByRole('button', { name: 'Save original note' }));
    await screen.findByText('Original note saved.');
    return submitted as unknown as { observations: Record<string, { state: string; value?: unknown }> };
  }

  it('renders Taken, Missed and Unknown chips in create mode', async () => {
    renderNotes(`${root}/new`, dataFetcher());
    const group = await screen.findByRole('radiogroup', { name: 'Medication taken' });
    expect(within(group).getAllByRole('radio').map((chip) => chip.textContent))
      .toEqual(['Taken', 'Missed', 'Unknown']);
  });

  it('submits RECORDED TAKEN when the Taken chip is selected', async () => {
    const submitted = await createWithChip('Taken');
    expect(submitted.observations.medicationStatus).toEqual({ state: 'RECORDED', value: 'TAKEN' });
  });

  it('returns to NOT_RECORDED when the selected chip is clicked again', async () => {
    const submitted = await createWithChip('Taken', true);
    expect(submitted.observations.medicationStatus).toEqual({ state: 'NOT_RECORDED', value: null });
  });

  it('submits RECORDED MISSED when the Missed chip is selected', async () => {
    const submitted = await createWithChip('Missed');
    expect(submitted.observations.medicationStatus).toEqual({ state: 'RECORDED', value: 'MISSED' });
  });

  it('submits UNKNOWN with a null value when the Unknown chip is selected', async () => {
    const submitted = await createWithChip('Unknown');
    expect(submitted.observations.medicationStatus).toEqual({ state: 'UNKNOWN', value: null });
  });

  it('renders medication status chips with nothing selected in append mode', async () => {
    renderNotes(`${detail}/addenda/new`, dataFetcher());
    await screen.findByRole('form', { name: 'Add a correction' });
    await userEvent.click(screen.getByRole('checkbox', { name: 'Correct this field: Medication taken' }));
    const group = screen.getByRole('radiogroup', { name: 'Medication taken' });
    expect(within(group).getAllByRole('radio').map((chip) => chip.textContent)).toEqual(['Taken', 'Missed', 'Unknown', 'Clear']);
    expect(within(group).queryByRole('radio', { checked: true })).not.toBeInTheDocument();
    expect(screen.queryByRole('group', { name: 'Medication taken — Observation status' })).not.toBeInTheDocument();
  });

  it('keeps ObservationStateField out of the create form', async () => {
    renderNotes(`${root}/new`, dataFetcher());
    await screen.findByRole('button', { name: 'Add details' });
    expect(screen.queryByRole('group', { name: /Observation status/ })).not.toBeInTheDocument();
  });
});

describe('medication verification chips on the create form', () => {
  async function createWithVerification(chip?: 'Verified' | 'Unverified', deselect = false) {
    let submitted: Record<string, unknown> | null = null;
    const fetcher = dataFetcher((path, init) => {
      if (path === '/api/notes' && init.method === 'POST') {
        submitted = JSON.parse(init.body as string);
        return jsonResponse(noteListFixture, 201);
      }
    });
    renderNotes(`${root}/new`, fetcher);
    await metadata();
    if (chip) {
      await selectVerificationChip(chip);
      if (deselect) await selectVerificationChip(chip);
    }
    await userEvent.click(screen.getByRole('button', { name: 'Save original note' }));
    await screen.findByText('Original note saved.');
    return submitted as unknown as { medicationVerification: string };
  }

  it('renders Verified and Unverified chips in create mode', async () => {
    renderNotes(`${root}/new`, dataFetcher());
    const group = await screen.findByRole('radiogroup', { name: 'Medication report verified' });
    expect(within(group).getAllByRole('radio').map((chip) => chip.textContent))
      .toEqual(['Verified', 'Unverified']);
  });

  it('sends NOT_ASSERTED when nothing is chosen', async () => {
    const submitted = await createWithVerification();
    expect(submitted.medicationVerification).toBe('NOT_ASSERTED');
  });

  it('sends VERIFIED when the Verified chip is selected', async () => {
    const submitted = await createWithVerification('Verified');
    expect(submitted.medicationVerification).toBe('VERIFIED');
  });

  it('sends UNVERIFIED when the Unverified chip is selected', async () => {
    const submitted = await createWithVerification('Unverified');
    expect(submitted.medicationVerification).toBe('UNVERIFIED');
  });

  it('returns to NOT_ASSERTED when the selected chip is clicked again', async () => {
    const submitted = await createWithVerification('Verified', true);
    expect(submitted.medicationVerification).toBe('NOT_ASSERTED');
  });

  it('keeps the three-option select in append mode', async () => {
    renderNotes(`${detail}/addenda/new`, dataFetcher());
    await screen.findByRole('form', { name: 'Add a correction' });
    await userEvent.click(screen.getByRole('checkbox', { name: 'Correct this field: Medication report verified' }));
    const select = screen.getByRole('combobox', { name: 'Medication report verified' });
    expect(within(select).getAllByRole('option').map((option) => option.textContent))
      .toEqual(['Not asserted', 'Verified', 'Unverified']);
    expect(screen.queryByRole('radiogroup', { name: 'Medication report verified' })).not.toBeInTheDocument();
  });

  it('keeps the note-medicationVerification element id in create mode', async () => {
    renderNotes(`${root}/new`, dataFetcher());
    await screen.findByRole('radiogroup', { name: 'Medication report verified' });
    expect(document.getElementById('note-medicationVerification')).not.toBeNull();
    expect(document.getElementById('note-medicationVerification-hint')).not.toBeNull();
  });
});

describe('fall reported chips on the create form', () => {
  async function createWithFall(chip?: 'Yes' | 'No' | 'Unknown', deselect = false) {
    let submitted: Record<string, unknown> | null = null;
    const fetcher = dataFetcher((path, init) => {
      if (path === '/api/notes' && init.method === 'POST') {
        submitted = JSON.parse(init.body as string);
        return jsonResponse(noteListFixture, 201);
      }
    });
    renderNotes(`${root}/new`, fetcher);
    await metadata();
    if (chip) {
      await selectFallChip(chip);
      if (deselect) await selectFallChip(chip);
    }
    await userEvent.click(screen.getByRole('button', { name: 'Save original note' }));
    await screen.findByText('Original note saved.');
    return submitted as unknown as { observations: { fallReported: { state: string; value?: unknown } } };
  }

  it('renders Yes, No and Unknown chips in create mode', async () => {
    renderNotes(`${root}/new`, dataFetcher());
    const group = await screen.findByRole('radiogroup', { name: 'Fall reported' });
    expect(within(group).getAllByRole('radio').map((chip) => chip.textContent))
      .toEqual(['Yes', 'No', 'Unknown']);
  });

  it('submits RECORDED false when the No chip is selected', async () => {
    const submitted = await createWithFall('No');
    expect(submitted.observations.fallReported.state).toBe('RECORDED');
    expect(submitted.observations.fallReported.value).toBe(false);
  });

  it('submits RECORDED true when the Yes chip is selected', async () => {
    const submitted = await createWithFall('Yes');
    expect(submitted.observations.fallReported).toEqual({ state: 'RECORDED', value: true });
  });

  it('submits UNKNOWN with a null value when the Unknown chip is selected', async () => {
    const submitted = await createWithFall('Unknown');
    expect(submitted.observations.fallReported).toEqual({ state: 'UNKNOWN', value: null });
  });

  it('returns to NOT_RECORDED when the selected chip is clicked again', async () => {
    const submitted = await createWithFall('No', true);
    expect(submitted.observations.fallReported).toEqual({ state: 'NOT_RECORDED', value: null });
  });

  it('renders fall reported chips with nothing selected in append mode', async () => {
    renderNotes(`${detail}/addenda/new`, dataFetcher());
    await screen.findByRole('form', { name: 'Add a correction' });
    await userEvent.click(screen.getByRole('checkbox', { name: 'Correct this field: Fall reported' }));
    const group = screen.getByRole('radiogroup', { name: 'Fall reported' });
    expect(within(group).getAllByRole('radio').map((chip) => chip.textContent)).toEqual(['Yes', 'No', 'Unknown', 'Clear']);
    expect(within(group).queryByRole('radio', { checked: true })).not.toBeInTheDocument();
    expect(screen.queryByRole('group', { name: 'Fall reported — Observation status' })).not.toBeInTheDocument();
  });
});

describe('mood chips on the create form', () => {
  async function createWithMood(chip?: string, deselect = false) {
    let submitted: Record<string, unknown> | null = null;
    const fetcher = dataFetcher((path, init) => {
      if (path === '/api/notes' && init.method === 'POST') {
        submitted = JSON.parse(init.body as string);
        return jsonResponse(noteListFixture, 201);
      }
    });
    renderNotes(`${root}/new`, fetcher);
    await metadata();
    if (chip) {
      await selectMoodChip(chip);
      if (deselect) await selectMoodChip(chip);
    }
    await userEvent.click(screen.getByRole('button', { name: 'Save original note' }));
    await screen.findByText('Original note saved.');
    return submitted as unknown as { observations: { mood: { state: string; value?: unknown } } };
  }

  it('renders seven mood chips in order in create mode', async () => {
    renderNotes(`${root}/new`, dataFetcher());
    const group = await screen.findByRole('radiogroup', { name: 'Mood' });
    expect(within(group).getAllByRole('radio').map((chip) => chip.textContent))
      .toEqual(['Calm', 'Happy', 'Anxious', 'Agitated', 'Sad', 'Confused', 'Unknown']);
  });

  it('submits RECORDED CALM when the Calm chip is selected', async () => {
    const submitted = await createWithMood('Calm');
    expect(submitted.observations.mood).toEqual({ state: 'RECORDED', value: 'CALM' });
  });

  it('submits RECORDED CONFUSED when the Confused chip is selected', async () => {
    const submitted = await createWithMood('Confused');
    expect(submitted.observations.mood).toEqual({ state: 'RECORDED', value: 'CONFUSED' });
  });

  it('submits UNKNOWN with a null value when the Unknown chip is selected', async () => {
    const submitted = await createWithMood('Unknown');
    expect(submitted.observations.mood).toEqual({ state: 'UNKNOWN', value: null });
  });

  it('returns to NOT_RECORDED when the selected chip is clicked again', async () => {
    const submitted = await createWithMood('Sad', true);
    expect(submitted.observations.mood).toEqual({ state: 'NOT_RECORDED', value: null });
  });

  it('renders mood chips with nothing selected in append mode', async () => {
    renderNotes(`${detail}/addenda/new`, dataFetcher());
    await screen.findByRole('form', { name: 'Add a correction' });
    await userEvent.click(screen.getByRole('checkbox', { name: 'Correct this field: Mood' }));
    const group = screen.getByRole('radiogroup', { name: 'Mood' });
    expect(within(group).getAllByRole('radio')).toHaveLength(8);
    expect(within(group).queryByRole('radio', { checked: true })).not.toBeInTheDocument();
    expect(screen.queryByRole('group', { name: 'Mood — Observation status' })).not.toBeInTheDocument();
    expect(screen.queryByRole('textbox', { name: 'Mood' })).not.toBeInTheDocument();
  });
});

describe('appetite chips on the create form', () => {
  async function createWithAppetite(chip?: string, deselect = false) {
    let submitted: Record<string, unknown> | null = null;
    const fetcher = dataFetcher((path, init) => {
      if (path === '/api/notes' && init.method === 'POST') {
        submitted = JSON.parse(init.body as string);
        return jsonResponse(noteListFixture, 201);
      }
    });
    renderNotes(`${root}/new`, fetcher);
    await metadata();
    if (chip) {
      await selectAppetiteChip(chip);
      if (deselect) await selectAppetiteChip(chip);
    }
    await userEvent.click(screen.getByRole('button', { name: 'Save original note' }));
    await screen.findByText('Original note saved.');
    return submitted as unknown as { observations: { appetite: { state: string; value?: unknown } } };
  }

  it('renders five appetite chips in order in create mode', async () => {
    renderNotes(`${root}/new`, dataFetcher());
    const group = await screen.findByRole('radiogroup', { name: 'Appetite' });
    expect(within(group).getAllByRole('radio').map((chip) => chip.textContent))
      .toEqual(['Good', 'Fair', 'Poor', 'Refused food', 'Unknown']);
  });

  it('submits RECORDED REFUSED for the Refused food chip', async () => {
    const submitted = await createWithAppetite('Refused food');
    expect(submitted.observations.appetite).toEqual({ state: 'RECORDED', value: 'REFUSED' });
  });

  it('submits RECORDED POOR when the Poor chip is selected', async () => {
    const submitted = await createWithAppetite('Poor');
    expect(submitted.observations.appetite).toEqual({ state: 'RECORDED', value: 'POOR' });
  });

  it('submits UNKNOWN with a null value when the Unknown chip is selected', async () => {
    const submitted = await createWithAppetite('Unknown');
    expect(submitted.observations.appetite).toEqual({ state: 'UNKNOWN', value: null });
  });

  it('returns to NOT_RECORDED when the selected chip is clicked again', async () => {
    const submitted = await createWithAppetite('Good', true);
    expect(submitted.observations.appetite).toEqual({ state: 'NOT_RECORDED', value: null });
  });

  it('renders appetite chips with nothing selected in append mode', async () => {
    renderNotes(`${detail}/addenda/new`, dataFetcher());
    await screen.findByRole('form', { name: 'Add a correction' });
    await userEvent.click(screen.getByRole('checkbox', { name: 'Correct this field: Appetite' }));
    const group = screen.getByRole('radiogroup', { name: 'Appetite' });
    expect(within(group).getAllByRole('radio').map((chip) => chip.textContent))
      .toEqual(['Good', 'Fair', 'Poor', 'Refused food', 'Unknown', 'Clear']);
    expect(within(group).queryByRole('radio', { checked: true })).not.toBeInTheDocument();
    expect(screen.queryByRole('group', { name: 'Appetite — Observation status' })).not.toBeInTheDocument();
    expect(screen.queryByRole('textbox', { name: 'Appetite' })).not.toBeInTheDocument();
  });
});

describe('sleep chips on the create form', () => {
  async function createWithSleep(chip?: string, deselect = false) {
    let submitted: Record<string, unknown> | null = null;
    const fetcher = dataFetcher((path, init) => {
      if (path === '/api/notes' && init.method === 'POST') {
        submitted = JSON.parse(init.body as string);
        return jsonResponse(noteListFixture, 201);
      }
    });
    renderNotes(`${root}/new`, fetcher);
    await metadata();
    if (chip) {
      await selectSleepChip(chip);
      if (deselect) await selectSleepChip(chip);
    }
    await userEvent.click(screen.getByRole('button', { name: 'Save original note' }));
    await screen.findByText('Original note saved.');
    return submitted as unknown as { observations: { sleep: { state: string; value?: unknown } } };
  }

  it('renders five sleep chips in order in create mode', async () => {
    renderNotes(`${root}/new`, dataFetcher());
    const group = await screen.findByRole('radiogroup', { name: 'Sleep' });
    expect(within(group).getAllByRole('radio').map((chip) => chip.textContent))
      .toEqual(['Slept well', 'Restless', 'Poor sleep', 'Did not sleep', 'Unknown']);
  });

  it('submits RECORDED SLEPT_WELL for the Slept well chip', async () => {
    const submitted = await createWithSleep('Slept well');
    expect(submitted.observations.sleep).toEqual({ state: 'RECORDED', value: 'SLEPT_WELL' });
  });

  it('submits RECORDED DID_NOT_SLEEP when the Did not sleep chip is selected', async () => {
    const submitted = await createWithSleep('Did not sleep');
    expect(submitted.observations.sleep).toEqual({ state: 'RECORDED', value: 'DID_NOT_SLEEP' });
  });

  it('submits UNKNOWN with a null value when the Unknown chip is selected', async () => {
    const submitted = await createWithSleep('Unknown');
    expect(submitted.observations.sleep).toEqual({ state: 'UNKNOWN', value: null });
  });

  it('returns to NOT_RECORDED when the selected chip is clicked again', async () => {
    const submitted = await createWithSleep('Restless', true);
    expect(submitted.observations.sleep).toEqual({ state: 'NOT_RECORDED', value: null });
  });

  it('renders sleep chips with nothing selected in append mode', async () => {
    renderNotes(`${detail}/addenda/new`, dataFetcher());
    await screen.findByRole('form', { name: 'Add a correction' });
    await userEvent.click(screen.getByRole('checkbox', { name: 'Correct this field: Sleep' }));
    const group = screen.getByRole('radiogroup', { name: 'Sleep' });
    expect(within(group).getAllByRole('radio').map((chip) => chip.textContent))
      .toEqual(['Slept well', 'Restless', 'Poor sleep', 'Did not sleep', 'Unknown', 'Clear']);
    expect(within(group).queryByRole('radio', { checked: true })).not.toBeInTheDocument();
    expect(screen.queryByRole('group', { name: 'Sleep — Observation status' })).not.toBeInTheDocument();
    expect(screen.queryByRole('textbox', { name: 'Sleep' })).not.toBeInTheDocument();
  });
});

describe('mobility chips on the create form', () => {
  async function createWithMobility(chip?: string, deselect = false) {
    let submitted: Record<string, unknown> | null = null;
    const fetcher = dataFetcher((path, init) => {
      if (path === '/api/notes' && init.method === 'POST') {
        submitted = JSON.parse(init.body as string);
        return jsonResponse(noteListFixture, 201);
      }
    });
    renderNotes(`${root}/new`, fetcher);
    await metadata();
    if (chip) {
      await selectMobilityChip(chip);
      if (deselect) await selectMobilityChip(chip);
    }
    await userEvent.click(screen.getByRole('button', { name: 'Save original note' }));
    await screen.findByText('Original note saved.');
    return submitted as unknown as { observations: { mobility: { state: string; value?: unknown } } };
  }

  it('renders four mobility chips in order in create mode', async () => {
    renderNotes(`${root}/new`, dataFetcher());
    const group = await screen.findByRole('radiogroup', { name: 'Mobility' });
    expect(within(group).getAllByRole('radio').map((chip) => chip.textContent))
      .toEqual(['Independent', 'Needs assistance', 'Bedridden', 'Unknown']);
  });

  it('submits RECORDED INDEPENDENT for the Independent chip', async () => {
    const submitted = await createWithMobility('Independent');
    expect(submitted.observations.mobility).toEqual({ state: 'RECORDED', value: 'INDEPENDENT' });
  });

  it('submits RECORDED NEEDS_ASSISTANCE when the Needs assistance chip is selected', async () => {
    const submitted = await createWithMobility('Needs assistance');
    expect(submitted.observations.mobility).toEqual({ state: 'RECORDED', value: 'NEEDS_ASSISTANCE' });
  });

  it('submits UNKNOWN with a null value when the Unknown chip is selected', async () => {
    const submitted = await createWithMobility('Unknown');
    expect(submitted.observations.mobility).toEqual({ state: 'UNKNOWN', value: null });
  });

  it('returns to NOT_RECORDED when the selected chip is clicked again', async () => {
    const submitted = await createWithMobility('Bedridden', true);
    expect(submitted.observations.mobility).toEqual({ state: 'NOT_RECORDED', value: null });
  });

  it('renders mobility chips with nothing selected in append mode', async () => {
    renderNotes(`${detail}/addenda/new`, dataFetcher());
    await screen.findByRole('form', { name: 'Add a correction' });
    await userEvent.click(screen.getByRole('checkbox', { name: 'Correct this field: Mobility' }));
    const group = screen.getByRole('radiogroup', { name: 'Mobility' });
    expect(within(group).getAllByRole('radio').map((chip) => chip.textContent))
      .toEqual(['Independent', 'Needs assistance', 'Bedridden', 'Unknown', 'Clear']);
    expect(within(group).queryByRole('radio', { checked: true })).not.toBeInTheDocument();
    expect(screen.queryByRole('group', { name: 'Mobility — Observation status' })).not.toBeInTheDocument();
    expect(screen.queryByRole('textbox', { name: 'Mobility' })).not.toBeInTheDocument();
  });
});

describe('pain score chips on the create form', () => {
  async function createWithPain(chip?: string, deselect = false) {
    let submitted: Record<string, unknown> | null = null;
    const fetcher = dataFetcher((path, init) => {
      if (path === '/api/notes' && init.method === 'POST') {
        submitted = JSON.parse(init.body as string);
        return jsonResponse(noteListFixture, 201);
      }
    });
    renderNotes(`${root}/new`, fetcher);
    await metadata();
    if (chip) {
      await selectPainScoreChip(chip);
      if (deselect) await selectPainScoreChip(chip);
    }
    await userEvent.click(screen.getByRole('button', { name: 'Save original note' }));
    await screen.findByText('Original note saved.');
    return submitted as unknown as { observations: { painScore: { state: string; value?: unknown } } };
  }

  it('renders twelve pain score chips in order in create mode', async () => {
    renderNotes(`${root}/new`, dataFetcher());
    const group = await screen.findByRole('radiogroup', { name: 'Pain score (0–10)' });
    expect(within(group).getAllByRole('radio').map((chip) => chip.textContent))
      .toEqual(['0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '10', 'Unknown']);
  });

  it('submits RECORDED numeric 0 for the 0 chip', async () => {
    const submitted = await createWithPain('0');
    expect(submitted.observations.painScore.state).toBe('RECORDED');
    expect(submitted.observations.painScore.value).toBe(0);
  });

  it('submits RECORDED 10 when the 10 chip is selected', async () => {
    const submitted = await createWithPain('10');
    expect(submitted.observations.painScore).toEqual({ state: 'RECORDED', value: 10 });
  });

  it('submits RECORDED 7 when the 7 chip is selected', async () => {
    const submitted = await createWithPain('7');
    expect(submitted.observations.painScore).toEqual({ state: 'RECORDED', value: 7 });
  });

  it('submits UNKNOWN with a null value when the Unknown chip is selected', async () => {
    const submitted = await createWithPain('Unknown');
    expect(submitted.observations.painScore).toEqual({ state: 'UNKNOWN', value: null });
  });

  it('returns to NOT_RECORDED when the selected chip is clicked again', async () => {
    const submitted = await createWithPain('5', true);
    expect(submitted.observations.painScore).toEqual({ state: 'NOT_RECORDED', value: null });
  });

  it('renders Western digits for the numeric chips in Arabic', async () => {
    renderNotes(`${root}/new`, dataFetcher(), 'ar');
    const group = await screen.findByRole('radiogroup', { name: 'درجة الألم (0–10)' });
    const labels = within(group).getAllByRole('radio').map((chip) => chip.textContent);
    expect(labels).toEqual(['0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '10', 'غير معروف']);
  });

  it('renders pain score chips with nothing selected in append mode', async () => {
    renderNotes(`${detail}/addenda/new`, dataFetcher());
    await screen.findByRole('form', { name: 'Add a correction' });
    await userEvent.click(screen.getByRole('checkbox', { name: 'Correct this field: Pain score (0–10)' }));
    const group = screen.getByRole('radiogroup', { name: 'Pain score (0–10)' });
    expect(within(group).getAllByRole('radio')).toHaveLength(13);
    expect(within(group).queryByRole('radio', { checked: true })).not.toBeInTheDocument();
    expect(screen.queryByRole('group', { name: 'Pain score (0–10) — Observation status' })).not.toBeInTheDocument();
    expect(screen.queryByRole('spinbutton', { name: 'Pain score (0–10)' })).not.toBeInTheDocument();
  });
});

describe('collapsible free text on the create form', () => {
  async function submitCreate() {
    let submitted: Record<string, unknown> | null = null;
    const fetcher = dataFetcher((path, init) => {
      if (path === '/api/notes' && init.method === 'POST') {
        submitted = JSON.parse(init.body as string);
        return jsonResponse(noteListFixture, 201);
      }
    });
    renderNotes(`${root}/new`, fetcher);
    await metadata();
    await userEvent.click(screen.getByRole('button', { name: 'Save original note' }));
    await screen.findByText('Original note saved.');
    return submitted as unknown as { observations: { freeText: { state: string; value?: unknown } } };
  }

  it('renders an Add details chip instead of the textarea when collapsed', async () => {
    renderNotes(`${root}/new`, dataFetcher());
    await screen.findByRole('button', { name: 'Add details' });
    expect(screen.queryByRole('textbox', { name: 'Free-text observations' })).not.toBeInTheDocument();
  });

  it('expands the textarea and sets aria-expanded when Add details is clicked', async () => {
    renderNotes(`${root}/new`, dataFetcher());
    const toggle = await screen.findByRole('button', { name: 'Add details' });
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
    await userEvent.click(toggle);
    expect(screen.getByRole('textbox', { name: 'Free-text observations' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Show less' })).toHaveAttribute('aria-expanded', 'true');
  });

  it('collapses again via the collapse control while the textarea is empty', async () => {
    renderNotes(`${root}/new`, dataFetcher());
    await userEvent.click(await screen.findByRole('button', { name: 'Add details' }));
    await userEvent.click(screen.getByRole('button', { name: 'Show less' }));
    expect(screen.queryByRole('textbox', { name: 'Free-text observations' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Add details' })).toHaveAttribute('aria-expanded', 'false');
  });

  it('removes the collapse control once the textarea has text', async () => {
    renderNotes(`${root}/new`, dataFetcher());
    await userEvent.click(await screen.findByRole('button', { name: 'Add details' }));
    fireEvent.change(screen.getByRole('textbox', { name: 'Free-text observations' }), { target: { value: 'Details' } });
    expect(screen.queryByRole('button', { name: 'Show less' })).not.toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Free-text observations' })).toBeInTheDocument();
  });

  it('submits NOT_RECORDED when the textarea stays collapsed', async () => {
    const submitted = await submitCreate();
    expect(submitted.observations.freeText).toEqual({ state: 'NOT_RECORDED', value: null });
  });

  it('submits RECORDED with the entered text', async () => {
    let submitted: Record<string, unknown> | null = null;
    const fetcher = dataFetcher((path, init) => {
      if (path === '/api/notes' && init.method === 'POST') {
        submitted = JSON.parse(init.body as string);
        return jsonResponse(noteListFixture, 201);
      }
    });
    renderNotes(`${root}/new`, fetcher);
    await metadata();
    await userEvent.click(screen.getByRole('button', { name: 'Add details' }));
    fireEvent.change(screen.getByRole('textbox', { name: 'Free-text observations' }), { target: { value: 'Walked to the garden' } });
    await userEvent.click(screen.getByRole('button', { name: 'Save original note' }));
    await screen.findByText('Original note saved.');
    expect(submitted).toMatchObject({ observations: { freeText: { state: 'RECORDED', value: 'Walked to the garden' } } });
  });

  it('keeps maxLength 20000 and dir auto on the textarea', async () => {
    renderNotes(`${root}/new`, dataFetcher());
    await userEvent.click(await screen.findByRole('button', { name: 'Add details' }));
    const textarea = screen.getByRole('textbox', { name: 'Free-text observations' });
    expect(textarea).toHaveAttribute('maxlength', '20000');
    expect(textarea).toHaveAttribute('dir', 'auto');
  });

  it('does not change other field values when expanding', async () => {
    let submitted: Record<string, unknown> | null = null;
    const fetcher = dataFetcher((path, init) => {
      if (path === '/api/notes' && init.method === 'POST') {
        submitted = JSON.parse(init.body as string);
        return jsonResponse(noteListFixture, 201);
      }
    });
    renderNotes(`${root}/new`, fetcher);
    await metadata();
    await selectMoodChip('Calm');
    await selectPainScoreChip('3');
    await userEvent.click(screen.getByRole('button', { name: 'Add details' }));
    await userEvent.click(screen.getByRole('button', { name: 'Save original note' }));
    await screen.findByText('Original note saved.');
    const observations = (submitted as unknown as { observations: Record<string, { state: string; value?: unknown }> }).observations;
    expect(observations.mood).toEqual({ state: 'RECORDED', value: 'CALM' });
    expect(observations.painScore).toEqual({ state: 'RECORDED', value: 3 });
    expect(observations.freeText).toEqual({ state: 'NOT_RECORDED', value: null });
  });

  it('keeps the free-text field visible in append mode without an Add details chip', async () => {
    renderNotes(`${detail}/addenda/new`, dataFetcher());
    await screen.findByRole('form', { name: 'Add a correction' });
    await userEvent.click(screen.getByRole('checkbox', { name: 'Correct this field: Free-text observations' }));
    await setRecorded('Free-text observations');
    expect(screen.getByRole('textbox', { name: 'Free-text observations' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Add details' })).not.toBeInTheDocument();
  });
});

describe('answered count progress line on the create form', () => {
  it('reads 0 of 8 answered on an empty create form', async () => {
    renderNotes(`${root}/new`, dataFetcher());
    expect(await screen.findByText('0 of 8 answered')).toBeInTheDocument();
  });

  it('reads 1 of 8 answered after selecting one recorded chip', async () => {
    renderNotes(`${root}/new`, dataFetcher());
    await screen.findByText('0 of 8 answered');
    await selectMoodChip('Calm');
    expect(screen.getByText('1 of 8 answered')).toBeInTheDocument();
  });

  it('counts an Unknown chip as answered', async () => {
    renderNotes(`${root}/new`, dataFetcher());
    await screen.findByText('0 of 8 answered');
    await selectMoodChip('Calm');
    await selectAppetiteChip('Unknown');
    expect(screen.getByText('2 of 8 answered')).toBeInTheDocument();
  });

  it('decrements when a selected chip is tapped again', async () => {
    renderNotes(`${root}/new`, dataFetcher());
    await screen.findByText('0 of 8 answered');
    await selectMoodChip('Calm');
    await selectMoodChip('Calm');
    expect(screen.getByText('0 of 8 answered')).toBeInTheDocument();
  });

  it('counts entered free text as answered', async () => {
    renderNotes(`${root}/new`, dataFetcher());
    await screen.findByText('0 of 8 answered');
    await userEvent.click(screen.getByRole('button', { name: 'Add details' }));
    fireEvent.change(screen.getByRole('textbox', { name: 'Free-text observations' }), { target: { value: 'A note' } });
    expect(screen.getByText('1 of 8 answered')).toBeInTheDocument();
  });

  it('never disables saving or shows an error at zero', async () => {
    renderNotes(`${root}/new`, dataFetcher());
    await screen.findByText('0 of 8 answered');
    expect(screen.getByRole('button', { name: 'Save original note' })).toBeEnabled();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('renders the Arabic count in Arabic', async () => {
    renderNotes(`${root}/new`, dataFetcher(), 'ar');
    expect(await screen.findByText('0 من 8 مكتملة')).toBeInTheDocument();
    await userEvent.click(within(screen.getByRole('radiogroup', { name: 'المزاج' })).getByRole('radio', { name: 'هادئ' }));
    expect(screen.getByText('1 من 8 مكتملة')).toBeInTheDocument();
  });

  it('is not rendered in append mode', async () => {
    renderNotes(`${detail}/addenda/new`, dataFetcher());
    await screen.findByRole('form', { name: 'Add a correction' });
    expect(screen.queryByText(/of 8 answered/)).not.toBeInTheDocument();
    expect(screen.queryByText(/من 8 مكتملة/)).not.toBeInTheDocument();
  });
});

describe('chips in correction mode', () => {
  async function setupAppend() {
    let submitted: Record<string, unknown> | null = null;
    const fetcher = dataFetcher((path, init) => {
      if (path.endsWith('/addenda') && init.method === 'POST') {
        submitted = JSON.parse(init.body as string);
        return jsonResponse(addendumFixture, 201);
      }
      if (path.endsWith('/addenda')) return jsonResponse([]);
    });
    renderNotes(`${detail}/addenda/new`, fetcher);
    await screen.findByRole('form', { name: 'Add a correction' }, { timeout: 5000 });
    return {
      fetcher,
      get submitted() { return submitted as { observations: Record<string, { state: string; value?: unknown }> } | null; },
    };
  }
  async function tick(label: string) {
    await userEvent.click(screen.getByRole('checkbox', { name: `Correct this field: ${label}` }));
  }
  async function save() {
    await userEvent.click(screen.getByRole('button', { name: 'Save addendum' }));
  }

  it('renders the eight mood chips including Clear with nothing selected after ticking', async () => {
    await setupAppend();
    await tick('Mood');
    const group = screen.getByRole('radiogroup', { name: 'Mood' });
    expect(within(group).getAllByRole('radio').map((chip) => chip.textContent))
      .toEqual(['Calm', 'Happy', 'Anxious', 'Agitated', 'Sad', 'Confused', 'Unknown', 'Clear']);
    expect(within(group).queryByRole('radio', { checked: true })).not.toBeInTheDocument();
  });

  it('shows Choose one option and blocks the save when no chip is chosen', async () => {
    const { fetcher } = await setupAppend();
    await tick('Mood');
    await save();
    expect(screen.getByRole('alert')).toHaveTextContent('Choose one option for this field.');
    expect(fetcher.mock.calls.some(([path, init]) => path.endsWith('/addenda') && init.method === 'POST')).toBe(false);
  });

  it('submits RECORDED SAD for a chosen mood chip', async () => {
    const ctx = await setupAppend();
    await tick('Mood');
    await userEvent.click(within(screen.getByRole('radiogroup', { name: 'Mood' })).getByRole('radio', { name: 'Sad' }));
    await save();
    await screen.findByText('Addendum saved. The original note is unchanged.');
    expect(ctx.submitted?.observations.mood).toEqual({ state: 'RECORDED', value: 'SAD' });
  });

  it('submits numeric 0 for the pain score 0 chip', async () => {
    const ctx = await setupAppend();
    await tick('Pain score (0–10)');
    await userEvent.click(within(screen.getByRole('radiogroup', { name: 'Pain score (0–10)' })).getByRole('radio', { name: '0' }));
    await save();
    await screen.findByText('Addendum saved. The original note is unchanged.');
    expect(ctx.submitted?.observations.painScore).toEqual({ state: 'RECORDED', value: 0 });
  });

  it('deselecting the chosen chip blocks the save again', async () => {
    const { fetcher } = await setupAppend();
    await tick('Mood');
    const sad = within(screen.getByRole('radiogroup', { name: 'Mood' })).getByRole('radio', { name: 'Sad' });
    await userEvent.click(sad);
    await userEvent.click(sad);
    await save();
    expect(screen.getByRole('alert')).toHaveTextContent('Choose one option for this field.');
    expect(fetcher.mock.calls.some(([path, init]) => path.endsWith('/addenda') && init.method === 'POST')).toBe(false);
  });

  it('clears the selection and error when the correction is unticked', async () => {
    await setupAppend();
    await tick('Mood');
    await save();
    expect(screen.getByRole('alert')).toHaveTextContent('Choose one option for this field.');
    await userEvent.click(screen.getByRole('checkbox', { name: 'Correct this field: Mood' }));
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    await tick('Mood');
    const group = screen.getByRole('radiogroup', { name: 'Mood' });
    expect(within(group).queryByRole('radio', { checked: true })).not.toBeInTheDocument();
  });

  it('keeps the ObservationStateField textarea editor for free text', async () => {
    await setupAppend();
    await tick('Free-text observations');
    expect(screen.getByRole('group', { name: 'Free-text observations — Observation status' })).toBeVisible();
    await setRecorded('Free-text observations');
    expect(screen.getByRole('textbox', { name: 'Free-text observations' })).toBeInTheDocument();
    expect(screen.queryByRole('radiogroup', { name: 'Free-text observations' })).not.toBeInTheDocument();
  });

  it('keeps the three-option verification select in append mode', async () => {
    await setupAppend();
    await tick('Medication report verified');
    const select = screen.getByRole('combobox', { name: 'Medication report verified' });
    expect(within(select).getAllByRole('option').map((option) => option.textContent))
      .toEqual(['Not asserted', 'Verified', 'Unverified']);
  });

  it('does not render a Clear chip in create mode', async () => {
    renderNotes(`${root}/new`, dataFetcher());
    const group = await screen.findByRole('radiogroup', { name: 'Mood' });
    expect(within(group).queryByRole('radio', { name: 'Clear' })).not.toBeInTheDocument();
    expect(within(group).getAllByRole('radio')).toHaveLength(7);
  });

  it('submits NOT_RECORDED null when Clear is chosen for mood', async () => {
    const ctx = await setupAppend();
    await tick('Mood');
    await userEvent.click(within(screen.getByRole('radiogroup', { name: 'Mood' })).getByRole('radio', { name: 'Clear' }));
    await save();
    await screen.findByText('Addendum saved. The original note is unchanged.');
    expect(ctx.submitted?.observations.mood).toEqual({ state: 'NOT_RECORDED', value: null });
  });

  it('does not show the choose-one error after choosing Clear', async () => {
    const ctx = await setupAppend();
    await tick('Mood');
    await userEvent.click(within(screen.getByRole('radiogroup', { name: 'Mood' })).getByRole('radio', { name: 'Clear' }));
    await save();
    await screen.findByText('Addendum saved. The original note is unchanged.');
    expect(ctx.fetcher.mock.calls.some(([path, init]) => path.endsWith('/addenda') && init.method === 'POST')).toBe(true);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('replaces a chosen chip with NOT_RECORDED when Clear is selected', async () => {
    const ctx = await setupAppend();
    await tick('Mood');
    const group = screen.getByRole('radiogroup', { name: 'Mood' });
    await userEvent.click(within(group).getByRole('radio', { name: 'Sad' }));
    await userEvent.click(within(group).getByRole('radio', { name: 'Clear' }));
    await save();
    await screen.findByText('Addendum saved. The original note is unchanged.');
    expect(ctx.submitted?.observations.mood).toEqual({ state: 'NOT_RECORDED', value: null });
  });

  it('re-shows the choose-one error and blocks save when Clear is tapped again', async () => {
    const { fetcher } = await setupAppend();
    await tick('Mood');
    const clear = within(screen.getByRole('radiogroup', { name: 'Mood' })).getByRole('radio', { name: 'Clear' });
    await userEvent.click(clear);
    await userEvent.click(clear);
    await save();
    expect(screen.getByRole('alert')).toHaveTextContent('Choose one option for this field.');
    expect(fetcher.mock.calls.some(([path, init]) => path.endsWith('/addenda') && init.method === 'POST')).toBe(false);
  });

  it('replaces Clear with a normal chip selection', async () => {
    const ctx = await setupAppend();
    await tick('Mood');
    const group = screen.getByRole('radiogroup', { name: 'Mood' });
    await userEvent.click(within(group).getByRole('radio', { name: 'Clear' }));
    await userEvent.click(within(group).getByRole('radio', { name: 'Happy' }));
    await save();
    await screen.findByText('Addendum saved. The original note is unchanged.');
    expect(ctx.submitted?.observations.mood).toEqual({ state: 'RECORDED', value: 'HAPPY' });
  });

  it('submits NOT_RECORDED null when Clear is chosen for pain score', async () => {
    const ctx = await setupAppend();
    await tick('Pain score (0–10)');
    await userEvent.click(within(screen.getByRole('radiogroup', { name: 'Pain score (0–10)' })).getByRole('radio', { name: 'Clear' }));
    await save();
    await screen.findByText('Addendum saved. The original note is unchanged.');
    expect(ctx.submitted?.observations.painScore).toEqual({ state: 'NOT_RECORDED', value: null });
  });
});

describe('chip labels for saved values', () => {
  async function renderSavedWith(patch: Partial<NoteObservations>, language = 'en') {
    const observations = { ...structuredObservations, ...patch };
    const fetcher = dataFetcher((path) =>
      path === `/api/notes/${noteFixture.id}`
        ? jsonResponse({ ...noteDetailFixture, observations })
        : undefined);
    renderNotes(detail, fetcher, language);
    return screen.findByRole('article', {}, { timeout: 5000 });
  }
  function rowFor(article: HTMLElement, label: string) {
    return within(article).getByText(label).closest('.note-field-row') as HTMLElement;
  }

  it('renders SLEPT_WELL as Slept well', async () => {
    const article = await renderSavedWith({ sleep: { state: 'RECORDED', value: 'SLEPT_WELL' } });
    expect(within(rowFor(article, 'Sleep')).getByText('Slept well')).toBeVisible();
  });

  it('renders REFUSED appetite as Refused food', async () => {
    const article = await renderSavedWith({ appetite: { state: 'RECORDED', value: 'REFUSED' } });
    expect(within(rowFor(article, 'Appetite')).getByText('Refused food')).toBeVisible();
  });

  it('renders NEEDS_ASSISTANCE mobility as Needs assistance', async () => {
    const article = await renderSavedWith({ mobility: { state: 'RECORDED', value: 'NEEDS_ASSISTANCE' } });
    expect(within(rowFor(article, 'Mobility')).getByText('Needs assistance')).toBeVisible();
  });

  it('renders CALM mood as Calm', async () => {
    const article = await renderSavedWith({ mood: { state: 'RECORDED', value: 'CALM' } });
    expect(within(rowFor(article, 'Mood')).getByText('Calm')).toBeVisible();
  });

  it('renders a lowercase non-code mood value verbatim', async () => {
    const article = await renderSavedWith({ mood: { state: 'RECORDED', value: 'good' } });
    const row = rowFor(article, 'Mood');
    expect(within(row).getByText('good')).toBeVisible();
    expect(within(row).queryByText('Good')).not.toBeInTheDocument();
  });

  it('renders free-text mood verbatim', async () => {
    const article = await renderSavedWith({ mood: { state: 'RECORDED', value: 'some free text' } });
    expect(within(rowFor(article, 'Mood')).getByText('some free text')).toBeVisible();
  });

  it('keeps the existing TAKEN label for medication status', async () => {
    const article = await renderSavedWith({ medicationStatus: { state: 'RECORDED', value: 'TAKEN' } });
    expect(within(rowFor(article, 'Medication taken')).getByText('Taken')).toBeVisible();
  });

  it('keeps the boolean No label for fall reported', async () => {
    const article = await renderSavedWith({ fallReported: { state: 'RECORDED', value: false } });
    expect(within(rowFor(article, 'Fall reported')).getByText('No')).toBeVisible();
  });

  it('keeps the number formatting for pain score', async () => {
    const article = await renderSavedWith({ painScore: { state: 'RECORDED', value: 0 } });
    const row = rowFor(article, 'Pain score (0–10)');
    expect(within(row).getByText('0')).toBeVisible();
  });

  it('renders the Arabic sleep label in Arabic', async () => {
    const article = await renderSavedWith({ sleep: { state: 'RECORDED', value: 'SLEPT_WELL' } }, 'ar');
    expect(within(article).getByText('نام جيدًا')).toBeVisible();
  });
});

describe('note medication status vs intake events', () => {
  const medication = {
    id: 'c3333333-3333-4333-8333-333333333333', recipientId: recipientFixture.id,
    displayName: 'Vitamin D', doseText: '1 tablet', scheduleText: 'once daily', active: true,
    createdAt: '2026-10-01T10:00:00Z', updatedAt: '2026-10-01T10:00:00Z',
  };
  // Noon local time keeps the event on the note's calendar day in any timezone.
  const sameDay = new Date(`${noteFixture.date}T12:00:00`).toISOString();
  const event = (intakeState: 'TAKEN' | 'MISSED', id: string) => ({
    id, medicationId: medication.id, recipientId: recipientFixture.id, intakeState,
    occurredAt: sameDay, verificationState: 'VERIFIED', createdAt: sameDay,
  });

  function renderWith(noteStatus: 'TAKEN' | 'MISSED' | 'UNKNOWN', events: unknown[], language = 'en') {
    const fetcher = dataFetcher((path) => {
      if (path === `/api/notes/${noteFixture.id}`) return jsonResponse({
        ...noteDetailFixture,
        observations: {
          ...structuredObservations,
          medicationStatus: noteStatus === 'UNKNOWN'
            ? { state: 'UNKNOWN' }
            : { state: 'RECORDED', value: noteStatus },
        },
      });
      if (path === `/api/recipients/${recipientFixture.id}/medications`) return jsonResponse([medication]);
      if (path === `/api/recipients/${recipientFixture.id}/medications/${medication.id}/intake-events`) {
        return jsonResponse(events);
      }
      return undefined;
    });
    renderNotes(detail, fetcher, language);
  }

  it('warns when a MISSED note status disagrees with a TAKEN intake event that day', async () => {
    renderWith('MISSED', [event('TAKEN', 'e1111111-1111-4111-8111-111111111111')]);

    expect(await screen.findByText(/Medication reports disagree/i)).toBeVisible();
    // The warning is informational: the recorded status is still shown unchanged.
    expect(await screen.findByText('Missed')).toBeVisible();
  });

  it('warns in Arabic when a TAKEN note status disagrees with a MISSED intake event', async () => {
    renderWith('TAKEN', [event('MISSED', 'e2222222-2222-4222-8222-222222222222')], 'ar');

    expect(await screen.findByText(/تتعارض سجلات الدواء/)).toBeVisible();
  });

  it('does not warn when the note status agrees with the intake events', async () => {
    renderWith('TAKEN', [event('TAKEN', 'e3333333-3333-4333-8333-333333333333')]);

    await screen.findByRole('article', {}, { timeout: 5000 });
    expect(screen.queryByText(/Medication reports disagree/i)).not.toBeInTheDocument();
  });

  it('does not warn when the note status is not a recorded TAKEN/MISSED value', async () => {
    renderWith('UNKNOWN', [event('TAKEN', 'e4444444-4444-4444-8444-444444444444')]);

    await screen.findByRole('article', {}, { timeout: 5000 });
    expect(screen.queryByText(/Medication reports disagree/i)).not.toBeInTheDocument();
  });

  it('does not warn for an intake event on a different day', async () => {
    const otherDay = { ...event('TAKEN', 'e5555555-5555-4555-8555-555555555555'), occurredAt: new Date('2020-01-01T12:00:00').toISOString() };
    renderWith('MISSED', [otherDay]);

    await screen.findByRole('article', {}, { timeout: 5000 });
    expect(screen.queryByText(/Medication reports disagree/i)).not.toBeInTheDocument();
  });
});
