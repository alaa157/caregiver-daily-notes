import { useEffect, useRef, type ReactNode } from 'react';
import { Link, NavLink, useLocation } from 'react-router-dom';
import { useI18n } from '../i18n/I18nProvider';
import { Icon } from './Icon';
import { useAuth } from '../features/auth/AuthProvider';

export function AppShell({ children }: { children: ReactNode }) {
  const { t, language, setLanguage } = useI18n();
  const auth = useAuth();
  const { pathname } = useLocation();
  const mainRef = useRef<HTMLElement>(null);
  const previousPath = useRef(pathname);

  useEffect(() => {
    const title = pathname === '/' ? t('home') : pathname === '/login' ? t('loginTitle')
      : pathname === '/app' ? t('dashboardTitle') : t('pageNotFound');
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
      <header className="site-header">
        <div className="header-inner container">
          <Link className="brand" to="/">
            <span className="brand-mark"><Icon name="leaf" size={25} /></span>
            <span className="brand-text"><span>{t('brand')}</span><span className="brand-tagline">{t('brandTagline')}</span></span>
          </Link>
          <nav className={'site-nav' + (auth.status === 'authenticated' ? ' site-nav--authenticated' : '')} aria-label={t('mainNavigation')}>
            <div className="nav-links">
              <NavLink end to="/" className={navClass}>{t('home')}</NavLink>
              {auth.status === 'authenticated' ? <>
                <NavLink to="/app" className={navClass}>{t('dashboard')}</NavLink>
                <button type="button" className="nav-link auth-logout" onClick={auth.logout}>{t('authSignOut')}</button>
              </> : <NavLink to="/login" className={navClass}>{t('signIn')}</NavLink>}
            </div>
            <button type="button" className="language-toggle" lang={nextLanguage} dir={nextLanguage === 'ar' ? 'rtl' : 'ltr'}
              aria-label={language === 'ar' ? 'Switch to English' : 'التغيير إلى العربية'}
              onClick={() => setLanguage(nextLanguage)}>
              <Icon name="globe" size={17} />{t('switchLanguage')}
            </button>
          </nav>
        </div>
      </header>
      <main id="main-content" ref={mainRef} tabIndex={-1} className="main-content">{children}</main>
      <footer className="site-footer">
        <div className="container footer-inner">
          <span className="footer-brand"><Icon name="leaf" size={18} />{t('footer')}</span>
          <span className="footer-note">{t('note')}</span>
        </div>
      </footer>
    </div>
  );
}
