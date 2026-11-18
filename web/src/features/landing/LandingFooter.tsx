import type { Language } from '../../i18n/translations';
import { Link } from 'react-router-dom';
import type { LandingCopy } from './landingCopy';

const repo = 'https://github.com/alaa157/caregiver-daily-notes';

/** The prototype's darker footer, with real auth links and new-tab notices. */
export function LandingFooter({ c, language, onLanguageToggle }: {
  c: LandingCopy;
  language: Language;
  onLanguageToggle: () => void;
}) {
  return (
    <footer className="site-footer">
      <div className="container footer-inner">
        <div>
          <div className="footer-brand">
            <svg aria-hidden="true" fill="none" focusable="false" height="26" viewBox="0 0 28 28" width="26">
              <path d="M14 24.2S3.3 17.8 3.3 10.5A5.9 5.9 0 0 1 14 7.1a5.9 5.9 0 0 1 10.7 3.4C24.7 17.8 14 24.2 14 24.2Z" stroke="currentColor" strokeWidth="1.7" />
              <path d="M9.1 14.2c2.2-.3 4.1.2 5.3 2.5 1-2 2.5-3.2 4.8-3.5" stroke="currentColor" strokeLinecap="round" strokeWidth="1.5" />
            </svg>
            <span lang="en">{c.brand}</span>
          </div>
          <div className="footer-copy">{c.footerCopy}</div>
        </div>
        <div className="footer-links">
          <a href={repo} rel="noopener noreferrer" target="_blank">
            {c.repoLink}<span className="visually-hidden">{c.opensNewTab}</span>
          </a>
          <a href={`${repo}/releases`} rel="noopener noreferrer" target="_blank">
            {c.releasesLink}<span className="visually-hidden">{c.opensNewTab}</span>
          </a>
          <Link to="/login">{c.signIn}</Link>
        </div>
        <button
          aria-label={c.switchAria}
          className="language-toggle footer-locale"
          lang={language}
          onClick={onLanguageToggle}
          type="button"
        >
          <span lang={language === 'ar' ? 'en' : 'ar'}>{c.switchLabel}</span>
        </button>
      </div>
    </footer>
  );
}
