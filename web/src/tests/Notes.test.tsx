import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { App } from '../app/App';
import { AuthProvider } from '../features/auth/AuthProvider';
import { NoteTimelineRow } from '../features/notes/NoteTimelineRow';
import { I18nProvider } from '../i18n/I18nProvider';
import { ApiProvider } from '../lib/api';
import { jsonResponse, testToken } from './authFixtures';
import { addendumFixture, noteDetailFixture, noteFixture, noteListFixture, noteObservations } from './noteFixtures';
import { observationFields, type FieldCorrection } from '../api/notes';
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
    expect(within(screen.getByRole('group', { name: 'Daily wellbeing' })).getByRole('radio', { name: 'Mood — Recorded' })).toBeInTheDocument();
    expect(within(screen.getByRole('group', { name: 'Medication' })).getByLabelText('Medication report verified')).toBeInTheDocument();
    expect(within(screen.getByRole('group', { name: 'Pain and safety' })).getByLabelText('Fall reported')).toBeInTheDocument();
    expect(within(screen.getByRole('group', { name: 'Additional notes' }))
      .getByRole('group', { name: 'Free-text observations — Observation status' })).toBeInTheDocument();
  });

  it('keeps state choices keyboard accessible and exposes the selected state', async () => {
    renderNotes(`${root}/new`, dataFetcher());
    await screen.findByRole('group', { name: 'Mood — Observation status' });
    const group = screen.getByRole('group', { name: 'Mood — Observation status' });
    await userEvent.click(within(group).getByText('Recorded', { exact: true }));
    const recorded = within(group).getByRole('radio', { name: 'Mood — Recorded' });
    recorded.focus();
    expect(recorded).toBeChecked();
    await userEvent.keyboard('{ArrowDown}');
    expect(within(group).getByRole('radio', { name: 'Mood — Unknown' })).toBeChecked();
    expect(recorded).not.toBeChecked();
    expect(within(group).getByText('Unknown', { exact: true })).toBeVisible();
  });

  it('submits recorded zero and false without coercion', async () => {
    const fetcher = dataFetcher();
    renderNotes(`${root}/new`, fetcher);
    await metadata();
    await setRecorded('Pain score (0–10)');
    fireEvent.change(screen.getByRole('spinbutton', { name: 'Pain score (0–10)' }), { target: { value: '0' } });
    await setRecorded('Fall reported');
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Fall reported' }), 'false');
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
    await setObservationState('Pain score (0–10)', 'UNKNOWN');
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
    await setObservationState('Fall reported', 'NOT_RECORDED');
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
    await setObservationState('Medication taken', 'UNKNOWN');
    await userEvent.selectOptions(screen.getByLabelText('Medication report verified'), 'VERIFIED');
    await userEvent.click(screen.getByRole('button', { name: 'Save original note' }));
    await screen.findByText('Original note saved.');

    expect(submitted).toMatchObject({
      observations: { medicationStatus: { state: 'UNKNOWN', value: null } },
      medicationVerification: 'VERIFIED',
    });
  });

  it('preserves recorded empty text', async () => {
    let submitted: Record<string, unknown> | null = null;
    const fetcher = dataFetcher((path, init) => {
      if (path === '/api/notes' && init.method === 'POST') {
        submitted = JSON.parse(init.body as string);
        return jsonResponse(noteListFixture, 201);
      }
    });
    renderNotes(`${root}/new`, fetcher);
    await metadata();
    await setRecorded('Free-text observations');
    await userEvent.type(screen.getByRole('textbox', { name: 'Free-text observations' }), 'x');
    await userEvent.clear(screen.getByRole('textbox', { name: 'Free-text observations' }));
    await userEvent.click(screen.getByRole('button', { name: 'Save original note' }));

    expect(submitted).toMatchObject({ observations: { freeText: { state: 'RECORDED', value: '' } } });
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
    await setRecorded('Mood');
    fireEvent.change(screen.getByRole('textbox', { name: 'Mood' }), { target: { value: 'Calm' } });
    await setObservationState('Mood', 'UNKNOWN');
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
    renderNotes(`${root}/new`, dataFetcher());
    const group = await screen.findByRole('group', { name: 'Mood — Observation status' });
    expect(within(group).queryByRole('radio', { name: 'Mood — Legacy ambiguous' })).not.toBeInTheDocument();
  });

  it('announces save success and save failure separately', async () => {
    const successView = renderNotes(`${root}/new`, dataFetcher((path, init) =>
      path === '/api/notes' && init.method === 'POST' ? jsonResponse(noteListFixture, 201) : undefined));
    await metadata();
    await userEvent.click(screen.getByRole('button', { name: 'Save original note' }));
    expect(await screen.findByRole('status')).toHaveTextContent('Original note saved.');
    successView.unmount();

    renderNotes(`${root}/new`, dataFetcher((path, init) =>
      path === '/api/notes' && init.method === 'POST' ? jsonResponse({ message: 'private server detail' }, 500) : undefined));
    await metadata();
    await setRecorded('Free-text observations');
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
    sequenceTwo.previousEffective.eventId = sequenceOne.id;
    sequenceTwo.effectiveAfter.eventId = sequenceTwo.id;
    corrections.effectiveObservations.mood = sequenceOne.effectiveAfter;
    corrections.effectiveObservations.painScore = sequenceTwo.effectiveAfter;
    corrections.originalNote = {
      ...corrections.originalNote,
      createdAt: timestamp,
      observations: {
        ...corrections.originalNote.observations,
        mood: { ...corrections.originalNote.observations.mood, updatedAt: timestamp },
        painScore: { ...corrections.originalNote.observations.painScore, updatedAt: timestamp },
      },
    };
    const addenda = [
      { ...addendumFixture, id: 'e5555555-5555-4555-8555-000000000004', createdAt: timestamp },
      { ...addendumFixture, id: 'e5555555-5555-4555-8555-000000000003', createdAt: timestamp },
    ];
    const fetcher = dataFetcher((path) => path.endsWith('/addenda') ? jsonResponse(addenda)
      : path.endsWith('/corrections') ? jsonResponse(corrections) : undefined);
    renderNotes(detail, fetcher);
    await waitFor(() => expect(screen.queryByRole('status', { name: 'Loading note data…' })).toBeNull());
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


  it('protects direct note/addendum URLs before any API request', () => {
    const fetcher = vi.fn();
    renderNotes(`${detail}/addenda/new`, fetcher, 'en', false);
    expect(screen.getByRole('heading', { name: 'Caregiver sign in' })).toBeInTheDocument();
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
    for (const [label, value] of [['Mood', noteObservations.mood], ['Appetite', noteObservations.appetite], ['Sleep', noteObservations.sleep], ['Mobility', noteObservations.mobility]] as const) {
      await setRecorded(label);
      fireEvent.change(screen.getByRole('textbox', { name: label }), { target: { value } });
    }
    await setObservationState('Medication taken', 'UNKNOWN');
    await userEvent.selectOptions(screen.getByLabelText('Medication report verified'), 'UNVERIFIED');
    await setRecorded('Pain score (0–10)');
    fireEvent.change(screen.getByRole('spinbutton', { name: 'Pain score (0–10)' }), { target: { value: '0' } });
    await setRecorded('Fall reported');
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Fall reported' }), 'false');
    await setRecorded('Free-text observations');
    fireEvent.change(screen.getByRole('textbox', { name: 'Free-text observations' }), { target: { value: noteObservations.freeText } });
    await userEvent.click(screen.getByRole('button', { name: 'Save original note' }));
    expect(await screen.findByText('Original note saved.')).toHaveFocus();
    expect(saved).toEqual({
      recipientId: recipientFixture.id,
      date: noteFixture.date,
      observations: {
        mood: { state: 'RECORDED', value: noteObservations.mood },
        appetite: { state: 'RECORDED', value: noteObservations.appetite },
        sleep: { state: 'RECORDED', value: noteObservations.sleep },
        mobility: { state: 'RECORDED', value: noteObservations.mobility },
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
    expect(within(screen.getByRole('group', { name: 'Free-text observations — Observation status' })).getAllByRole('radio')[0]).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'Retry the same save' }));
    await screen.findByText('Original note saved.');
    expect(posts).toHaveLength(2);
    expect(posts[1].body).toBe(posts[0].body);
    expect(new Headers(posts[1].headers).get('Idempotency-Key')).toBe(new Headers(posts[0].headers).get('Idempotency-Key'));
  });
  it('keeps values editable after explicit backend validation failure', async () => {
    renderNotes(`${root}/new`, dataFetcher((path, init) => path === '/api/notes' && init.method === 'POST' ? jsonResponse({ message: 'private validation' }, 400) : undefined));
    await metadata();
    await setRecorded('Free-text observations');
    fireEvent.change(screen.getByRole('textbox', { name: 'Free-text observations' }), { target: { value: 'القيمة الأصلية' } });
    await userEvent.click(screen.getByRole('button', { name: 'Save original note' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Review the entered values');
    await waitFor(() => expect(screen.getByRole('textbox', { name: 'Free-text observations' })).toBeEnabled());
    expect(screen.getByRole('textbox', { name: 'Free-text observations' })).toBeEnabled();
    expect(screen.getByRole('textbox', { name: 'Free-text observations' })).toHaveValue('القيمة الأصلية');
    expect(screen.queryByText('private validation')).not.toBeInTheDocument();
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
    await setRecorded('Medication taken');
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Medication taken' }), 'MISSED');
    await userEvent.click(screen.getByRole('checkbox', { name: 'Correct this field: Medication report verified' }));
    await userEvent.selectOptions(screen.getByLabelText('Medication report verified', { exact: true }), 'UNVERIFIED');
    await userEvent.click(screen.getByRole('checkbox', { name: 'Correct this field: Free-text observations' }));
    await setRecorded('Free-text observations');
    fireEvent.change(screen.getByRole('textbox', { name: 'Free-text observations' }), { target: { value: addendumFixture.freeText } });
    await userEvent.click(screen.getByRole('button', { name: 'Save addendum' }));
    expect(await screen.findByText('Addendum saved. The original note is unchanged.')).toHaveFocus();
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
  it('validates a recorded correction without its typed value', async () => {
    renderNotes(`${detail}/addenda/new`, dataFetcher());
    await screen.findByRole('form', { name: 'Add a correction' });
    await userEvent.click(screen.getByRole('checkbox', { name: 'Correct this field: Medication taken' }));
    await setRecorded('Medication taken');
    await userEvent.click(screen.getByRole('button', { name: 'Save addendum' }));
    expect(within(screen.getByRole('group', { name: 'Medication taken — Observation status' })).getAllByRole('radio')[0]).toHaveFocus();
    expect(screen.getByRole('alert')).toHaveTextContent('Enter a value when status is Recorded');
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
