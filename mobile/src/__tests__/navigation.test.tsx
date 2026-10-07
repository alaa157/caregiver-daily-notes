import {fireEvent, render} from '@testing-library/react-native';
import React from 'react';
import RootNavigator from '../app/RootNavigator';
import {AuthProvider} from '../features/auth/AuthContext';
import {ApiClient} from '../lib/ApiClient';
import {FakeTransport} from '../lib/FakeTransport';

jest.mock('@react-native-async-storage/async-storage', () => require('../test-utils/inMemoryStorage').mock);

function withAuth(ui: React.JSX.Element): React.JSX.Element {
  return <AuthProvider client={new ApiClient(new FakeTransport({}))}>{ui}</AuthProvider>;
}

describe('navigation', () => {
  it('shows the login screen when signed out', async () => {
    const screen = await render(withAuth(<RootNavigator signedIn={false} />));
    expect(screen.getByTestId('login-email')).toBeTruthy();
  });

  it('shows all four tabs when signed in', async () => {
    const screen = await render(withAuth(<RootNavigator signedIn={true} />));
    // Header title + tab label both render the route name; either proves presence.
    for (const label of ['Notes', 'History', 'Plans', 'More']) {
      expect(screen.getAllByText(label).length).toBeGreaterThan(0);
    }
  });

  it('reaches the server-URL screen from the More tab', async () => {
    const screen = await render(withAuth(<RootNavigator signedIn={true} />));
    // The lazy More screen is unmounted, so its tab label is the only 'More' text.
    const moreTabs = screen.getAllByText('More');
    expect(moreTabs).toHaveLength(1);
    await fireEvent.press(moreTabs[0]);
    expect(await screen.findByTestId('more-settings-button')).toBeTruthy();
    await fireEvent.press(screen.getByTestId('more-settings-button'));

    expect(await screen.findByTestId('server-url-input')).toBeTruthy();
  });
});
