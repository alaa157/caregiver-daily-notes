import type { Language } from './translations';

export interface PluralForms {
  zero?: string;
  one: string;
  two: string;
  few: string;
  many: string;
  other: string;
}

/** CLDR plural-category selection: English has one/other; Arabic has zero/one/two/few/many/other. */
export function pluralForm(count: number, language: Language, forms: PluralForms): string {
  if (language !== 'ar') return count === 1 ? forms.one : forms.other;
  if (count === 0) return forms.zero ?? forms.other;
  if (count === 1) return forms.one;
  if (count === 2) return forms.two;
  const mod100 = Math.abs(count) % 100;
  if (mod100 >= 3 && mod100 <= 10) return forms.few;
  return count >= 100 || count % 1 !== 0 ? forms.other : forms.many;
}
