import type { SummaryRequest } from '../api/summary';
import { recipientFixture } from './recipientFixtures';

/** Contract fixtures, not an existing or deployed summary endpoint. */
export const summaryTestPath = '/api/test-only-summary';
export const summaryRequest: SummaryRequest = { recipientId: recipientFixture.id, periodEndInclusive: '2026-10-24', periodDays: 7 };
export function summaryOpenApi(path = summaryTestPath) {
  return {
    openapi: '3.0.1',
    paths: { [path]: { post: {
      requestBody: { content: { 'application/json': { schema: { $ref: '#/components/schemas/SummaryRequest' } } } },
      responses: { '200': { content: { 'application/json': { schema: { $ref: '#/components/schemas/SummaryResult' } } } } },
    } } },
    components: { schemas: {
      SummaryRequest: { properties: { recipientId: { type: 'string' }, periodEndInclusive: { type: 'string', format: 'date' }, periodDays: { type: 'integer' } } },
      SummaryResult: { properties: { summary: { type: 'object' }, safety: { type: 'object' }, trends: { type: 'array' } } },
    } },
  };
}
export function summaryFixture() {
  return {
    summary: {
      observations: [{ text: 'Reported appetite varied. ملاحظة غير مؤكدة.', noteId: 'c3333333-3333-4333-8333-333333333333', quote: '  الأكل قليل\nغير متأكد من الدواء <script>alert(1)</script>  ' }],
      uncertainties: [{ topic: 'Medication / الدواء', detail: 'Conflicting entries; taking medication is unverified. لا يمكن الجزم.' }],
      aiUnavailable: false,
    },
    safety: { needsDoctorBanner: true, signals: [{ ruleId: 'FALL_DETECTED', severity: 'HIGH', reason: 'Fall reported in period / تم الإبلاغ عن سقوط' }] },
    trends: [{ metric: 'poor_appetite', previousCount: 1, currentCount: 3, delta: 2 }],
  };
}
