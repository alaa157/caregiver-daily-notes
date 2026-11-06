import { describe, expect, it, vi } from 'vitest';
import { ApiError, createApiClient } from '../lib/api';
import { createSummaryApi, findSummaryEndpoint, readSummaryResponse, readSummarySafety, retainSafety, SummaryEndpointUnavailable, summaryErrorMessage, type ByokSummaryRequest } from '../api/summary';
import { jsonResponse } from './authFixtures';
import { summaryFixture, summaryOpenApi, summaryRequest, summaryTestPath } from './summaryFixtures';

const byokCredentials = { providerId: 'GEMINI', model: 'gemini-test-model', apiKey: 'synthetic-byok-summary-key' } as const;
const byokRequest: ByokSummaryRequest = { summary: summaryRequest, credentials: byokCredentials };

describe('summary contract and owner-advertised endpoint', () => {
  it('discovers the single compatible POST from OpenAPI, ignoring foreign server URLs', () => {
    expect(findSummaryEndpoint({ ...summaryOpenApi(), servers: [{ url: 'https://foreign.invalid' }] })).toBe(summaryTestPath);
  });
  it.each(['https://foreign.invalid/api/summary', '//foreign.invalid/summary', '/api/../auth', '/api/summaries/{id}', '/api/summary?token=x', '/api/summary%2fredirect', '/api/a\\b'])('rejects unsafe or templated operation path %s', (path) => {
    expect(() => findSummaryEndpoint(summaryOpenApi(path))).toThrow(SummaryEndpointUnavailable);
  });
  it('does not guess an endpoint when the current backend has no summary controller', () => {
    expect(() => findSummaryEndpoint({ openapi: '3.0.1', paths: { '/api/notes': { get: {} } } })).toThrow(SummaryEndpointUnavailable);
  });
  it('rejects ambiguous operations, external references and incomplete schemas', () => {
    const document = summaryOpenApi();
    expect(() => findSummaryEndpoint({ ...document, paths: { ...document.paths, '/api/another': document.paths[summaryTestPath] } })).toThrow();
    document.paths[summaryTestPath].post.requestBody.content['application/json'].schema.$ref = 'https://foreign.invalid/contract';
    expect(() => findSummaryEndpoint(document)).toThrow();
    const incomplete = summaryOpenApi();
    Reflect.deleteProperty(incomplete.components.schemas.SummaryRequest.properties, 'recipientId');
    expect(() => findSummaryEndpoint(incomplete)).toThrow();
  });
  it('sends only request fields to the advertised endpoint through the existing transport', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(jsonResponse(summaryOpenApi())).mockResolvedValueOnce(jsonResponse(summaryFixture()));
    const api = createSummaryApi(createApiClient({ apiBaseUrl: '' }, fetcher), fetcher);
    const controller = new AbortController();
    const result = await api.generate({ ...summaryRequest, caregiverId: 'untrusted', model: 'secret' } as typeof summaryRequest, controller.signal);
    expect(fetcher.mock.calls[0][0]).toBe('/v3/api-docs');
    expect(fetcher.mock.calls[1][0]).toBe(summaryTestPath);
    expect(fetcher.mock.calls[1][1]).toMatchObject({ method: 'POST', signal: controller.signal, cache: 'no-store', credentials: 'omit', redirect: 'error' });
    expect(JSON.parse(fetcher.mock.calls[1][1].body)).toEqual(summaryRequest);
    expect(result.safety.needsDoctorBanner).toBe(true);
  });
  it.each([7, 14, 30] as const)('supports %s days', async (periodDays) => {
    const fetcher = vi.fn().mockResolvedValueOnce(jsonResponse(summaryOpenApi())).mockResolvedValueOnce(jsonResponse(summaryFixture()));
    await createSummaryApi(createApiClient({ apiBaseUrl: '' }, fetcher), fetcher).generate({ ...summaryRequest, periodDays });
    expect(JSON.parse(fetcher.mock.calls[1][1].body).periodDays).toBe(periodDays);
  });
  it.each([{ periodDays: 8 }, { recipientId: '../auth' }, { periodEndInclusive: '2026-02-30' }])('rejects invalid input before any network request: %j', async (change) => {
    const fetcher = vi.fn();
    await expect(createSummaryApi(createApiClient({ apiBaseUrl: '' }, fetcher), fetcher).generate({ ...summaryRequest, ...change } as typeof summaryRequest)).rejects.toThrow();
    expect(fetcher).not.toHaveBeenCalled();
  });
  it('stops before POST when the endpoint is missing or a discovery request was cancelled', async () => {
    const fetcher = vi.fn().mockResolvedValue(jsonResponse({ openapi: '3.0.1', paths: {} }));
    await expect(createSummaryApi(createApiClient({ apiBaseUrl: '' }, fetcher), fetcher).generate(summaryRequest)).rejects.toThrow(SummaryEndpointUnavailable);
    expect(fetcher).toHaveBeenCalledTimes(1);
    const controller = new AbortController(); controller.abort();
    const late = vi.fn().mockResolvedValue(jsonResponse(summaryOpenApi()));
    await expect(createSummaryApi(createApiClient({ apiBaseUrl: '' }, late), late).generate(summaryRequest, controller.signal)).rejects.toMatchObject({ name: 'AbortError' });
    expect(late).toHaveBeenCalledTimes(1);
  });
  it('loads public OpenAPI beside an /api runtime base and keeps generation on the authenticated transport', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(jsonResponse(summaryOpenApi())).mockResolvedValueOnce(jsonResponse(summaryFixture()));
    const client = createApiClient({ apiBaseUrl: 'https://backend.example/prefix/api' }, fetcher);
    await createSummaryApi(client, fetcher).generate(summaryRequest);
    expect(fetcher.mock.calls[0][0]).toBe('https://backend.example/prefix/v3/api-docs');
    expect(fetcher.mock.calls[1][0]).toBe('https://backend.example/prefix/api/test-only-summary');
    expect(fetcher.mock.calls[0][1].headers.has('Authorization')).toBe(false);
  });
});

