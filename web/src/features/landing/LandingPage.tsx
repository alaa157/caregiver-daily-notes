import { useCallback, useEffect, useRef, useState } from 'react';
import { useI18n } from '../../i18n/I18nProvider';
import '@fontsource/dm-serif-display/400.css';
import '@fontsource/dm-serif-display/400-italic.css';
import { LandingFooter } from './LandingFooter';
import { LandingHeader } from './LandingHeader';
import { LandingHero } from './LandingHero';
import { LandingSections } from './LandingSections';
import { landingCopy } from './landingCopy';
import './landing.css';

/**
 * The route component. Kept under this name so `routes/LandingRoute.tsx`
 * can re-export it and `app/App.tsx` needs no change.
 */
export function LandingRoute() {
  const { language, direction, setLanguage, t } = useI18n();
  const c = landingCopy[language];
  const [menuOpen, setMenuOpen] = useState(false);
  const menuButtonRef = useRef<HTMLButtonElement>(null);
  const menuRef = useRef<HTMLElement>(null);

  const closeMenu = useCallback(() => {
    setMenuOpen(false);
    // Defer: in-page anchor activation moves focus to <body> after the click
    // handler runs, so focus the trigger on the next task instead.
    setTimeout(() => menuButtonRef.current?.focus());
  }, []);
  const toggleLanguage = useCallback(() => {
    setLanguage(language === 'ar' ? 'en' : 'ar');
  }, [language, setLanguage]);

  useEffect(() => {
    document.title = `${t('home')} · ${t('brand')}`;
  }, [t]);

  useEffect(() => {
    if (!menuOpen) return;
    menuRef.current?.querySelector<HTMLElement>('a, button')?.focus();
  }, [menuOpen]);

  useEffect(() => {
    if (!menuOpen) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') closeMenu();
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [menuOpen, closeMenu]);

  return (
    <div className="landing-page" dir={direction} lang={language}>
      <a
        className="skip-link"
        href="#main-content"
        onClick={(event) => {
          event.preventDefault();
          document.getElementById('main-content')?.focus();
        }}
      >
        {c.skip}
      </a>
      <LandingHeader
        c={c}
        closeMenu={closeMenu}
        language={language}
        menuButtonRef={menuButtonRef}
        menuOpen={menuOpen}
        menuRef={menuRef}
        onLanguageToggle={toggleLanguage}
        setMenuOpen={setMenuOpen}
      />
      <main className="landing-main" id="main-content" tabIndex={-1}>
        <LandingHero c={c} />
        <LandingSections c={c} />
      </main>
      <LandingFooter c={c} language={language} onLanguageToggle={toggleLanguage} />
    </div>
  );
}
