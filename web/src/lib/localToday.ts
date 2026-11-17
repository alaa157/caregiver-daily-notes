import { useEffect, useState } from 'react';

/** The current local calendar date as YYYY-MM-DD. */
export function localToday(): string {
  const now = new Date();
  return [now.getFullYear(), String(now.getMonth() + 1).padStart(2, '0'),
    String(now.getDate()).padStart(2, '0')].join('-');
}

/**
 * The local calendar date, refreshed when the day actually changes.
 *
 * One timeout is scheduled for the next local midnight and reschedules itself
 * after firing. Nothing runs on ordinary renders, and the timer is cleared on
 * unmount so a late tick cannot update an unmounted component.
 */
export function useLocalToday(): string {
  const [today, setToday] = useState(localToday);

  useEffect(() => {
    let timer: ReturnType<typeof setTimeout> | undefined;
    const scheduleNextMidnight = () => {
      const now = new Date();
      const nextMidnight = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1);
      timer = setTimeout(() => {
        setToday(localToday());
        scheduleNextMidnight();
      }, nextMidnight.getTime() - now.getTime());
    };
    scheduleNextMidnight();
    return () => {
      if (timer !== undefined) clearTimeout(timer);
    };
  }, []);

  return today;
}
