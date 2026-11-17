import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { RecipientSafety } from '../features/safety/RecipientSafety';
import { I18nProvider } from '../i18n/I18nProvider';
import { ApiProvider } from '../lib/api';
import { jsonResponse } from './authFixtures';

const recipientId = '11999999-9999-4999-8999-999999999999';

function renderPanel(body: unknown, status = 200) {
  const fetcher = vi.fn().mockImplementation(() => Promise.resolve(jsonResponse(body, status)));
  vi.stubGlobal('fetch', fetcher);
  const view = render(<I18nProvider><ApiProvider config={{ apiBaseUrl: '' }}>
    <MemoryRouter><RecipientSafety recipientId={recipientId} periodEndInclusive="2026-10-26" /></MemoryRouter>
  </ApiProvider></I18nProvider>);
  return { fetcher, ...view };
}

describe('provider-free safety flags', () => {
  beforeEach(() => localStorage.setItem('caregiver-language', 'en'));

  it('shows fall and high-pain flags without any AI provider credentials', async () => {
    const { fetcher } = renderPanel({
      signals: [
        { ruleId: 'FALL_DETECTED', severity: 'HIGH', reason: 'Fall reported in period' },
        { ruleId: 'HIGH_PAIN', severity: 'HIGH', reason: 'Max pain 9 in period' },
      ],
      needsDoctorBanner: true,
    });

    expect(await screen.findByText('Fall detected')).toBeInTheDocument();
    expect(screen.getByText('High pain recorded')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Clinical review requested' })).toBeInTheDocument();
    expect(screen.getByText('Fall reported in period')).toBeInTheDocument();
    expect(screen.queryByText('FALL_DETECTED')).not.toBeInTheDocument();
    expect(fetcher.mock.calls.some(([path]) => String(path) ===
      `/api/recipients/${recipientId}/safety-signals?periodEndInclusive=2026-10-26&periodDays=7`)).toBe(true);
    const init = fetcher.mock.calls.find(([path]) => String(path).includes('/safety-signals?'))?.[1];
    expect(new Headers(init?.headers).has('Authorization')).toBe(false);
  });

  it('shows emergency guidance for a watch-level free-text flag without a doctor banner', async () => {
    renderPanel({
      signals: [
        { ruleId: 'FREE_TEXT_CONCERN', severity: 'WATCH', reason: 'Possible fall mentioned in note text — please review' },
      ],
      needsDoctorBanner: false,
    });

    expect(await screen.findByText('Concern in note text')).toBeInTheDocument();
    expect(screen.getByText('If you suspect a medical emergency, contact your local emergency services immediately.')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Clinical review requested' })).not.toBeInTheDocument();
  });

  it('shows no emergency guidance when no flags are present', async () => {
    renderPanel({ signals: [], needsDoctorBanner: false });
    expect(await screen.findByText(/No safety flags were returned for this period/)).toBeInTheDocument();
    expect(screen.queryByText(/emergency services/)).not.toBeInTheDocument();
  });

  it('keeps a clean evaluation explicitly neutral', async () => {
    renderPanel({ signals: [], needsDoctorBanner: false });
    expect(await screen.findByText(/No safety flags were returned for this period/)).toBeInTheDocument();
    expect(screen.queryByText('Clinical review requested')).not.toBeInTheDocument();
  });

  it('reports unavailability without claiming an all-clear', async () => {
    renderPanel({ message: 'private diagnostics' }, 503);
    expect(await screen.findByText('Safety signals are unavailable right now. Review the original notes.')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.queryByText(/No safety flags were returned/)).not.toBeInTheDocument();
  });

  it('renders localized labels and reasons in Arabic, never the raw rule code', async () => {
    localStorage.setItem('caregiver-language', 'ar');
    renderPanel({
      signals: [
        { ruleId: 'FALL_DETECTED', severity: 'HIGH', reason: 'Fall reported in period' },
        { ruleId: 'FUTURE_RULE', severity: 'WATCH', reason: 'Some unreviewed reason' },
      ],
      needsDoctorBanner: true,
    });

    expect(await screen.findByText('تم الإبلاغ عن سقوط')).toBeInTheDocument();
    expect(screen.getByText('تم الإبلاغ عن سقوط خلال الفترة المحددة.')).toBeInTheDocument();
    expect(screen.getByText('إشارة سلامة')).toBeInTheDocument();
    expect(screen.getByText('تعذّر تفسير هذه الإشارة. راجع الملاحظات الأصلية.')).toBeInTheDocument();
    expect(screen.queryByText('FALL_DETECTED')).not.toBeInTheDocument();
    expect(screen.queryByText('FUTURE_RULE')).not.toBeInTheDocument();
    const fallItem = screen.getByText('تم الإبلاغ عن سقوط').closest('li');
    expect(fallItem).toHaveAttribute('data-rule-id', 'FALL_DETECTED');
  });
});
