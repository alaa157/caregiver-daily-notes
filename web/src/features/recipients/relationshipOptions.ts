import type { MessageKey } from '../../i18n/translations';

export const relationshipCodes = [
  'MOTHER',
  'FATHER',
  'SPOUSE',
  'GRANDMOTHER',
  'GRANDFATHER',
  'CHILD',
  'SIBLING',
  'RELATIVE',
  'FRIEND',
  'CLIENT',
  'OTHER',
] as const;

export type RelationshipCode = typeof relationshipCodes[number];

export const relationshipOptions: ReadonlyArray<{ code: RelationshipCode; labelKey: MessageKey }> = [
  { code: 'MOTHER', labelKey: 'relationshipMOTHER' },
  { code: 'FATHER', labelKey: 'relationshipFATHER' },
  { code: 'SPOUSE', labelKey: 'relationshipSPOUSE' },
  { code: 'GRANDMOTHER', labelKey: 'relationshipGRANDMOTHER' },
  { code: 'GRANDFATHER', labelKey: 'relationshipGRANDFATHER' },
  { code: 'CHILD', labelKey: 'relationshipCHILD' },
  { code: 'SIBLING', labelKey: 'relationshipSIBLING' },
  { code: 'RELATIVE', labelKey: 'relationshipRELATIVE' },
  { code: 'FRIEND', labelKey: 'relationshipFRIEND' },
  { code: 'CLIENT', labelKey: 'relationshipCLIENT' },
  { code: 'OTHER', labelKey: 'relationshipOTHER' },
];

export function relationshipLabel(value: string | null | undefined, t: (key: MessageKey) => string): string {
  const stored = value ?? '';
  const option = relationshipOptions.find((candidate) => candidate.code === stored.trim());
  return option ? t(option.labelKey) : stored;
}
