import { Link } from 'react-router-dom';
import { Icon } from '../../components/Icon';
import { useI18n } from '../../i18n/I18nProvider';
import type { OverviewRow } from './useDashboardOverview';

export function RecipientRow({ row, date, onRetry }: { row: OverviewRow; date: string; onRetry: () => void }) {
  const { t } = useI18n();
  const { recipient, note, status } = row;
  const recipientNameId = `overview-recipient-name-${recipient.id}`;
  const openNoteLabelId = `overview-open-note-label-${recipient.id}`;
  const recordNoteLabelId = `overview-record-note-label-${recipient.id}`;
  const retryLabelId = `overview-retry-label-${recipient.id}`;

  return <tr className="overview-table-row">
    <td data-label={t('overviewRecipientColumn')}>
      <div className="overview-person">
        <span className="person-avatar"><Icon name="people" size={20} /></span>
        <div>
          <h3><Link to={`/app/recipients/${recipient.id}`}><bdi id={recipientNameId}>{recipient.name}</bdi></Link></h3>
          <span className="overview-secondary"><bdi>{recipient.relationship || t('recipientRelationshipEmpty')}</bdi></span>
          {!recipient.active && <span className="overview-inactive">{t('recipientInactive')}</span>}
        </div>
      </div>
    </td>
    <td data-label={t('overviewStatusColumn')}>
      <div className="overview-note-status" role="status" aria-live="polite">
        {status === 'recorded' && <span className="overview-status overview-status--recorded">{t('overviewNoteRecorded')}</span>}
        {status === 'missing' && <span className="overview-status">{t('overviewNoNote')}</span>}
        {status === 'error' && <span className="overview-status overview-status--error">{t('overviewNoteStatusUnavailable')}</span>}
      </div>
    </td>
    <td data-label={t('overviewActionColumn')}>
      {status === 'recorded' && note && <Link className="overview-row-action" aria-labelledby={`${recipientNameId} ${openNoteLabelId}`} to={`/app/notes/${note.id}`}>
        <span id={openNoteLabelId}>{t('overviewOpenNote')}</span>
      </Link>}
      {status === 'missing' && recipient.active && <Link className="overview-row-action"
        aria-labelledby={`${recipientNameId} ${recordNoteLabelId}`}
        to={`/app/notes/new?recipientId=${encodeURIComponent(recipient.id)}&date=${encodeURIComponent(date)}`}>
        <span id={recordNoteLabelId}>{t('overviewRecordNote')}</span>
      </Link>}
      {status === 'error' && <button type="button" className="overview-retry" aria-labelledby={`${recipientNameId} ${retryLabelId}`} onClick={onRetry}>
        <span id={retryLabelId}>{t('retry')}</span>
      </button>}
    </td>
  </tr>;
}
