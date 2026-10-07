import {Note, Plan, Recipient, Summary} from '../types/api';

// Synthetic fixture data for tests and backend-less dev. Never real data.
export const RECIPIENT: Recipient = {
  id: 'r1',
  name: 'Fatma Hassan',
  active: true,
};

export const ENGLISH_NOTE: Note = {
  id: 'n1',
  recipientId: 'r1',
  date: '2026-10-01',
  mood: 'good',
  appetite: 'good',
  sleep: 'good',
  mobility: 'good',
  medicationTaken: 'taken',
  pain: 3,
  fall: false,
  text: 'Patient was in good mood today, mild pain level 3.',
};

export const ARABIC_NOTE: Note = {
  id: 'n2',
  recipientId: 'r2',
  date: '2026-10-02',
  mood: 'good',
  appetite: 'poor',
  sleep: 'fair',
  mobility: 'limited',
  medicationTaken: 'unknown',
  pain: 3,
  fall: true,
  text: 'المريضة كانت مزاجها جيد اليوم لكنها سقطت في الحمام، درجة الألم 3.',
};

export const EG_NOTE: Note = {
  id: 'n3',
  recipientId: 'r1',
  date: '2026-10-03',
  mood: 'fair',
  appetite: 'fair',
  sleep: 'poor',
  mobility: 'good',
  medicationTaken: 'unknown',
  pain: 0,
  fall: false,
  text: 'الست فاطمة انهارده كانت كويسة بس مش فاكرة خدت الدوا ولا لأ.',
};

export const SUMMARY_WITH_FLAGS: Summary = {
  id: 's1',
  recipientId: 'r2',
  periodDays: 7,
  text: 'Fall reported with mild pain; medication state unclear.',
  redFlags: ['FALL_REPORTED'],
  evidence: [{noteId: 'n2', quote: 'سقطت في الحمام'}],
  uncertainties: [{topic: 'medication', detail: 'Caregiver unsure whether medication was taken.'}],
};

export const PLAN: Plan = {
  id: 'p1',
  recipientId: 'r1',
  versions: [
    {
      version: 1,
      status: 'Suggested',
      items: ['Monitor pain daily'],
      createdAt: '2026-10-03',
      reason: 'Initial suggestion from summary s1.',
    },
    {
      version: 0,
      status: 'Dismissed',
      items: ['Extra evening check'],
      createdAt: '2026-09-26',
      reason: 'Superseded by newer observations.',
    },
  ],
};
