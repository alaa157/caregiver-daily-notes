import {render} from '@testing-library/react-native';
import RootNavigator from '../app/RootNavigator';

describe('navigation', () => {
  it('shows the login placeholder when signed out', async () => {
    const screen = await render(<RootNavigator signedIn={false} />);
    expect(screen.getByTestId('login-placeholder')).toBeTruthy();
  });

  it('shows all four tabs when signed in', async () => {
    const screen = await render(<RootNavigator signedIn={true} />);
    // Header title + tab label both render the route name; either proves presence.
    for (const label of ['Notes', 'History', 'Plans', 'More']) {
      expect(screen.getAllByText(label).length).toBeGreaterThan(0);
    }
  });
});
