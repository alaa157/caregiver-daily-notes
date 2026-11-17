import { describe, expect, it, vi } from 'vitest';
import { createSafetySignalsApi } from '../api/safetySignals';
import { ApiError, createApiClient } from '../lib/api';
import { jsonResponse } from './authFixtures';

const recipientId = '11999999-9999-4999-8999-999999999999';
const period = { periodEndInclusive: '2026-10-26', periodDays: 7 as const };

function setup(body: unknown, status = 200) {
  const fetcher = vi.fn().mockImplementation(() => Promise.resolve(jsonResponse(body, status)));
  return { api: createSafetySignalsApi(createApiClient({ apiBaseUrl: '' }, fetcher)), fetcher };
}

describe('provider-free safety signals API', () => {
  it('reads fall and high-pain flags from a plain evaluator response', async () => {
    const { api, fetcher } = setup({
      signals: [
        { ruleId: 'FALL_DETECTED', severity: 'HIGH', reason: 'Fall reported in period' },
        { ruleId: 'HIGH_PAIN', severity: 'HIGH', reason: 'Max pain 9 in period' },
      ],
      needsDoctorBanner: true,
    });

    const safety = await api.evaluate(recipientId, period);

    expect(safety.incomplete).toBe(false);
    expect(safety.needsDoctorBanner).toBe(true);
    expect(safety.signals.map((signal) => signal.ruleId)).toEqual(['FALL_DETECTED', 'HIGH_PAIN']);
    const [path, init] = fetcher.mock.calls[0];
    expect(String(path)).toBe(`/api/recipients/${recipientId}/safety-signals?periodEndInclusive=2026-10-26&periodDays=7`);
    expect((init?.method ?? 'GET')).toBe('GET');
    expect(new Headers(init?.headers).has('Authorization')).toBe(false);
  });

  it('marks a malformed response incomplete so callers never treat it as an all-clear', async () => {
    const { api } = setup({ needsDoctorBanner: false });
    const safety = await api.evaluate(recipientId, period);
    expect(safety.incomplete).toBe(true);
    expect(safety.signals).toEqual([]);
  });

  it.each([
    ['recipient', 'not-an-id', period],
    ['date', recipientId, { ...period, periodEndInclusive: '26/10/2026' }],
    ['period', recipientId, { ...period, periodDays: 5 as 7 }],
  ])('rejects an invalid %s before any network request', async (_name, id, input) => {
    const { api, fetcher } = setup({ signals: [], needsDoctorBanner: false });
    await expect(api.evaluate(id, input)).rejects.toBeInstanceOf(ApiError);
    expect(fetcher).not.toHaveBeenCalled();
  });
});
