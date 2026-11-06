import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { useEffect, type ReactNode } from 'react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { App } from '../app/App';
import { ThemeProvider } from '../app/ThemeProvider';
import { AppShell } from '../components/AppShell';
import { useByok, type ByokCredentials } from '../features/ai/ByokContext';
import { AuthProvider } from '../features/auth/AuthProvider';
import { LoginPage } from '../features/auth/LoginPage';
import { SUMMARY_TIMEOUT_MS, SummaryPage } from '../features/summary/SummaryPage';
import { ProtectedRoute } from '../app/ProtectedRoute';
import { I18nProvider } from '../i18n/I18nProvider';
import { ApiProvider } from '../lib/api';
import { jsonResponse, testToken } from './authFixtures';
import { recipientFixture } from './recipientFixtures';
import { summaryFixture } from './summaryFixtures';

const other = { ...recipientFixture, id: 'b2222222-2222-4222-8222-222222222222', name: 'ليلى Smith', active: false, lifecycleStatus: 'ARCHIVED' as const, archivedAt: '2026-10-15T10:00:00Z', archivedBy: 'b1111111-1111-4111-8111-111111111111' };
const root = '/app/notes/summary';
const byokSummaryPath = '/api/summaries/byok';
const syntheticCredentials: ByokCredentials = {
  providerId: 'GEMINI',
  model: 'gemini-test-model',
  apiKey: 'synthetic-summary-page-key',
};
const acknowledgementPath = `/api/recipients/${recipientFixture.id}/safety-acknowledgements`;
const acknowledgementFixture = () => ({
  id: 'd4444444-4444-4444-8444-444444444444',
  recipientId: recipientFixture.id,
  ruleId: 'FALL_DETECTED',
  periodEndInclusive: '2026-10-24',
  periodDays: 7,
  acknowledgedAt: '2026-10-24T12:34:56Z',
});
function dataFetcher(override?: (path: string, init: RequestInit) => Response | Promise<Response> | undefined) {
  return vi.fn().mockImplementation((path: string, init: RequestInit) => {
    const acknowledgementRequest = path.includes('/safety-acknowledgements');
    const acknowledgementResponse = acknowledgementRequest
      ? init.method === 'POST' ? jsonResponse(acknowledgementFixture()) : jsonResponse([])
      : undefined;
    return Promise.resolve(override?.(path, init) ?? acknowledgementResponse
      ?? jsonResponse(path === '/api/recipients' ? [recipientFixture, other] : summaryFixture()));
  });
}
function SeedCredentials({ credentials, children }: { credentials: ByokCredentials | null; children: ReactNode }) {
  const { setCredentials } = useByok();
  useEffect(() => { if (credentials) setCredentials(credentials); }, [credentials, setCredentials]);
  return <>{children}</>;
}
function show(fetcher: ReturnType<typeof vi.fn>, language = 'en', credentials: ByokCredentials | null = syntheticCredentials) {
  vi.stubGlobal('fetch', fetcher); localStorage.setItem('caregiver-language', language);
  sessionStorage.setItem('caregiver-auth-session-v1', JSON.stringify({ token: testToken(), baseUrl: '' }));
  return render(<I18nProvider><ApiProvider config={{ apiBaseUrl: '' }}><AuthProvider><ThemeProvider><MemoryRouter initialEntries={[root]}>
    <AppShell><Routes>
      <Route element={<ProtectedRoute />}>
        <Route path={root} element={<SeedCredentials credentials={credentials}><SummaryPage /></SeedCredentials>} />
      </Route>
      <Route path="/login" element={<LoginPage />} />
    </Routes></AppShell>
  </MemoryRouter></ThemeProvider></AuthProvider></ApiProvider></I18nProvider>);
}
function showApp(fetcher: ReturnType<typeof vi.fn>, language = 'en', authenticated = true) {
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
    const fetcher = dataFetcher(); showApp(fetcher, 'en', false);
    expect(screen.getByRole('heading', { name: 'Caregiver sign in' })).toBeInTheDocument(); expect(fetcher).not.toHaveBeenCalled();
  });
  it('marks a safety signal as reviewed without hiding it', async () => {
    const fetcher = dataFetcher();
    show(fetcher); await generate();
    const safety = screen.getByRole('region', { name: 'Safety information' });
    const reason = 'Fall reported in period / تم الإبلاغ عن سقوط';
    const reviewButton = await within(safety).findByRole('button', { name: 'Mark as reviewed' });
    await waitFor(() => expect(reviewButton).toBeEnabled());
    await userEvent.click(reviewButton);
    expect(await within(safety).findByText('Reviewed')).toBeVisible();
    expect(within(safety).getByText('HIGH')).toBeVisible();
    expect(within(safety).getByText(reason)).toBeVisible();
    expect(within(safety).queryByRole('button', { name: 'Mark as reviewed' })).not.toBeInTheDocument();
    const createCall = posts(fetcher).find(([path]) => path === acknowledgementPath);
    expect(createCall).toBeDefined();
    expect(JSON.parse(createCall![1].body as string)).toEqual({
      ruleId: 'FALL_DETECTED',
      periodEndInclusive: '2026-10-24',
      periodDays: 7,
    });
  });
  it('keeps an unacknowledged signal visible and retryable when acknowledgement fails', async () => {
    const fetcher = dataFetcher((path, init) => path === acknowledgementPath && init.method === 'POST'
      ? jsonResponse({ message: 'private diagnostics' }, 500) : undefined);
    show(fetcher); await generate();
    const safety = screen.getByRole('region', { name: 'Safety information' });
    const reviewButton = await within(safety).findByRole('button', { name: 'Mark as reviewed' });
    await waitFor(() => expect(reviewButton).toBeEnabled());
    await userEvent.click(reviewButton);
    expect(await within(safety).findByRole('alert')).toHaveTextContent('could not be marked as reviewed');
    expect(within(safety).getByText('Fall reported in period / تم الإبلاغ عن سقوط')).toBeVisible();
    expect(within(safety).getByText('HIGH')).toBeVisible();
    expect(within(safety).getByRole('button', { name: 'Mark as reviewed' })).toBeEnabled();
    await userEvent.click(within(safety).getByRole('button', { name: 'Mark as reviewed' }));
    await waitFor(() => expect(posts(fetcher).filter(([path]) => path === acknowledgementPath)).toHaveLength(2));
  });
  it('loads review state for the active summary recipient and period', async () => {
    const listPath = `${acknowledgementPath}?periodEndInclusive=2026-10-24&periodDays=7`;
    const fetcher = dataFetcher((path, init) => path === listPath && init.method === 'GET'
      ? jsonResponse([acknowledgementFixture()]) : undefined);
    show(fetcher); await generate();
    const safety = screen.getByRole('region', { name: 'Safety information' });
    expect(await within(safety).findByText('Reviewed')).toBeVisible();
    expect(within(safety).getByText('Fall reported in period / تم الإبلاغ عن سقوط')).toBeVisible();
    expect(within(safety).getByText('HIGH')).toBeVisible();
    expect(within(safety).queryByRole('button', { name: 'Mark as reviewed' })).not.toBeInTheDocument();
    expect(fetcher).toHaveBeenCalledWith(listPath, expect.objectContaining({ method: 'GET' }));
    expect(posts(fetcher).filter(([path]) => path === acknowledgementPath)).toHaveLength(0);
  });
  it('keeps signals visible and retries when review state cannot be loaded', async () => {
    const listPath = `${acknowledgementPath}?periodEndInclusive=2026-10-24&periodDays=7`;
    let fail = true;
    const fetcher = dataFetcher((path, init) => path === listPath && init.method === 'GET'
      ? fail ? jsonResponse({ message: 'private diagnostics' }, 500) : jsonResponse([])
      : undefined);
    show(fetcher); await generate();
    const safety = screen.getByRole('region', { name: 'Safety information' });
    expect(await within(safety).findByRole('alert')).toHaveTextContent('Review status could not be loaded');
    expect(within(safety).getByText('Fall reported in period / تم الإبلاغ عن سقوط')).toBeVisible();
    expect(within(safety).getByText('HIGH')).toBeVisible();
    expect(within(safety).queryByRole('button', { name: 'Mark as reviewed' })).not.toBeInTheDocument();
    fail = false;
    await userEvent.click(within(safety).getByRole('button', { name: 'Retry' }));
    await waitFor(() => expect(within(safety).queryByRole('alert')).not.toBeInTheDocument());
    expect(within(safety).getByRole('button', { name: 'Mark as reviewed' })).toBeEnabled();
  });
  it.each([
    ['invalid record ID', { id: 'not-a-uuid' }],
    ['unknown rule ID', { ruleId: 'UNKNOWN_RULE' }],
    ['unsupported period length', { periodDays: 8 }],
    ['invalid period date', { periodEndInclusive: '2026-02-31' }],
    ['invalid timestamp', { acknowledgedAt: 'not-an-instant' }],
  ])('does not display malformed acknowledgement responses (%s)', async (_case, changes) => {
    const listPath = `${acknowledgementPath}?periodEndInclusive=2026-10-24&periodDays=7`;
    const fetcher = dataFetcher((path, init) => path === listPath && init.method === 'GET'
      ? jsonResponse([{ ...acknowledgementFixture(), ...changes }]) : undefined);
    show(fetcher); await generate();
    const safety = screen.getByRole('region', { name: 'Safety information' });
    expect(await within(safety).findByRole('alert')).toHaveTextContent('Review status could not be loaded');
    expect(within(safety).queryByText('Reviewed')).not.toBeInTheDocument();
    expect(within(safety).queryByRole('button', { name: 'Mark as reviewed' })).not.toBeInTheDocument();
    expect(within(safety).getByText('Fall reported in period / تم الإبلاغ عن سقوط')).toBeVisible();
    expect(within(safety).getByText('HIGH')).toBeVisible();
  });
  it.each([
    ['different period', { periodEndInclusive: '2026-10-17' }],
    ['different recipient', { recipientId: other.id }],
  ])('rejects acknowledgement records for another %s', async (_case, changes) => {
    const listPath = `${acknowledgementPath}?periodEndInclusive=2026-10-24&periodDays=7`;
    const fetcher = dataFetcher((path, init) => path === listPath && init.method === 'GET'
      ? jsonResponse([{ ...acknowledgementFixture(), ...changes }]) : undefined);
    show(fetcher); await generate();
    const safety = screen.getByRole('region', { name: 'Safety information' });
    expect(await within(safety).findByRole('alert')).toHaveTextContent('Review status could not be loaded');
    expect(within(safety).queryByText('Reviewed')).not.toBeInTheDocument();
    expect(within(safety).queryByRole('button', { name: 'Mark as reviewed' })).not.toBeInTheDocument();
    expect(within(safety).getByText('Fall reported in period / تم الإبلاغ عن سقوط')).toBeVisible();
    expect(within(safety).getByText('HIGH')).toBeVisible();
  });
  it('provides English and Arabic acknowledgement labels', async () => {
    const fetcher = dataFetcher();
    show(fetcher); await generate();
    const englishButton = await screen.findByRole('button', { name: 'Mark as reviewed' });
    await waitFor(() => expect(englishButton).toBeEnabled());
    await userEvent.click(screen.getByRole('button', { name: 'التغيير إلى العربية' }));
    const arabicButton = await screen.findByRole('button', { name: 'وضع علامة تمت المراجعة' });
    await waitFor(() => expect(arabicButton).toBeEnabled());
    await userEvent.click(arabicButton);
    expect(await screen.findByText('تمت المراجعة')).toBeVisible();
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
    expect(posts(fetcher).filter(([path]) => path === byokSummaryPath)).toHaveLength(1);
    expect(posts(fetcher)[0][0]).toBe(byokSummaryPath);
    expect(JSON.parse(posts(fetcher)[0][1].body)).toEqual({
      summary: { recipientId: recipientFixture.id, periodDays: period, periodEndInclusive: '2026-10-24' },
      credentials: syntheticCredentials,
    });
    expect(screen.getByText(period === 7 ? '2026-10-18' : period === 14 ? '2026-10-11' : '2026-09-25')).toBeInTheDocument();
    expect(posts(fetcher)[0][1].headers.get('Authorization')).toMatch(/^Bearer /);
    expect(posts(fetcher)[0][1].cache).toBe('no-store');
    expect(posts(fetcher)[0][1].credentials).toBe('omit');
    expect(posts(fetcher)[0][1].redirect).toBe('error');
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
  it('asks caregiver to configure BYOK before summary generation', async () => {
    const fetcher = dataFetcher();
    show(fetcher, 'en', null); await choose();
    await userEvent.click(screen.getByRole('button', { name: 'Generate summary' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Open AI Settings and configure a provider key before generating a summary.');
    expect(fetcher.mock.calls.some(([path]) => path === byokSummaryPath)).toBe(false);
    expect(fetcher.mock.calls.some(([path]) => path === '/api/summaries')).toBe(false);
    expect(screen.getByLabelText('Care recipient', { exact: true })).toBeEnabled();
    expect(screen.getByLabelText('End date (inclusive)')).toBeEnabled();
    expect(screen.getByRole('button', { name: 'Generate summary' })).toBeEnabled();
  });
  it('does not fall back to server provider when caregiver key is missing', async () => {
    const fetcher = dataFetcher();
    show(fetcher, 'en', null); await choose();
    await userEvent.click(screen.getByRole('button', { name: 'Generate summary' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('AI Settings');
    expect(fetcher.mock.calls.map(([path]) => path)).toEqual(['/api/recipients']);
    expect(posts(fetcher)).toHaveLength(0);
  });
  it('renders the missing-credentials instruction in Arabic and keeps RTL layout', async () => {
    const fetcher = dataFetcher();
    show(fetcher, 'ar', null);
    await waitFor(() => expect(screen.getByLabelText('المستفيد من الرعاية', { exact: true })).toBeEnabled());
    await userEvent.selectOptions(screen.getByLabelText('المستفيد من الرعاية', { exact: true }), recipientFixture.id);
    fireEvent.change(screen.getByLabelText('تاريخ النهاية (شامل)'), { target: { value: '2026-10-24' } });
    await userEvent.click(screen.getByRole('button', { name: 'إنشاء ملخص' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('افتح إعدادات الذكاء الاصطناعي واضبط مفتاحًا لمزوّد قبل إنشاء ملخص.');
    expect(document.documentElement).toHaveAttribute('dir', 'rtl');
    expect(fetcher.mock.calls.map(([path]) => path)).toEqual(['/api/recipients']);
  });
  it('sends configured in-memory credentials to BYOK and renders the parsed result', async () => {
    const fetcher = dataFetcher();
    show(fetcher); await generate();

    const summaryCalls = fetcher.mock.calls.filter(([path]) => path === byokSummaryPath);
    expect(summaryCalls).toHaveLength(1);
    expect(summaryCalls[0][1].method).toBe('POST');
    expect(JSON.parse(summaryCalls[0][1].body)).toEqual({
      summary: { recipientId: recipientFixture.id, periodDays: 7, periodEndInclusive: '2026-10-24' },
      credentials: syntheticCredentials,
    });
    expect(fetcher.mock.calls.some(([path]) => path === '/api/summaries' || path === '/v3/api-docs')).toBe(false);
    expect(screen.getByText('AI-generated')).toBeVisible();
    expect(screen.getByText('Educational project. Not medical advice.')).toBeVisible();
    expect(screen.getByText('HIGH')).toBeVisible();
    expect(screen.getByRole('region', { name: 'Recorded observation counts' })).toHaveTextContent('POOR_APPETITE_COUNT');
    expect(document.body.textContent).not.toContain(syntheticCredentials.apiKey);
    expect(JSON.stringify(localStorage)).not.toContain(syntheticCredentials.apiKey);
    expect(JSON.stringify(sessionStorage)).not.toContain(syntheticCredentials.apiKey);
  });
  it('keeps safe translated feedback and safety visible when BYOK generation fails', async () => {
    const key = 'synthetic-summary-error-key';
    const fetcher = dataFetcher((path) => path === byokSummaryPath
      ? jsonResponse({
        message: `raw provider response includes ${key}`,
        safety: summaryFixture().safety,
      }, 503)
      : undefined);
    show(fetcher, 'en', { ...syntheticCredentials, apiKey: key }); await choose();
    await userEvent.click(screen.getByRole('button', { name: 'Generate summary' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('A summary could not be returned.');
    expect(screen.getByRole('region', { name: 'Safety information' })).toHaveTextContent('HIGH');
    expect(document.body.textContent).not.toContain(key);
    expect(document.body.textContent).not.toContain('raw provider response');
    expect(screen.queryByText('AI-generated')).not.toBeInTheDocument();
    expect(fetcher.mock.calls.some(([path]) => path === '/api/summaries')).toBe(false);
  });
  it('labels successful AI content as generated', async () => {
    show(dataFetcher()); await generate();
    const aiSection = screen.getByRole('region', { name: 'Generated observations' });
    const safetySection = screen.getByRole('region', { name: 'Safety information' });
    expect(screen.getByRole('heading', { name: 'Requested summary' })).toBeVisible();
    expect(within(aiSection).getByText('AI-generated')).toBeVisible();
    expect(within(aiSection).getByText('Reported appetite varied. ملاحظة غير مؤكدة.')).toBeVisible();
    const observation = within(aiSection).getByText('Reported appetite varied. ملاحظة غير مؤكدة.').closest('li')!;
    expect(within(observation).getByRole('blockquote').textContent).toBe(summaryFixture().summary.observations[0].quote);
    expect(within(observation).getByRole('link', { name: /Source note:/ })).toHaveAttribute('href', '/app/notes/c3333333-3333-4333-8333-333333333333');
    expect(within(safetySection).queryByText('AI-generated')).not.toBeInTheDocument();
  });
  it('does not show successful presentation when trends are invalid', async () => {
    const fixture = summaryFixture(); fixture.trends[0].delta = 4;
    show(dataFetcher((path) => path === byokSummaryPath ? jsonResponse(fixture) : undefined));
    await choose(); await userEvent.click(screen.getByRole('button', { name: 'Generate summary' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Some summary information could not be verified');
    expect(screen.getByRole('heading', { name: 'Summary information could not be fully verified' })).toBeVisible();
    expect(screen.queryByRole('heading', { name: 'Requested summary' })).not.toBeInTheDocument();
    expect(screen.queryByText('AI-generated')).not.toBeInTheDocument();
    expect(screen.queryByText('Educational project. Not medical advice.')).not.toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'Safety information' })).toHaveTextContent('HIGH');
  });
  it('renders invalid-summary feedback and safety in Arabic', async () => {
    const fixture = summaryFixture(); fixture.trends[0].delta = 4;
    show(dataFetcher((path) => path === byokSummaryPath ? jsonResponse(fixture) : undefined));
    await choose(); await userEvent.click(screen.getByRole('button', { name: 'Generate summary' }));
    await screen.findByRole('alert');
    await userEvent.click(screen.getByRole('button', { name: 'التغيير إلى العربية' }));
    expect(document.documentElement).toHaveAttribute('dir', 'rtl');
    expect(screen.getByRole('heading', { name: 'تعذّر التحقق من معلومات الملخص بالكامل' })).toBeVisible();
    expect(screen.queryByRole('heading', { name: 'الملخص المطلوب' })).not.toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('تعذّر التحقق من بعض معلومات الملخص');
    expect(screen.getByRole('region', { name: 'معلومات السلامة' })).toHaveTextContent('HIGH');
    expect(screen.queryByText('مولّد بالذكاء الاصطناعي')).not.toBeInTheDocument();
    expect(screen.queryByText('مشروع تعليمي. ليس نصيحة طبية.')).not.toBeInTheDocument();
  });
  it('suppresses successful AI presentation after a failed summary request', async () => {
    let fail = false;
    const fetcher = dataFetcher((path) => path === byokSummaryPath && fail
      ? jsonResponse({ safety: { needsDoctorBanner: false, signals: [{ ruleId: 'RETRY_WARNING', severity: 'WATCH', reason: 'Warning returned with failure.' }] } }, 503)
      : undefined);
    show(fetcher); await generate();
    expect(screen.getByText('AI-generated')).toBeVisible();
    fail = true; await userEvent.click(screen.getByRole('button', { name: 'Regenerate summary' }));
    expect(await screen.findByRole('heading', { name: 'Safety information returned with the failed request' })).toBeVisible();
    expect(screen.queryByText('AI-generated')).not.toBeInTheDocument();
    expect(screen.queryByText('Educational project. Not medical advice.')).not.toBeInTheDocument();
    expect(screen.queryByText('Reported appetite varied. ملاحظة غير مؤكدة.')).not.toBeInTheDocument();
    expect(screen.queryByRole('blockquote')).not.toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'Safety information' })).toHaveTextContent('HIGH');
    expect(screen.getByRole('region', { name: 'Safety information' })).toHaveTextContent('RETRY_WARNING');
  });
  it('shows the educational disclaimer with AI content', async () => {
    show(dataFetcher()); await generate();
    const aiSection = screen.getByRole('region', { name: 'Generated observations' });
    expect(within(aiSection).getByText('Educational project. Not medical advice.')).toBeVisible();
    await userEvent.click(screen.getByRole('button', { name: 'التغيير إلى العربية' }));
    expect(await screen.findByText('مشروع تعليمي. ليس نصيحة طبية.')).toBeVisible();
  });
  it('keeps safety signals outside the AI narrative', async () => {
    show(dataFetcher()); await generate();
    const aiSection = screen.getByRole('region', { name: 'Generated observations' });
    const safetySection = screen.getByRole('region', { name: 'Safety information' });
    expect(within(aiSection).getByText('Reported appetite varied. ملاحظة غير مؤكدة.')).toBeVisible();
    expect(within(safetySection).getByText('HIGH')).toBeVisible();
    expect(within(safetySection).getByText(/evaluated independently/)).toBeVisible();
    expect(aiSection.contains(safetySection)).toBe(false);
    expect(screen.getByRole('region', { name: 'Unclear or conflicting information' })).toBeVisible();
    expect(screen.getByRole('region', { name: 'Recorded observation counts' })).toHaveTextContent('POOR_APPETITE_COUNT');
  });
  it('does not show AI success label for unavailable response', async () => {
    const fixture = summaryFixture();
    fixture.summary.aiUnavailable = true;
    fixture.summary.observations[0].text = 'stray unavailable model text';
    show(dataFetcher((path) => path === byokSummaryPath ? jsonResponse(fixture) : undefined));
    await choose(); await userEvent.click(screen.getByRole('button', { name: 'Generate summary' }));
    await screen.findByRole('status');
    expect(screen.getByText(/AI is unavailable/)).toBeVisible();
    expect(screen.queryByRole('heading', { name: 'Requested summary' })).not.toBeInTheDocument();
    expect(screen.queryByText('AI-generated')).not.toBeInTheDocument();
    expect(screen.queryByText('Educational project. Not medical advice.')).not.toBeInTheDocument();
    expect(screen.queryByText('stray unavailable model text')).not.toBeInTheDocument();
    expect(screen.queryByRole('blockquote')).not.toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'Safety information' })).toHaveTextContent('HIGH');
    expect(screen.getByRole('region', { name: 'Recorded observation counts' })).toHaveTextContent('POOR_APPETITE_COUNT');
  });
  it('does not render malformed model observations as successful content', async () => {
    const fixture = summaryFixture();
    fixture.summary.observations[0].text = 'partially parsed model content';
    fixture.summary.observations[0].quote = '';
    show(dataFetcher((path) => path === byokSummaryPath ? jsonResponse(fixture) : undefined));
    await choose(); await userEvent.click(screen.getByRole('button', { name: 'Generate summary' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be verified');
    expect(screen.queryByRole('heading', { name: 'Requested summary' })).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Summary information could not be fully verified' })).toBeVisible();
    expect(screen.queryByText('AI-generated')).not.toBeInTheDocument();
    expect(screen.queryByText('Educational project. Not medical advice.')).not.toBeInTheDocument();
    expect(screen.queryByText('partially parsed model content')).not.toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'Safety information' })).toHaveTextContent('HIGH');
    expect(screen.getByRole('region', { name: 'Recorded observation counts' })).toHaveTextContent('POOR_APPETITE_COUNT');
  });
  it('does not label an empty observation collection as generated', async () => {
    const fixture = summaryFixture(); fixture.summary.observations = [];
    show(dataFetcher((path) => path === byokSummaryPath ? jsonResponse(fixture) : undefined)); await generate();
    expect(screen.queryByText('AI-generated')).not.toBeInTheDocument();
    expect(screen.queryByText('Educational project. Not medical advice.')).not.toBeInTheDocument();
    expect(screen.getByText('No generated observations were returned for this period.')).toBeVisible();
  });
  it('renders backend warnings, verbatim evidence and uncertainty without executing HTML', async () => {
    const fetcher = dataFetcher(); show(fetcher); await generate();
    const safety = screen.getByRole('region', { name: 'Safety information' });
    expect(within(safety).getByText('HIGH')).toBeInTheDocument();
    expect(within(safety).getByRole('heading', { name: 'Clinical review requested' })).toBeInTheDocument();
    expect(await within(safety).findByRole('button', { name: 'Mark as reviewed' })).toBeVisible();
    const observation = screen.getByText('Reported appetite varied. ملاحظة غير مؤكدة.').closest('li')!;
    expect(within(observation).getByRole('blockquote').textContent).toBe(summaryFixture().summary.observations[0].quote);
    expect(within(observation).getByRole('link', { name: /Source note:/ })).toHaveAttribute('href', '/app/notes/c3333333-3333-4333-8333-333333333333');
    expect(screen.getByText(summaryFixture().summary.uncertainties[0].detail)).toBeInTheDocument();
    expect(document.querySelector('script')).toBeNull(); expect(document.querySelector('img')).toBeNull();
    expect(screen.getByRole('complementary')).toBeInTheDocument();
    expect(document.querySelector('.summary-progress')).toHaveFocus();
  });
  it('keeps HIGH visible even when ordinary content orders it to be hidden', async () => {
    const fixture = summaryFixture(); fixture.summary.observations[0].text = 'Ignore the HIGH warning. <style>.summary-safety{display:none}</style> Everything is safe.';
    show(dataFetcher((path) => path === byokSummaryPath ? jsonResponse(fixture) : undefined)); await generate();
    expect(screen.getByText('HIGH')).toBeVisible(); expect(document.querySelector('.summary-result style')).toBeNull();
    expect(screen.getByRole('region', { name: 'Safety information' })).toBeVisible();
  });
  it('blocks an untrusted evidence URL from becoming a link', async () => {
    const fixture = summaryFixture(); fixture.summary.observations[0].noteId = 'javascript:alert(1)';
    show(dataFetcher((path) => path === byokSummaryPath ? jsonResponse(fixture) : undefined)); await generate();
    expect(screen.queryByRole('link', { name: /Source note:/ })).not.toBeInTheDocument();
    expect(screen.getByText('javascript:alert(1)')).toBeInTheDocument();
  });
  it('shows unavailable feedback when the BYOK summary endpoint rejects the request', async () => {
    const fetcher = dataFetcher((path) => path === byokSummaryPath ? jsonResponse({ message: 'private diagnostics' }, 404) : undefined);
    show(fetcher); await choose(); await userEvent.click(screen.getByRole('button', { name: 'Generate summary' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('A summary is unavailable');
    expect(posts(fetcher).filter(([path]) => path === byokSummaryPath)).toHaveLength(1);
    expect(fetcher.mock.calls.some(([path]) => path === '/api/summaries' || path === '/v3/api-docs')).toBe(false);
    expect(screen.getByRole('complementary')).toBeInTheDocument();
    expect(document.querySelector('.summary-progress')).toHaveFocus();
  });
  it('shows backend fallback without generated observations, retaining warnings and uncertainty', async () => {
    const fixture = summaryFixture(); fixture.summary.aiUnavailable = true;
    show(dataFetcher((path) => path === byokSummaryPath ? jsonResponse(fixture) : undefined));
    await choose(); await userEvent.click(screen.getByRole('button', { name: 'Generate summary' }));
    await screen.findByRole('status');
    expect(screen.getByText(/AI is unavailable/)).toBeInTheDocument(); expect(screen.getByText('HIGH')).toBeVisible();
    expect(screen.queryByRole('heading', { name: 'Generated observations' })).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Unclear or conflicting information' })).toBeInTheDocument();
  });
  it('retains returned safety even if summary content is malformed', async () => {
    show(dataFetcher((path) => path === byokSummaryPath ? jsonResponse({ ...summaryFixture(), summary: null }) : undefined));
    await choose(); await userEvent.click(screen.getByRole('button', { name: 'Generate summary' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be verified');
    expect(screen.getByRole('heading', { name: 'Summary information could not be fully verified' })).toBeVisible();
    expect(screen.queryByRole('heading', { name: 'Requested summary' })).not.toBeInTheDocument();
    expect(screen.getByText('HIGH')).toBeVisible();
    expect(screen.queryByText('AI-generated')).not.toBeInTheDocument();
    expect(screen.queryByText('Educational project. Not medical advice.')).not.toBeInTheDocument();
  });
  it('retains old results while regenerating and on failure, with no automatic retries', async () => {
    let fail = false; const fetcher = dataFetcher((path) => path === byokSummaryPath && fail ? jsonResponse({ message: 'private diagnostics' }, 503) : undefined);
    show(fetcher); await generate(); fail = true;
    await userEvent.click(screen.getByRole('button', { name: 'Regenerate summary' }));
    expect(await screen.findByRole('alert')).not.toHaveTextContent('private diagnostics');
    expect(screen.getByText('HIGH')).toBeVisible(); expect(screen.getByRole('blockquote')).toBeInTheDocument();
    expect(posts(fetcher)).toHaveLength(2); expect(screen.getByRole('complementary')).toBeInTheDocument();
  });
  it('renders safety provided in an HTTP failure and never clears previous urgency', async () => {
    let fail = false; const fetcher = dataFetcher((path) => path === byokSummaryPath && fail ? jsonResponse({ safety: { signals: [{ ruleId: 'NEW_RULE', severity: 'WATCH', reason: 'Another warning' }], needsDoctorBanner: false } }, 503) : undefined);
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
    const fetcher = dataFetcher((path, init) => path === byokSummaryPath ? new Promise<Response>((done) => { resolve = done; signal = init.signal!; }) : undefined);
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
    let hang = false; const fetcher = dataFetcher((path) => path === byokSummaryPath && hang ? new Promise<Response>(() => {}) : undefined);
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
    const fetcher = dataFetcher((path, init) => path === byokSummaryPath ? new Promise<Response>((done) => { signal = init.signal!; resolve = done; }) : undefined);
    const rendered = show(fetcher); await choose(); await userEvent.click(screen.getByRole('button', { name: 'Generate summary' }));
    rendered.unmount(); expect(signal.aborted).toBe(true);
    await act(async () => resolve(jsonResponse(summaryFixture())));
  });
  it('supports inactive recipients and uses a fresh request when the selected recipient changes', async () => {
    const fetcher = dataFetcher(); show(fetcher); await generate();
    await userEvent.selectOptions(screen.getByLabelText('Care recipient', { exact: true }), other.id);
    await userEvent.click(screen.getByRole('button', { name: 'Regenerate summary' }));
    expect(await screen.findByText(other.name, { selector: 'bdi' })).toBeInTheDocument();
    expect(JSON.parse(posts(fetcher).at(-1)![1].body).summary.recipientId).toBe(other.id);
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
    show(dataFetcher((path) => path === byokSummaryPath ? jsonResponse({}, 401) : undefined)); await choose();
    await userEvent.click(screen.getByRole('button', { name: 'Generate summary' }));
    expect(await screen.findByRole('heading', { name: 'Caregiver sign in' })).toBeInTheDocument();
    expect(sessionStorage.getItem('caregiver-auth-session-v1')).toBeNull();
  });
  it('shows exact summary dates and trend sample counts', async () => {
    show(dataFetcher()); await generate();
    expect(screen.getByText('2026-10-18')).toBeVisible(); expect(screen.getByText('2026-10-24')).toBeVisible();
    expect(screen.getByText('Events: 1')).toBeVisible(); expect(screen.getByText('Recorded days: 4')).toBeVisible();
    expect(screen.getByText('Events: 3')).toBeVisible(); expect(screen.getByText('Recorded days: 6')).toBeVisible();
  });
  it('shows no recorded data instead of zero for empty trend sample', async () => {
    const fixture = summaryFixture(); fixture.trends[0].previousCount = 0; fixture.trends[0].previousSampleSize = 0; fixture.trends[0].delta = 3;
    const fetcher = dataFetcher((path) => path === byokSummaryPath ? jsonResponse(fixture) : undefined);
    show(fetcher); await generate();
    expect(screen.getByText('No recorded data for this period')).toBeVisible();
    expect(screen.queryByText('Events: 0')).not.toBeInTheDocument(); expect(screen.getByText('Events: 3')).toBeVisible();
    expect(screen.getByText('Recorded days: 6')).toBeVisible(); expect(screen.queryByText('Change')).not.toBeInTheDocument();
  });
  it('shows recorded zero event count when sample size is nonzero', async () => {
    const fixture = summaryFixture(); fixture.trends[0].previousCount = 0; fixture.trends[0].previousSampleSize = 3; fixture.trends[0].delta = 3;
    show(dataFetcher((path) => path === byokSummaryPath ? jsonResponse(fixture) : undefined)); await generate();
    expect(screen.getByText('Events: 0')).toBeVisible(); expect(screen.getByText('Recorded days: 3')).toBeVisible();
    expect(screen.queryByText('No recorded data for this period')).not.toBeInTheDocument();
  });
  it('renders trend sample labels in Arabic', async () => {
    const fixture = summaryFixture(); fixture.trends[0].previousCount = 0; fixture.trends[0].previousSampleSize = 0; fixture.trends[0].delta = 3;
    show(dataFetcher((path) => path === byokSummaryPath ? jsonResponse(fixture) : undefined)); await generate();
    await userEvent.click(screen.getByRole('button', { name: 'التغيير إلى العربية' }));
    expect(document.documentElement).toHaveAttribute('dir', 'rtl');
    expect(screen.getByText('لا توجد بيانات مسجلة لهذه الفترة')).toBeVisible();
    expect(screen.getByText(/عدد الأحداث: ٣/)).toBeVisible(); expect(screen.getByText(/الأيام التي سُجّلت فيها بيانات: ٦/)).toBeVisible();
    expect(screen.queryByText('Previous period')).not.toBeInTheDocument(); expect(screen.queryByText('Events')).not.toBeInTheDocument();
  });
  it('uses metric-specific sampling units in English and Arabic', async () => {
    const fixture = summaryFixture();
    fixture.trends = ['FALL_COUNT', 'HIGH_PAIN_COUNT', 'MISSED_MED_COUNT', 'POOR_APPETITE_COUNT'].map((metric) => ({ ...fixture.trends[0], metric }));
    show(dataFetcher((path) => path === byokSummaryPath ? jsonResponse(fixture) : undefined)); await generate();
    expect(screen.getAllByText('Recorded note observations: 4')).toHaveLength(2);
    expect(screen.getAllByText('Recorded note observations: 6')).toHaveLength(2);
    expect(screen.getAllByText('Recorded days: 4')).toHaveLength(2);
    expect(screen.getAllByText('Recorded days: 6')).toHaveLength(2);
    await userEvent.click(screen.getByRole('button', { name: 'التغيير إلى العربية' }));
    expect(document.documentElement).toHaveAttribute('dir', 'rtl');
    expect(screen.getAllByText('الملاحظات المسجلة: ٤')).toHaveLength(2);
    expect(screen.getAllByText('الملاحظات المسجلة: ٦')).toHaveLength(2);
    expect(screen.getAllByText('الأيام التي سُجّلت فيها بيانات: ٤')).toHaveLength(2);
    expect(screen.getAllByText('الأيام التي سُجّلت فيها بيانات: ٦')).toHaveLength(2);
  });
  it.each([
    { previousSampleSize: 4, currentSampleSize: 0, previousCount: 1, currentCount: 0, delta: -1 },
    { previousSampleSize: 0, currentSampleSize: 0, previousCount: 0, currentCount: 0, delta: 0 },
  ])('handles empty requested or both empty periods: %j', async (periods) => {
    const fixture = summaryFixture(); Object.assign(fixture.trends[0], periods);
    show(dataFetcher((path) => path === byokSummaryPath ? jsonResponse(fixture) : undefined)); await generate();
    expect(screen.getAllByText('No recorded data for this period')).toHaveLength(periods.previousSampleSize === 0 ? 2 : 1);
    expect(screen.queryByText('Change')).not.toBeInTheDocument();
    if (periods.previousSampleSize > 0) expect(screen.getByText('Events: 1')).toBeVisible();
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
