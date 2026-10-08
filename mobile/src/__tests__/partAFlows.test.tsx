import {fireEvent, render} from '@testing-library/react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import React from 'react';
import App from '../../App';
import {createPartAClient} from '../app/partAClient';

jest.mock('@react-native-async-storage/async-storage', () => require('../test-utils/inMemoryStorage').mock);

async function signIn(screen: Awaited<ReturnType<typeof render>>): Promise<void> {
  await fireEvent.changeText(screen.getByTestId('login-email'), 'demo@example.com');
  await fireEvent.changeText(screen.getByTestId('login-password'), 'password123');
  await fireEvent.press(screen.getByTestId('login-submit'));
  await screen.findByTestId('recipients-list');
}

describe('Part A backend-less flows', () => {
  beforeEach(async () => {
    await AsyncStorage.clear();
    // Preview demo build defaults App to FetchTransport; tests opt back
    // into the scripted Part A client. Must be set before App renders.
    globalThis.__USE_PART_A_CLIENT__ = true;
  });

  afterEach(() => {
    globalThis.__USE_PART_A_CLIENT__ = undefined;
  });

  it('signs in without touching the network', async () => {
    const fetchMock = jest.fn(async () => {
      throw new Error('Part A must not use fetch');
    });
    globalThis.fetch = fetchMock as unknown as typeof fetch;

    const screen = await render(<App />);
    await signIn(screen);

    expect(fetchMock).not.toHaveBeenCalled();
    expect(screen.getByText('Fatma Hassan')).toBeTruthy();
    await screen.unmount();
  });

  it('exposes a FakeTransport-backed client (no FetchTransport in the Part A path)', async () => {
    const client = createPartAClient();
    expect(client.transport.constructor.name).toBe('FakeTransport');
  });

  it('runs the recipient flow: list, add, detail, back', async () => {
    const screen = await render(<App />);
    await signIn(screen);

    await fireEvent.press(screen.getByTestId('recipients-add-toggle'));
    await fireEvent.changeText(screen.getByTestId('recipient-name'), 'Sara Adel');
    await fireEvent.press(screen.getByTestId('recipient-submit'));
    expect(await screen.findByText('Sara Adel')).toBeTruthy();

    await fireEvent.press(screen.getByTestId('recipient-row-r1'));
    expect(await screen.findByTestId('recipient-detail-name')).toBeTruthy();

    await fireEvent.press(screen.getByTestId('recipient-detail-back'));
    expect(await screen.findByTestId('recipient-row-r1')).toBeTruthy();
    await screen.unmount();
  });

  it('runs the note flow: editor submit, detail, addendum, summary with banner', async () => {
    const screen = await render(<App />);
    await signIn(screen);

    await fireEvent.press(screen.getByTestId('recipient-row-r1'));
    await fireEvent.press(await screen.findByTestId('recipient-detail-write-note'));

    await fireEvent.changeText(screen.getByTestId('note-mood'), 'good');
    await fireEvent.changeText(screen.getByTestId('note-pain'), '3');
    await fireEvent.changeText(screen.getByTestId('note-text'), 'Evening check, all calm.');
    await fireEvent.press(screen.getByTestId('note-submit'));

    expect(await screen.findByTestId('note-detail')).toBeTruthy();
    expect(screen.getByTestId('note-text')).toBeTruthy();
    expect(screen.getByTestId('addendum-original')).toBeTruthy();

    await fireEvent.changeText(screen.getByTestId('addendum-text'), 'Late update: slept well.');
    await fireEvent.press(screen.getByTestId('addendum-submit'));
    expect(await screen.findByText('Late update: slept well.')).toBeTruthy();

    await fireEvent.press(screen.getByTestId('note-detail-view-summary'));
    expect(await screen.findByTestId('safety-banner')).toBeTruthy();
    expect(screen.queryByTestId('safety-banner-dismiss')).toBeNull();
    await screen.unmount();
  });

  it('runs the history flow with filters', async () => {
    const screen = await render(<App />);
    await signIn(screen);

    await fireEvent.press(screen.getAllByText('History')[0]);
    expect(await screen.findByTestId('history-screen')).toBeTruthy();
    expect(screen.getAllByTestId('history-note').length).toBe(3);

    await fireEvent.changeText(screen.getByTestId('history-recipient-filter'), 'r2');
    expect(screen.getAllByTestId('history-note').length).toBe(1);
    await screen.unmount();
  });

  it('runs the plans flow: list, detail, action', async () => {
    const screen = await render(<App />);
    await signIn(screen);

    await fireEvent.press(screen.getAllByText('Plans')[0]);
    expect(await screen.findByTestId('plans-screen')).toBeTruthy();

    await fireEvent.press(screen.getByTestId('plan-press-p1'));
    expect(await screen.findByTestId('plan-detail')).toBeTruthy();
    await fireEvent.press(screen.getByTestId('plan-action-accept'));
    expect(await screen.findByTestId('plan-detail')).toBeTruthy();
    await screen.unmount();
  });
});
