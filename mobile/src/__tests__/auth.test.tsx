import {fireEvent, render} from '@testing-library/react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import React from 'react';
import {ApiClient} from '../lib/ApiClient';
import {FakeTransport} from '../lib/FakeTransport';
import {TokenStore} from '../lib/TokenStore';
import GatedRoot from '../app/GatedRoot';
import RootNavigator from '../app/RootNavigator';
import {AuthProvider, useAuth} from '../features/auth/AuthContext';
import {setLanguage} from '../i18n/i18n';

jest.mock('@react-native-async-storage/async-storage', () => require('../test-utils/inMemoryStorage').mock);

function harness(routes: Record<string, {status: number; body: unknown} | Error>, startToken?: string) {
  const client = new ApiClient(new FakeTransport(routes));
  return {
    client,
    ui: (
      <AuthProvider client={client}>
        <GatedRoot />
      </AuthProvider>
    ),
    async seed(startToken?: string) {
      if (startToken) {
        await TokenStore.set(startToken);
      }
    },
  };
}

describe('auth', () => {
  beforeEach(async () => {
    await AsyncStorage.clear();
    await setLanguage('en');
  });

  it('signIn stores the token and shows tabs', async () => {
    const h = harness({'POST /api/auth/login': {status: 200, body: {token: 'tok123'}}});
    const screen = await render(h.ui);

    await fireEvent.changeText(screen.getByTestId('login-email'), 'a@b.c');
    await fireEvent.changeText(screen.getByTestId('login-password'), 'password123');
    await fireEvent.press(screen.getByTestId('login-submit'));

    expect(await screen.findByTestId('notes-home')).toBeTruthy();
    expect(await TokenStore.get()).toBe('tok123');
  });

  it('401 shows an inline error and stays on login', async () => {
    const h = harness({'POST /api/auth/login': {status: 401, body: {message: 'nope'}}});
    const screen = await render(h.ui);

    await fireEvent.changeText(screen.getByTestId('login-email'), 'a@b.c');
    await fireEvent.changeText(screen.getByTestId('login-password'), 'password123');
    await fireEvent.press(screen.getByTestId('login-submit'));

    expect(await screen.findByTestId('login-error')).toBeTruthy();
    expect(screen.queryByTestId('notes-home')).toBeNull();
    expect(await TokenStore.get()).toBeNull();
  });

  it('validates blank email in both languages', async () => {
    const h = harness({});
    const screen = await render(h.ui);

    await fireEvent.press(screen.getByTestId('login-submit'));
    expect(await screen.findByText('Email is required.')).toBeTruthy();

    await setLanguage('ar');
    const arScreen = await render(h.ui);
    await fireEvent.press(arScreen.getByTestId('login-submit'));
    expect(await arScreen.findByText('البريد الإلكتروني مطلوب.')).toBeTruthy();
    await setLanguage('en');
  });

  it('signOut clears the token and shows login', async () => {
    const h = harness({});
    await h.seed('tok123');
    const screen = await render(h.ui);

    expect(await screen.findByTestId('notes-home')).toBeTruthy();
    const moreTabs = screen.getAllByText('More');
    await fireEvent.press(moreTabs[moreTabs.length - 1]);
    await fireEvent.press(await screen.findByTestId('more-signout-button'));

    expect(await screen.findByTestId('login-email')).toBeTruthy();
    expect(await TokenStore.get()).toBeNull();
  });

  it('expired token on an authed call redirects to login', async () => {
    const h = harness({'GET /api/protected': {status: 401, body: {}}});
    await h.seed('tok123');
    function Probe() {
      const auth = useAuth();
      const {Button} = require('react-native') as typeof import('react-native');
      return (
        <Button
          testID="probe-fetch"
          title="fetch"
          onPress={() => {
            auth.authedFetch(client => client.get('/api/protected')).catch(() => {});
          }}
        />
      );
    }
    const screen = await render(
      <AuthProvider client={h.client}>
        <GatedRoot />
        <Probe />
      </AuthProvider>,
    );
    expect(await screen.findByTestId('notes-home')).toBeTruthy();
    await fireEvent.press(screen.getByTestId('probe-fetch'));

    expect(await screen.findByTestId('login-email')).toBeTruthy();
    expect(await TokenStore.get()).toBeNull();
  });

  it('RootNavigator still honors the signedIn prop', async () => {
    const fake = new ApiClient(new FakeTransport({}));
    const out = await render(
      <AuthProvider client={fake}>
        <RootNavigator signedIn={false} />
      </AuthProvider>,
    );
    expect(out.getByTestId('login-email')).toBeTruthy();
    const inn = await render(
      <AuthProvider client={fake}>
        <RootNavigator signedIn={true} />
      </AuthProvider>,
    );
    expect(inn.getByTestId('notes-home')).toBeTruthy();
  });
});
