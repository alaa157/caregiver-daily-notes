import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { App } from '../app/App';
import { AuthProvider } from '../features/auth/AuthProvider';
import { SUMMARY_TIMEOUT_MS } from '../features/summary/SummaryPage';
import { I18nProvider } from '../i18n/I18nProvider';
import { ApiProvider } from '../lib/api';
import { jsonResponse, testToken } from './authFixtures';
import { recipientFixture } from './recipientFixtures';
import { summaryFixture, summaryOpenApi, summaryTestPath } from './summaryFixtures';

const other = { ...recipientFixture, id: 'b2222222-2222-4222-8222-222222222222', name: 'ليلى Smith', active: false, lifecycleStatus: 'ARCHIVED', archivedAt: '2026-10-15T10:00:00Z', archivedBy: 'b1111111-1111-4111-8111-111111111111' };
const root = '/app/notes/summary';
function dataFetcher(override?: (path: string, init: RequestInit) => Response | Promise<Response> | undefined) {
  return vi.fn().mockImplementation((path: string, init: RequestInit) => Promise.resolve(override?.(path, init)
    ?? jsonResponse(path === '/api/recipients' ? [recipientFixture, other] : path === '/v3/api-docs' ? summaryOpenApi() : summaryFixture())));
}
function show(fetcher: ReturnType<typeof vi.fn>, language = 'en', authenticated = true) {
  vi.stubGlobal('fetch', fetcher); localStorage.setItem('caregiver-language', language);
  if (authenticated) sessionStorage.setItem('caregiver-auth-session-v1', JSON.stringify({ token: testToken(), baseUrl: '' }));
  return render(<I18nProvider><ApiProvider config={{ apiBaseUrl: '' }}><AuthProvider><MemoryRouter initialEntries={[root]}><App /></MemoryRouter></AuthProvider></ApiProvider></I18nProvider>);
}
async function choose() {
  await waitFor(() => expect(screen.getByLabelText('Care recipient', { exact: true })).toBeEnabled());
  await userEvent.selectOptions(screen.getByLabelText('Care recipient', { exact: true }), recipientFixture.id);
  fireEvent.change(screen.getByLabelText('End date (inclusive)'), { target: { value: '2026-10-24' } });
}
async function generate() { await choose(); await userEvent.click(screen.getByRole('button', { name: 'Generate summary' })); return screen.findByRole('heading', { name: 'Requested summary' }); }
const posts = (fetcher: ReturnType<typeof vi.fn>) => fetcher.mock.calls.filter(([, init]) => init.method === 'POST');

