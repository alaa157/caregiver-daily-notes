import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { Bootstrap } from './app/Bootstrap';
import '@fontsource-variable/dm-sans';
import '@fontsource-variable/noto-sans-arabic';
import '../../docs/design-system/tokens.css';
import './styles/base.css';
import './styles/ui.css';
import './styles/public.css';

const root = document.getElementById('root');
if (!root) throw new Error('Missing root element');
createRoot(root).render(<StrictMode><Bootstrap /></StrictMode>);
