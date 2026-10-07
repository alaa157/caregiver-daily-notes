import {fireEvent, render} from '@testing-library/react-native';
import React from 'react';
import HistoryScreen from '../features/history/HistoryScreen';
import NoteDetailScreen from '../features/notes/NoteDetailScreen';
import SummaryCard from '../features/ai/SummaryCard';
import PlanDetailScreen, {PlansScreen} from '../features/plans/PlansScreen';
import RecipientsScreen from '../features/recipients/RecipientsScreen';
import {ApiError} from '../lib/errors';
import {ARABIC_NOTE, PLAN, RECIPIENT, SUMMARY_WITH_FLAGS} from '../test-utils/fixtures';

jest.mock('@react-native-async-storage/async-storage', () => require('../test-utils/inMemoryStorage').mock);

const networkError = () => new ApiError('NETWORK', 'down');
const aiDown = () =>
  new ApiError('AI_UNAVAILABLE', 'AI_UNAVAILABLE: down');

describe('data screens', () => {
  it('recipients list shows loading without content, then content', async () => {
    const loading = await render(
      <RecipientsScreen recipients={[]} state="loading" />,
    );
    expect(loading.getByTestId('api-loading')).toBeTruthy();
    expect(loading.queryByTestId('recipient-row-r1')).toBeNull();

    const content = await render(
      <RecipientsScreen recipients={[RECIPIENT]} state="content" />,
    );
    expect(content.getByText('Fatma Hassan')).toBeTruthy();
  });

  it('recipients error shows copy with retry only when retryable', async () => {
    const onRetry = jest.fn();
    const retryable = await render(
      <RecipientsScreen recipients={[]} state="error" error={networkError()} onRetry={onRetry} />,
    );
    expect(retryable.getByTestId('api-error')).toBeTruthy();
    await fireEvent.press(retryable.getByTestId('api-retry'));
    expect(onRetry).toHaveBeenCalledTimes(1);

    const fatal = await render(
      <RecipientsScreen recipients={[]} state="error" error={aiDown()} onRetry={onRetry} />,
    );
    expect(fatal.getByTestId('api-error')).toBeTruthy();
    expect(fatal.queryByTestId('api-retry')).toBeNull();
    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it('history shows empty state with no notes', async () => {
    const screen = await render(<HistoryScreen notes={[]} recipientIds={[]} state="empty" />);
    expect(screen.getByTestId('api-empty')).toBeTruthy();
  });

  it('note detail shows loading without the note text', async () => {
    const screen = await render(
      <NoteDetailScreen note={ARABIC_NOTE} addenda={[]} state="loading" />,
    );
    expect(screen.getByTestId('api-loading')).toBeTruthy();
    expect(screen.queryByTestId('note-text')).toBeNull();
  });

  it('summary card shows the degraded copy on AI failure', async () => {
    const screen = await render(
      <SummaryCard summary={SUMMARY_WITH_FLAGS} state="error" error={aiDown()} />,
    );
    expect(screen.getByTestId('api-error')).toBeTruthy();
    expect(screen.getByText('AI summary is unavailable right now. Your notes are saved.')).toBeTruthy();
    expect(screen.queryByTestId('api-retry')).toBeNull();
  });

  it('plans screens forward error state without content flash', async () => {
    const list = await render(<PlansScreen plans={[]} state="loading" />);
    expect(list.getByTestId('api-loading')).toBeTruthy();
    expect(list.queryByTestId('plan-row')).toBeNull();

    const detail = await render(
      <PlanDetailScreen plan={PLAN} onAction={() => {}} state="error" error={networkError()} />,
    );
    expect(detail.getByTestId('api-error')).toBeTruthy();
    expect(detail.queryByTestId('plan-action-accept')).toBeNull();
  });
});
