import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { App } from '../app/App';
import { AuthProvider } from '../features/auth/AuthProvider';
import { OverviewKpis } from '../features/overview/OverviewKpis';
import { messages } from '../i18n/translations';
import { I18nProvider } from '../i18n/I18nProvider';
import { ApiProvider } from '../lib/api';
import { jsonResponse, testToken } from './authFixtures';
import { recipientFixture } from './recipientFixtures';
import { noteListFixture } from './noteFixtures';

/**
 * Dashboard localization and RTL coverage for [Home-11].
 * Only behaviour that jsdom can actually observe is asserted: rendered copy, the
 * document `dir`/`lang`, bidi isolation elements, locale-formatted digits, and
 * machine-readable `dateTime` attributes. Real layout geometry is verified by
 * the Playwright overview suite, not here.
 */

const activityId = '00000000-0000-4000-8000-000000000207';

/** The dashboard keys this suite is responsible for (translation parity scope). */
const dashboardKeys = [
  'dashboard', 'dashboardTitle', 'overviewDescription', 'overviewDateLabel', 'overviewMtdLabel',
  'overviewMtdLoading', 'overviewMtdError', 'overviewLoading', 'overviewKpisTitle',
  'overviewKpiRecipients', 'overviewKpiRecorded', 'overviewKpiNotRecorded', 'overviewKpiUnavailable',
  'overviewRecipientStatus', 'overviewNoRecipients', 'overviewNoteRecorded', 'overviewNoNote',
  'overviewOpenNote', 'overviewRecordNote', 'overviewNoteStatusUnavailable',
  'overviewMissingIsNotNegative', 'overviewPartialData', 'overviewMoreWorkflows',
  'overviewQuickActions', 'overviewPeopleHint', 'overviewNotesHint', 'overviewHistoryHint',
  'overviewSummaryHint', 'activityFeedTitle', 'activityFeedLoading', 'activityFeedError',
  'activityFeedEmpty', 'activityFeedRecipientFallback', 'activityFeedRefresh', 'activityKindNote',
  'activityKindCorrection', 'activityKindAddendum',
] as const;

function historyResponse(content: unknown[]) {
  return jsonResponse({ content, number: 0, size: 100, totalElements: content.length, totalPages: content.length ? 1 : 0, first: true, last: true });
}

type DashboardOptions = {
  language?: 'en' | 'ar';
  recipients?: unknown[];
  historyForDate?: unknown[];
  activity?: unknown[];
  activityFails?: boolean;
};

function renderDashboard({
  language = 'en', recipients = [recipientFixture], historyForDate = [noteListFixture], activity = [], activityFails = false,
}: DashboardOptions = {}) {
  const fetcher = vi.fn().mockImplementation((path: string) => {
    if (path.startsWith('/api/activity?')) {
      return activityFails
        ? Promise.resolve(jsonResponse({ message: 'private diagnostics' }, 503))
        : Promise.resolve(jsonResponse(activity));
    }
    if (path === '/api/recipients') return Promise.resolve(jsonResponse(recipients));
    if (path.startsWith('/api/notes?')) {
      // The month-to-date window starts on the 1st; only the selected date has notes.
      const from = new URL(path, 'http://localhost').searchParams.get('from');
      return Promise.resolve(historyResponse(from === '2026-10-18' ? historyForDate : []));
    }
    return Promise.resolve(jsonResponse({}, 404));
  });
  vi.stubGlobal('fetch', fetcher);
  localStorage.setItem('caregiver-language', language);
  sessionStorage.setItem('caregiver-auth-session-v1', JSON.stringify({ token: testToken(), baseUrl: '' }));
  const view = render(
    <I18nProvider><ApiProvider config={{ apiBaseUrl: '' }}><AuthProvider>
      <MemoryRouter initialEntries={['/app?date=2026-10-18']}><App /></MemoryRouter>
    </AuthProvider></ApiProvider></I18nProvider>,
  );
  return { view, fetcher };
}

const arActivity = [{ id: activityId, kind: 'note', recipientId: recipientFixture.id, createdAt: '2026-10-18T13:00:00Z' }];

describe('dashboard translation key parity', () => {
  it('has every dashboard key in both English and Arabic', () => {
    const missing = dashboardKeys.filter((key) => !(key in messages.en) || !(key in messages.ar));
    expect(missing).toEqual([]);
  });

  it('never leaves a dashboard Arabic value empty, missing, or an untranslated English placeholder', () => {
    const problems: string[] = [];
    for (const key of dashboardKeys) {
      const arabic: string = messages.ar[key];
      const english: string = messages.en[key];
      if (!arabic || !arabic.trim()) problems.push(`${key}: empty`);
      // A dashboard string must contain Arabic script; bare English is an untranslated placeholder.
      else if (!/[\u0600-\u06FF]/.test(arabic)) problems.push(`${key}: no Arabic script`);
      if (english === arabic) problems.push(`${key}: identical to English`);
    }
    expect(problems).toEqual([]);
  });
});

