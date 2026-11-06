import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Link, MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { App } from '../app/App';
import { AuthProvider } from '../features/auth/AuthProvider';
import { I18nProvider } from '../i18n/I18nProvider';
import { ApiProvider } from '../lib/api';
import { activePlanFixture as active, suggestedPlanFixture as suggestion } from './planFixtures';
import { protectedActiveFixture as protectedActive, protectedSuggestionFixture as protectedSuggestion } from './planFixtures';
import type { CarePlan } from '../api/plans';
import { recipientFixture as recipient } from './recipientFixtures';
import { noteListFixture } from './noteFixtures';
import { jsonResponse, testToken } from './authFixtures';

const root = '/app/notes/plans';
const selected = `${root}?recipientId=${recipient.id}`;
const other = { ...recipient, id: 'b2222222-2222-4222-8222-222222222222', name: 'ليلى Smith', active: false, lifecycleStatus: 'ARCHIVED' as const, archivedAt: '2026-10-15T10:00:00Z', archivedBy: 'b1111111-1111-4111-8111-111111111111' };
const legacyContent = ' \tخطة قديمة legacy-ID-42 — “keep exact.”\nLiteral text: <script>alert("legacy")</script>\n  final line \n\t';
const draftEvidenceId = 'b8888888-8888-4888-8888-888888888888';
const legacyPlan: CarePlan = {
  ...suggestion,
  id: 'a7777777-7777-4777-8777-777777777777',
  version: 3,
  sourceType: 'CAREGIVER_SUBMITTED',
  structuredContent: null,
  editable: false,
  content: legacyContent,
};
function renderPlans(fetcher: ReturnType<typeof vi.fn>, path = selected, language = 'en', authenticated = true) {
  vi.stubGlobal('fetch', fetcher); localStorage.setItem('caregiver-language', language);
  if (authenticated) sessionStorage.setItem('caregiver-auth-session-v1', JSON.stringify({ token: testToken(), baseUrl: '' }));
  return render(<I18nProvider><ApiProvider config={{ apiBaseUrl: '' }}><AuthProvider><MemoryRouter initialEntries={[path]}><App /><Link to="/app">Leave plans</Link></MemoryRouter></AuthProvider></ApiProvider></I18nProvider>);
}
function serve(options: { inactive?: boolean; history?: CarePlan[]; active?: CarePlan | null; override?: (url: URL, init: RequestInit) => Response | Promise<Response> | undefined } = {}) {
  let history = options.history ?? [suggestion, active];
  let current = options.active === undefined ? active : options.active;
  return vi.fn().mockImplementation((path: string, init: RequestInit) => {
    const url = new URL(path, 'http://localhost');
    const custom = options.override?.(url, init); if (custom) return Promise.resolve(custom);
    if (url.pathname === '/api/notes') {
      const now = new Date();
      const date = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
      return Promise.resolve(jsonResponse({
        content: [{ ...noteListFixture, id: draftEvidenceId, recipientId: recipient.id, date }],
        number: Number(url.searchParams.get('page')), size: Number(url.searchParams.get('size')),
        totalElements: 1, totalPages: 1, first: true, last: true,
      }));
    }
    if (url.pathname === '/api/recipients') {
      const current = options.inactive ? { ...recipient, active: false, lifecycleStatus: 'ARCHIVED', archivedAt: '2026-10-15T10:00:00Z', archivedBy: 'b1111111-1111-4111-8111-111111111111' } : recipient;
      return Promise.resolve(jsonResponse([current, other]));
    }
    if (url.pathname.endsWith(`/recipients/${recipient.id}`)) {
      const current = options.inactive ? { ...recipient, active: false, lifecycleStatus: 'ARCHIVED', archivedAt: '2026-10-15T10:00:00Z', archivedBy: 'b1111111-1111-4111-8111-111111111111' } : recipient;
      return Promise.resolve(jsonResponse(current));
    }
    if (url.pathname.endsWith(`/recipients/${other.id}`)) return Promise.resolve(jsonResponse(other));
    if (init.method === 'POST') {
      if (url.pathname.endsWith('/edit-and-accept')) {
        const original = history.find((plan) => plan.id === suggestion.id)!;
        const payload = JSON.parse(init.body as string);
        const structuredContent = { ...original.structuredContent!, changes: original.structuredContent!.changes.map((change, index) => ({ ...change, params: payload.changes.find((item: { index: number }) => item.index === index).params })) };
        const edited = { ...original, id: 'f6666666-6666-4666-8666-666666666666', version: Math.max(...history.map((plan) => plan.version)) + 1,
          status: 'EDITED_AND_ACCEPTED', sourceType: 'CAREGIVER_EDITED', sourcePlanId: original.id, editable: false, structuredContent, content: JSON.stringify(structuredContent) };
        history = [edited, ...history.map((plan) => ({ ...plan, status: 'ARCHIVED', editable: false }))]; current = edited;
        return Promise.resolve(jsonResponse({ ...edited, createdAt: null }));
      }
      if (url.pathname.endsWith('/plans/suggestions')) {
        const payload = JSON.parse(init.body as string);
        const created = {
          ...suggestion,
          id: 'f7777777-7777-4777-8777-777777777777',
          recipientId: recipient.id,
          version: Math.max(...history.map((plan) => plan.version)) + 1,
          status: 'SUGGESTED',
          content: '',
          sourceSummaryId: null,
          sourcePlanId: null,
          evidenceNoteIds: null,
          createdAt: '2026-10-18T12:00:00Z',
          sourceType: 'CAREGIVER_SUBMITTED',
          editable: false,
          structuredContent: payload.structuredContent,
        };
        history = [created, ...history];
        return Promise.resolve(jsonResponse(created, 201));
      }
      const dismiss = url.pathname.endsWith('/dismiss');
      history = history.map((plan) => plan.id === suggestion.id ? { ...plan, status: dismiss ? 'DISMISSED' : 'ACCEPTED', ...(plan.editable !== undefined ? { editable: false } : {}) }
        : !dismiss && plan.id === current?.id ? { ...plan, status: 'ARCHIVED' } : plan);
      if (!dismiss) current = history.find((plan) => plan.id === suggestion.id)!;
      return Promise.resolve(jsonResponse({ ...current, createdAt: null }));
    }
    if (url.pathname.includes(other.id)) return Promise.resolve(jsonResponse(url.pathname.endsWith('/history') ? [] : { code: 'NOT_FOUND' }, url.pathname.endsWith('/history') ? 200 : 404));
    return Promise.resolve(url.pathname.endsWith('/history') ? jsonResponse(history) : current ? jsonResponse(current) : jsonResponse({ code: 'NOT_FOUND' }, 404));
  });
}
const writes = (fetcher: ReturnType<typeof vi.fn>) => fetcher.mock.calls.filter(([, init]) => init.method === 'POST');
const suggestions = () => screen.getByRole('region', { name: 'Suggested plans' });
async function loaded() { return screen.findByRole('region', { name: 'Active accepted plan' }); }
async function begin(action = 'Accept suggestion') { await userEvent.click(within(suggestions()).getByRole('button', { name: action })); }

