import type { RefObject } from 'react';
import { Link } from 'react-router-dom';
import type { Language } from '../../i18n/translations';
import type { LandingCopy } from './landingCopy';

interface LandingHeaderProps {
  c: LandingCopy;
  language: Language;
  menuOpen: boolean;
  setMenuOpen: (open: boolean) => void;
  closeMenu: () => void;
  menuButtonRef: RefObject<HTMLButtonElement>;
  menuRef: RefObject<HTMLElement>;
  onLanguageToggle: () => void;
}

function BrandMark() {
  return (
    <svg fill="none" height="25" viewBox="0 0 28 28" width="25" aria-hidden="true" focusable="false">
      <path d="M14 24.2S3.3 17.8 3.3 10.5A5.9 5.9 0 0 1 14 7.1a5.9 5.9 0 0 1 10.7 3.4C24.7 17.8 14 24.2 14 24.2Z" stroke="currentColor" strokeLinejoin="round" strokeWidth="1.7" />
      <path d="M9.1 14.2c2.2-.3 4.1.2 5.3 2.5 1-2 2.5-3.2 4.8-3.5M14.3 16.5c-.7-3.3-.4-5.5 1.7-7.3" stroke="currentColor" strokeLinecap="round" strokeWidth="1.5" />
    </svg>
  );
}

/** Ported from the prototype's sticky header, with the app's real routes and i18n. */
export function LandingHeader({
  c,
  language,
  menuOpen,
  setMenuOpen,
  closeMenu,
  menuButtonRef,
  menuRef,
  onLanguageToggle,
}: LandingHeaderProps) {
  return (
    <header className="site-header">
      <div className="container header-inner">
        <a aria-label={c.brandAria} className="brand" href="#top" onClick={closeMenu}>
          <span aria-hidden="true" className="brand-mark"><BrandMark /></span>
          <span className="brand-name"><strong lang="en">{c.brand}</strong><span>{c.brandTag}</span></span>
        </a>
        <nav aria-label={c.navAria} className={`main-nav${menuOpen ? ' is-open' : ''}`} id="main-nav" ref={menuRef}>
          <a href="#how" onClick={closeMenu}>{c.navHow}</a>
          <a href="#daily-notes" onClick={closeMenu}>{c.navNotes}</a>
          <a href="#safety" onClick={closeMenu}>{c.navSafety}</a>
          <a href="#languages" onClick={closeMenu}>{c.navLanguages}</a>
        </nav>
        <div className="header-actions">
          <button
            aria-label={c.switchAria}
            className="language-toggle"
            lang={language}
            onClick={onLanguageToggle}
            type="button"
          >
            <span lang={language === 'ar' ? 'en' : 'ar'}>{c.switchLabel}</span>
          </button>
          <Link className="button button-primary" to="/login">{c.signIn}</Link>
        </div>
        <button
          aria-controls="main-nav"
          aria-expanded={menuOpen}
          aria-label={menuOpen ? c.menuClose : c.menuOpen}
          className="menu-toggle"
          onClick={() => setMenuOpen(!menuOpen)}
          ref={menuButtonRef}
          type="button"
        >
          <svg aria-hidden="true" fill="none" focusable="false" stroke="currentColor" strokeLinecap="round" strokeWidth="1.8" viewBox="0 0 24 24">
            <path d="M4 7h16M4 12h16M4 17h16" />
          </svg>
        </button>
      </div>
    </header>
  );
}
