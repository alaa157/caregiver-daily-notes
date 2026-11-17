import { useCallback, useMemo } from 'react';
import { useNoteResource, useNotesApis } from '../notes/useNotes';
import type { ActivityKind } from '../../api/notes';

export const ACTIVITY_FEED_LIMIT = 10;

export type ActivityItem = {
  id: string;
  noteId: string;
  kind: ActivityKind;
  recipientId: string;
  createdAt: string;
  state: 'loading' | 'ready' | 'error';
};

export type ActivityFeedState =
  | { status: 'loading' }
  | { status: 'error'; error: unknown }
  | { status: 'ready'; items: ActivityItem[] };

export type DashboardActivityItem = {
  id: string;
  noteId: string;
  type: ActivityKind;
  recipientId: string;
  createdAt: string;
  summary: string;
  state: 'loading' | 'ready' | 'error';
};

/** Projects API activity metadata into the localized, presentation-only dashboard contract. */
export function mapDashboardActivityItem(item: ActivityItem, localizedKindLabel: string): DashboardActivityItem {
  return {
    id: item.id,
    noteId: item.noteId,
    type: item.kind,
    recipientId: item.recipientId,
    createdAt: item.createdAt,
    summary: localizedKindLabel,
    state: 'ready',
  };
}

export function useActivityFeed(): {
  state: ActivityFeedState;
  reload: () => void;
  recipientNames: ReadonlyMap<string, string>;
} {
  const api = useNotesApis();
  const load = useCallback(async (signal: AbortSignal): Promise<ActivityItem[]> => {
    const items = await api.notes.recentActivity(ACTIVITY_FEED_LIMIT, signal);
    return items.map((item) => ({ ...item, state: 'ready' }));
  }, [api]);
  const { state, reload } = useNoteResource(load);
  const loadRecipients = useCallback((signal: AbortSignal) => api.recipients.list(signal, 'ALL'), [api]);
  const recipients = useNoteResource(loadRecipients);
  const recipientNames = useMemo(() => {
    const names = new Map<string, string>();
    if (recipients.state.status === 'ready') {
      for (const recipient of recipients.state.data) names.set(recipient.id, recipient.name);
    }
    return names;
  }, [recipients.state]);
  const reloadAll = useCallback(() => {
    reload();
    recipients.reload();
  }, [reload, recipients.reload]);
  return {
    state: state.status === 'ready' ? { status: 'ready', items: state.data } : state,
    reload: reloadAll,
    recipientNames,
  };
}