describe('dashboard locale switching', () => {
  it('renders Arabic copy with no leftover English then restores English', async () => {
    const user = userEvent.setup();
    renderDashboard({ language: 'en' });
    expect(await screen.findByRole('heading', { name: 'Caregiver workspace' })).toBeInTheDocument();

    // The language toggle exposes the name of the language it switches to.
    await user.click(screen.getByRole('button', { name: messages.en.switchToArabic }));

    expect(await screen.findByRole('heading', { name: messages.ar.dashboardTitle })).toBeInTheDocument();
    expect(screen.getByText(messages.ar.overviewDescription)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: messages.ar.overviewKpisTitle })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: messages.ar.overviewKpiRecipients })).toBeInTheDocument();
    expect(screen.getByRole('region', { name: messages.ar.overviewMtdLabel })).toBeInTheDocument();
    expect(screen.getByRole('region', { name: messages.ar.activityFeedTitle })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: messages.ar.activityFeedRefresh })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: messages.ar.overviewRecipientStatus })).toBeInTheDocument();
    expect(screen.getByText(messages.ar.overviewNoteRecorded)).toBeInTheDocument();
    expect(screen.getByText(messages.ar.overviewOpenNote)).toBeInTheDocument();
    expect(screen.getByText(messages.ar.overviewMissingIsNotNegative)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: messages.ar.overviewQuickActions })).toBeInTheDocument();

    // No dashboard-scoped English copy survives the switch.
    const dashboard = document.querySelector('.workspace-overview') as HTMLElement;
    const englishCopy: string[] = [messages.en.dashboardTitle, messages.en.overviewDescription,
      messages.en.overviewKpisTitle, messages.en.overviewKpiRecipients, messages.en.overviewMtdLabel,
      messages.en.activityFeedTitle, messages.en.activityFeedRefresh, messages.en.overviewRecipientStatus,
      messages.en.overviewNoteRecorded, messages.en.overviewOpenNote, messages.en.overviewMissingIsNotNegative,
      messages.en.overviewQuickActions];
    for (const english of englishCopy) {
      expect(within(dashboard).queryByText(english, { exact: true })).not.toBeInTheDocument();
    }

    await user.click(screen.getByRole('button', { name: messages.ar.switchToEnglish }));
    expect(await screen.findByRole('heading', { name: 'Caregiver workspace' })).toBeInTheDocument();
    expect(screen.getByText(messages.en.overviewDescription)).toBeInTheDocument();
    expect(screen.queryByText(messages.ar.overviewDescription)).not.toBeInTheDocument();
  });

  it('renders the Arabic activity feed, KPI labels, and states' , async () => {
    renderDashboard({ language: 'ar', activity: arActivity });
    expect(await screen.findByRole('heading', { name: messages.ar.dashboardTitle })).toBeInTheDocument();
    const activity = screen.getByRole('region', { name: messages.ar.activityFeedTitle });
    expect(await within(activity).findByText(messages.ar.activityKindNote)).toBeInTheDocument();
    expect(within(activity).getByRole('button', { name: messages.ar.activityFeedRefresh })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: messages.ar.overviewKpiRecorded })).toBeInTheDocument();
    expect(screen.getByText(messages.ar.overviewNoteRecorded)).toBeInTheDocument();
  });
});

describe('dashboard direction', () => {
  it('sets lang and dir on the document root for each locale', async () => {
    const user = userEvent.setup();
    renderDashboard({ language: 'en' });
    await screen.findByRole('heading', { name: 'Caregiver workspace' });
    await waitFor(() => expect(document.documentElement).toHaveAttribute('dir', 'ltr'));
    expect(document.documentElement).toHaveAttribute('lang', 'en');

    await user.click(screen.getByRole('button', { name: messages.en.switchToArabic }));
    await waitFor(() => expect(document.documentElement).toHaveAttribute('dir', 'rtl'));
    expect(document.documentElement).toHaveAttribute('lang', 'ar');

    await user.click(screen.getByRole('button', { name: messages.ar.switchToEnglish }));
    await waitFor(() => expect(document.documentElement).toHaveAttribute('dir', 'ltr'));
    expect(document.documentElement).toHaveAttribute('lang', 'en');
  });

  it('does not hard-override direction on the dashboard container', async () => {
    renderDashboard({ language: 'ar' });
    await screen.findByRole('heading', { name: messages.ar.dashboardTitle });
    const dashboard = document.querySelector('.workspace-overview') as HTMLElement;
    // The container must inherit document direction rather than pin one.
    expect(dashboard).not.toHaveAttribute('dir');
    // Directions are only pinned where they are required: the native date control stays
    // LTR for ISO input, and locale-formatted dates use `dir="auto"` for bidi isolation.
    const pinned = Array.from(dashboard.querySelectorAll('[dir]'));
    expect(pinned.every((element) => ['ltr', 'auto'].includes(element.getAttribute('dir')!))).toBe(true);
    const dateInput = dashboard.querySelector('input[type="date"]');
    expect(dateInput).toHaveAttribute('dir', 'ltr');
    for (const time of Array.from(dashboard.querySelectorAll('time'))) {
      expect(time).toHaveAttribute('dir', 'auto');
    }
  });
});

