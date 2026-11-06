import { useEffect, useId, useMemo, useRef, useState } from 'react';
import { Button } from '../../components/Button';
import { useTheme, type ThemeMode } from '../../app/ThemeProvider';
import { useI18n } from '../../i18n/I18nProvider';
import type { MessageKey } from '../../i18n/translations';
import tokensStyles from '../../../../docs/design-system/tokens.css?inline';
import baseStyles from '../../styles/base.css?inline';

const themeModes: ReadonlyArray<{ mode: ThemeMode; label: MessageKey }> = [
  { mode: 'system', label: 'themeSystem' },
  { mode: 'light', label: 'themeLight' },
  { mode: 'dark', label: 'themeDark' },
];

const htmlEntities: Record<string, string> = {
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#39;',
};

function escapeHtml(value: string) {
  return value.replace(/[&<>"']/g, (character) => htmlEntities[character]);
}

export function ThemeColorsPreview() {
  const { mode, setMode } = useTheme();
  const { direction, language, t } = useI18n();
  const [previewMode, setPreviewMode] = useState<ThemeMode>(mode);
  const previousAppliedMode = useRef(mode);
  const groupId = useId();

  useEffect(() => {
    const previousMode = previousAppliedMode.current;
    previousAppliedMode.current = mode;

    // Follow external changes only when the preview still matched the previous applied mode.
    if (previewMode === previousMode) setPreviewMode(mode);
  }, [mode, previewMode]);

  const previewDocument = useMemo(() => {
    const heading = escapeHtml(t('settingsThemePreviewHeading'));
    const description = escapeHtml(t('settingsThemePreviewDescription'));
    const cardHeading = escapeHtml(t('settingsThemePreviewCardHeading'));
    const cardDescription = escapeHtml(t('settingsThemePreviewCardDescription'));
    const action = escapeHtml(t('settingsThemePreviewAction'));

    return `<!doctype html>
<html lang="${language}" dir="${direction}" data-theme="${previewMode}">
  <head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <style>${tokensStyles}
${baseStyles}</style>
  </head>
  <body class="theme-preview-frame-document">
    <main class="theme-preview-frame-content">
      <h1 class="theme-preview-frame-heading">${heading}</h1>
      <p class="theme-preview-frame-description">${description}</p>
      <section class="theme-preview-frame-card">
        <h2 class="theme-preview-frame-card-heading">${cardHeading}</h2>
        <p class="theme-preview-frame-card-description">${cardDescription}</p>
      </section>
      <button class="theme-preview-frame-button theme-preview-frame-button--focus-example" type="button">${action}</button>
    </main>
  </body>
</html>`;
  }, [direction, language, previewMode, t]);

  return (
    <section className="theme-colors-preview" aria-label={t('settingsThemeColors')}>
      <fieldset className="theme-colors-preview__fieldset">
        <legend>{t('settingsThemePreviewMode')}</legend>
        <div className="theme-colors-preview__choices">
          {themeModes.map(({ mode: option, label }) => {
            const id = `${groupId}-${option}`;
            return (
              <label className="theme-colors-preview__choice" htmlFor={id} key={option}>
                <input
                  checked={previewMode === option}
                  id={id}
                  name={groupId}
                  onChange={() => setPreviewMode(option)}
                  type="radio"
                  value={option}
                />
                <span>{t(label)}</span>
              </label>
            );
          })}
        </div>
      </fieldset>

      <iframe
        className="theme-colors-preview__frame"
        sandbox=""
        srcDoc={previewDocument}
        title={t('settingsThemePreviewHeading')}
      />

      <div className="theme-colors-preview__actions">
        <Button
          className="theme-colors-preview__apply"
          disabled={previewMode === mode}
          onClick={() => setMode(previewMode)}
        >
          {t('settingsThemePreviewApply')}
        </Button>
        <Button
          className="theme-colors-preview__cancel"
          onClick={() => setPreviewMode(mode)}
          variant="secondary"
        >
          {t('settingsThemePreviewCancel')}
        </Button>
      </div>
    </section>
  );
}
