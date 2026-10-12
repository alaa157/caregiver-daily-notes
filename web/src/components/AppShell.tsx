import { Link, NavLink } from 'react-router-dom';
import { useI18n } from '../i18n/I18nProvider';
import type { ReactNode } from 'react';

export function AppShell({ children }: { children: ReactNode }) {
  const { t, language, setLanguage } = useI18n();
  return <div className="site-shell">
    <a className="skip-link" href="#main-content">{language === 'ar' ? 'انتقل إلى المحتوى' : 'Skip to content'}</a>
    <header className="site-header">
      <div className="header-inner container">
        <Link className="brand" to="/" aria-label={t('brand')}>
          <span className="brand-mark" aria-hidden="true">✳</span>
          <span>{t('brand')}</span>
        </Link>
        <nav className="site-nav" aria-label={language === 'ar' ? 'القائمة الرئيسية' : 'Main navigation'}>
          <NavLink end to="/" className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}>{t('home')}</NavLink>
          <NavLink to="/login" className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}>{t('signIn')}</NavLink>
          <button type="button" className="language-toggle" aria-label={language === 'ar' ? 'Switch to English' : 'التغيير إلى العربية'} onClick={() => setLanguage(language === 'en' ? 'ar' : 'en')}>{t('switchLanguage')}</button>
        </nav>
      </div>
    </header>
    <main id="main-content" className="main-content">{children}</main>
    <footer className="site-footer"><div className="container footer-inner"><span>{t('footer')}</span><span>{t('note')}</span></div></footer>
  </div>;
}
