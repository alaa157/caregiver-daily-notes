import type { DailyNote, LegacyObservations, NoteAddendum } from '../api/notes';
import type { NoteObservations } from '../api/observation';
import { recipientFixture } from './recipientFixtures';

export const noteObservations: LegacyObservations = {
  mood: 'هادئ', appetite: 'As reported', sleep: '6 hours', mobility: 'Walked with help',
  medicationStatus: 'UNKNOWN', medicationVerified: false, painScore: 0, fallReported: false,
  freeText: '  غير متأكد من الدواء.\nEnglish & عربي — «نص» <script>alert(1)</script>  ',
};
export const structuredObservations: NoteObservations = {
  mood: { state: 'RECORDED', value: noteObservations.mood! },
  appetite: { state: 'RECORDED', value: noteObservations.appetite! },
  sleep: { state: 'RECORDED', value: noteObservations.sleep! },
  mobility: { state: 'RECORDED', value: noteObservations.mobility! },
  medicationStatus: { state: 'UNKNOWN' },
  medicationVerification: 'UNVERIFIED',
  painScore: { state: 'RECORDED', value: 0 },
  fallReported: { state: 'RECORDED', value: false },
  freeText: { state: 'RECORDED', value: noteObservations.freeText! },
};
export const noteFixture: DailyNote = {
  ...noteObservations, observations: structuredObservations,
  id: 'd4444444-4444-4444-8444-444444444444', recipientId: recipientFixture.id,
  date: '2026-10-18', addendumCount: 0, createdAt: '2026-10-18T10:00:00Z',
};
export const addendumObservations: NoteObservations = {
  mood: { state: 'NOT_RECORDED' },
  appetite: { state: 'NOT_RECORDED' },
  sleep: { state: 'NOT_RECORDED' },
  mobility: { state: 'NOT_RECORDED' },
  medicationStatus: { state: 'RECORDED', value: 'MISSED' },
  medicationVerification: 'UNVERIFIED',
  painScore: { state: 'NOT_RECORDED' },
  fallReported: { state: 'NOT_RECORDED' },
  freeText: { state: 'RECORDED', value: 'تصحيح مستقل — original stays unchanged.' },
};
export const addendumFixture: NoteAddendum = {
  id: 'e5555555-5555-4555-8555-555555555555', noteId: noteFixture.id,
  createdAt: '2026-10-18T11:00:00Z',
  mood: null, appetite: null, sleep: null, mobility: null, medicationStatus: 'MISSED',
  medicationVerified: false, painScore: null, fallReported: null,
  freeText: 'تصحيح مستقل — original stays unchanged.',
  observations: addendumObservations, providedFields: ['medicationStatus', 'freeText'],
  medicationVerificationProvided: true,
};
