import { formatNumber } from '../lib/format';
import { pluralForm } from './plural';
import { summaryDayForms } from './summaryTranslations';
import type { Language } from './translations';

/** Localized "N day(s)" label; the count selects the correct plural form per language. */
export function dayCountLabel(count: number, language: Language): string {
  return `${formatNumber(count, language)} ${pluralForm(count, language, summaryDayForms[language])}`;
}