describe('dashboard mixed-direction isolation', () => {
  it('isolates a Latin recipient name inside Arabic UI', async () => {
    renderDashboard({ language: 'ar', activity: arActivity });
    const activity = screen.getByRole('region', { name: messages.ar.activityFeedTitle });
    await within(activity).findByText(messages.ar.activityKindNote);
    expect(within(activity).getByText(recipientFixture.name).closest('bdi')).toHaveTextContent(recipientFixture.name);
    const card = screen.getByRole('heading', { name: recipientFixture.name, level: 3 });
    expect(card.querySelector('bdi')).toHaveTextContent(recipientFixture.name);
  });

  it('isolates an Arabic recipient name inside English UI', async () => {
    const arabicRecipient = { ...recipientFixture, name: 'سامر حسن', relationship: 'الأب' };
    const activity = [{ id: activityId, kind: 'note', recipientId: arabicRecipient.id, createdAt: '2026-10-18T13:00:00Z' }];
    renderDashboard({ language: 'en', recipients: [arabicRecipient], activity });
    const feed = screen.getByRole('region', { name: messages.en.activityFeedTitle });
    await within(feed).findByText(messages.en.activityKindNote);
    expect(within(feed).getByText('سامر حسن').closest('bdi')).toHaveTextContent('سامر حسن');
    const card = screen.getByRole('heading', { name: 'سامر حسن', level: 3 });
    expect(card.querySelector('bdi')).toHaveTextContent('سامر حسن');
  });
});

describe('dashboard Arabic state semantics', () => {
  it('keeps error, unavailable, and empty activity states distinct in Arabic', async () => {
    renderDashboard({ language: 'ar', activityFails: true });
    expect(await screen.findByText(messages.ar.activityFeedError)).toBeInTheDocument();
    // A failed feed must never be presented as an empty feed.
    expect(screen.queryByText(messages.ar.activityFeedEmpty)).not.toBeInTheDocument();
    expect(screen.getByRole('region', { name: messages.ar.activityFeedTitle }).querySelector('ul')).toBeNull();
    // Unavailable is its own word, distinct from the empty-state wording.
    const ar: Record<string, string> = messages.ar;
    expect(ar.activityFeedError).not.toBe(ar.activityFeedEmpty);
    expect(ar.overviewKpiUnavailable).not.toBe(ar.activityFeedEmpty);
  });

  it('shows the Arabic empty activity message only after a successful empty response', async () => {
    renderDashboard({ language: 'ar', activity: [] });
    const activity = screen.getByRole('region', { name: messages.ar.activityFeedTitle });
    expect(await within(activity).findByText(messages.ar.activityFeedEmpty)).toBeInTheDocument();
    expect(screen.queryByText(messages.ar.activityFeedError)).not.toBeInTheDocument();
  });

  it('keeps an Arabic note-status failure distinct from "no note recorded"', async () => {
    const fetcher = vi.fn().mockImplementation((path: string) => {
      if (path.startsWith('/api/activity?')) return Promise.resolve(jsonResponse([]));
      if (path === '/api/recipients') return Promise.resolve(jsonResponse([recipientFixture]));
      if (path.startsWith('/api/notes?')) {
        const from = new URL(path, 'http://localhost').searchParams.get('from');
        return Promise.resolve(from === '2026-10-18'
          ? jsonResponse({ message: 'private diagnostics' }, 500) : historyResponse([]));
      }
      return Promise.resolve(jsonResponse({}, 404));
    });
    vi.stubGlobal('fetch', fetcher);
    localStorage.setItem('caregiver-language', 'ar');
    sessionStorage.setItem('caregiver-auth-session-v1', JSON.stringify({ token: testToken(), baseUrl: '' }));
    render(<I18nProvider><ApiProvider config={{ apiBaseUrl: '' }}><AuthProvider>
      <MemoryRouter initialEntries={['/app?date=2026-10-18']}><App /></MemoryRouter>
    </AuthProvider></ApiProvider></I18nProvider>);

    expect(await screen.findByText(messages.ar.overviewNoteStatusUnavailable)).toBeInTheDocument();
    expect(screen.queryByText(messages.ar.overviewNoNote)).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: new RegExp(messages.ar.retry) })).toBeInTheDocument();
  });

  it('shows Arabic unavailable KPI wording instead of a fake zero when statuses are unverified', () => {
    vi.stubGlobal('fetch', vi.fn());
    localStorage.setItem('caregiver-language', 'ar');
    render(<I18nProvider><OverviewKpis state={{ status: 'ready', data: [
      { recipient: recipientFixture, note: noteListFixture, status: 'recorded' },
      { recipient: { ...recipientFixture, id: 'b2222222-2222-4222-8222-222222222222' }, note: null, status: 'error' },
    ] }} /></I18nProvider>);

    const recorded = screen.getByRole('heading', { name: messages.ar.overviewKpiRecorded });
    const notRecorded = screen.getByRole('heading', { name: messages.ar.overviewKpiNotRecorded });
    expect(recorded.parentElement?.querySelector('output')).toHaveTextContent(messages.ar.overviewKpiUnavailable);
    expect(notRecorded.parentElement?.querySelector('output')).toHaveTextContent(messages.ar.overviewKpiUnavailable);
    expect(recorded.parentElement?.querySelector('output')).not.toHaveTextContent('0');
  });
});

