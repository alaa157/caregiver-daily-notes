import type { DailyNote, NoteAddendum, Observations } from '../api/notes';
import { recipientFixture } from './recipientFixtures';

export const noteObservations: Observations = {
  mood: 'هادئ', appetite: 'As reported', sleep: '6 hours', mobility: 'Walked with help',
  medicationStatus: 'UNKNOWN', medicationVerified: false, painScore: 0, fallReported: false,
  freeText: '  غير متأكد من الدواء.\nEnglish & عربي — «نص» <script>alert(1)</script>  ',
};
export const noteFixture: DailyNote = {
  ...noteObservations, id: 'd4444444-4444-4444-8444-444444444444', recipientId: recipientFixture.id,
  date: '2026-10-18', addendumCount: 0, createdAt: '2026-10-18T10:00:00Z',
};
export const addendumFixture: NoteAddendum = {
  id: 'e5555555-5555-4555-8555-555555555555', noteId: noteFixture.id, createdAt: '2026-10-18T11:00:00Z',
  mood: null, appetite: null, sleep: null, mobility: null, medicationStatus: 'MISSED', medicationVerified: false,
  painScore: null, fallReported: null, freeText: 'تصحيح مستقل — original stays unchanged.',
};
