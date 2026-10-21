import { Navigate, Route, Routes } from 'react-router-dom';
import { CreateNotePage, NotePage, NotesNotFoundPage } from '../features/notes/NotesPages';

export function NotesRoutes() {
  return <Routes>
    <Route index element={<Navigate to="new" replace />} />
    <Route path="new" element={<CreateNotePage />} />
    <Route path=":id" element={<NotePage />} />
    <Route path=":id/addenda/new" element={<NotePage append />} />
    <Route path="*" element={<NotesNotFoundPage />} />
  </Routes>;
}
