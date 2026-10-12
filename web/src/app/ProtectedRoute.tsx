import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuthStatus } from './AuthContext';
import { LoadingState } from '../components/Feedback';

export function ProtectedRoute() {
  const status = useAuthStatus();
  const location = useLocation();
  if (status === 'checking') return <LoadingState />;
  if (status !== 'authenticated') {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  }
  return <Outlet />;
}