describe('care-plan review and authoritative backend decisions', () => {
  it('protects the deep link before any plan/recipient requests', () => {
    const fetcher = vi.fn(); renderPlans(fetcher, selected, 'en', false);
    expect(screen.getByRole('heading', { name: 'Caregiver sign in' })).toBeInTheDocument(); expect(fetcher).not.toHaveBeenCalled();
  });
  it('waits for explicit recipient selection, then loads only that recipient', async () => {
    const fetcher = serve(); renderPlans(fetcher, root);
    await screen.findByLabelText('Care recipient');
    expect(fetcher).toHaveBeenCalledTimes(1);
    await userEvent.selectOptions(screen.getByLabelText('Care recipient'), recipient.id); await loaded();
    expect(fetcher.mock.calls.every(([path, init]) => (path === '/api/recipients' || path.includes(recipient.id)) && new Headers(init.headers).has('Authorization'))).toBe(true);
    expect(document.title).toMatch(/^Care plans/);
    expect(screen.getByRole('link', { name: 'Care plans' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', { name: 'History' })).not.toHaveAttribute('aria-current');
  });
  it('handles no recipients and invalid/unknown URL IDs without fetching plans', async () => {
    const fetcher = serve({ override: (url) => url.pathname === '/api/recipients' ? jsonResponse([]) : undefined });
    const view = renderPlans(fetcher); await screen.findByText('No care recipients yet');
    expect(screen.getByRole('link', { name: 'Add care recipient' })).toBeInTheDocument(); expect(fetcher).toHaveBeenCalledTimes(1);
    view.unmount(); const invalid = serve(); renderPlans(invalid, `${root}?recipientId=../other`);
    await screen.findByText('Choose an available care recipient to continue.'); expect(invalid).toHaveBeenCalledTimes(1);
  });
  it('renders separate accepted/suggested/history records and preserves mixed text and protected content', async () => {
    const fetcher = serve(); const view = renderPlans(fetcher); const section = await loaded();
    expect(within(section).getByText(active.content, { normalizer: (value) => value })).toHaveAttribute('dir', 'auto');
    expect(within(suggestions()).getByText(/مُراجعة notes/).textContent).toBe(suggestion.content);
    expect(view.container.querySelector('script')).toBeNull();
    expect(within(suggestions()).queryByRole('textbox')).not.toBeInTheDocument();
    expect(within(suggestions()).getByRole('button', { name: 'Edit and accept' })).toBeDisabled();
    expect(within(suggestions()).getByRole('link', { name: 'e5555555-5555-4555-8555-555555555555' })).toHaveAttribute('href', '/app/notes/e5555555-5555-4555-8555-555555555555');
    expect(within(suggestions()).getByText('reference:/external').tagName).toBe('BDI');
    expect(within(suggestions()).getByText('summary-2').closest('a')).toBeNull();
    expect(within(suggestions()).getByText(/AI authorship cannot be confirmed/)).toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'Plan history' }).querySelectorAll('details')).toHaveLength(2);
  });
  it('rendersOpaqueLegacyPlanContentWithoutStructuredEditing', async () => {
    renderPlans(serve({ history: [legacyPlan, active] }));
    await loaded();
    const content = within(suggestions()).getByText((_text, element) => element?.classList.contains('plan-content') ?? false);
    expect(content).toHaveAttribute('dir', 'auto');
    expect(content.textContent).toBe(legacyContent);
    expect(content.querySelector('script')).toBeNull();
    expect(suggestions().querySelector('.plan-structured')).toBeNull();
    expect(within(suggestions()).getByRole('button', { name: 'Accept suggestion' })).toBeEnabled();
    expect(within(suggestions()).getByRole('button', { name: 'Dismiss suggestion' })).toBeEnabled();
    const edit = within(suggestions()).getByRole('button', { name: 'Edit and accept' });
    expect(edit).toBeDisabled();
    await userEvent.click(edit);
    expect(within(suggestions()).queryByRole('textbox')).not.toBeInTheDocument();
  });
  it('keepsOpaqueLegacyPlanVisibleInHistoryBesideStructuredVersions', async () => {
    const newerStructured: CarePlan = {
      ...protectedActive,
      id: 'f8888888-8888-4888-8888-888888888888',
      recipientId: recipient.id,
      version: 2,
      status: 'ARCHIVED',
    };
    const olderLegacy = { ...legacyPlan, version: 1, status: 'DISMISSED' };
    renderPlans(serve({ active: null, history: [newerStructured, olderLegacy] }));
    await loaded();
    const history = screen.getByRole('region', { name: 'Plan history' });
    const entries = [...history.querySelectorAll('details')];
    expect(entries).toHaveLength(2);
    expect(entries[0]).toHaveTextContent('Version 2');
    expect(entries[1]).toHaveTextContent('Version 1');
    await userEvent.click(entries[0].querySelector('summary')!);
    await userEvent.click(entries[1].querySelector('summary')!);
    expect(entries[0].querySelector('.plan-structured')).toBeInTheDocument();
    const content = entries[1].querySelector('.plan-content');
    expect(content).toBeVisible();
    expect(content).toHaveAttribute('dir', 'auto');
    expect(content?.textContent).toBe(legacyContent);
    expect(within(entries[1]).queryByRole('button', { name: 'Edit and accept' })).not.toBeInTheDocument();
    expect(within(entries[1]).queryByRole('textbox')).not.toBeInTheDocument();
  });
  it('opens the linked original in history while preserving edited and original versions', async () => {
    const edited = { ...suggestion, status: 'EDITED_AND_ACCEPTED', sourcePlanId: active.id, content: 'Edited saved text' };
    const original = { ...active, status: 'ARCHIVED' };
    renderPlans(serve({ active: edited, history: [edited, original] })); const section = await loaded();
    await userEvent.click(within(section).getByRole('link', { name: active.id }));
    const details = document.getElementById(`plan-history-${active.id}`)!;
    expect(details).toHaveAttribute('open'); expect(details.querySelector('summary')).toHaveFocus();
    expect(within(details).getByText(active.content, { normalizer: (value) => value })).toBeInTheDocument();
    expect(within(section).getByText('Edited saved text')).toBeInTheDocument();
  });
  it('requires confirmation and returns keyboard focus on cancellation without writing', async () => {
    const fetcher = serve(); renderPlans(fetcher); await loaded(); await begin();
    expect(screen.getByRole('button', { name: 'Cancel' })).toHaveFocus(); expect(writes(fetcher)).toHaveLength(0);
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(within(suggestions()).getByRole('button', { name: 'Accept suggestion' })).toHaveFocus(); expect(writes(fetcher)).toHaveLength(0);
  });
  it.each(['accept', 'dismiss'])('confirms %s using one bodyless POST then reloads the committed snapshot', async (action) => {
    const fetcher = serve(); renderPlans(fetcher); await loaded();
    await begin(action === 'accept' ? 'Accept suggestion' : 'Dismiss suggestion');
    await userEvent.click(screen.getByRole('button', { name: action === 'accept' ? 'Confirm acceptance' : 'Confirm dismissal' }));
    const status = await screen.findByText(action === 'accept' ? /The server accepted the suggestion/ : /The server dismissed the suggestion/);
    expect(status.closest('[role=status]')).toHaveFocus();
    expect(writes(fetcher)).toHaveLength(1); expect(writes(fetcher)[0][0]).toBe(`/api/recipients/${recipient.id}/plans/${suggestion.id}/${action}`);
    expect(writes(fetcher)[0][1].body).toBeUndefined();
    expect(within(suggestions()).queryByRole('button', { name: 'Accept suggestion' })).not.toBeInTheDocument();
    const current = screen.getByRole('region', { name: 'Active accepted plan' });
    expect(within(current).getByText(action === 'accept' ? /مُراجعة notes/ : active.content, { normalizer: (value) => value })).toBeInTheDocument();
    expect(fetcher.mock.calls.filter(([path]) => path.endsWith('/history'))).toHaveLength(2);
  });
  it('blocks duplicate decisions and changing recipients during an in-flight POST', async () => {
    let finish!: (response: Response) => void;
    const fetcher = serve({ override: (_url, init) => init.method === 'POST' ? new Promise<Response>((resolve) => { finish = resolve; }) : undefined });
    renderPlans(fetcher); await loaded(); await begin();
    const confirm = screen.getByRole('button', { name: 'Confirm acceptance' });
    fireEvent.click(confirm); fireEvent.click(confirm);
    expect(writes(fetcher)).toHaveLength(1); expect(screen.getByLabelText('Care recipient')).toBeDisabled();
    expect(screen.getByText('Saving your decision…').closest('[role=status]')).toHaveFocus();
    await act(async () => finish(jsonResponse({}, 409)));
    expect(screen.getByLabelText('Care recipient')).toBeEnabled();
  });
  it('handles invalid transitions safely and requires a read refresh before allowing another decision', async () => {
    const fetcher = serve({ override: (_url, init) => init.method === 'POST' ? jsonResponse({ code: 'INVALID_PLAN_TRANSITION', message: 'private diagnostic' }, 409) : undefined });
    renderPlans(fetcher); await loaded(); await begin(); await userEvent.click(screen.getByRole('button', { name: 'Confirm acceptance' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('can no longer use that action'); expect(screen.getByRole('alert')).toHaveFocus();
    expect(screen.getByRole('alert')).not.toHaveTextContent('private diagnostic');
    expect(within(suggestions()).getByRole('button', { name: 'Accept suggestion' })).toBeDisabled(); expect(writes(fetcher)).toHaveLength(1);
    await userEvent.click(screen.getByRole('button', { name: 'Reload saved plans' }));
    await screen.findByText('Latest saved plans loaded.');
    expect(within(suggestions()).getByRole('button', { name: 'Accept suggestion' })).toBeEnabled(); expect(writes(fetcher)).toHaveLength(1);
  });
  it('treats a lost/server-failed decision as uncertain and does not automatically replay it', async () => {
    const fetcher = serve({ override: (_url, init) => init.method === 'POST' ? Promise.reject(new TypeError('lost response')) : undefined });
    renderPlans(fetcher); await loaded(); await begin('Dismiss suggestion'); await userEvent.click(screen.getByRole('button', { name: 'Confirm dismissal' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('may have been saved'); expect(writes(fetcher)).toHaveLength(1);
    await userEvent.click(screen.getByRole('button', { name: 'Reload saved plans' })); await screen.findByText('Latest saved plans loaded.'); expect(writes(fetcher)).toHaveLength(1);
  });
  it('keeps inactive recipients reviewable with all decisions disabled', async () => {
    const fetcher = serve({ inactive: true }); renderPlans(fetcher); await loaded();
    expect(screen.getByText(/This recipient is inactive/)).toBeInTheDocument();
    expect(within(suggestions()).getByRole('button', { name: 'Accept suggestion' })).toBeDisabled(); expect(writes(fetcher)).toHaveLength(0);
  });
  it('distinguishes empty plans from route errors and refuses inconsistent active/history reads', async () => {
    const empty = renderPlans(serve({ history: [], active: null })); await screen.findByText('No accepted plan yet');
    expect(screen.getByText('No plan history yet')).toBeInTheDocument(); empty.unmount();
    renderPlans(serve({ history: [suggestion] }));
    expect(await screen.findByRole('alert')).toHaveTextContent('changed while it was loading'); expect(screen.queryByRole('button', { name: 'Accept suggestion' })).not.toBeInTheDocument();
  });
  it('renders unknown statuses without exposing actions', async () => {
    renderPlans(serve({ active: null, history: [{ ...suggestion, status: 'toString' }] })); await loaded();
    expect(screen.getAllByText('toString').length).toBeGreaterThan(0); expect(screen.queryByRole('button', { name: 'Accept suggestion' })).not.toBeInTheDocument();
  });
  it('creates a suggested draft without activating it and reloads the server history', async () => {
    const fetcher = serve(); renderPlans(fetcher); await loaded();
    await userEvent.type(await screen.findByLabelText('Reason for this action'), 'Review the loaded note.');
    await userEvent.click(screen.getByRole('checkbox', { name: new RegExp(draftEvidenceId) }));
    await userEvent.click(screen.getByRole('button', { name: 'Save as suggested draft' }));
    await screen.findByText('A new structured draft was saved as a suggestion. It has not been accepted.');
    const create = writes(fetcher).find(([path]) => path.endsWith('/plans/suggestions'));
    expect(create?.[0]).toBe(`/api/recipients/${recipient.id}/plans/suggestions`);
    expect(JSON.parse(create?.[1].body as string)).toEqual({
      content: '',
      structuredContent: {
        schemaVersion: 1,
        changes: [{ action: 'SCHEDULE_CHECK_IN', params: { withinDays: '3' }, reason: 'Review the loaded note.', evidenceNoteIds: [draftEvidenceId] }],
        medications: [],
      },
    });
    expect(writes(fetcher)).toHaveLength(1);
    expect(within(suggestions()).getAllByText('Suggested — not accepted').length).toBeGreaterThan(0);
    expect(fetcher.mock.calls.filter(([path]) => path.endsWith('/history'))).toHaveLength(2);
  });
  it('exposes only the five supported actions with their exact parameter fields', async () => {
    renderPlans(serve()); await loaded();
    const action = await screen.findByLabelText('Action') as HTMLSelectElement;
    expect(Array.from(action.options, (option) => option.value)).toEqual([
      'SCHEDULE_CHECK_IN', 'UPDATE_MEAL_REMINDER', 'LOG_MOBILITY_GOAL', 'CAREGIVER_EDUCATION_TOPIC', 'ESCALATE_TO_DOCTOR',
    ]);
    const parameters: Record<string, string[]> = {
      SCHEDULE_CHECK_IN: ['Within days'],
      UPDATE_MEAL_REMINDER: ['Meal', 'Time'],
      LOG_MOBILITY_GOAL: ['Activity', 'Frequency'],
      CAREGIVER_EDUCATION_TOPIC: ['Topic'],
      ESCALATE_TO_DOCTOR: ['Escalation reason'],
    };
    const allParameters = Object.values(parameters).flat();
    for (const [actionId, expected] of Object.entries(parameters)) {
      await userEvent.selectOptions(action, actionId);
      for (const label of allParameters) {
        if (expected.includes(label)) expect(screen.getByLabelText(label)).toBeInTheDocument();
        else expect(screen.queryByLabelText(label)).not.toBeInTheDocument();
      }
    }
    const medications = screen.getByRole('region', { name: 'Medication record — read-only' });
    expect(within(medications).queryByRole('textbox')).not.toBeInTheDocument();
  });
  it('rejects invalid check-in days before posting a draft', async () => {
    const fetcher = serve(); renderPlans(fetcher); await loaded();
    await userEvent.clear(await screen.findByLabelText('Within days'));
    await userEvent.type(screen.getByLabelText('Within days'), '0');
    await userEvent.type(screen.getByLabelText('Reason for this action'), 'Review the loaded note.');
    await userEvent.click(screen.getByRole('checkbox', { name: new RegExp(draftEvidenceId) }));
    await userEvent.click(screen.getByRole('button', { name: 'Save as suggested draft' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Enter an integer from 1 through 365');
    expect(writes(fetcher)).toHaveLength(0);
  });
  it.each(['reason', 'evidence'])('rejects a draft with missing %s before the request', async (missing) => {
    const fetcher = serve(); renderPlans(fetcher); await loaded();
    await screen.findByRole('checkbox', { name: new RegExp(draftEvidenceId) });
    if (missing === 'reason') await userEvent.click(screen.getByRole('checkbox', { name: new RegExp(draftEvidenceId) }));
    else await userEvent.type(screen.getByLabelText('Reason for this action'), 'Review the loaded note.');
    await userEvent.click(screen.getByRole('button', { name: 'Save as suggested draft' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(missing === 'reason' ? 'Enter a reason' : 'Choose at least one note');
    expect(writes(fetcher)).toHaveLength(0);
  });
  it('keeps legacy free text visible and unchanged after creating a structured draft', async () => {
    const fetcher = serve({ history: [legacyPlan, active] }); renderPlans(fetcher); await loaded();
    await userEvent.type(await screen.findByLabelText('Reason for this action'), 'Review the loaded note.');
    await userEvent.click(screen.getByRole('checkbox', { name: new RegExp(draftEvidenceId) }));
    await userEvent.click(screen.getByRole('button', { name: 'Save as suggested draft' }));
    await screen.findByText('A new structured draft was saved as a suggestion. It has not been accepted.');
    const content = within(suggestions()).getByText((_text, element) => element?.classList.contains('plan-content') ?? false);
    expect(content.textContent).toBe(legacyContent);
    expect(writes(fetcher)).toHaveLength(1);
  });
  it('cancels old recipient reads and ignores a late result', async () => {
    let resolve!: (response: Response) => void; let oldSignal!: AbortSignal;
    const fetcher = serve({ override: (url, init) => url.pathname.includes(recipient.id) && url.pathname.endsWith('/history')
      ? new Promise<Response>((done) => { resolve = done; oldSignal = init.signal!; }) : undefined });
    renderPlans(fetcher); await waitFor(() => expect(resolve).toBeDefined());
    await userEvent.selectOptions(screen.getByLabelText('Care recipient'), other.id); await screen.findByText('No accepted plan yet');
    expect(oldSignal.aborted).toBe(true); await act(async () => resolve(jsonResponse([suggestion, active])));
    expect(screen.queryByText(/مُراجعة notes/)).not.toBeInTheDocument();
  });
  it('ignores an abandoned decision response and never replays it after navigation', async () => {
    let resolve!: (response: Response) => void; let oldSignal!: AbortSignal;
    const fetcher = serve({ override: (_url, init) => init.method === 'POST' ? new Promise<Response>((done) => { resolve = done; oldSignal = init.signal!; }) : undefined });
    renderPlans(fetcher); await loaded(); await begin(); await userEvent.click(screen.getByRole('button', { name: 'Confirm acceptance' }));
    await userEvent.click(screen.getByRole('link', { name: 'Leave plans' })); expect(oldSignal.aborted).toBe(true);
    await act(async () => resolve(jsonResponse({}))); expect(screen.getByRole('heading', { name: 'Caregiver workspace' })).toBeInTheDocument(); expect(writes(fetcher)).toHaveLength(1);
  });
  it('times out a stalled read and ignores its later data', async () => {
    let resolve!: (response: Response) => void;
    const fetcher = serve({ override: (url) => url.pathname.endsWith('/history') ? new Promise<Response>((done) => { resolve = done; }) : undefined });
    vi.useFakeTimers(); renderPlans(fetcher);
    await act(async () => {});
    await act(async () => { vi.advanceTimersByTime(20_000); });
    expect(screen.getByRole('alert')).toHaveTextContent('could not be loaded');
    await act(async () => resolve(jsonResponse([suggestion, active])));
    expect(screen.queryByRole('button', { name: 'Accept suggestion' })).not.toBeInTheDocument();
  });
  it('treats a timed-out decision as uncertain without resubmitting it', async () => {
    const fetcher = serve({ override: (_url, init) => init.method === 'POST' ? new Promise<Response>((_resolve, reject) => {
      init.signal!.addEventListener('abort', () => reject(new DOMException('Timed out', 'AbortError')), { once: true });
    }) : undefined });
    renderPlans(fetcher); await loaded(); await begin(); vi.useFakeTimers();
    fireEvent.click(screen.getByRole('button', { name: 'Confirm acceptance' }));
    await act(async () => { vi.advanceTimersByTime(20_000); });
    expect(screen.getByRole('alert')).toHaveTextContent('may have been saved');
    expect(within(suggestions()).getByRole('button', { name: 'Accept suggestion' })).toBeDisabled(); expect(writes(fetcher)).toHaveLength(1);
  });
  it('retries a read error without exposing diagnostics and clears a rejected session', async () => {
    let fail = true;
    const fetcher = serve({ override: (url) => fail && url.pathname.endsWith('/history') ? jsonResponse({ message: 'private diagnostic' }, 503) : undefined });
    const view = renderPlans(fetcher); expect(await screen.findByRole('alert')).not.toHaveTextContent('private diagnostic');
    fail = false; await userEvent.click(screen.getByRole('button', { name: 'Retry' })); await loaded(); view.unmount();
    renderPlans(serve({ override: (url) => url.pathname.endsWith('/history') ? jsonResponse({}, 401) : undefined }));
    expect(await screen.findByRole('heading', { name: 'Caregiver sign in' })).toBeInTheDocument(); expect(sessionStorage.getItem('caregiver-auth-session-v1')).toBeNull();
  });
  it('switches Arabic/English and direction without refetching or rewriting plan text', async () => {
    const fetcher = serve(); renderPlans(fetcher, selected, 'ar'); await screen.findByRole('heading', { name: 'خطط الرعاية' });
    await screen.findByRole('region', { name: 'الخطة المقبولة النشطة' });
    expect(document.documentElement).toHaveAttribute('dir', 'rtl'); const calls = fetcher.mock.calls.length;
    await userEvent.click(screen.getByRole('button', { name: 'Switch to English' }));
    expect(document.documentElement).toHaveAttribute('dir', 'ltr'); expect(fetcher).toHaveBeenCalledTimes(calls);
    expect(within(suggestions()).getByText(/مُراجعة notes/).textContent).toBe(suggestion.content);
  });

  it('reviews protected edits, posts only allowed parameter values and preserves the AI original and medication record', async () => {
    const fetcher = serve({ active: protectedActive, history: [protectedSuggestion, protectedActive] });
    renderPlans(fetcher); await loaded();
    expect(within(suggestions()).getByText('AI-generated original')).toBeInTheDocument();
    const meds = within(suggestions()).getByRole('region', { name: 'Medication record — read-only' });
    expect(within(meds).getByText('SYNTHETIC_REPORTED_ITEM')).toBeInTheDocument(); expect(within(meds).queryByRole('textbox')).not.toBeInTheDocument();
    await userEvent.click(within(suggestions()).getByRole('button', { name: 'Edit and accept' }));
    expect(within(suggestions()).getByLabelText('Within days')).toHaveFocus();
    await userEvent.clear(within(suggestions()).getByLabelText('Within days')); await userEvent.type(within(suggestions()).getByLabelText('Within days'), '10');
    await userEvent.click(screen.getByRole('button', { name: 'Review changes' }));
    expect(screen.getByRole('heading', { name: 'Review and accept a new version' })).toHaveFocus(); expect(writes(fetcher)).toHaveLength(0);
    await userEvent.click(screen.getByRole('button', { name: 'Confirm edit and acceptance' }));
    expect(await screen.findByText(/The server saved a new edited/)).toBeInTheDocument();
    expect(writes(fetcher)).toHaveLength(1);
    expect(JSON.parse(writes(fetcher)[0][1].body)).toEqual({ expectedVersion: 2, changes: [{ index: 0, params: { withinDays: '10' } }] });
    const accepted = screen.getByRole('region', { name: 'Active accepted plan' });
    expect(within(accepted).getByText('Caregiver-edited version')).toBeInTheDocument();
    expect(within(accepted).getByText('SYNTHETIC_REPORTED_ITEM')).toBeInTheDocument();
    await userEvent.click(within(accepted).getByRole('link', { name: protectedSuggestion.id }));
    const original = document.getElementById(`plan-history-${protectedSuggestion.id}`)!;
    expect(original).toHaveAttribute('open'); expect(within(original).getByText('AI-generated original')).toBeInTheDocument();
    expect(within(original).getByText('7')).toBeInTheDocument(); expect(within(original).getByText('reported-value')).toBeInTheDocument();
  });
  it('validates empty/unchanged edits without a write and restores focus on cancel', async () => {
    const fetcher = serve({ active: protectedActive, history: [protectedSuggestion, protectedActive] }); renderPlans(fetcher); await loaded();
    const button = within(suggestions()).getByRole('button', { name: 'Edit and accept' }); await userEvent.click(button);
    await userEvent.click(screen.getByRole('button', { name: 'Review changes' })); expect(screen.getByRole('alert')).toHaveTextContent('change at least one parameter');
    await userEvent.clear(within(suggestions()).getByLabelText('Within days')); await userEvent.click(screen.getByRole('button', { name: 'Review changes' }));
    expect(within(suggestions()).getByLabelText('Within days')).toHaveFocus(); expect(writes(fetcher)).toHaveLength(0);
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' })); expect(button).toHaveFocus();
  });
  it.each([409, 422])('handles protected-edit rejection HTTP %s without retrying or changing the original', async (code) => {
    const fetcher = serve({ active: protectedActive, history: [protectedSuggestion, protectedActive], override: (_url, init) => init.method === 'POST' ? jsonResponse({ code: 'INVALID_PLAN_TRANSITION' }, code) : undefined });
    renderPlans(fetcher); await loaded(); await userEvent.click(within(suggestions()).getByRole('button', { name: 'Edit and accept' }));
    await userEvent.type(within(suggestions()).getByLabelText('Within days'), '0'); await userEvent.click(screen.getByRole('button', { name: 'Review changes' }));
    await userEvent.click(screen.getByRole('button', { name: 'Confirm edit and acceptance' }));
    expect(await screen.findByRole('alert')).toBeInTheDocument(); expect(writes(fetcher)).toHaveLength(1);
    expect(within(suggestions()).getByText('reported-schedule')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Confirm edit and acceptance' })).toBeDisabled();
  });

  it('requires confirmation before accepting a suggested version', async () => {
    const fetcher = serve(); renderPlans(fetcher); await loaded(); await begin();
    // Test A: confirmation UI appears before any POST, confirm sends the exact endpoint.
    expect(screen.getByRole('alertdialog', { name: 'Confirm acceptance' })).toBeInTheDocument();
    expect(writes(fetcher)).toHaveLength(0);
    await userEvent.click(screen.getByRole('button', { name: 'Confirm acceptance' }));
    await screen.findByText(/The server accepted the suggestion/);
    expect(writes(fetcher)).toHaveLength(1);
    expect(writes(fetcher)[0][0]).toBe(`/api/recipients/${recipient.id}/plans/${suggestion.id}/accept`);
    expect(writes(fetcher)[0][1].method).toBe('POST');
    expect(writes(fetcher)[0][1].body).toBeUndefined();
  });

  it('does not show lifecycle actions on historical versions', async () => {
    // Test B: ARCHIVED and DISMISSED history entries are strictly read-only.
    const archived: CarePlan = { ...active, status: 'ARCHIVED' };
    const dismissed: CarePlan = { ...suggestion, status: 'DISMISSED', editable: false };
    renderPlans(serve({ active: null, history: [dismissed, archived] })); await loaded();
    const history = screen.getByRole('region', { name: 'Plan history' });
    const entries = [...history.querySelectorAll('details.plan-history-version')];
    expect(entries).toHaveLength(2);
    for (const entry of entries) {
      expect(within(entry as HTMLElement).queryByRole('button', { name: 'Accept suggestion' })).not.toBeInTheDocument();
      expect(within(entry as HTMLElement).queryByRole('button', { name: 'Dismiss suggestion' })).not.toBeInTheDocument();
      expect(within(entry as HTMLElement).queryByRole('button', { name: 'Edit and accept' })).not.toBeInTheDocument();
      expect(within(entry as HTMLElement).queryByRole('textbox')).not.toBeInTheDocument();
    }
  });

  it('shows version status and created time for every historical plan', async () => {
    // Test C: every history entry summary carries version, status and created timestamp.
    const archived: CarePlan = { ...active, status: 'ARCHIVED' };
    const dismissed: CarePlan = { ...suggestion, status: 'DISMISSED', editable: false };
    renderPlans(serve({ active: null, history: [dismissed, archived] })); await loaded();
    const expectations: Array<[CarePlan, string, string]> = [
      [dismissed, 'Version 2', 'Dismissed'],
      [archived, 'Version 1', 'Archived'],
    ];
    for (const [plan, versionText, statusText] of expectations) {
      const entry = document.getElementById(`plan-history-${plan.id}`)!;
      expect(entry).not.toBeNull();
      const summary = entry.querySelector('summary')!;
      expect(summary).toHaveTextContent(versionText);
      expect(summary).toHaveTextContent(statusText);
      const time = summary.querySelector('time');
      expect(time).not.toBeNull();
      expect(time).toHaveAttribute('dateTime', plan.createdAt);
      expect(time?.textContent).toMatch(/2026/);
      // The expanded card preserves the same timestamp and provenance rendering.
      expect(within(entry).getAllByText(/Created:/).length).toBeGreaterThanOrEqual(2);
    }
  });

  it('reloads active plan and history after acceptance', async () => {
    // Test D: after confirm, GET active+history are re-run and backend state is shown.
    const fetcher = serve(); renderPlans(fetcher); await loaded(); await begin();
    await userEvent.click(screen.getByRole('button', { name: 'Confirm acceptance' }));
    await screen.findByText(/The server accepted the suggestion/);
    const current = screen.getByRole('region', { name: 'Active accepted plan' });
    expect(within(current).getByText(/مُراجعة notes/)).toBeInTheDocument();
    expect(fetcher.mock.calls.filter(([path]) => path.endsWith('/history'))).toHaveLength(2);
    expect(fetcher.mock.calls.filter(([path]) => path.endsWith('/active'))).toHaveLength(2);
    // The previous active version now appears in history as ARCHIVED.
    const previous = document.getElementById(`plan-history-${active.id}`)!;
    expect(previous).toHaveTextContent('Archived');
    const acceptedEntry = document.getElementById(`plan-history-${suggestion.id}`)!;
    expect(acceptedEntry).toHaveTextContent('Accepted');
  });

  it('requires confirmation before dismissing a suggested version', async () => {
    // Test E: dismiss mirrors accept — no POST before confirm, exact endpoint after.
    const fetcher = serve(); renderPlans(fetcher); await loaded(); await begin('Dismiss suggestion');
    expect(screen.getByRole('alertdialog', { name: 'Confirm dismissal' })).toBeInTheDocument();
    expect(writes(fetcher)).toHaveLength(0);
    await userEvent.click(screen.getByRole('button', { name: 'Confirm dismissal' }));
    await screen.findByText(/The server dismissed the suggestion/);
    expect(writes(fetcher)).toHaveLength(1);
    expect(writes(fetcher)[0][0]).toBe(`/api/recipients/${recipient.id}/plans/${suggestion.id}/dismiss`);
    expect(writes(fetcher)[0][1].method).toBe('POST');
    expect(writes(fetcher)[0][1].body).toBeUndefined();
    expect(within(suggestions()).queryByRole('button', { name: 'Accept suggestion' })).not.toBeInTheDocument();
    expect(document.getElementById(`plan-history-${suggestion.id}`)).toHaveTextContent('Dismissed');
  });

  it('shows Edit only as enabled for a suggested editable plan', async () => {
    // Test F: Edit available only when editable === true and status === SUGGESTED.
    const archivedPlan: CarePlan = { ...active, id: 'f1111111-1111-4111-8111-111111111111', version: 4, status: 'ARCHIVED' };
    const dismissedPlan: CarePlan = { ...suggestion, id: 'f2222222-2222-4222-8222-222222222222', version: 5, status: 'DISMISSED', editable: false };
    const fetcher = serve({
      active: protectedActive,
      history: [dismissedPlan, archivedPlan, legacyPlan, protectedSuggestion, protectedActive],
    });
    renderPlans(fetcher); await loaded();
    const editButtons = within(suggestions()).getAllByRole('button', { name: 'Edit and accept' });
    expect(editButtons).toHaveLength(2);
    expect(editButtons.filter((button) => !(button as HTMLButtonElement).disabled)).toHaveLength(1);
    expect(editButtons.filter((button) => (button as HTMLButtonElement).disabled)).toHaveLength(1);
    // The single enabled Edit belongs to the editable protected suggestion (withinDays = 7).
    const enabled = editButtons.find((button) => !(button as HTMLButtonElement).disabled)!;
    expect(enabled.closest('li')).toHaveTextContent('7');
    // Accepted (active) and every historical entry expose no Edit at all.
    expect(within(screen.getByRole('region', { name: 'Active accepted plan' })).queryByRole('button', { name: 'Edit and accept' })).not.toBeInTheDocument();
    const history = screen.getByRole('region', { name: 'Plan history' });
    for (const entry of history.querySelectorAll('details.plan-history-version')) {
      expect(within(entry as HTMLElement).queryByRole('button', { name: 'Edit and accept' })).not.toBeInTheDocument();
      expect(within(entry as HTMLElement).queryByRole('button', { name: 'Accept suggestion' })).not.toBeInTheDocument();
      expect(within(entry as HTMLElement).queryByRole('button', { name: 'Dismiss suggestion' })).not.toBeInTheDocument();
    }
  });

  it('reloads active plan and history after edit-and-accept', async () => {
    // Test G: edited version becomes active; source version remains in history.
    const fetcher = serve({ active: protectedActive, history: [protectedSuggestion, protectedActive] });
    renderPlans(fetcher); await loaded();
    await userEvent.click(within(suggestions()).getByRole('button', { name: 'Edit and accept' }));
    await userEvent.clear(within(suggestions()).getByLabelText('Within days'));
    await userEvent.type(within(suggestions()).getByLabelText('Within days'), '10');
    await userEvent.click(screen.getByRole('button', { name: 'Review changes' }));
    expect(writes(fetcher)).toHaveLength(0);
    await userEvent.click(screen.getByRole('button', { name: 'Confirm edit and acceptance' }));
    await screen.findByText(/The server saved a new edited/);
    expect(writes(fetcher)).toHaveLength(1);
    expect(writes(fetcher)[0][0]).toBe(`/api/recipients/${recipient.id}/plans/${protectedSuggestion.id}/edit-and-accept`);
    expect(JSON.parse(writes(fetcher)[0][1].body)).toEqual({ expectedVersion: 2, changes: [{ index: 0, params: { withinDays: '10' } }] });
    const current = screen.getByRole('region', { name: 'Active accepted plan' });
    expect(within(current).getByText('Caregiver-edited version')).toBeInTheDocument();
    expect(fetcher.mock.calls.filter(([path]) => path.endsWith('/history'))).toHaveLength(2);
    // Source version preserved in history as ARCHIVED, new version as EDITED_AND_ACCEPTED.
    expect(document.getElementById(`plan-history-${protectedSuggestion.id}`)).toHaveTextContent('Archived');
    expect(document.getElementById('plan-history-f6666666-6666-4666-8666-666666666666')).toHaveTextContent('Edited and accepted');
  });
});
