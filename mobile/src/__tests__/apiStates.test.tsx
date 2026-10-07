import {fireEvent, render} from '@testing-library/react-native';
import React from 'react';
import {Text} from 'react-native';
import ApiStateView from '../components/ApiStateView';
import {ApiError} from '../lib/errors';

jest.mock('@react-native-async-storage/async-storage', () => require('../test-utils/inMemoryStorage').mock);

function err(code: 'NETWORK' | 'SERVER' | 'UNAUTHORIZED' | 'AI_UNAVAILABLE' | 'FORBIDDEN' | 'VALIDATION' | 'UNKNOWN') {
  return new ApiError(code, `${code} message`);
}

describe('API states', () => {
  it('loading shows the indicator without content', async () => {
    const screen = await render(
      <ApiStateView state="loading">
        <Text>content</Text>
      </ApiStateView>,
    );

    expect(screen.getByTestId('api-loading')).toBeTruthy();
    expect(screen.queryByText('content')).toBeNull();
  });

  it('renders content without state chrome', async () => {
    const screen = await render(
      <ApiStateView state="content">
        <Text>content</Text>
      </ApiStateView>,
    );

    expect(screen.getByText('content')).toBeTruthy();
    expect(screen.queryByTestId('api-loading')).toBeNull();
    expect(screen.queryByTestId('api-error')).toBeNull();
  });

  it('maps each error code to the right copy', async () => {
    const cases: Array<{
      code: 'NETWORK' | 'SERVER' | 'UNAUTHORIZED' | 'AI_UNAVAILABLE';
      copy: string;
    }> = [
      {code: 'NETWORK', copy: 'No connection. Check your network and retry.'},
      {code: 'SERVER', copy: 'Something went wrong. Please retry.'},
      {code: 'UNAUTHORIZED', copy: 'Session expired. Please log in again.'},
      {code: 'AI_UNAVAILABLE', copy: 'AI summary is unavailable right now. Your notes are saved.'},
    ];
    for (const {code, copy} of cases) {
      const screen = await render(<ApiStateView state="error" error={err(code)} />);
      expect(screen.getByTestId('api-error')).toBeTruthy();
      expect(screen.getByText(copy)).toBeTruthy();
      await screen.unmount();
    }
  });

  it('passes through messages for codes without dedicated copy', async () => {
    const screen = await render(
      <ApiStateView state="error" error={new ApiError('FORBIDDEN', 'Custom forbidden')} />,
    );

    expect(screen.getByText('Custom forbidden')).toBeTruthy();
  });

  it('offers retry only when retryable, and calls back on press', async () => {
    const onRetry = jest.fn();
    const retryable = await render(
      <ApiStateView state="error" error={err('NETWORK')} onRetry={onRetry} />,
    );
    expect(retryable.getByTestId('api-retry')).toBeTruthy();
    await fireEvent.press(retryable.getByTestId('api-retry'));
    expect(onRetry).toHaveBeenCalledTimes(1);

    const degraded = await render(
      <ApiStateView state="error" error={err('AI_UNAVAILABLE')} onRetry={onRetry} />,
    );
    expect(degraded.queryByTestId('api-retry')).toBeNull();

    const expired = await render(
      <ApiStateView state="error" error={err('UNAUTHORIZED')} onRetry={onRetry} />,
    );
    expect(expired.queryByTestId('api-retry')).toBeNull();
  });

  it('empty renders the empty copy', async () => {
    const screen = await render(<ApiStateView state="empty" />);

    expect(screen.getByTestId('api-empty')).toBeTruthy();
  });
});