describe('dashboard locale-sensitive formatting', () => {
  it('formats KPI counts with the active locale digits and isolates them', () => {
    vi.stubGlobal('fetch', vi.fn());
    localStorage.setItem('caregiver-language', 'ar');
    const rows = [
      { recipient: recipientFixture, note: noteListFixture, status: 'recorded' as const },
      { recipient: { ...recipientFixture, id: 'c3333333-3333-4333-8333-333333333333' }, note: null, status: 'missing' as const },
    ];
    render(<I18nProvider><OverviewKpis state={{ status: 'ready', data: rows }} /></I18nProvider>);

    const recipients = screen.getByRole('heading', { name: messages.ar.overviewKpiRecipients });
    const output = recipients.parentElement?.querySelector('output') as HTMLElement;
    expect(output).toHaveTextContent(new Intl.NumberFormat('ar-EG').format(2));
    expect(output.querySelector('bdi')).toHaveTextContent(new Intl.NumberFormat('ar-EG').format(2));

    const record = screen.getByRole('heading', { name: messages.ar.overviewKpiRecorded });
    expect(record.parentElement?.querySelector('output')).toHaveTextContent(new Intl.NumberFormat('ar-EG').format(1));
  });

  it('formats the selected date with the active locale while keeping an ISO dateTime attribute', async () => {
    renderDashboard({ language: 'ar' });
    await screen.findByRole('heading', { name: messages.ar.dashboardTitle });
    const selected = document.querySelector('.overview-date time') as HTMLTimeElement;
    expect(selected.getAttribute('datetime')).toBe('2026-10-18');
    expect(selected.textContent).toBe(new Intl.DateTimeFormat('ar-EG', { dateStyle: 'long', timeZone: 'UTC' }).format(new Date('2026-10-18T12:00:00Z')));
  });

  it('keeps activity timestamps machine-readable in ISO form in Arabic', async () => {
    renderDashboard({ language: 'ar', activity: arActivity });
    const activity = screen.getByRole('region', { name: messages.ar.activityFeedTitle });
    await within(activity).findByText(messages.ar.activityKindNote);
    const time = activity.querySelector('time') as HTMLTimeElement;
    expect(time.getAttribute('datetime')).toBe('2026-10-18T13:00:00Z');
    expect(time.textContent).toBe(new Intl.DateTimeFormat('ar-EG', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date('2026-10-18T13:00:00Z')));
  });
});

describe('dashboard locale does not change navigation', () => {
  it('keeps routes, note links, and ?date= handling identical across locales', async () => {
    const check = async (language: 'en' | 'ar') => {
      const { view } = renderDashboard({ language, activity: arActivity });
      await screen.findByRole('link', { name: new RegExp(messages[language].overviewOpenNote) });
      const hrefs = Array.from(document.querySelectorAll('.workspace-overview a[href]'))
        .map((anchor) => anchor.getAttribute('href')).sort();
      const openNote = document.querySelector('.overview-note-status a')?.getAttribute('href');
      const location = window.location.search;
      view.unmount();
      return { hrefs, openNote, location };
    };

    const english = await check('en');
    const arabic = await check('ar');
    expect(arabic.hrefs).toEqual(english.hrefs);
    expect(arabic.openNote).toBe(english.openNote);
    expect(arabic.openNote).toBe(`/app/notes/${noteListFixture.id ?? 'x'}`.replace('x', english.openNote!.split('/').pop()!));
    expect(arabic.location).toBe(english.location);
  });
});