describe('BYOK summary request', () => {
  it('posts summary and credentials to the BYOK route', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(jsonResponse(summaryFixture()));
    const controller = new AbortController();
    const result = await createSummaryApi(createApiClient({ apiBaseUrl: '' }, fetcher), fetcher)
      .generateByok(byokRequest, controller.signal);

    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(fetcher.mock.calls[0][0]).toBe('/api/summaries/byok');
    expect(fetcher.mock.calls[0][1]).toMatchObject({
      method: 'POST',
      signal: controller.signal,
      cache: 'no-store',
      credentials: 'omit',
      redirect: 'error',
      referrerPolicy: 'no-referrer',
    });
    expect(new Headers(fetcher.mock.calls[0][1].headers).get('Content-Type')).toBe('application/json');
    expect(JSON.parse(fetcher.mock.calls[0][1].body)).toEqual({
      summary: summaryRequest,
      credentials: byokCredentials,
    });
    expect(result).toEqual(readSummaryResponse(summaryFixture()));
  });

  it('does not include credentials in summary response parsing', async () => {
    const response = { ...summaryFixture(), credentials: byokCredentials, apiKey: byokCredentials.apiKey };
    const fetcher = vi.fn().mockResolvedValueOnce(jsonResponse(response));
    const result = await createSummaryApi(createApiClient({ apiBaseUrl: '' }, fetcher), fetcher).generateByok(byokRequest);

    expect(result).toEqual(readSummaryResponse(summaryFixture()));
    expect(Object.keys(result).sort()).toEqual(['invalidContent', 'safety', 'summary', 'trends']);
    expect(JSON.stringify(result)).not.toContain(byokCredentials.apiKey);
  });

  it('redacts credential and raw provider details while preserving safe error safety', async () => {
    const providerText = `provider rejected ${byokCredentials.apiKey}`;
    const safety = {
      needsDoctorBanner: true,
      signals: [{ ruleId: 'FALL_DETECTED', severity: 'HIGH', reason: `Fall reported; diagnostic ${byokCredentials.apiKey}` }],
    };
    const rawProviderDetails = { message: providerText, diagnostic: `raw response ${byokCredentials.apiKey}` };
    const fetcher = vi.fn().mockResolvedValueOnce(jsonResponse({ ...rawProviderDetails, safety }, 504));
    let failure: unknown;
    try {
      await createSummaryApi(createApiClient({ apiBaseUrl: '' }, fetcher), fetcher).generateByok(byokRequest);
    } catch (error) {
      failure = error;
    }

    expect(failure).toBeInstanceOf(ApiError);
    const error = failure as ApiError;
    expect(error.status).toBe(504);
    expect(error.message).not.toContain(byokCredentials.apiKey);
    expect(error.message).not.toContain(providerText);
    expect(error.details).toEqual({
      safety: {
        needsDoctorBanner: true,
        signals: [{ ruleId: 'FALL_DETECTED', severity: 'HIGH', reason: 'Fall reported; diagnostic [redacted]' }],
        incomplete: false,
      },
    });
    expect(JSON.stringify(error)).not.toContain(byokCredentials.apiKey);
    expect(JSON.stringify(error)).not.toContain(providerText);
    expect(JSON.stringify(error)).not.toContain('raw response');
    expect(summaryErrorMessage(error)).toBe('summaryTimeout');
  });

  it('rejects invalid summary input before network activity', async () => {
    const fetcher = vi.fn();
    const api = createSummaryApi(createApiClient({ apiBaseUrl: '' }, fetcher), fetcher);
    const invalidSummaries = [
      { ...summaryRequest, recipientId: '../auth' },
      { ...summaryRequest, periodEndInclusive: '2026-02-30' },
      { ...summaryRequest, periodDays: 8 },
    ];

    for (const summary of invalidSummaries) {
      await expect(api.generateByok({ summary: summary as ByokSummaryRequest['summary'], credentials: byokCredentials })).rejects.toBeInstanceOf(ApiError);
    }
    expect(fetcher).not.toHaveBeenCalled();
  });

  it('rejects unusable credentials before network activity', async () => {
    const fetcher = vi.fn();
    const api = createSummaryApi(createApiClient({ apiBaseUrl: '' }, fetcher), fetcher);
    const invalidCredentials = [
      { ...byokCredentials, providerId: 'ANTHROPIC' },
      { ...byokCredentials, model: '  ' },
      { ...byokCredentials, apiKey: '' },
    ];

    for (const credentials of invalidCredentials) {
      await expect(api.generateByok({
        summary: summaryRequest,
        credentials: credentials as ByokSummaryRequest['credentials'],
      })).rejects.toBeInstanceOf(ApiError);
    }
    expect(fetcher).not.toHaveBeenCalled();
  });

  it('forwards cancellation to the BYOK request', async () => {
    const fetcher = vi.fn((_input: RequestInfo | URL, init?: RequestInit) => new Promise<Response>((_, reject) => {
      const signal = init?.signal;
      if (signal?.aborted) reject(new DOMException('Cancelled.', 'AbortError'));
      else signal?.addEventListener('abort', () => reject(new DOMException('Cancelled.', 'AbortError')), { once: true });
    }));
    const controller = new AbortController();
    const pending = createSummaryApi(createApiClient({ apiBaseUrl: '' }, fetcher as typeof fetch), fetcher as typeof fetch)
      .generateByok(byokRequest, controller.signal);

    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(fetcher.mock.calls[0][1]?.signal).toBe(controller.signal);
    controller.abort();
    await expect(pending).rejects.toMatchObject({ name: 'AbortError' });
  });
});

