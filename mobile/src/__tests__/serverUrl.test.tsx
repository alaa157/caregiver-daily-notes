import {fireEvent, render} from '@testing-library/react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import {setLanguage} from '../i18n/i18n';
import ServerUrlScreen from '../features/auth/ServerUrlScreen';
import {InvalidServerUrlError, ServerUrlStore} from '../lib/ServerUrlStore';

jest.mock('@react-native-async-storage/async-storage', () => require('../test-utils/inMemoryStorage').mock);

describe('server URL', () => {
  beforeEach(async () => {
    await AsyncStorage.clear();
    await setLanguage('en');
  });

  it('persists a valid URL across store instances', async () => {
    await ServerUrlStore.set('https://new.tunnel');

    expect(await ServerUrlStore.get()).toBe('https://new.tunnel');
  });

  it('rejects blank and malformed URLs with a typed error', async () => {
    await expect(ServerUrlStore.set('')).rejects.toBeInstanceOf(InvalidServerUrlError);
    await expect(ServerUrlStore.set('not-a-url')).rejects.toBeInstanceOf(
      InvalidServerUrlError,
    );
    await expect(ServerUrlStore.set('ftp://host/x')).rejects.toBeInstanceOf(
      InvalidServerUrlError,
    );
  });

  it('falls back to the default when nothing is stored', async () => {
    expect(await ServerUrlStore.get()).toBe('https://CHANGE-ME');
  });

  it('screen saves a typed URL and shows confirmation', async () => {
    const screen = await render(<ServerUrlScreen />);

    await fireEvent.changeText(screen.getByTestId('server-url-input'), 'https://demo.tunnel');
    await fireEvent.press(screen.getByTestId('server-url-save'));

    expect(await screen.findByTestId('server-url-saved')).toBeTruthy();
    expect(await ServerUrlStore.get()).toBe('https://demo.tunnel');
  });

  it('screen shows a localized error for bad input', async () => {
    const screen = await render(<ServerUrlScreen />);

    await fireEvent.changeText(screen.getByTestId('server-url-input'), 'bogus');
    await fireEvent.press(screen.getByTestId('server-url-save'));

    expect(await screen.findByTestId('server-url-error')).toBeTruthy();
  });

  it('confirmation copy follows the active language', async () => {
    await setLanguage('en');
    const enScreen = await render(<ServerUrlScreen />);
    await fireEvent.changeText(enScreen.getByTestId('server-url-input'), 'https://en.tunnel');
    await fireEvent.press(enScreen.getByTestId('server-url-save'));
    expect(await enScreen.findByText('Server URL saved.')).toBeTruthy();

    await setLanguage('ar');
    const arScreen = await render(<ServerUrlScreen />);
    await fireEvent.changeText(arScreen.getByTestId('server-url-input'), 'https://ar.tunnel');
    await fireEvent.press(arScreen.getByTestId('server-url-save'));
    expect(await arScreen.findByText('تم حفظ رابط الخادم.')).toBeTruthy();

    await setLanguage('en');
  });

  it('reset restores the default URL with confirmation', async () => {
    await ServerUrlStore.set('https://custom.tunnel');
    const screen = await render(<ServerUrlScreen />);

    await fireEvent.press(screen.getByTestId('server-url-reset'));

    expect(await screen.findByTestId('server-url-reset-done')).toBeTruthy();
    expect(await ServerUrlStore.get()).toBe('https://CHANGE-ME');
  });
});
