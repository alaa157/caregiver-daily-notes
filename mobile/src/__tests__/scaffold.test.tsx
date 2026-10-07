import {render} from '@testing-library/react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import App from '../../App';

jest.mock('@react-native-async-storage/async-storage', () => require('../test-utils/inMemoryStorage').mock);

describe('scaffold', () => {
  beforeEach(async () => {
    await AsyncStorage.clear();
  });

  it('renders the app root', async () => {
    const tree = await render(<App />);
    expect(tree.getByTestId('app-root')).toBeTruthy();
  });

  it('applies the saved language before first content', async () => {
    await AsyncStorage.setItem('app.language', 'ar');
    const tree = await render(<App />);

    expect(await tree.findByText('تسجيل الدخول')).toBeTruthy();
  });
});
