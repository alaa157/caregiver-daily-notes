import {NativeModules} from 'react-native';
import i18n, {getLanguage, isRTL, loadSavedLanguage, setLanguage, t} from '../i18n/i18n';

jest.mock('@react-native-async-storage/async-storage', () => {
  const store = new Map<string, string>();
  return {
    __esModule: true,
    default: {
      setItem: jest.fn(async (k: string, v: string) => {
        store.set(k, v);
      }),
      getItem: jest.fn(async (k: string) => store.get(k) ?? null),
      removeItem: jest.fn(async (k: string) => {
        store.delete(k);
      }),
      clear: jest.fn(async () => {
        store.clear();
      }),
    },
  };
});

const forceRTL = (NativeModules.I18nManager as unknown as {forceRTL: jest.Mock}).forceRTL;

describe('i18n', () => {
  beforeEach(async () => {
    forceRTL.mockClear();
    await setLanguage('en');
    forceRTL.mockClear();
  });

  it('switches to Arabic with RTL sync', async () => {
    await setLanguage('ar');

    expect(forceRTL).toHaveBeenCalledWith(true);
    expect(t('tabs.notes')).toBe('الملاحظات');
    expect(getLanguage()).toBe('ar');
    expect(isRTL()).toBe(true);
  });

  it('switches back to English with LTR sync', async () => {
    await setLanguage('ar');
    forceRTL.mockClear();

    await setLanguage('en');

    expect(forceRTL).toHaveBeenCalledWith(false);
    expect(t('tabs.notes')).toBe('Notes');
    expect(getLanguage()).toBe('en');
    expect(isRTL()).toBe(false);
  });

  it('persists the choice across restarts', async () => {
    await setLanguage('ar');
    // Simulate a restart: memory says English, storage says Arabic.
    await i18n.changeLanguage('en');
    expect(getLanguage()).toBe('en');

    await loadSavedLanguage();

    expect(getLanguage()).toBe('ar');
    expect(isRTL()).toBe(true);
  });

  it('passes mixed Arabic/English text through untouched', () => {
    expect(t('test.mixed', {defaultValue: 'ملاحظة Note 123'})).toBe('ملاحظة Note 123');
  });
});
