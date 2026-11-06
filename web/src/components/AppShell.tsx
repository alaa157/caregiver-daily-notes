import { useEffect, useRef, type ReactNode } from 'react';
import { Link, NavLink, useLocation } from 'react-router-dom';
import { useI18n } from '../i18n/I18nProvider';
import { Icon } from './Icon';
import { useAuth } from '../features/auth/AuthProvider';
import { useTheme, type ThemeMode } from '../app/ThemeProvider';
import { ByokProvider } from '../features/ai/ByokContext';
import './AppShell.css';

function isHistoryRoute(pathname: string) {
  if (pathname === '/app/notes' || pathname === '/app/notes/history') return true;
  const noteRoute = pathname.match(/^\/app\/notes\/([^/]+)(?:\/addenda\/new)?$/);
  return Boolean(noteRoute && !['new', 'summary', 'plans', 'history'].includes(noteRoute[1]));
}

export function AppShell({ children }: { children: ReactNode }) {
  const { t, language, setLanguage } = useI18n();
  const auth = useAuth();
  const { mode, setMode } = useTheme();
  const { pathname } = useLocation();
  const mainRef = useRef<HTMLElement>(null);
  const previousPath = useRef(pathname);

  useEffect(() => {
    const title = pathname === '/' ? t('home') : pathname === '/login' ? t('loginTitle')
      : pathname === '/app' ? t('dashboardTitle')
      : pathname === '/app/recipients/new' ? t('recipientCreate')
      : pathname === '/app/notes/new' ? t('notesCreate')
      : pathname === '/app/notes/summary' ? t('summaryTitle')
      : pathname === '/app/notes/plans' ? t('plansTitle')
      : pathname === '/app/notes' || pathname === '/app/notes/history' ? t('historyTitle')
      : pathname.startsWith('/app/notes/') ? t(pathname.endsWith('/addenda/new') ? 'notesAppend' : 'notesDetails')
      : pathname.startsWith('/app/recipients/') ? t(pathname.endsWith('/edit') ? 'recipientEdit' : 'recipientDetails')
      : pathname === '/app/recipients' ? t('recipientsTitle') : t('pageNotFound');
    document.title = `${title} · ${t('brand')}`;
  }, [pathname, t]);

  useEffect(() => {
    if (previousPath.current !== pathname) {
      mainRef.current?.focus();
      window.scrollTo({ top: 0, behavior: 'instant' });
      previousPath.current = pathname;
    }
  }, [pathname]);

  const navClass = ({ isActive }: { isActive: boolean }) => `nav-link${isActive ? ' active' : ''}`;
  const nextLanguage = language === 'en' ? 'ar' : 'en';

  return (
    <div className="site-shell">
      <a className="skip-link" href="#main-content" onClick={() => mainRef.current?.focus()}>{t('skipToContent')}</a>
      <header className={`site-header${auth.status === 'authenticated' ? ' site-header--workspace' : ''}`}>
        <div className="header-inner container">
          <Link className="brand" to={auth.status === 'authenticated' ? '/app' : '/'}>
            <span className="brand-mark"><Icon name="leaf" size={25} /></span>
            <span className="brand-text"><span>{t('brand')}</span><span className="brand-tagline">{t('brandTagline')}</span></span>
          </Link>
          <div className="nav-utilities">
            <div className="appearance-control">
              <Icon name={mode === 'system' ? 'monitor' : mode === 'dark' ? 'moon' : 'sun'} size={17} />
              <select aria-label={t('appearance')} value={mode} onChange={(event) => setMode(event.target.value as ThemeMode)}>
                <option value="system">{t('themeSystem')}</option>
                <option value="light">{t('themeLight')}</option>
                <option value="dark">{t('themeDark')}</option>
              </select>
            </div>
            <button type="button" className="language-toggle" lang={nextLanguage} dir={nextLanguage === 'ar' ? 'rtl' : 'ltr'}
            aria-label={t(language === 'ar' ? 'switchToEnglish' : 'switchToArabic')}
            onClick={() => setLanguage(nextLanguage)}>
            <Icon name="globe" size={17} />{t('switchLanguage')}
            </button>
            {auth.status === 'authenticated' && <button type="button" className="nav-link auth-logout" onClick={auth.logout}><Icon name="logout" size={18} /><span>{t('authSignOut')}</span></button>}
          </div>
          <nav className={'site-nav' + (auth.status === 'authenticated' ? ' site-nav--authenticated' : '')} aria-label={t('mainNavigation')}>
            <div className="nav-links">
              {auth.status === 'authenticated' ? <>
                <div className="workspace-primary-links" role="group" aria-label={t('workspacePrimaryNavigation')}>
                  <NavLink end to="/app" className={navClass}><Icon name="home" size={19} />{t('home')}</NavLink>
                  <NavLink to="/app/recipients" className={navClass}><Icon name="people" size={19} />{t('workspacePeople')}</NavLink>
                  <NavLink end to="/app/notes/new" className={navClass}><Icon name="edit" size={19} />{t('workspaceNotes')}</NavLink>
                  <Link to="/app/notes" aria-current={isHistoryRoute(pathname) ? 'page' : undefined}
                    className={navClass({ isActive: isHistoryRoute(pathname) })}>
                    <Icon name="history" size={19} />{t('workspaceHistory')}
                  </Link>
                </div>
                <div className="workspace-secondary-links" role="group" aria-label={t('workspaceNotesNavigation')}>
                  <NavLink to="/app/notes/plans" className={({ isActive }) => `${navClass({ isActive })} workspace-secondary-link`}><Icon name="shield" size={18} />{t('plansTitle')}</NavLink>
                </div>
              </> : <>
                <NavLink end to="/" className={navClass}>{t('home')}</NavLink>
                <NavLink to="/login" className={navClass}>{t('signIn')}</NavLink>
              </>}
            </div>
          </nav>
        </div>
      </header>
      <main id="main-content" ref={mainRef} tabIndex={-1} className="main-content">
        {auth.status === 'authenticated' ? <ByokProvider>{children}</ByokProvider> : children}
      </main>
      <footer className="site-footer">
        <div className="container footer-inner">
          <span className="footer-brand"><Icon name="leaf" size={18} />{t('footer')}</span>
          <span className="footer-note">{t('note')}</span>
        </div>
      </footer>
    </div>
  );
}
