import type { MessageKey } from '../../i18n/translations';
import type { ByokProviderId } from './ByokContext';

const providerLabels: Record<ByokProviderId, MessageKey> = {
  OPENAI: 'byokProviderOpenAi',
  GEMINI: 'byokProviderGemini',
  XAI: 'byokProviderXai',
  OPEN_ROUTER: 'byokProviderOpenRouter',
};

export const providers: readonly { id: ByokProviderId; label: MessageKey }[] =
  (Object.keys(providerLabels) as ByokProviderId[]).map((id) => ({ id, label: providerLabels[id] }));

export function providerLabelKey(id: ByokProviderId): MessageKey {
  return providerLabels[id];
}