describe('safety survives invalid generated content', () => {
  it('preserves mixed text, quotes, uncertainty and backend counts verbatim', () => {
    const fixture = summaryFixture(); const result = readSummaryResponse(fixture);
    expect(result.summary).toEqual(fixture.summary);
    expect(result.trends).toEqual(fixture.trends);
    expect(result.safety).toEqual({ ...fixture.safety, incomplete: false });
  });
  it.each([null, {}, { observations: [], uncertainties: [] }, { observations: [], uncertainties: [], aiUnavailable: 'false' }, { observations: [{ text: 'unsupported', noteId: 'n', quote: '' }], uncertainties: [], aiUnavailable: false }, { observations: [], uncertainties: [{ topic: '', detail: 'missing topic' }], aiUnavailable: false }])('retains HIGH warnings when model content is malformed: %j', (summary) => {
    const result = readSummaryResponse({ ...summaryFixture(), summary });
    expect(result.summary).toBeNull(); expect(result.invalidContent).toBe(true);
    expect(result.safety.needsDoctorBanner).toBe(true);
    expect(result.safety.signals[0].severity).toBe('HIGH');
  });
  it('marks missing or malformed safety as incomplete, never an all-clear', () => {
    for (const safety of [undefined, null, {}, { signals: [], needsDoctorBanner: 'false' }, { signals: [null], needsDoctorBanner: false }]) {
      expect(readSummarySafety(safety).incomplete).toBe(true);
    }
  });
  it('preserves unknown severities and new rule IDs rather than filtering or relabeling them', () => {
    const safety = readSummarySafety({ signals: [{ severity: 'CRITICAL', ruleId: 'NEW_RULE', reason: 'raw reason' }], needsDoctorBanner: false });
    expect(safety.signals).toEqual([{ severity: 'CRITICAL', ruleId: 'NEW_RULE', reason: 'raw reason' }]);
    expect(safety.incomplete).toBe(true);
  });
  it('retains valid flags alongside malformed flags', () => {
    const fixture = summaryFixture();
    const safety = readSummarySafety({ ...fixture.safety, signals: [...fixture.safety.signals, null] });
    expect(safety.signals[0]).toEqual(fixture.safety.signals[0]); expect(safety.incomplete).toBe(true);
  });
  it('keeps the previous banner on partial or error responses; a complete fresh backend evaluation may replace it', () => {
    const previous = readSummarySafety(summaryFixture().safety);
    const none = readSummarySafety({ signals: [], needsDoctorBanner: false });
    expect(retainSafety(previous, readSummarySafety(null)).needsDoctorBanner).toBe(true);
    expect(retainSafety(previous, none, true).signals[0].severity).toBe('HIGH');
    expect(retainSafety(previous, none).signals).toEqual([]);
  });
  it('labels provider fallback and suppresses any stray generated observations', () => {
    const fixture = summaryFixture(); fixture.summary.aiUnavailable = true;
    const result = readSummaryResponse(fixture);
    expect(result.summary?.observations).toEqual([]); expect(result.summary?.aiUnavailable).toBe(true);
    expect(result.safety.needsDoctorBanner).toBe(true); expect(result.trends).toEqual(fixture.trends);
  });
  it('does not parse unavailable output as successful observations', () => {
    const fixture = summaryFixture();
    fixture.summary.aiUnavailable = true;
    fixture.summary.observations.push({ text: 'stray model output', noteId: 'n2', quote: 'stray quote' });
    fixture.summary.observations[0].quote = '';

    const result = readSummaryResponse(fixture);

    expect(result.summary?.aiUnavailable).toBe(true);
    expect(result.summary?.observations).toEqual([]);
    expect(result.summary?.uncertainties).toEqual(fixture.summary.uncertainties);
    expect(result.safety.needsDoctorBanner).toBe(true);
    expect(result.trends).toEqual(fixture.trends);
    expect(result.invalidContent).toBe(false);
  });
  it('marks malformed summary response as invalid content', () => {
    const fixture = summaryFixture();
    fixture.summary.observations = [{ text: 'partial generated text', noteId: 'n1', quote: '' }];

    const result = readSummaryResponse(fixture);

    expect(result.invalidContent).toBe(true);
    expect(result.summary).toBeNull();
    expect(result.safety).toMatchObject({ needsDoctorBanner: true, signals: fixture.safety.signals });
    expect(result.trends).toEqual(fixture.trends);
  });
  it('parses trend sample sizes', () => {
    const result = readSummaryResponse(summaryFixture());
    expect(result.invalidContent).toBe(false);
    expect(result.trends[0]).toEqual({ metric: 'POOR_APPETITE_COUNT', previousCount: 1, currentCount: 3, delta: 2, previousSampleSize: 4, currentSampleSize: 6 });
  });
  it('rejects negative trend sample size', () => {
    for (const change of [{ currentSampleSize: -1 }, { previousSampleSize: undefined }, { currentSampleSize: null }, { previousSampleSize: '4' }, { currentSampleSize: 1.5 }, { previousSampleSize: Number.MAX_SAFE_INTEGER + 1 }]) {
      const fixture = summaryFixture(); Object.assign(fixture.trends[0], change);
      const result = readSummaryResponse(fixture);
      expect(result.trends).toEqual([]); expect(result.invalidContent).toBe(true);
    }
  });
  it('rejects impossible event counts and accepts valid count/sample boundaries', () => {
    const impossible = [
      { previousCount: 5, previousSampleSize: 4, currentCount: 3, currentSampleSize: 6, delta: -2 },
      { previousCount: 1, previousSampleSize: 4, currentCount: 7, currentSampleSize: 6, delta: 6 },
      { previousCount: 5, previousSampleSize: 4, currentCount: 7, currentSampleSize: 6, delta: 2 },
    ];
    for (const values of impossible) {
      const fixture = summaryFixture(); Object.assign(fixture.trends[0], values);
      const result = readSummaryResponse(fixture);
      expect(result.trends).toEqual([]); expect(result.invalidContent).toBe(true);
    }
    const valid = [
      { previousCount: 4, previousSampleSize: 4, currentCount: 6, currentSampleSize: 6, delta: 2 },
      { previousCount: 0, previousSampleSize: 3, currentCount: 1, currentSampleSize: 6, delta: 1 },
      { previousCount: 0, previousSampleSize: 0, currentCount: 0, currentSampleSize: 0, delta: 0 },
    ];
    for (const values of valid) {
      const fixture = summaryFixture(); Object.assign(fixture.trends[0], values);
      const result = readSummaryResponse(fixture);
      expect(result.trends).toHaveLength(1); expect(result.invalidContent).toBe(false);
    }
  });
  it('rejects negative trend counts', () => {
    for (const values of [{ previousCount: -1 }, { currentCount: -1 }]) {
      const fixture = summaryFixture(); Object.assign(fixture.trends[0], values);
      const result = readSummaryResponse(fixture);
      expect(result.trends).toEqual([]); expect(result.invalidContent).toBe(true);
    }
  });
  it('rejects trend delta that does not match counts', () => {
    const fixture = summaryFixture(); fixture.trends[0].previousCount = 2; fixture.trends[0].currentCount = 3; fixture.trends[0].delta = 0;
    const result = readSummaryResponse(fixture);
    expect(result.trends).toEqual([]); expect(result.invalidContent).toBe(true);
  });
  it('does not invent counts or repair contradictory backend trends', () => {
    const fixture = summaryFixture(); fixture.trends[0].delta = 99;
    const result = readSummaryResponse(fixture);
    expect(result.trends).toEqual([]); expect(result.invalidContent).toBe(true);
    expect(result.safety.signals[0].severity).toBe('HIGH');
  });
  it.each([[404, 'summaryUnavailable'], [403, 'summaryUnavailable'], [400, 'summaryValidation'], [429, 'summaryRateLimited'], [504, 'summaryTimeout'], [500, 'summaryServiceError']])('maps HTTP %s without exposing diagnostics', (status, expected) => {
    expect(summaryErrorMessage(new ApiError(Number(status), { message: 'private token' }))).toBe(expected);
  });
});
