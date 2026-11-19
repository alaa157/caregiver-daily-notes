import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { useEffect, type ReactNode } from 'react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { App } from '../app/App';
import { ThemeProvider } from '../app/ThemeProvider';
import { AppShell, AppLayout } from '../components/AppShell';
import { useByok, type ByokCredentials } from '../features/ai/ByokContext';
import { AuthProvider } from '../features/auth/AuthProvider';
import { LoginPage } from '../features/auth/LoginPage';
import { SUMMARY_TIMEOUT_MS, SummaryPage } from '../features/summary/SummaryPage';
import { ProtectedRoute } from '../app/ProtectedRoute';
import { I18nProvider } from '../i18n/I18nProvider';
import { summaryMessages } from '../i18n/summaryTranslations';
import { readSummaryResponse, type SummaryUnavailableReason } from '../api/summary';
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
function SwapCredentials({ next, label = 'swap credentials' }: { next: ByokCredentials; label?: string }) {
  const { setCredentials } = useByok();
  return <button onClick={() => setCredentials(next)}>{label}</button>;
}
function show(fetcher: ReturnType<typeof vi.fn>, language = 'en', credentials: ByokCredentials | null = syntheticCredentials, extras?: ReactNode) {
  vi.stubGlobal('fetch', fetcher); localStorage.setItem('caregiver-language', language);
  sessionStorage.setItem('caregiver-auth-session-v1', JSON.stringify({ token: testToken(), baseUrl: '' }));
  // The workspace chrome (AppLayout) owns the language toggle now; render the
  // summary route inside it so locale switching goes through the real control.
  return render(<I18nProvider><ApiProvider config={{ apiBaseUrl: '' }}><AuthProvider><ThemeProvider><MemoryRouter initialEntries={[root]}>
    <AppShell><Routes>
      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          <Route path={root} element={<SeedCredentials credentials={credentials}><SummaryPage />{extras}</SeedCredentials>} />
        </Route>
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
const renderedTrendLines = (period: Element) => Array.from(period.querySelectorAll('dd > span'), (line) => line.textContent?.trim() ?? '');

describe('summary unavailable reason response field', () => {
  it.each(['INVALID_KEY', 'INVALID_MODEL', 'RATE_LIMIT', 'TIMEOUT', 'PROVIDER_UNAVAILABLE', 'INVALID_OUTPUT'] as const)(
    'accepts %s without invalidating the response',
    (unavailableReason) => {
      const fixture = summaryFixture();
      const result = readSummaryResponse({
        ...fixture,
        summary: { ...fixture.summary, unavailableReason },
      });

      expect(result.invalidContent).toBe(false);
      expect(result.summary?.unavailableReason).toBe(unavailableReason);
    },
  );
  it('treats missing, null, and unrecognized reasons as absent', () => {
    const fixture = summaryFixture();
    const baseline = readSummaryResponse(fixture);

    for (const unavailableReason of [undefined, null, 'SOMETHING_ELSE']) {
      const result = readSummaryResponse({
        ...fixture,
        summary: {
          ...fixture.summary,
          ...(unavailableReason === undefined ? {} : { unavailableReason }),
        },
      });
      expect(result).toEqual(baseline);
    }
  });
  it('still rejects malformed summary fields', () => {
    const fixture = summaryFixture();
    const result = readSummaryResponse({
      ...fixture,
      summary: { ...fixture.summary, aiUnavailable: 'false' },
    });

    expect(result.summary).toBeNull();
    expect(result.invalidContent).toBe(true);
  });
});

describe('summary unavailable reason display', () => {
  const unavailableReasonCases: Array<[SummaryUnavailableReason, keyof typeof summaryMessages.en]> = [
    ['INVALID_KEY', 'summaryUnavailableInvalidKey'],
    ['INVALID_MODEL', 'summaryUnavailableInvalidModel'],
    ['RATE_LIMIT', 'summaryUnavailableRateLimit'],
    ['TIMEOUT', 'summaryUnavailableTimeout'],
    ['PROVIDER_UNAVAILABLE', 'summaryUnavailableProvider'],
    ['INVALID_OUTPUT', 'summaryUnavailableInvalidOutput'],
  ];

  it.each(unavailableReasonCases)('appends the %s reason in English and keeps safety visible', async (unavailableReason, key) => {
    const fixture = summaryFixture();
    const response = { ...fixture, summary: { ...fixture.summary, aiUnavailable: true, unavailableReason } };
    show(dataFetcher((path) => path === byokSummaryPath ? jsonResponse(response) : undefined));
    await choose();
    await userEvent.click(screen.getByRole('button', { name: 'Generate summary' }));

    const status = await screen.findByText(`${summaryMessages.en.summaryFallback} ${summaryMessages.en[key]}`);
    expect(status).toHaveAttribute('role', 'status');
    expect(screen.getByText('HIGH')).toBeVisible();
  });

  it('shows only the fallback text when no reason is returned', async () => {
    const fixture = summaryFixture();
    const response = { ...fixture, summary: { ...fixture.summary, aiUnavailable: true } };
    show(dataFetcher((path) => path === byokSummaryPath ? jsonResponse(response) : undefined));
    await choose();
    await userEvent.click(screen.getByRole('button', { name: 'Generate summary' }));

    const status = await screen.findByText(summaryMessages.en.summaryFallback);
    expect(status).toHaveAttribute('role', 'status');
    expect(screen.getByText('HIGH')).toBeVisible();
  });

  it('appends an Arabic reason in RTL and keeps safety visible', async () => {
    const fixture = summaryFixture();
    const response = { ...fixture, summary: { ...fixture.summary, aiUnavailable: true, unavailableReason: 'INVALID_KEY' as const } };
    show(dataFetcher((path) => path === byokSummaryPath ? jsonResponse(response) : undefined), 'ar');
    await waitFor(() => expect(screen.getByLabelText('المستفيد من الرعاية', { exact: true })).toBeEnabled());
    await userEvent.selectOptions(screen.getByLabelText('المستفيد من الرعاية', { exact: true }), recipientFixture.id);
    fireEvent.change(screen.getByLabelText('تاريخ النهاية (شامل)'), { target: { value: '2026-10-24' } });
    await userEvent.click(screen.getByRole('button', { name: 'إنشاء ملخص' }));

    const status = await screen.findByText(`${summaryMessages.ar.summaryFallback} ${summaryMessages.ar.summaryUnavailableInvalidKey}`);
    expect(document.documentElement).toHaveAttribute('dir', 'rtl');
    expect(status).toHaveAttribute('role', 'status');
    expect(screen.getByText('HIGH')).toBeVisible();
  });
});

describe('on-demand summary and safety workflow', () => {
  it('keeps summary translation keys in parity and uses neutral attribution', () => {
    expect(Object.keys(summaryMessages.en).sort()).toEqual(Object.keys(summaryMessages.ar).sort());
    expect(summaryMessages.en.summaryAiGeneratedLabel).not.toBe('AI-generated');
    expect(summaryMessages.ar.summaryAiGeneratedLabel).not.toBe('مولّد بالذكاء الاصطناعي');
  });
  it('protects the route before fetching private data', async () => {
    const fetcher = dataFetcher(); showApp(fetcher, 'en', false);
    expect(await screen.findByRole('heading', { name: 'Caregiver sign in' })).toBeInTheDocument(); expect(fetcher).not.toHaveBeenCalled();
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
    const page = screen.getByRole('region', { name: 'AI summary & safety' });
    expect(within(page).getByRole('complementary')).toHaveTextContent('not a diagnosis');
    expect(document.title).toContain('AI summary & safety');
    expect(posts(fetcher)).toHaveLength(0); expect(fetcher).toHaveBeenCalledTimes(2);
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
      summary: { recipientId: recipientFixture.id, periodDays: period, periodEndInclusive: '2026-10-24', language: 'en' },
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
  it('sends the selected Arabic interface language with the summary request', async () => {
    const fetcher = dataFetcher();
    show(fetcher, 'ar');
    await waitFor(() => expect(screen.getByLabelText('المستفيد من الرعاية', { exact: true })).toBeEnabled());
    await userEvent.selectOptions(screen.getByLabelText('المستفيد من الرعاية', { exact: true }), recipientFixture.id);
    fireEvent.change(screen.getByLabelText('تاريخ النهاية (شامل)'), { target: { value: '2026-10-24' } });
    await userEvent.click(screen.getByRole('button', { name: 'إنشاء ملخص' }));
    await waitFor(() => expect(posts(fetcher).filter(([path]) => path === byokSummaryPath)).toHaveLength(1));

    expect(JSON.parse(posts(fetcher).find(([path]) => path === byokSummaryPath)![1].body).summary.language)
      .toBe('ar');
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
    expect(fetcher.mock.calls.map(([path]) => path)).toEqual(['/api/recipients', '/api/recipients']);
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
    expect(fetcher.mock.calls.map(([path]) => path)).toEqual(['/api/recipients', '/api/recipients']);
  });
  it('sends configured in-memory credentials to BYOK and renders the parsed result', async () => {
    const fetcher = dataFetcher();
    show(fetcher); await generate();

    const summaryCalls = fetcher.mock.calls.filter(([path]) => path === byokSummaryPath);
    expect(summaryCalls).toHaveLength(1);
    expect(summaryCalls[0][1].method).toBe('POST');
    expect(JSON.parse(summaryCalls[0][1].body)).toEqual({
      summary: { recipientId: recipientFixture.id, periodDays: 7, periodEndInclusive: '2026-10-24', language: 'en' },
      credentials: syntheticCredentials,
    });
    expect(fetcher.mock.calls.some(([path]) => path === '/api/summaries' || path === '/v3/api-docs')).toBe(false);
    expect(screen.getByText('Model-generated text')).toBeVisible();
    expect(screen.getByText('Educational project. Not medical advice.')).toBeVisible();
    expect(screen.getByText('HIGH')).toBeVisible();
    expect(screen.getByRole('region', { name: 'Recorded observation counts' })).toHaveTextContent('Poor appetite days');
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
    expect(screen.queryByText('Model-generated text')).not.toBeInTheDocument();
    expect(fetcher.mock.calls.some(([path]) => path === '/api/summaries')).toBe(false);
  });
  it('shows a localized notice when note text was truncated for the model', async () => {
    show(dataFetcher((path) => path === byokSummaryPath ? jsonResponse({
      ...summaryFixture(), truncatedNoteCount: 2,
    }) : undefined));
    await generate();

    expect(await screen.findByText('2 notes were omitted or shortened to fit the summary.')).toBeVisible();
  });

  it('shows a localized notice when unsafe medical text was removed', async () => {
    const fixture = summaryFixture();
    show(dataFetcher((path) => path === byokSummaryPath ? jsonResponse({
      ...fixture,
      summary: { ...fixture.summary, droppedContentCount: 2, droppedContentReason: 'UNSAFE_CONTENT' },
    }) : undefined));
    await generate();

    expect(await screen.findByText('2 items removed (contained medical advice).')).toBeVisible();
  });

  it('shows a localized notice when observations were dropped for failed grounding', async () => {
    const fixture = summaryFixture();
    show(dataFetcher((path) => path === byokSummaryPath ? jsonResponse({
      ...fixture,
      summary: { ...fixture.summary, droppedObservationCount: 2, droppedReason: 'UNVERIFIED_QUOTE' },
    }) : undefined));
    await generate();

    expect(await screen.findByText('2 observations removed (could not be verified).')).toBeVisible();
    expect(screen.getByRole('region', { name: 'Summary observations' })).toBeVisible();
  });

  it('labels successful AI content as generated', async () => {
    show(dataFetcher()); await generate();
    const aiSection = screen.getByRole('region', { name: 'Summary observations' });
    const safetySection = screen.getByRole('region', { name: 'Safety information' });
    expect(screen.getByRole('heading', { name: 'Requested summary' })).toBeVisible();
    expect(within(aiSection).getByText('Model-generated text')).toBeVisible();
    expect(within(aiSection).getByText('Reported appetite varied. ملاحظة غير مؤكدة.')).toBeVisible();
    const observation = within(aiSection).getByText('Reported appetite varied. ملاحظة غير مؤكدة.').closest('li')!;
    expect(within(observation).getByRole('blockquote').textContent).toBe(summaryFixture().summary.observations[0].quote);
    expect(within(observation).getByRole('link', { name: /Source note:/ })).toHaveAttribute('href', '/app/notes/c3333333-3333-4333-8333-333333333333');
    expect(within(safetySection).queryByText('Model-generated text')).not.toBeInTheDocument();
    expect(within(aiSection).queryByText('AI-generated')).not.toBeInTheDocument();
  });
  it('renders the grounding caveat in both locales and marks model text by language', async () => {
    const fixture = summaryFixture();
    fixture.summary.observations.push({
      text: 'Appetite improved.',
      noteId: recipientFixture.id,
      quote: 'Appetite improved.',
    });
    show(dataFetcher((path) => path === byokSummaryPath ? jsonResponse(fixture) : undefined)); await generate();
    const observations = screen.getByRole('region', { name: summaryMessages.en.summaryObservations });
    expect(within(observations).getAllByText(summaryMessages.en.summaryEvidenceCaveat)).toHaveLength(2);
    const observation = within(observations).getByText('Reported appetite varied. ملاحظة غير مؤكدة.', { exact: true });
    expect(observation.tagName).toBe('BDI');
    expect(observation).toHaveAttribute('lang', 'ar');
    expect(observation.parentElement).toHaveAttribute('dir', 'auto');
    expect(within(observations).getAllByText('Appetite improved.')[0]).toHaveAttribute('lang', 'en');
    const quote = within(observations).getAllByRole('blockquote')[0];
    expect(quote).toHaveAttribute('dir', 'auto');
    expect(quote.querySelector('bdi')).toHaveAttribute('lang', 'ar');

    await userEvent.click(screen.getByRole('button', { name: 'التغيير إلى العربية' }));
    const arabicObservations = screen.getByRole('region', { name: summaryMessages.ar.summaryObservations });
    expect(within(arabicObservations).getAllByText(summaryMessages.ar.summaryEvidenceCaveat)).toHaveLength(2);
    expect(within(arabicObservations).getByText(summaryMessages.ar.summaryAiGeneratedLabel)).toBeVisible();
    expect(within(arabicObservations).queryByText('AI-generated')).not.toBeInTheDocument();
  });
  it('does not show successful presentation when trends are invalid', async () => {
    const fixture = summaryFixture(); fixture.trends[0].delta = 4;
    show(dataFetcher((path) => path === byokSummaryPath ? jsonResponse(fixture) : undefined));
    await choose(); await userEvent.click(screen.getByRole('button', { name: 'Generate summary' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Some summary information could not be verified');
    expect(screen.getByRole('heading', { name: 'Summary information could not be fully verified' })).toBeVisible();
    expect(screen.queryByRole('heading', { name: 'Requested summary' })).not.toBeInTheDocument();
    expect(screen.queryByText('Model-generated text')).not.toBeInTheDocument();
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
    expect(screen.queryByText('نص مولّد بواسطة النموذج')).not.toBeInTheDocument();
    expect(screen.queryByText('مشروع تعليمي. ليس نصيحة طبية.')).not.toBeInTheDocument();
  });
  it('suppresses successful AI presentation after a failed summary request', async () => {
    let fail = false;
    const fetcher = dataFetcher((path) => path === byokSummaryPath && fail
      ? jsonResponse({ safety: { needsDoctorBanner: false, signals: [{ ruleId: 'RETRY_WARNING', severity: 'WATCH', reason: 'Warning returned with failure.' }] } }, 503)
      : undefined);
    show(fetcher); await generate();
    expect(screen.getByText('Model-generated text')).toBeVisible();
    fail = true; await userEvent.click(screen.getByRole('button', { name: 'Regenerate summary' }));
    expect(await screen.findByRole('heading', { name: 'Safety information returned with the failed request' })).toBeVisible();
    expect(screen.queryByText('Model-generated text')).not.toBeInTheDocument();
    expect(screen.queryByText('Educational project. Not medical advice.')).not.toBeInTheDocument();
    expect(screen.queryByText('Reported appetite varied. ملاحظة غير مؤكدة.')).not.toBeInTheDocument();
    expect(screen.queryByRole('blockquote')).not.toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'Safety information' })).toHaveTextContent('HIGH');
    expect(screen.getByRole('region', { name: 'Safety information' })).toHaveTextContent('Warning returned with failure.');
  });
  it('shows the educational disclaimer with AI content', async () => {
    show(dataFetcher()); await generate();
    const aiSection = screen.getByRole('region', { name: 'Summary observations' });
    expect(within(aiSection).getByText('Educational project. Not medical advice.')).toBeVisible();
    await userEvent.click(screen.getByRole('button', { name: 'التغيير إلى العربية' }));
    expect(await screen.findByText('مشروع تعليمي. ليس نصيحة طبية.')).toBeVisible();
  });
  it('keeps safety signals outside the AI narrative', async () => {
    show(dataFetcher()); await generate();
    const aiSection = screen.getByRole('region', { name: 'Summary observations' });
    const safetySection = screen.getByRole('region', { name: 'Safety information' });
    expect(within(aiSection).getByText('Reported appetite varied. ملاحظة غير مؤكدة.')).toBeVisible();
    expect(within(safetySection).getByText('HIGH')).toBeVisible();
    expect(within(safetySection).getByText(/evaluated independently/)).toBeVisible();
    expect(aiSection.contains(safetySection)).toBe(false);
    expect(screen.getByRole('region', { name: 'Unclear or conflicting information' })).toBeVisible();
    expect(screen.getByRole('region', { name: 'Recorded observation counts' })).toHaveTextContent('Poor appetite days');
  });
  it('does not show AI success label for unavailable response', async () => {
    const fixture = summaryFixture();
    fixture.summary.aiUnavailable = true;
    fixture.summary.observations[0].text = 'stray unavailable model text';
    show(dataFetcher((path) => path === byokSummaryPath ? jsonResponse(fixture) : undefined));
    await choose(); await userEvent.click(screen.getByRole('button', { name: 'Generate summary' }));
    await screen.findByText(/AI is unavailable/);
    expect(screen.getByText(/AI is unavailable/)).toBeVisible();
    expect(screen.queryByRole('heading', { name: 'Requested summary' })).not.toBeInTheDocument();
    expect(screen.queryByText('Model-generated text')).not.toBeInTheDocument();
    expect(screen.queryByText('Educational project. Not medical advice.')).not.toBeInTheDocument();
    expect(screen.queryByText('stray unavailable model text')).not.toBeInTheDocument();
    expect(screen.queryByRole('blockquote')).not.toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'Safety information' })).toHaveTextContent('HIGH');
    expect(screen.getByRole('region', { name: 'Recorded observation counts' })).toHaveTextContent('Poor appetite days');
  });
  it('does not render unavailable reasons and preserves output for absent or unknown values', async () => {
    const renderedResults: Array<string | null> = [];
    for (const unavailableReason of [undefined, null, 'SOMETHING_ELSE', 'RATE_LIMIT'] as const) {
      const fixture = summaryFixture();
      const response = {
        ...fixture,
        summary: {
          ...fixture.summary,
          ...(unavailableReason === undefined ? {} : { unavailableReason }),
        },
      };
      const view = show(dataFetcher((path) => path === byokSummaryPath ? jsonResponse(response) : undefined));
      await generate();
      expect(screen.getByRole('region', { name: 'Summary observations' })).toHaveTextContent('Reported appetite varied.');
      if (unavailableReason) expect(document.body.textContent).not.toContain(unavailableReason);
      renderedResults.push(screen.getByRole('region', { name: 'Requested summary' }).textContent);
      view.unmount();
    }
    expect(new Set(renderedResults).size).toBe(1);
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
    expect(screen.queryByText('Model-generated text')).not.toBeInTheDocument();
    expect(screen.queryByText('Educational project. Not medical advice.')).not.toBeInTheDocument();
    expect(screen.queryByText('partially parsed model content')).not.toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'Safety information' })).toHaveTextContent('HIGH');
    expect(screen.getByRole('region', { name: 'Recorded observation counts' })).toHaveTextContent('Poor appetite days');
  });
  it('shows the empty-observations state without calling it an AI failure in English and Arabic', async () => {
    const fixture = summaryFixture();
    fixture.summary.observations = [];
    fixture.summary.uncertainties = [];
    show(dataFetcher((path) => path === byokSummaryPath ? jsonResponse(fixture) : undefined));
    await generate();

    expect(screen.getByText('No observations were returned for this period.')).toBeVisible();
    expect(screen.getByText('No uncertainty items were returned. This does not confirm certainty.')).toBeVisible();
    expect(screen.queryByText(/AI is unavailable/)).not.toBeInTheDocument();
    expect(screen.queryByText('A summary is unavailable')).not.toBeInTheDocument();
    expect(screen.queryByText('Model-generated text')).not.toBeInTheDocument();
    expect(screen.queryByText('Educational project. Not medical advice.')).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'التغيير إلى العربية' }));
    expect(screen.getByText('لم تَرِد مشاهدات لهذه الفترة.')).toBeVisible();
    expect(screen.getByText('لم تُرجع عناصر لعدم اليقين. هذا لا يؤكد اليقين.')).toBeVisible();
    expect(screen.queryByText(/الذكاء الاصطناعي غير متاح/)).not.toBeInTheDocument();
    expect(screen.queryByText('نص مولّد بواسطة النموذج')).not.toBeInTheDocument();
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
    expect(within(screen.getByRole('region', { name: 'AI summary & safety' })).getByRole('complementary')).toBeInTheDocument();
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
    expect(within(screen.getByRole('region', { name: 'AI summary & safety' })).getByRole('complementary')).toBeInTheDocument();
    expect(document.querySelector('.summary-progress')).toHaveFocus();
  });
  it('shows backend fallback without generated observations, retaining warnings and uncertainty', async () => {
    const fixture = summaryFixture(); fixture.summary.aiUnavailable = true;
    show(dataFetcher((path) => path === byokSummaryPath ? jsonResponse(fixture) : undefined));
    await choose(); await userEvent.click(screen.getByRole('button', { name: 'Generate summary' }));
    await screen.findByText(/AI is unavailable/);
    expect(screen.getByText(/AI is unavailable/)).toBeInTheDocument(); expect(screen.getByText('HIGH')).toBeVisible();
    expect(screen.queryByRole('heading', { name: 'Summary observations' })).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Unclear or conflicting information' })).toBeInTheDocument();
  });
  it('retains returned safety even if summary content is malformed', async () => {
    show(dataFetcher((path) => path === byokSummaryPath ? jsonResponse({ ...summaryFixture(), summary: null }) : undefined));
    await choose(); await userEvent.click(screen.getByRole('button', { name: 'Generate summary' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be verified');
    expect(screen.getByRole('heading', { name: 'Summary information could not be fully verified' })).toBeVisible();
    expect(screen.queryByRole('heading', { name: 'Requested summary' })).not.toBeInTheDocument();
    expect(screen.getByText('HIGH')).toBeVisible();
    expect(screen.queryByText('Model-generated text')).not.toBeInTheDocument();
    expect(screen.queryByText('Educational project. Not medical advice.')).not.toBeInTheDocument();
  });
  it('retains old results while regenerating and on failure, with no automatic retries', async () => {
    let fail = false; const fetcher = dataFetcher((path) => path === byokSummaryPath && fail ? jsonResponse({ message: 'private diagnostics' }, 503) : undefined);
    show(fetcher); await generate(); fail = true;
    await userEvent.click(screen.getByRole('button', { name: 'Regenerate summary' }));
    expect(await screen.findByRole('alert')).not.toHaveTextContent('private diagnostics');
    expect(screen.getByText('HIGH')).toBeVisible(); expect(screen.getByRole('blockquote')).toBeInTheDocument();
    expect(posts(fetcher)).toHaveLength(2); expect(within(screen.getByRole('region', { name: 'AI summary & safety' })).getByRole('complementary')).toBeInTheDocument();
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
    show(fetcher);
    const page = screen.getByRole('region', { name: 'AI summary & safety' });
    expect(await within(page).findByRole('alert')).toHaveTextContent('could not be loaded'); fail = false;
    await userEvent.click(within(page).getByRole('button', { name: 'Retry' })); await choose(); expect(posts(fetcher)).toHaveLength(0);
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
    const trends = screen.getByRole('region', { name: 'Recorded observation counts' }).querySelectorAll('li');
    const chart = trends[0].querySelector('figure')!;
    expect(chart).toHaveAttribute('aria-labelledby', trends[0].querySelector('h4')!.id);
    expect(chart).toHaveAttribute('aria-describedby', trends[0].querySelector('dl')!.id);
    expect(trends[0].querySelectorAll('.summary-trend-bar-row')).toHaveLength(2);
    const periods = trends[0].querySelectorAll('dl > div');
    expect(renderedTrendLines(periods[0])).toEqual(['Events: 1', 'Recorded days: 4']);
    expect(renderedTrendLines(periods[1])).toEqual(['Events: 3', 'Recorded days: 6']);
  });
  it('shows no recorded data instead of zero for empty trend sample', async () => {
    const fixture = summaryFixture(); fixture.trends[0].previousCount = 0; fixture.trends[0].previousSampleSize = 0; fixture.trends[0].delta = 3;
    const fetcher = dataFetcher((path) => path === byokSummaryPath ? jsonResponse(fixture) : undefined);
    show(fetcher); await generate();
    expect(screen.getByText('No recorded data for this period')).toBeVisible();
    expect(screen.queryByText('Events: 0')).not.toBeInTheDocument();
    const periods = screen.getByRole('region', { name: 'Recorded observation counts' }).querySelectorAll('dl > div');
    expect(periods[0].querySelector('dd bdi')).toBeNull();
    expect(renderedTrendLines(periods[1])).toEqual(['Events: 3', 'Recorded days: 6']);
    expect(screen.queryByText('Change')).not.toBeInTheDocument();
  });
  it('shows recorded zero event count when sample size is nonzero', async () => {
    const fixture = summaryFixture(); fixture.trends[0].previousCount = 0; fixture.trends[0].previousSampleSize = 3; fixture.trends[0].delta = 3;
    show(dataFetcher((path) => path === byokSummaryPath ? jsonResponse(fixture) : undefined)); await generate();
    const previous = screen.getByRole('region', { name: 'Recorded observation counts' }).querySelector('dl > div')!;
    expect(renderedTrendLines(previous)).toEqual(['Events: 0', 'Recorded days: 3']);
    expect(screen.queryByText('No recorded data for this period')).not.toBeInTheDocument();
  });
  it('renders trend sample labels in Arabic', async () => {
    const fixture = summaryFixture(); fixture.trends[0].previousCount = 0; fixture.trends[0].previousSampleSize = 0; fixture.trends[0].delta = 3;
    show(dataFetcher((path) => path === byokSummaryPath ? jsonResponse(fixture) : undefined)); await generate();
    await userEvent.click(screen.getByRole('button', { name: 'التغيير إلى العربية' }));
    expect(document.documentElement).toHaveAttribute('dir', 'rtl');
    expect(screen.getByText('لا توجد بيانات مسجلة لهذه الفترة')).toBeVisible();
    const periods = screen.getByRole('region', { name: 'أعداد المشاهدات المسجلة' }).querySelectorAll('dl > div');
    expect(renderedTrendLines(periods[1])).toEqual(['عدد الأحداث: 3', 'الأيام التي سُجّلت فيها بيانات: 6']);
    expect(screen.queryByText('Previous period')).not.toBeInTheDocument(); expect(screen.queryByText('Events')).not.toBeInTheDocument();
  });
  it('uses metric-specific sampling units in English and Arabic', async () => {
    const fixture = summaryFixture();
    fixture.trends = ['FALL_COUNT', 'HIGH_PAIN_COUNT', 'MISSED_MED_COUNT', 'POOR_APPETITE_COUNT'].map((metric) => ({ ...fixture.trends[0], metric }));
    show(dataFetcher((path) => path === byokSummaryPath ? jsonResponse(fixture) : undefined)); await generate();
    const trends = screen.getByRole('region', { name: 'Recorded observation counts' }).querySelectorAll('li');
    expect(trends).toHaveLength(4);
    expect(trends[0].textContent).toContain('Recorded note observations: 4');
    expect(trends[0].textContent).toContain('Recorded note observations: 6');
    expect(trends[2].textContent).toContain('Recorded days: 4');
    expect(trends[2].textContent).toContain('Recorded days: 6');
    await userEvent.click(screen.getByRole('button', { name: 'التغيير إلى العربية' }));
    expect(document.documentElement).toHaveAttribute('dir', 'rtl');
    expect(trends[0].textContent).toContain('الملاحظات المسجلة: 4');
    expect(trends[0].textContent).toContain('الملاحظات المسجلة: 6');
    expect(trends[2].textContent).toContain('الأيام التي سُجّلت فيها بيانات: 4');
    expect(trends[2].textContent).toContain('الأيام التي سُجّلت فيها بيانات: 6');
  });
  it('renders localized trend metric labels and never the raw metric code', async () => {
    const fixture = summaryFixture();
    fixture.trends = ['FALL_COUNT', 'HIGH_PAIN_COUNT', 'MISSED_MED_COUNT', 'POOR_APPETITE_COUNT', 'FUTURE_METRIC'].map((metric) => ({ ...fixture.trends[0], metric }));
    show(dataFetcher((path) => path === byokSummaryPath ? jsonResponse(fixture) : undefined)); await generate();
    const trends = screen.getByRole('region', { name: 'Recorded observation counts' }).querySelectorAll('li');
    expect(trends).toHaveLength(5);
    expect(trends[0].querySelector('h4')).toHaveTextContent('Falls recorded');
    expect(trends[1].querySelector('h4')).toHaveTextContent('High pain observations');
    expect(trends[2].querySelector('h4')).toHaveTextContent('Missed medication days');
    expect(trends[3].querySelector('h4')).toHaveTextContent('Poor appetite days');
    expect(trends[4].querySelector('h4')).toHaveTextContent('Recorded observation');
    expect(trends[4].querySelector('h4')).toHaveAttribute('data-metric', 'FUTURE_METRIC');
    for (const code of ['FALL_COUNT', 'HIGH_PAIN_COUNT', 'MISSED_MED_COUNT', 'POOR_APPETITE_COUNT', 'FUTURE_METRIC']) {
      expect(screen.queryByText(code)).not.toBeInTheDocument();
    }
    await userEvent.click(screen.getByRole('button', { name: 'التغيير إلى العربية' }));
    expect(trends[0].querySelector('h4')).toHaveTextContent('حالات السقوط المسجلة');
    expect(trends[2].querySelector('h4')).toHaveTextContent('أيام تفويت الأدوية');
    expect(trends[4].querySelector('h4')).toHaveTextContent('مشاهدات مسجلة');
  });
  it.each([
    { previousSampleSize: 4, currentSampleSize: 0, previousCount: 1, currentCount: 0, delta: -1 },
    { previousSampleSize: 0, currentSampleSize: 0, previousCount: 0, currentCount: 0, delta: 0 },
  ])('handles empty requested or both empty periods: %j', async (periods) => {
    const fixture = summaryFixture(); Object.assign(fixture.trends[0], periods);
    show(dataFetcher((path) => path === byokSummaryPath ? jsonResponse(fixture) : undefined)); await generate();
    expect(screen.getAllByText('No recorded data for this period')).toHaveLength(periods.previousSampleSize === 0 ? 2 : 1);
    expect(screen.queryByText('Change')).not.toBeInTheDocument();
    if (periods.previousSampleSize > 0) {
      const previous = screen.getByRole('region', { name: 'Recorded observation counts' }).querySelector('dl > div')!;
      expect(renderedTrendLines(previous)).toContain('Events: 1');
    }
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
  it('isolates mixed Arabic evidence and Latin note id', async () => {
    const fixture = summaryFixture();
    const observationText = 'تم تسجيل سقوط في الساعة 10:30 — NOTE-123 (review pending)';
    const evidenceQuote = 'تمت المراجعة الساعة 10:30 — NOTE-123 (review pending)';
    const unlinkedId = 'NOTE-123';
    const linkedId = 'c3333333-3333-4333-8333-333333333333';
    fixture.summary.observations = [
      { text: observationText, noteId: unlinkedId, quote: evidenceQuote },
      { text: 'ملاحظة مرتبطة بالرعاية', noteId: linkedId, quote: 'تم التحقق من المصدر NOTE-456.' },
    ];
    fixture.summary.uncertainties = [{ topic: 'موعد / FOLLOW_UP', detail: 'التفاصيل غير مؤكدة؛ review pending for ID ABC-9 at 10:30.' }];
    fixture.safety.signals = [{ ruleId: 'FALL_DETECTED', severity: 'HIGH', reason: 'تنبيه FALL_DETECTED — review 2 notes.' }];
    const fetcher = dataFetcher((path) => path === byokSummaryPath ? jsonResponse(fixture) : undefined);
    show(fetcher); await generate();
    await userEvent.click(screen.getByRole('button', { name: 'التغيير إلى العربية' }));

    expect(document.documentElement).toHaveAttribute('lang', 'ar');
    expect(document.documentElement).toHaveAttribute('dir', 'rtl');
    const generated = screen.getByRole('region', { name: 'مشاهدات الملخص' });
    const observation = within(generated).getByText(observationText, { exact: true });
    expect(observation).toBeVisible(); expect(observation.textContent).toBe(observationText);
    expect(observation).toHaveAttribute('lang', 'ar');
    expect(observation.parentElement).toHaveAttribute('dir', 'auto');
    const firstCard = observation.closest('li')!;
    const quote = within(firstCard).getByRole('blockquote');
    expect(quote.textContent).toBe(evidenceQuote); expect(quote).toHaveAttribute('dir', 'auto');
    const unlinked = within(firstCard).getByText(unlinkedId, { exact: true });
    expect(unlinked.tagName).toBe('BDI');
    expect(unlinked.parentElement?.tagName).toBe('P');
    expect(unlinked.parentElement?.textContent).toContain(unlinkedId);
    const linkedCard = within(generated).getByText('ملاحظة مرتبطة بالرعاية', { exact: true }).closest('li')!;
    expect(within(linkedCard).getByRole('link')).toHaveAttribute('href', `/app/notes/${linkedId}`);
    expect(within(linkedCard).getByText(linkedId, { exact: true }).tagName).toBe('BDI');

    const dates = within(screen.getByRole('region', { name: 'الملخص المطلوب' }));
    expect(dates.getByText('2026-10-18', { exact: true }).tagName).toBe('BDI');
    expect(dates.getByText('2026-10-24', { exact: true }).tagName).toBe('BDI');
    const uncertainty = screen.getByRole('region', { name: 'معلومات غير واضحة أو متعارضة' });
    const uncertaintyDetail = within(uncertainty).getByText('التفاصيل غير مؤكدة؛ review pending for ID ABC-9 at 10:30.', { exact: true });
    expect(uncertaintyDetail).toHaveAttribute('lang', 'ar');
    expect(uncertaintyDetail.parentElement).toHaveAttribute('dir', 'auto');
    const safety = screen.getByRole('region', { name: 'معلومات السلامة' });
    expect(within(safety).getByText('HIGH', { exact: true }).tagName).toBe('BDI');
    expect(within(safety).getByText('تم الإبلاغ عن سقوط', { exact: true }).tagName).toBe('BDI');
    expect(within(safety).getByText('تم الإبلاغ عن سقوط خلال الفترة المحددة.', { exact: true })).toHaveAttribute('dir', 'auto');
    expect(within(safety).getByText('تم الإبلاغ عن سقوط', { exact: true }).closest('li')).toHaveAttribute('data-rule-id', 'FALL_DETECTED');
  });
  it('keeps trend values readable in Arabic RTL', async () => {
    const fixture = summaryFixture();
    Object.assign(fixture.trends[0], { previousCount: 3, previousSampleSize: 4, currentCount: 1, currentSampleSize: 2, delta: -2 });
    const fetcher = dataFetcher((path) => path === byokSummaryPath ? jsonResponse(fixture) : undefined);
    show(fetcher); await generate();
    await userEvent.click(screen.getByRole('button', { name: 'التغيير إلى العربية' }));

    expect(document.documentElement).toHaveAttribute('dir', 'rtl');
    const trend = screen.getByRole('region', { name: 'أعداد المشاهدات المسجلة' });
    const periods = trend.querySelectorAll<HTMLElement>('dl > div');
    expect(periods).toHaveLength(3);
    const format = (value: number) => new Intl.NumberFormat('ar-EG-u-nu-latn').format(value);
    expect(within(periods[0]).getByText('الفترة السابقة', { exact: true })).toBeVisible();
    expect(Array.from(periods[0].querySelectorAll('dd bdi'), (item) => item.textContent)).toEqual([format(3), format(4)]);
    expect(within(periods[1]).getByText('الفترة المطلوبة', { exact: true })).toBeVisible();
    expect(Array.from(periods[1].querySelectorAll('dd bdi'), (item) => item.textContent)).toEqual([format(1), format(2)]);
    expect(within(periods[2]).getByText('التغير', { exact: true })).toBeVisible();
    const delta = periods[2].querySelector('dd bdi');
    expect(delta?.textContent).toBe(format(-2));
    expect(delta?.textContent).toContain('-');
  });
  it('uses the translated no-data message for a zero sample in Arabic', async () => {
    const fixture = summaryFixture();
    Object.assign(fixture.trends[0], { previousCount: 0, previousSampleSize: 0, currentCount: 2, currentSampleSize: 3, delta: 2 });
    const fetcher = dataFetcher((path) => path === byokSummaryPath ? jsonResponse(fixture) : undefined);
    show(fetcher); await generate();
    await userEvent.click(screen.getByRole('button', { name: 'التغيير إلى العربية' }));

    const trend = screen.getByRole('region', { name: 'أعداد المشاهدات المسجلة' });
    const previous = trend.querySelectorAll<HTMLElement>('dl > div')[0];
    expect(within(previous).getByRole('status')).toHaveTextContent('لا توجد بيانات مسجلة لهذه الفترة');
    expect(previous.querySelector('dd bdi')).toBeNull();
    expect(within(previous).queryByText(/عدد الأحداث:/)).not.toBeInTheDocument();
    expect(Array.from(trend.querySelectorAll('dl > div')[1].querySelectorAll('dd bdi'), (item) => item.textContent))
      .toEqual([new Intl.NumberFormat('ar-EG-u-nu-latn').format(2), new Intl.NumberFormat('ar-EG-u-nu-latn').format(3)]);
  });
});

describe('generated-with attribution', () => {
  it('shows the provider and model after a successful generation', async () => {
    show(dataFetcher()); await generate();

    const line = screen.getByText(/Generated with/);
    expect(line).toHaveTextContent('Gemini');
    expect(line).toHaveTextContent('gemini-test-model');
    expect(line.textContent).not.toContain(syntheticCredentials.apiKey);
  });

  it('is absent when the summary is aiUnavailable', async () => {
    const fixture = summaryFixture();
    const response = { ...fixture, summary: { ...fixture.summary, aiUnavailable: true } };
    show(dataFetcher((path) => path === byokSummaryPath ? jsonResponse(response) : undefined));
    await choose();
    await userEvent.click(screen.getByRole('button', { name: 'Generate summary' }));
    await screen.findByText(/AI is unavailable/);

    expect(screen.queryByText(/Generated with/)).not.toBeInTheDocument();
    expect(screen.queryByText('gemini-test-model')).not.toBeInTheDocument();
  });

  it('keeps the provider and model from request start after credentials change', async () => {
    const swapped: ByokCredentials = {
      providerId: 'OPENAI',
      model: 'swapped-model-id',
      apiKey: 'synthetic-swap-key',
    };
    show(dataFetcher(), 'en', syntheticCredentials, <SwapCredentials next={swapped} />);
    await generate();

    const line = screen.getByText(/Generated with/);
    expect(line).toHaveTextContent('Gemini');
    expect(line).toHaveTextContent('gemini-test-model');

    await userEvent.click(screen.getByRole('button', { name: 'swap credentials' }));

    expect(screen.getByText(/Generated with/)).toHaveTextContent('Gemini');
    expect(screen.getByText(/Generated with/)).toHaveTextContent('gemini-test-model');
    expect(document.body.textContent).not.toContain('swapped-model-id');
    expect(document.body.textContent).not.toContain('synthetic-swap-key');
  });
});

describe('summary coverage and empty periods', () => {
  it('shows deterministic coverage for a summary backed by notes', async () => {
    const fixture = summaryFixture();
    show(dataFetcher((path) => path === byokSummaryPath
      ? jsonResponse({ ...fixture, daysWithNotes: 2, periodDays: 30 }) : undefined));

    await choose();
    await userEvent.click(screen.getByRole('button', { name: 'Generate summary' }));

    expect(await screen.findByText('2 of 30 days have notes')).toBeInTheDocument();
  });

  it('shows a dedicated empty state without uncertainty for a zero-note period', async () => {
    const fixture = summaryFixture();
    show(dataFetcher((path) => path === byokSummaryPath ? jsonResponse({
      ...fixture,
      daysWithNotes: 0,
      periodDays: 7,
      summary: { ...fixture.summary, observations: [], uncertainties: [] },
      trends: [],
    }) : undefined));

    await choose();
    await userEvent.click(screen.getByRole('button', { name: 'Generate summary' }));

    expect(await screen.findByText(/No notes were recorded in this period/)).toBeInTheDocument();
    expect(screen.getByText('0 of 7 days have notes')).toBeInTheDocument();
    expect(screen.queryByText(/No uncertainty items were returned/)).not.toBeInTheDocument();
    expect(screen.queryByText(/No observations were returned/)).not.toBeInTheDocument();
  });
});
