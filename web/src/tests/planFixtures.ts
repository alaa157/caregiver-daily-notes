import type { CarePlan } from '../api/plans';
import { recipientFixture } from './recipientFixtures';

/** Synthetic records only; these fixtures do not assert AI provenance. */
export const activePlanFixture: CarePlan = {
  id: 'c3333333-3333-4333-8333-333333333333', recipientId: recipientFixture.id,
  version: 1, status: 'ACCEPTED', content: 'Saved care routine.\nمراجعة الملاحظات يوميًا.',
  sourceSummaryId: 'summary-original', sourcePlanId: null, evidenceNoteIds: null, createdAt: '2026-10-01T12:00:00Z',
};
export const suggestedPlanFixture: CarePlan = {
  ...activePlanFixture, id: 'd4444444-4444-4444-8444-444444444444', version: 2, status: 'SUGGESTED',
  content: 'مُراجعة notes يوميًا.\n  Keep exact spacing & <script>alert("test")</script>\nMedication record: unchanged.',
  sourceSummaryId: 'summary-2', evidenceNoteIds: 'e5555555-5555-4555-8555-555555555555,reference:/external',
  createdAt: '2026-10-02T12:00:00Z',
};

export const protectedActiveFixture: CarePlan = {
  ...activePlanFixture, sourceType: 'AI_GENERATED', editable: false,
  structuredContent: { schemaVersion: 1,
    changes: [{ action: 'SCHEDULE_CHECK_IN', params: { withinDays: '5' }, reason: 'Review logged observations.', evidenceNoteIds: ['e5555555-5555-4555-8555-555555555555'] }],
    medications: [{ name: 'SYNTHETIC_REPORTED_ITEM', dose: 'reported-value', schedule: 'reported-schedule' }],
  },
};
protectedActiveFixture.content = JSON.stringify(protectedActiveFixture.structuredContent);
export const protectedSuggestionFixture: CarePlan = {
  ...suggestedPlanFixture, sourceType: 'AI_GENERATED', editable: true, sourcePlanId: activePlanFixture.id,
  structuredContent: { ...protectedActiveFixture.structuredContent!,
    changes: [{ ...protectedActiveFixture.structuredContent!.changes[0], params: { withinDays: '7' } }],
  },
};
protectedSuggestionFixture.content = JSON.stringify(protectedSuggestionFixture.structuredContent);
