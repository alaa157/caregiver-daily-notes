import { ApiError, type ApiClient } from '../lib/api';
import { isNoteDate } from './notes';
import { isRecipientId } from './recipients';
import { readSummarySafety, summaryPeriods, type SummarySafety } from './summary';

/** Provider-free read of the same deterministic safety the summary path shows. */
export function createSafetySignalsApi(client: ApiClient) {
  return {
    async evaluate(recipientId: string,
      period: { periodEndInclusive: string; periodDays: 7 | 14 | 30 },
      signal?: AbortSignal): Promise<SummarySafety> {
      if (!isRecipientId(recipientId) || !isNoteDate(period?.periodEndInclusive)
        || !summaryPeriods.includes(period?.periodDays)) throw new ApiError(400, null);
      const query = new URLSearchParams({
        periodEndInclusive: period.periodEndInclusive,
        periodDays: String(period.periodDays),
      });
      const path: `/api/recipients/${string}/safety-signals?${string}` =
        `/api/recipients/${encodeURIComponent(recipientId)}/safety-signals?${query}`;
      return readSummarySafety(await client.request<unknown>({ path, signal }));
    },
  };
}
