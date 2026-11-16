import type { SummaryRequest } from '../api/summary';
import { recipientFixture } from './recipientFixtures';

/** Contract fixtures for the BYOK summary route. */
export const summaryRequest: SummaryRequest = { recipientId: recipientFixture.id, periodEndInclusive: '2026-10-24', periodDays: 7 };
export function summaryFixture() {
  return {
    summary: {
      observations: [{ text: 'Reported appetite varied. ملاحظة غير مؤكدة.', noteId: 'c3333333-3333-4333-8333-333333333333', quote: '  الأكل قليل\nغير متأكد من الدواء <script>alert(1)</script>  ' }],
      uncertainties: [{ topic: 'Medication / الدواء', detail: 'Conflicting entries; taking medication is unverified. لا يمكن الجزم.' }],
      aiUnavailable: false,
    },
    safety: { needsDoctorBanner: true, signals: [{ ruleId: 'FALL_DETECTED', severity: 'HIGH', reason: 'Fall reported in period / تم الإبلاغ عن سقوط' }] },
    trends: [{ metric: 'POOR_APPETITE_COUNT', previousCount: 1, currentCount: 3, delta: 2, previousSampleSize: 4, currentSampleSize: 6 }],
  };
}
