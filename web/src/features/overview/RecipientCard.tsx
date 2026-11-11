import { Link } from 'react-router-dom';
import { Icon } from '../../components/Icon';
import type { OverviewRow } from './useDashboardOverview';
import { useI18n } from '../../i18n/I18nProvider';

export type RecipientCardRow = OverviewRow;

export function RecipientCard({ row, date, onRetry }: { row: RecipientCardRow; date: string; onRetry: () => void }) {
  const { t } = useI18n();
  const { recipient, note, status } = row;

  return <li className="overview-card">
    <div className="overview-person">
      <span className="person-avatar"><Icon name="people" size={20} /></span>
      <h3><Link to={`/app/recipients/${recipient.id}`}><bdi>{recipient.name}</bdi></Link></h3>
      <span className="overview-secondary"><bdi>{recipient.relationship || t('recipientRelationshipEmpty')}</bdi></span>
    </div>
    <div className="overview-note-status" aria-live="polite">
      {status === 'recorded' && <><span className="overview-status overview-status--recorded">{t('overviewNoteRecorded')}</span>
        {note && <Link to={`/app/notes/${note.id}`}>{t('overviewOpenNote')}</Link>}</>}
      {status === 'missing' && <><span className="overview-status">{t('overviewNoNote')}</span>
        {recipient.active && <Link to={`/app/notes/new?recipientId=${encodeURIComponent(recipient.id)}&date=${encodeURIComponent(date)}`}>{t('overviewRecordNote')}</Link>}</>}
      {status === 'error' && <><span className="overview-status overview-status--error">{t('overviewNoteStatusUnavailable')}</span>
        <button type="button" className="overview-retry" onClick={onRetry}>{t('retry')}</button></>}
    </div>
    {!recipient.active && <span className="overview-inactive">{t('recipientInactive')}</span>}
  </li>;
}
