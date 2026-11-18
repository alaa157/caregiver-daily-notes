import { useId, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { useI18n } from '../../i18n/I18nProvider';
import type { Language } from '../../i18n/translations';
import caregiverPhoto from './auth-caregiver.webp';
import notebookPhoto from './auth-notebook.webp';
import './auth.css';

/** The prototype's brand mark, kept as an inline vector rather than a bitmap. */
function BrandMark() {
  return (
    <svg aria-hidden="true" fill="none" focusable="false" height="25" viewBox="0 0 28 28" width="25">
      <path d="M14 24.2S3.3 17.8 3.3 10.5A5.9 5.9 0 0 1 14 7.1a5.9 5.9 0 0 1 10.7 3.4C24.7 17.8 14 24.2 14 24.2Z" stroke="currentColor" strokeLinejoin="round" strokeWidth="1.7" />
      <path d="M9.1 14.2c2.2-.3 4.1.2 5.3 2.5 1-2 2.5-3.2 4.8-3.5M14.3 16.5c-.7-3.3-.4-5.5 1.7-7.3" stroke="currentColor" strokeLinecap="round" strokeWidth="1.5" />
    </svg>
  );
}

/**
 * The prototype's two-panel auth screen, ported as presentation only.
 * The real form, validation and auth calls live in LoginPage / SignupPage.
 */
export function AuthScreen({ language, variant, title, notices, children }: {
  language: Language;
  variant: 'login' | 'register';
  title: string;
  notices?: ReactNode;
  children: ReactNode;
}) {
  const { t, setLanguage } = useI18n();
  const titleId = useId();
  const isLogin = variant === 'login';
  const nextLanguage = language === 'en' ? 'ar' : 'en';
  return (
    <div className="auth-screen">
      <section aria-label={t(isLogin ? 'authVisualLogin' : 'authVisualRegister')} className={`auth-visual auth-visual--${variant}`}>
        <img alt={t(isLogin ? 'authPhotoAltLogin' : 'authPhotoAltRegister')} className="auth-photo"
          decoding="async" loading="eager" src={isLogin ? caregiverPhoto : notebookPhoto} />
        <div className="auth-visual-shade" />
        <div className="auth-visual-copy">
          <span className="auth-photo-kicker">{t('authVisualKicker')}</span>
          <p className="auth-photo-title">{t(isLogin ? 'authVisualLogin' : 'authVisualRegister')}</p>
          <span className="auth-photo-caption">{t('authVisualCaption')}</span>
        </div>
      </section>
      <section aria-labelledby={titleId} className="auth-main">
        <div className="auth-topline">
          <Link className="auth-back" to="/">{t('returnHome')}</Link>
          <button aria-label={t(language === 'ar' ? 'switchToEnglish' : 'switchToArabic')}
            className="language-toggle auth-language-toggle" dir={nextLanguage === 'ar' ? 'rtl' : 'ltr'}
            lang={nextLanguage} onClick={() => setLanguage(nextLanguage)} type="button">
            {t('switchLanguage')}
          </button>
        </div>
        <div className="auth-form-wrap">
          <Link className="auth-brand" to="/">
            <span aria-hidden="true" className="brand-mark"><BrandMark /></span>
            <span className="brand-name"><strong>{t('brand')}</strong><span>{t('brandTagline')}</span></span>
          </Link>
          <div className="auth-form-heading">
            <p className="auth-eyebrow">{t(isLogin ? 'authLoginEyebrow' : 'authRegisterEyebrow')}</p>
            <h1 id={titleId}>{title}</h1>
            <p>{t(isLogin ? 'authLoginBody' : 'authRegisterBody')}</p>
          </div>
          {notices}
          {children}
          <p className="auth-switch">
            <span>{t(isLogin ? 'loginNoAccount' : 'signupHaveAccount')}</span>{' '}
            <Link to={isLogin ? '/signup' : '/login'}>{t(isLogin ? 'signupCta' : 'signupSignIn')}</Link>
          </p>
          <p className="auth-footnote">{t('authFootnote')}</p>
        </div>
      </section>
    </div>
  );
}
