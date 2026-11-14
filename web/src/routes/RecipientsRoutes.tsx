import { NavLink, Outlet, Route, Routes, useParams } from 'react-router-dom';
import { isRecipientId } from '../api/recipients';
import { useI18n } from '../i18n/I18nProvider';
import { HistoryPage } from '../features/history/HistoryPage';
import { PlansPage } from '../features/plans/PlansPage';
import { CreateRecipientPage, RecipientPage, RecipientsListPage, RecipientsNotFoundPage } from '../features/recipients/RecipientsPages';
import { SummaryPage } from '../features/summary/SummaryPage';

function RecipientTabsLayout() {
  const { id = '' } = useParams();
  const { t } = useI18n();
  if (!isRecipientId(id)) return <Outlet />;
  const base = `/app/recipients/${encodeURIComponent(id)}`;
  const tabs = [
    { path: '', label: 'recipientOverviewTab' },
    { path: '/notes', label: 'recipientNotesTab' },
    { path: '/summaries', label: 'recipientSummariesTab' },
    { path: '/plans', label: 'recipientPlansTab' },
  ] as const;
  return <>
    <nav className="recipient-tab-nav container" aria-label={t('recipientWorkspaceTabs')}>
      {tabs.map(({ path, label }) => <NavLink key={label} end to={`${base}${path}`}
        className={({ isActive }) => `recipient-tab${isActive ? ' recipient-tab--active' : ''}`}>
        {t(label)}
      </NavLink>)}
    </nav>
    <Outlet />
  </>;
}

function RecipientHistoryTab() {
  const { id = '' } = useParams();
  return <HistoryPage scopedRecipientId={id} />;
}

function RecipientSummaryTab() {
  const { id = '' } = useParams();
  return <SummaryPage scopedRecipientId={id} />;
}

function RecipientPlansTab() {
  const { id = '' } = useParams();
  return <PlansPage scopedRecipientId={id} />;
}

export function RecipientsRoutes() {
  return <Routes>
    <Route index element={<RecipientsListPage />} />
    <Route path="new" element={<CreateRecipientPage />} />
    <Route path=":id" element={<RecipientTabsLayout />}>
      <Route index element={<RecipientPage />} />
      <Route path="notes" element={<RecipientHistoryTab />} />
      <Route path="summaries" element={<RecipientSummaryTab />} />
      <Route path="plans" element={<RecipientPlansTab />} />
    </Route>
    <Route path=":id/edit" element={<RecipientPage edit />} />
    <Route path="*" element={<RecipientsNotFoundPage />} />
  </Routes>;
}
