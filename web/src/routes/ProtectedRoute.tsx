import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuthStatus } from '../features/auth/AuthProvider';
import { LoadingState } from '../components/Feedback';

export function ProtectedRoute() {
  const status = useAuthStatus();
  const location = useLocation();
  if (status === 'checking') return <LoadingState />;
  if (status !== 'authenticated') {
    return <Navigate to="/login" state={{ from: location.pathname + location.search + location.hash }} replace />;
  }
  return <Outlet />;
}
