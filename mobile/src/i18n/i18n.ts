import AsyncStorage from '@react-native-async-storage/async-storage';
import i18n from 'i18next';
import {initReactI18next} from 'react-i18next';
import {I18nManager} from 'react-native';
import ar from './locales/ar.json';
import en from './locales/en.json';

export type AppLanguage = 'ar' | 'en';

const LANGUAGE_KEY = 'app.language';

void i18n.use(initReactI18next).init({
  resources: {
    en: {translation: en},
    ar: {translation: ar},
  },
  lng: 'en',
  fallbackLng: 'en',
  interpolation: {escapeValue: false},
});

export function getLanguage(): AppLanguage {
  return i18n.language && i18n.language.startsWith('ar') ? 'ar' : 'en';
}

export function isRTL(): boolean {
  return getLanguage() === 'ar';
}

/** 'rtl' when Arabic, else 'ltr' — for direction-aware styles. */
export function dir(): 'rtl' | 'ltr' {
  return isRTL() ? 'rtl' : 'ltr';
}

export function t(key: string, options?: Record<string, unknown>): string {
  return i18n.t(key, options) as string;
}

export async function setLanguage(lang: AppLanguage): Promise<void> {
  await i18n.changeLanguage(lang);
  I18nManager.allowRTL(true);
  I18nManager.forceRTL(lang === 'ar');
  await AsyncStorage.setItem(LANGUAGE_KEY, lang);
}

export async function loadSavedLanguage(): Promise<void> {
  const saved = await AsyncStorage.getItem(LANGUAGE_KEY);
  if (saved === 'ar' || saved === 'en') {
    await setLanguage(saved);
  }
}

export default i18n;
