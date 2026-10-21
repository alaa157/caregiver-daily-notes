import { useI18n } from '../i18n/I18nProvider';
import { Icon } from './Icon';

/** An illustrated blank journal, not a preview of patient data or backend features. */
export function CareIllustration() {
  const { t } = useI18n();
  return (
    <div className="care-illustration" aria-hidden="true">
      <div className="illustration-halo" />
      <svg className="botanical" viewBox="0 0 460 460" fill="none" focusable="false">
        <path d="M348 371c-28-85-7-172 42-255" stroke="#688672" strokeWidth="2" />
        <path d="M359 293c-49-8-71-44-63-68 38 2 68 25 63 68Z" fill="#a3b8a0" />
        <path d="M354 240c36-4 66-31 59-63-35 4-63 22-59 63Z" fill="#7e9b80" />
        <path d="M373 180c-36-8-57-36-49-61 30 8 49 27 49 61Z" fill="#b3c4ad" />
        <path d="M384 145c32-3 50-26 46-52-28 3-47 20-46 52Z" fill="#66866f" />
        <path d="M111 376c4-50-12-99-46-131" stroke="#839a81" strokeWidth="2" />
        <path d="M107 334c-34 0-60-21-58-47 31-2 59 21 58 47Z" fill="#a6b89c" />
        <path d="M95 297c26-12 34-38 21-56-25 13-35 31-21 56Z" fill="#7d9678" />
        <path d="M77 271c-26 1-46-16-44-36 22-1 43 15 44 36Z" fill="#c1cbb2" />
        <ellipse cx="238" cy="393" rx="156" ry="14" fill="#d5decb" opacity=".6" />
      </svg>
      <div className="journal-paper journal-paper--back" />
      <div className="journal-paper journal-paper--front">
        <div className="journal-binding" />
        <div className="journal-heading"><Icon name="leaf" size={22} /><span>{t('illustrationLabel')}</span></div>
        <div className="journal-divider" />
        <div className="journal-rules"><span /><span /><span /><span /></div>
        <div className="journal-stamp"><Icon name="leaf" size={36} /></div>
        <span className="journal-caption">{t('illustrationCaption')}</span>
      </div>
      <div className="illustration-language"><Icon name="globe" size={18} /><span lang="ar" dir="rtl">العربية</span><span className="language-divider" /><span lang="en" dir="ltr">English</span></div>
      <div className="illustration-caption"><span className="caption-line" />{t('illustrationFootnote')}</div>
    </div>
  );
}