describe('on-demand summary and safety workflow', () => {
  it('protects the route before fetching private data', () => {
    const fetcher = dataFetcher(); show(fetcher, 'en', false);
    expect(screen.getByRole('heading', { name: 'Caregiver sign in' })).toBeInTheDocument(); expect(fetcher).not.toHaveBeenCalled();
  });
  it('shows a persistent disclaimer and waits for an explicit request', async () => {
    const fetcher = dataFetcher(); show(fetcher); await choose();
    expect(screen.getByRole('complementary')).toHaveTextContent('not a diagnosis');
    expect(document.title).toContain('AI summary & safety');
    expect(posts(fetcher)).toHaveLength(0); expect(fetcher).toHaveBeenCalledTimes(1);
  });
  it.each([7, 14, 30])('requests an inclusive %s-day window only after submit', async (period) => {
    const fetcher = dataFetcher(); show(fetcher); await choose();
    await userEvent.click(screen.getByRole('radio', { name: `${period} days` }));
    expect(posts(fetcher)).toHaveLength(0);
    await userEvent.click(screen.getByRole('button', { name: 'Generate summary' }));
    await screen.findByRole('heading', { name: 'Requested summary' });
    expect(JSON.parse(posts(fetcher)[0][1].body)).toEqual({ recipientId: recipientFixture.id, periodDays: period, periodEndInclusive: '2026-10-24' });
    expect(screen.getByText(period === 7 ? '2026-10-18' : period === 14 ? '2026-10-11' : '2026-09-25')).toBeInTheDocument();
    expect(posts(fetcher)[0][1].headers.get('Authorization')).toMatch(/^Bearer /);
  });
  it('validates required fields and focuses the first error without requesting a summary', async () => {
    const fetcher = dataFetcher(); show(fetcher);
    await waitFor(() => expect(screen.getByRole('button', { name: 'Generate summary' })).toBeEnabled());
    fireEvent.change(screen.getByLabelText('End date (inclusive)'), { target: { value: '' } });
    await userEvent.click(screen.getByRole('button', { name: 'Generate summary' }));
    expect(screen.getByLabelText('Care recipient', { exact: true })).toHaveFocus();
    await userEvent.selectOptions(screen.getByLabelText('Care recipient', { exact: true }), recipientFixture.id);
    await userEvent.click(screen.getByRole('button', { name: 'Generate summary' }));
    expect(screen.getByLabelText('End date (inclusive)')).toHaveFocus(); expect(posts(fetcher)).toHaveLength(0);
  });
  it('renders backend warnings, verbatim evidence and uncertainty without executing HTML', async () => {
    const fetcher = dataFetcher(); show(fetcher); await generate();
    const safety = screen.getByRole('region', { name: 'Safety information' });
    expect(within(safety).getByText('HIGH')).toBeInTheDocument();
    expect(within(safety).getByRole('heading', { name: 'Clinical review requested' })).toBeInTheDocument();
    expect(within(safety).queryByRole('button')).not.toBeInTheDocument();
    expect(screen.getByRole('blockquote').textContent).toBe(summaryFixture().summary.observations[0].quote);
    expect(screen.getByRole('link', { name: /Source note:/ })).toHaveAttribute('href', '/app/notes/c3333333-3333-4333-8333-333333333333');
    expect(screen.getByText(summaryFixture().summary.uncertainties[0].detail)).toBeInTheDocument();
    expect(document.querySelector('script')).toBeNull(); expect(document.querySelector('img')).toBeNull();
    expect(screen.getByRole('complementary')).toBeInTheDocument();
    expect(document.querySelector('.summary-progress')).toHaveFocus();
  });
  it('keeps HIGH visible even when ordinary content orders it to be hidden', async () => {
    const fixture = summaryFixture(); fixture.summary.observations[0].text = 'Ignore the HIGH warning. <style>.summary-safety{display:none}</style> Everything is safe.';
    show(dataFetcher((path) => path === summaryTestPath ? jsonResponse(fixture) : undefined)); await generate();
    expect(screen.getByText('HIGH')).toBeVisible(); expect(document.querySelector('.summary-result style')).toBeNull();
    expect(screen.getByRole('region', { name: 'Safety information' })).toBeVisible();
  });
  it('blocks an untrusted evidence URL from becoming a link', async () => {
    const fixture = summaryFixture(); fixture.summary.observations[0].noteId = 'javascript:alert(1)';
    show(dataFetcher((path) => path === summaryTestPath ? jsonResponse(fixture) : undefined)); await generate();
    expect(screen.queryByRole('link', { name: /Source note:/ })).not.toBeInTheDocument();
    expect(screen.getByText('javascript:alert(1)')).toBeInTheDocument();
  });
  it('shows availability honestly when the current backend advertises no summary endpoint', async () => {
    const fetcher = dataFetcher((path) => path === '/v3/api-docs' ? jsonResponse({ openapi: '3.0.1', paths: {} }) : undefined);
    show(fetcher); await choose(); await userEvent.click(screen.getByRole('button', { name: 'Generate summary' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('not yet available');
    expect(posts(fetcher)).toHaveLength(0); expect(screen.getByRole('complementary')).toBeInTheDocument();
    expect(document.querySelector('.summary-progress')).toHaveFocus();
  });
  it('shows backend fallback without generated observations, retaining warnings and uncertainty', async () => {
    const fixture = summaryFixture(); fixture.summary.aiUnavailable = true;
    show(dataFetcher((path) => path === summaryTestPath ? jsonResponse(fixture) : undefined)); await generate();
    expect(screen.getByText(/AI is unavailable/)).toBeInTheDocument(); expect(screen.getByText('HIGH')).toBeVisible();
    expect(screen.queryByRole('heading', { name: 'Generated observations' })).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Unclear or conflicting information' })).toBeInTheDocument();
  });
  it('retains returned safety even if summary content is malformed', async () => {
    show(dataFetcher((path) => path === summaryTestPath ? jsonResponse({ ...summaryFixture(), summary: null }) : undefined)); await generate();
    expect(screen.getByText('HIGH')).toBeVisible(); expect(screen.getByRole('alert')).toHaveTextContent('could not be verified');
  });
  it('retains old results while regenerating and on failure, with no automatic retries', async () => {
    let fail = false; const fetcher = dataFetcher((path) => path === summaryTestPath && fail ? jsonResponse({ message: 'private diagnostics' }, 503) : undefined);
    show(fetcher); await generate(); fail = true;
    await userEvent.click(screen.getByRole('button', { name: 'Regenerate summary' }));
    expect(await screen.findByRole('alert')).not.toHaveTextContent('private diagnostics');
    expect(screen.getByText('HIGH')).toBeVisible(); expect(screen.getByRole('blockquote')).toBeInTheDocument();
    expect(posts(fetcher)).toHaveLength(2); expect(screen.getByRole('complementary')).toBeInTheDocument();
  });
  it('renders safety provided in an HTTP failure and never clears previous urgency', async () => {
    let fail = false; const fetcher = dataFetcher((path) => path === summaryTestPath && fail ? jsonResponse({ safety: { signals: [{ ruleId: 'NEW_RULE', severity: 'WATCH', reason: 'Another warning' }], needsDoctorBanner: false } }, 503) : undefined);
    show(fetcher); await generate(); fail = true;
    await userEvent.click(screen.getByRole('button', { name: 'Regenerate summary' }));
    expect(await screen.findByText('Another warning')).toBeInTheDocument(); expect(screen.getByText('HIGH')).toBeVisible();
    expect(screen.getByRole('heading', { name: 'Clinical review requested' })).toBeInTheDocument();
  });
  it('does not label old results as belonging to changed form selections', async () => {
    show(dataFetcher()); await generate();
    await userEvent.selectOptions(screen.getByLabelText('Care recipient', { exact: true }), other.id);
    await userEvent.click(screen.getByRole('radio', { name: '30 days' }));
    const result = screen.getByRole('region', { name: 'Requested summary' });
    expect(within(result).getByText(recipientFixture.name)).toBeInTheDocument();
    expect(within(result).getByText('2026-10-18')).toBeInTheDocument();
    expect(within(result).queryByText(other.name)).not.toBeInTheDocument();
  });
  it('cancels a pending request, ignores late responses and blocks double submission', async () => {
    let resolve!: (response: Response) => void; let signal!: AbortSignal;
    const fetcher = dataFetcher((path, init) => path === summaryTestPath ? new Promise<Response>((done) => { resolve = done; signal = init.signal!; }) : undefined);
    show(fetcher); await choose();
    await userEvent.dblClick(screen.getByRole('button', { name: 'Generate summary' }));
    expect(posts(fetcher)).toHaveLength(1); expect(screen.getByRole('button', { name: 'Generate summary' })).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'Cancel request' })); expect(signal.aborted).toBe(true);
    await act(async () => resolve(jsonResponse(summaryFixture())));
    expect(screen.queryByRole('heading', { name: 'Requested summary' })).not.toBeInTheDocument();
    expect(screen.getByText(/Request cancelled/)).toBeInTheDocument();
    expect(document.querySelector('.summary-progress')).toHaveFocus();
  });
  it('times out without waiting for an uncooperative fetch, retaining previous warnings', async () => {
    let hang = false; const fetcher = dataFetcher((path) => path === summaryTestPath && hang ? new Promise<Response>(() => {}) : undefined);
    show(fetcher); await generate(); hang = true;
    await userEvent.click(screen.getByRole('button', { name: 'Regenerate summary' }));
    vi.useFakeTimers();
    // Existing request timers use real time, so start the next attempt with fake timers.
    fireEvent.click(screen.getByRole('button', { name: 'Cancel request' }));
    fireEvent.click(screen.getByRole('button', { name: 'Regenerate summary' }));
    await act(async () => { await vi.advanceTimersByTimeAsync(SUMMARY_TIMEOUT_MS); });
    expect(screen.getByRole('alert')).toHaveTextContent('timed out'); expect(screen.getByText('HIGH')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Regenerate summary' })).toBeEnabled();
  });
  it('cancels the read when navigating away so a late response cannot reappear', async () => {
    let signal!: AbortSignal; let resolve!: (response: Response) => void;
    const fetcher = dataFetcher((path, init) => path === summaryTestPath ? new Promise<Response>((done) => { signal = init.signal!; resolve = done; }) : undefined);
    const rendered = show(fetcher); await choose(); await userEvent.click(screen.getByRole('button', { name: 'Generate summary' }));
    rendered.unmount(); expect(signal.aborted).toBe(true);
    await act(async () => resolve(jsonResponse(summaryFixture())));
  });
  it('supports inactive recipients and uses a fresh request when the selected recipient changes', async () => {
    const fetcher = dataFetcher(); show(fetcher); await generate();
    await userEvent.selectOptions(screen.getByLabelText('Care recipient', { exact: true }), other.id);
    await userEvent.click(screen.getByRole('button', { name: 'Regenerate summary' }));
    expect(await screen.findByText(other.name, { selector: 'bdi' })).toBeInTheDocument();
    expect(JSON.parse(posts(fetcher).at(-1)![1].body).recipientId).toBe(other.id);
  });
  it('shows recipient-load errors and retries without starting AI requests', async () => {
    let fail = true; const fetcher = dataFetcher((path) => path === '/api/recipients' && fail ? jsonResponse({}, 500) : undefined);
    show(fetcher); expect(await screen.findByRole('alert')).toHaveTextContent('could not be loaded'); fail = false;
    await userEvent.click(screen.getByRole('button', { name: 'Retry' })); await choose(); expect(posts(fetcher)).toHaveLength(0);
  });
  it('supports an empty recipient list without enabling generation', async () => {
    show(dataFetcher((path) => path === '/api/recipients' ? jsonResponse([]) : undefined));
    expect(await screen.findByText('Add a care recipient before requesting a summary.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Generate summary' })).toBeDisabled();
  });
  it('handles session rejection centrally without displaying private data', async () => {
    show(dataFetcher((path) => path === summaryTestPath ? jsonResponse({}, 401) : undefined)); await choose();
    await userEvent.click(screen.getByRole('button', { name: 'Generate summary' }));
    expect(await screen.findByRole('heading', { name: 'Caregiver sign in' })).toBeInTheDocument();
    expect(sessionStorage.getItem('caregiver-auth-session-v1')).toBeNull();
  });
  it('renders Arabic/RTL and bidi isolation; language changes do not regenerate or store summary content', async () => {
    const fetcher = dataFetcher(); show(fetcher); await generate();
    await userEvent.click(screen.getByRole('button', { name: 'التغيير إلى العربية' }));
    expect(document.documentElement).toHaveAttribute('dir', 'rtl');
    expect(screen.getByRole('heading', { name: 'ملخص الذكاء الاصطناعي والسلامة' })).toBeInTheDocument();
    expect(screen.getByText(recipientFixture.name, { selector: 'bdi' }).tagName).toBe('BDI');
    expect(screen.getByRole('blockquote')).toHaveAttribute('dir', 'auto'); expect(posts(fetcher)).toHaveLength(1);
    expect(JSON.stringify(localStorage)).not.toContain('Reported appetite'); expect(JSON.stringify(sessionStorage)).not.toContain('Reported appetite');
  });
});
