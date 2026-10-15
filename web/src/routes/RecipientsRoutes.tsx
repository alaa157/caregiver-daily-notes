import { Route, Routes } from 'react-router-dom';
import { CreateRecipientPage, RecipientPage, RecipientsListPage, RecipientsNotFoundPage } from '../features/recipients/RecipientsPages';

export function RecipientsRoutes() {
  return <Routes>
    <Route index element={<RecipientsListPage />} />
    <Route path="new" element={<CreateRecipientPage />} />
    <Route path=":id" element={<RecipientPage />} />
    <Route path=":id/edit" element={<RecipientPage edit />} />
    <Route path="*" element={<RecipientsNotFoundPage />} />
  </Routes>;
}
