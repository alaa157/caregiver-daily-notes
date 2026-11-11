import { describe, expect, it } from 'vitest';
import { noteFixture } from '../../tests/noteFixtures';
import { recipientFixture } from '../../tests/recipientFixtures';
import { toSummaryState } from './summaryContract';
import type { OverviewRow, OverviewState } from './useDashboardOverview';

const selectedDate = '2026-10-18';
const recordedRow: OverviewRow = { recipient: recipientFixture, note: noteFixture, status: 'recorded' };

describe('read-only summary contract', () => {
  it('projects a confirmed selected-date note using its real recipient and note identifiers', () => {
    expect(toSummaryState({ status: 'ready', data: [recordedRow] }, selectedDate)).toEqual({
      status: 'ready',
      data: [{ recipientId: recipientFixture.id, noteId: noteFixture.id, observedAt: null, status: 'recorded' }],
    });
  });

  it('keeps an unavailable observation timestamp null rather than substituting note creation time', () => {
    expect(noteFixture.createdAt).toBeTruthy();
    expect(toSummaryState({ status: 'ready', data: [recordedRow] }, selectedDate)).toMatchObject({
      data: [{ status: 'recorded', observedAt: null }],
    });
  });

  it('maps only confirmed absence to missing with null identifiers and timestamps', () => {
    const row: OverviewRow = { recipient: recipientFixture, note: null, status: 'missing' };
    expect(toSummaryState({ status: 'ready', data: [row] }, selectedDate)).toEqual({
      status: 'ready',
      data: [{ recipientId: recipientFixture.id, noteId: null, observedAt: null, status: 'missing' }],
    });
  });

  it('preserves recipient failures as a partial result with null note values', () => {
    const row: OverviewRow = { recipient: recipientFixture, note: null, status: 'error' };
    expect(toSummaryState({ status: 'ready', data: [row] }, selectedDate)).toEqual({
      status: 'partial',
      data: [{ recipientId: recipientFixture.id, noteId: null, observedAt: null, status: 'error' }],
    });
  });

  it('preserves confirmed rows alongside failures without presenting the result as fully ready', () => {
    const failed: OverviewRow = { recipient: { ...recipientFixture, id: 'c3333333-3333-4333-8333-333333333333' }, note: null, status: 'error' };
    expect(toSummaryState({ status: 'ready', data: [recordedRow, failed] }, selectedDate)).toEqual({
      status: 'partial',
      data: [
        { recipientId: recipientFixture.id, noteId: noteFixture.id, observedAt: null, status: 'recorded' },
        { recipientId: failed.recipient.id, noteId: null, observedAt: null, status: 'error' },
      ],
    });
  });

  it('retains page-level loading and recipient-list errors for consumers', () => {
    const failure = new Error('recipient request failed');
    expect(toSummaryState({ status: 'loading' }, selectedDate)).toEqual({ status: 'loading' });
    expect(toSummaryState({ status: 'error', error: failure }, selectedDate)).toEqual({ status: 'error', error: failure });
  });

  it('rejects inconsistent recorded or missing rows whose note does not match the contract', () => {
    const wrongRecipient = { ...noteFixture, recipientId: 'c3333333-3333-4333-8333-333333333333' };
    const wrongDate = { ...noteFixture, date: '2026-10-17' };
    for (const note of [wrongRecipient, wrongDate]) {
      const row: OverviewRow = { recipient: recipientFixture, note, status: 'recorded' };
      expect(toSummaryState({ status: 'ready', data: [row] }, selectedDate)).toMatchObject({
        status: 'partial', data: [{ recipientId: recipientFixture.id, noteId: null, observedAt: null, status: 'error' }],
      });
    }
    const inconsistentMissing: OverviewRow = { recipient: recipientFixture, note: noteFixture, status: 'missing' };
    expect(toSummaryState({ status: 'ready', data: [inconsistentMissing] }, selectedDate)).toMatchObject({
      status: 'partial', data: [{ noteId: null, status: 'error' }],
    });
  });

  it('does not mutate or expand the source rows', () => {
    const state: OverviewState = { status: 'ready', data: [recordedRow] };
    const before = structuredClone(state);
    const summary = toSummaryState(state, selectedDate);
    expect(state).toEqual(before);
    expect(Object.keys(summary.status === 'ready' ? summary.data[0] : {})).toEqual([
      'recipientId', 'noteId', 'observedAt', 'status',
    ]);
  });
});
