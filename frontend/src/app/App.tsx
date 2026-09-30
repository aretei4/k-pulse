import { RouterProvider } from 'react-router-dom';
import { DemoBanner, ToastProvider } from '@/shared/components';
import { AuthProvider } from '@/shared/hooks/useAuth';
import { router } from './routes';

export function App() {
  return (
    <ToastProvider>
      <AuthProvider>
        <RouterProvider router={router} />
        <DemoBanner />
      </AuthProvider>
    </ToastProvider>
  );
}
