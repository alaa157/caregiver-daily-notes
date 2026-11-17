import { useEffect, useMemo, useState } from 'react';
import { createMedicationsApi, INTAKE_PAGE_SIZE } from '../../api/medications';
import type { DailyNote } from '../../api/notes';
import { useApiClient } from '../../lib/api';

type NoteStatus = 'TAKEN' | 'MISSED';

function recordedStatus(note: DailyNote): NoteStatus | null {
  const observation = note.observations.medicationStatus;
  if (observation?.state === 'RECORDED' && (observation.value === 'TAKEN' || observation.value === 'MISSED')) {
    return observation.value;
  }
  return null;
}

/** Match the note's local calendar day to an intake event's local day. */
function localDay(instant: string): string {
  const value = new Date(instant);
  if (Number.isNaN(value.getTime())) return '';
  return [value.getFullYear(), String(value.getMonth() + 1).padStart(2, '0'),
    String(value.getDate()).padStart(2, '0')].join('-');
}

function disagrees(status: NoteStatus, intakeState: string): boolean {
  return (status === 'TAKEN' && intakeState === 'MISSED')
    || (status === 'MISSED' && intakeState === 'TAKEN');
}

/**
 * Informational only: the note's coarse, per-day medication status and the
 * per-medication intake events are separate caregiver reports with no backend
 * reconciliation. This never changes either record — it only reports a same-day
 * contradiction so the caregiver can review both. A failure to load intake data
 * is deliberately silent and must not make the note unreadable.
 */
export function useMedicationIntakeConflict(note: DailyNote | null): boolean {
  const client = useApiClient();
  const medications = useMemo(() => createMedicationsApi(client), [client]);
  const [conflict, setConflict] = useState(false);
  const status = note ? recordedStatus(note) : null;
  const recipientId = note?.recipientId;
  const date = note?.date;
  const noteId = note?.id;

  useEffect(() => {
    setConflict(false);
    if (!status || !recipientId || !date) return;
    const controller = new AbortController();
    void (async () => {
      try {
        const records = await medications.list(recipientId, controller.signal);
        for (const record of records) {
          let page = 0;
          let events;
          do {
            if (controller.signal.aborted) return;
            events = await medications.listIntakeEvents(recipientId, record.id, controller.signal, page);
            if (events.some((event) => localDay(event.occurredAt) === date && disagrees(status, event.intakeState))) {
              if (!controller.signal.aborted) setConflict(true);
              return;
            }
            page += 1;
          } while (events.length === INTAKE_PAGE_SIZE);
        }
      } catch {
        // Best-effort warning; the note page stays fully usable without it.
      }
    })();
    return () => controller.abort();
  }, [medications, status, recipientId, date, noteId]);

  return conflict;
}
