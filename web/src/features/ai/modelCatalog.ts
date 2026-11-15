import type { MessageKey } from '../../i18n/translations';
import type { ByokProviderId } from './ByokContext';

export const MODEL_CATALOG_REVIEWED = '2026-11-14';
export const MODEL_ID_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$/;

export interface ModelOption {
  id: string;
  label: string;
  badge: MessageKey;
  note?: MessageKey;
}

export const MODEL_CATALOG: Record<ByokProviderId, ModelOption[]> = {
  GEMINI: [
    { id: 'gemini-3.8-flash', label: 'Gemini 3.8 Flash', badge: 'byokBadgeFreeTier', note: 'byokNoteFast' },
    { id: 'gemini-3.5-flash-lite', label: 'Gemini 3.5 Flash-Lite', badge: 'byokBadgeFreeTier', note: 'byokNoteLowestCost' },
    { id: 'gemini-4-hydrogen', label: 'Gemini 4 Hydrogen', badge: 'byokBadgeFreeTier', note: 'byokNoteNewest' },
    { id: 'gemini-4-argon', label: 'Gemini 4 Argon', badge: 'byokBadgePaid', note: 'byokNoteMostCapable' },
  ],
  OPENAI: [
    { id: 'gpt-6-luna', label: 'GPT-6 Luna', badge: 'byokBadgePaid', note: 'byokNoteLowestCost' },
    { id: 'gpt-5.4-mini', label: 'GPT-5.4 mini', badge: 'byokBadgePaid', note: 'byokNoteFast' },
  ],
  XAI: [
    { id: 'grok-4.7', label: 'Grok 4.7', badge: 'byokBadgePaid', note: 'byokNoteNewest' },
    { id: 'grok-4.3', label: 'Grok 4.3', badge: 'byokBadgePaid' },
  ],
  OPEN_ROUTER: [
    { id: 'nvidia/nemotron-3-ultra-550b-a55b:free', label: 'NVIDIA Nemotron 3 Ultra', badge: 'byokBadgeFree', note: 'byokNoteLargestFree' },
    { id: 'nvidia/nemotron-3.5-lightning:free', label: 'NVIDIA Nemotron 3.5 Lightning', badge: 'byokBadgeFree', note: 'byokNoteFast' },
    { id: 'baidu/ernie-5.5-flash-preview:free', label: 'Baidu ERNIE 5.5 Flash (preview)', badge: 'byokBadgeFree' },
    { id: 'inclusionai/ling-3.2-flash:free', label: 'inclusionAI Ling 3.2 Flash', badge: 'byokBadgeFree' },
  ],
};
