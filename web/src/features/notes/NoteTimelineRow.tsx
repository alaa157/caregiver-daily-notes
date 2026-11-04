import { useId, useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import { Button } from '../../components/Button';
import { useI18n } from '../../i18n/I18nProvider';

export interface NoteTimelineRowProps {
  entryLabel: ReactNode;
  dateLabel: string;
  dateTime?: string;
  body: ReactNode;
  metadata?: ReactNode;
  provenance?: ReactNode;
  actions?: ReactNode;
}

export function NoteTimelineRow({
  entryLabel,
  dateLabel,
  dateTime,
  body,
  metadata,
  provenance,
  actions,
}: NoteTimelineRowProps) {
  const { t } = useI18n();
  const headingId = useId();
  const bodyRef = useRef<HTMLDivElement>(null);
  const [isLong, setIsLong] = useState(false);
  const [expanded, setExpanded] = useState(false);

  useLayoutEffect(() => {
    const element = bodyRef.current;
    if (!element) return;

    const measure = () => {
      const style = window.getComputedStyle(element);
      const lineHeight = Number.parseFloat(style.lineHeight);
      const fontSize = Number.parseFloat(style.fontSize) || 16;
      const maxHeight = (Number.isFinite(lineHeight) ? lineHeight : fontSize * 1.5) * 4;
      setIsLong(element.scrollHeight > maxHeight + 1);
    };

    measure();
    window.addEventListener('resize', measure);
    const observer = typeof ResizeObserver === 'undefined' ? undefined : new ResizeObserver(measure);
    observer?.observe(element);
    if (element.parentElement) observer?.observe(element.parentElement);
    return () => {
      window.removeEventListener('resize', measure);
      observer?.disconnect();
    };
  }, [body]);

  return (
    <article className="note-timeline-row" aria-labelledby={headingId}>
      <header className="note-timeline-row__header">
        <div className="note-timeline-row__identity">
          <h3 id={headingId} className="note-timeline-row__label">{entryLabel}</h3>
          {metadata && <div className="note-timeline-row__metadata">{metadata}</div>}
          <time className="note-timeline-row__date" dateTime={dateTime}>{dateLabel}</time>
          {provenance && <div className="note-timeline-row__provenance">{provenance}</div>}
        </div>
        {actions && <div className="note-timeline-row__actions">{actions}</div>}
      </header>
      <div
        ref={bodyRef}
        id={`${headingId}-body`}
        className={`note-timeline-row__body${isLong && !expanded ? ' note-timeline-row__body--collapsed' : ''}`}
        data-testid="note-timeline-body"
        data-collapsed={isLong && !expanded}
      >
        {body}
      </div>
      {isLong && (
        <Button
          variant="text"
          className="note-timeline-row__toggle"
          aria-expanded={expanded}
          aria-controls={`${headingId}-body`}
          onClick={() => setExpanded((value) => !value)}
        >
          {t(expanded ? 'notesTimelineShowLess' : 'notesTimelineShowMore')}
        </Button>
      )}
    </article>
  );
}
