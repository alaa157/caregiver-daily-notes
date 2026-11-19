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
            <svg aria-hidden="true" fill="none" focusable="false" height="26" viewBox="0 0 96 96" width="26">
              <g stroke="currentColor" strokeLinecap="round" strokeWidth="10">
                <path d="M48 16a32 32 0 0 1 30 21" />
                <path d="M80 48a32 32 0 0 1-21 30" />
                <path d="M48 80a32 32 0 0 1-30-21" />
                <path d="M16 48a32 32 0 0 1 21-30" />
              </g>
              <circle cx="48" cy="48" r="6.4" fill="#C77F68" stroke="none" />
            </svg>
            <span lang={language}>{c.brand}</span>
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
