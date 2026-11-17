import type { Language } from '../i18n/translations';

// Western digits in both languages: they match what caregivers type and read on
// medication packaging, and keep formatted values consistent with static strings.
const intlLocale = (language: Language) => (language === 'ar' ? 'ar-EG-u-nu-latn' : 'en');

export function formatNumber(value: number, language: Language, options?: Intl.NumberFormatOptions): string {
  return new Intl.NumberFormat(intlLocale(language), options).format(value);
}

export function formatDate(value: Date | string | number, language: Language, options?: Intl.DateTimeFormatOptions): string {
  return new Intl.DateTimeFormat(intlLocale(language), options).format(new Date(value));
}
