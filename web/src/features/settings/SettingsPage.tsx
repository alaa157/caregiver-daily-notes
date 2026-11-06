import { useState } from 'react';
import { Icon } from '../../components/Icon';
import { useI18n } from '../../i18n/I18nProvider';
import type { MessageKey } from '../../i18n/translations';
import { AiSettingsDialog } from '../ai/AiSettingsDialog';
import './settings.css';

type SettingsRow = { label: MessageKey; icon: 'sun' | 'lock' | 'inbox' | 'info' };

const placeholderGroups: { title: MessageKey; rows: SettingsRow[] }[] = [
  { title: 'settingsPrivacyGroup', rows: [{ label: 'settingsPrivacy', icon: 'lock' }] },
  { title: 'settingsStorageGroup', rows: [
    { label: 'settingsStorage', icon: 'inbox' },
    { label: 'settingsBackupRestore', icon: 'inbox' },
  ] },
  { title: 'settingsSystemGroup', rows: [
    { label: 'settingsSupportedLinks', icon: 'info' },
    { label: 'settingsUpdater', icon: 'info' },
    { label: 'settingsChangeLog', icon: 'info' },
    { label: 'settingsAbout', icon: 'info' },
    { label: 'settingsVersionCheck', icon: 'info' },
  ] },
];

export function SettingsPage() {
  const { t } = useI18n();
  const [aiSettingsOpen, setAiSettingsOpen] = useState(false);

  return (
    <section className="settings-page container" aria-labelledby="settings-title">
      <header className="settings-heading">
        <h1 id="settings-title">{t('settingsTitle')}</h1>
        <p>{t('settingsDescription')}</p>
      </header>

      <section className="settings-group" aria-labelledby="settings-interface-title">
        <h2 id="settings-interface-title">{t('settingsInterfaceGroup')}</h2>
        <ul className="settings-list">
          <li className="settings-row" data-settings-theme-preview-entry>
            <span className="settings-row-icon"><Icon name="sun" size={20} /></span>
            <span className="settings-row-label">{t('settingsThemeColors')}</span>
            <span className="settings-unavailable">{t('settingsNotAvailable')}</span>
          </li>
          <li className="settings-row">
            <span className="settings-row-icon"><Icon name="info" size={20} /></span>
            <button className="settings-action" type="button" aria-haspopup="dialog" onClick={() => setAiSettingsOpen(true)}>
              {t('settingsAiSettings')}
            </button>
          </li>
        </ul>
      </section>

      {placeholderGroups.map((group) => (
        <section className="settings-group" key={group.title} aria-labelledby={`settings-${group.title}`}>
          <h2 id={`settings-${group.title}`}>{t(group.title)}</h2>
          <ul className="settings-list">
            {group.rows.map((row) => (
              <li className="settings-row" key={row.label}>
                <span className="settings-row-icon"><Icon name={row.icon} size={20} /></span>
                <span className="settings-row-label">{t(row.label)}</span>
                <span className="settings-unavailable">{t('settingsNotAvailable')}</span>
              </li>
            ))}
          </ul>
        </section>
      ))}

      <AiSettingsDialog open={aiSettingsOpen} onClose={() => setAiSettingsOpen(false)} />
    </section>
  );
}
