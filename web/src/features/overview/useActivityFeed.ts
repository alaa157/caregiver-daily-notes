import { useCallback } from 'react';
import { useNoteResource, useNotesApis } from '../notes/useNotes';
import type { ActivityKind } from '../../api/notes';

export const ACTIVITY_FEED_LIMIT = 10;

export type ActivityItem = {
  id: string;
  kind: ActivityKind;
  recipientId: string;
  createdAt: string;
  state: 'loading' | 'ready' | 'error';
};

export type ActivityFeedState =
  | { status: 'loading' }
  | { status: 'error'; error: unknown }
  | { status: 'ready'; items: ActivityItem[] };

export function useActivityFeed(): { state: ActivityFeedState; reload: () => void } {
  const api = useNotesApis();
  const load = useCallback(async (signal: AbortSignal): Promise<ActivityItem[]> => {
    const items = await api.notes.recentActivity(ACTIVITY_FEED_LIMIT, signal);
    return items.map((item) => ({ ...item, state: 'ready' }));
  }, [api]);
  const { state, reload } = useNoteResource(load);
  return {
    state: state.status === 'ready' ? { status: 'ready', items: state.data } : state,
    reload,
  };
}
