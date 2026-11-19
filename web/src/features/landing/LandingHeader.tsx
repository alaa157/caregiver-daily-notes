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
    <svg fill="none" height="25" viewBox="0 0 96 96" width="25" aria-hidden="true" focusable="false">
      <g stroke="currentColor" strokeLinecap="round" strokeWidth="10">
        <path d="M48 16a32 32 0 0 1 30 21" />
        <path d="M80 48a32 32 0 0 1-21 30" />
        <path d="M48 80a32 32 0 0 1-30-21" />
        <path d="M16 48a32 32 0 0 1 21-30" />
      </g>
      <circle cx="48" cy="48" r="6.4" fill="#C77F68" stroke="none" />
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
          <span className="brand-name"><strong lang={language}>{c.brand}</strong><span>{c.brandTag}</span></span>
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
