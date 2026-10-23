import { Route, Routes } from 'react-router-dom';
import { CreateNotePage, NotePage, NotesNotFoundPage } from '../features/notes/NotesPages';
import { HistoryPage } from '../features/history/HistoryPage';

export function NotesRoutes() {
  return <Routes>
    <Route index element={<HistoryPage />} />
    <Route path="history" element={<HistoryPage />} />
    <Route path="new" element={<CreateNotePage />} />
    <Route path=":id" element={<NotePage />} />
    <Route path=":id/addenda/new" element={<NotePage append />} />
    <Route path="*" element={<NotesNotFoundPage />} />
  </Routes>;
}
