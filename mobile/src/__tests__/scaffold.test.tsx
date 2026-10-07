import {render, screen} from '@testing-library/react-native';
import App from '../../App';

describe('scaffold', () => {
  it('renders the app root', async () => {
    const screen = await render(<App />);
    expect(screen.getByTestId('app-root')).toBeTruthy();
  });
});
