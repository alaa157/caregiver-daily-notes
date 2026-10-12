import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { messages, type Language, type MessageKey } from './translations';

const STORAGE_KEY = 'caregiver-language';

export function textDirection(language: Language): 'rtl' | 'ltr' {
  return language === 'ar' ? 'rtl' : 'ltr';
}

function storedLanguage(): Language {
  try {
    return localStorage.getItem(STORAGE_KEY) === 'ar' ? 'ar' : 'en';
  } catch {
    return 'en';
  }
}

interface I18nValue {
  language: Language;
  direction: 'rtl' | 'ltr';
  setLanguage: (language: Language) => void;
  t: (key: MessageKey) => string;
}

const I18nContext = createContext<I18nValue | null>(null);

export function I18nProvider({ children }: { children: ReactNode }) {
  const [language, setLanguage] = useState<Language>(storedLanguage);

  useEffect(() => {
    document.documentElement.lang = language;
    document.documentElement.dir = textDirection(language);
    try { localStorage.setItem(STORAGE_KEY, language); } catch { /* Storage can be blocked. */ }
  }, [language]);

  const value: I18nValue = {
    language,
    direction: textDirection(language),
    setLanguage,
    t: (key) => messages[language][key],
  };
  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18n(): I18nValue {
  const context = useContext(I18nContext);
  if (!context) throw new Error('useI18n must be called within I18nProvider.');
  return context;
}
