import type { Recipient } from '../api/recipients';

/** Synthetic fixtures only; no real care recipient records. */
export const recipientFixture: Recipient = {
  id: 'a1111111-1111-4111-8111-111111111111',
  name: 'Samir Hassan',
  relationship: 'Father',
  active: true,
  lifecycleStatus: 'ACTIVE',
  archivedAt: null,
  archivedBy: null,
  restoredAt: null,
  restoredBy: null,
  createdAt: '2026-10-01T10:00:00Z',
  updatedAt: '2026-10-01T10:00:00Z',
};