export const CARE_DATA_CHANGED_EVENT = 'care-data-changed';

export function notifyCareDataChanged(): void {
  window.dispatchEvent(new Event(CARE_DATA_CHANGED_EVENT));
}
