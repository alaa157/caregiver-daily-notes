import { useEffect, useMemo, useRef, useState, type KeyboardEvent, type ReactNode } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { isNoteDate } from '../api/notes';
import { createRecipientsApi, isRecipientId, recipientErrorMessage, type Recipient } from '../api/recipients';
import { useI18n } from '../i18n/I18nProvider';
import { useApiClient } from '../lib/api';
import { Icon } from './Icon';
import { useAuth } from '../features/auth/AuthProvider';
import { useTheme, type ThemeMode } from '../app/ThemeProvider';
import { ByokProvider } from '../features/ai/ByokContext';
import './AppShell.css';

type RecipientState =
  | { status: 'idle' | 'loading' }
  | { status: 'error'; error: unknown }
  | { status: 'ready'; data: Recipient[] };

function localToday() {
  const now = new Date();
  return [now.getFullYear(), String(now.getMonth() + 1).padStart(2, '0'), String(now.getDate()).padStart(2, '0')].join('-');
}

function normalizePathname(pathname: string) {
  return pathname.length > 1 ? pathname.replace(/\/+$/, '') : pathname;
}

function isHistoryRoute(pathname: string) {
  pathname = normalizePathname(pathname);
  if (pathname === '/app/notes' || pathname === '/app/notes/history') return true;
  const noteRoute = pathname.match(/^\/app\/notes\/([^/]+)(?:\/addenda\/new)?$/);
  return Boolean(noteRoute && !['new', 'summary', 'plans', 'history'].includes(noteRoute[1]));
}

function isAppRoute(pathname: string) {
  pathname = normalizePathname(pathname);
  return pathname === '/app' || pathname === '/app/settings'
    || pathname === '/app/recipients' || pathname.startsWith('/app/recipients/')
    || pathname === '/app/notes' || pathname.startsWith('/app/notes/');
}

export function AppShell({ children }: { children: ReactNode }) {
  const { t, language, setLanguage } = useI18n();
  const auth = useAuth();
  const { mode, setMode } = useTheme();
  const location = useLocation();
  const pathname = normalizePathname(location.pathname);
  const mainRef = useRef<HTMLElement>(null);
  const previousPath = useRef(pathname);
  const mounted = useRef(false);

  useEffect(() => {
    const title = pathname === '/' ? t('home') : pathname === '/login' ? t('loginTitle')
      : pathname === '/app' ? t('dashboardTitle')
      : pathname === '/app/settings' ? t('settingsTitle')
      : pathname === '/app/recipients/new' ? t('recipientCreate')
      : pathname === '/app/notes/new' ? t('notesCreate')
      : pathname === '/app/notes/summary' ? t('summaryTitle')
      : pathname === '/app/notes/plans' ? t('plansTitle')
      : pathname === '/app/notes' || pathname === '/app/notes/history' ? t('historyTitle')
      : pathname.endsWith('/summaries') && pathname.startsWith('/app/recipients/') ? t('recipientSummariesTab')
      : pathname.endsWith('/plans') && pathname.startsWith('/app/recipients/') ? t('recipientPlansTab')
      : pathname.endsWith('/notes') && pathname.startsWith('/app/recipients/') ? t('recipientNotesTab')
      : pathname.startsWith('/app/notes/') ? t(pathname.endsWith('/addenda/new') ? 'notesAppend' : 'notesDetails')
      : pathname.startsWith('/app/recipients/') ? t(pathname.endsWith('/edit') ? 'recipientEdit' : 'recipientDetails')
      : pathname === '/app/recipients' ? t('recipientsTitle') : t('pageNotFound');
    document.title = `${title} · ${t('brand')}`;
  }, [pathname, t]);

  useEffect(() => {
    if (!mounted.current) {
      mounted.current = true;
      // The shell no longer wraps the landing route, so it can mount as a result
      // of client-side navigation (e.g. leaving "/"). history.state.idx > 0
      // marks that case; a fresh page load keeps idx === 0 and is left alone.
      const idx = (window.history.state as { idx?: number } | null)?.idx;
      if (typeof idx === 'number' && idx > 0) {
        mainRef.current?.focus();
        window.scrollTo({ top: 0, behavior: 'instant' });
      }
      return;
    }
    if (previousPath.current !== pathname) {
      mainRef.current?.focus();
      window.scrollTo({ top: 0, behavior: 'instant' });
      previousPath.current = pathname;
    }
  }, [pathname]);

  const nextLanguage = language === 'en' ? 'ar' : 'en';
  const isWorkspace = isAppRoute(pathname);

  return (
    <div className="site-shell">
      <a className="skip-link" href="#main-content" onClick={() => mainRef.current?.focus()}>{t('skipToContent')}</a>
      {!isWorkspace && <header className={`site-header${auth.status === 'authenticated' ? ' site-header--workspace' : ''}`}>
        <div className="header-inner container">
          <Link className="brand" to={auth.status === 'authenticated' ? '/app' : '/'}>
            <span className="brand-mark"><Icon name="leaf" size={25} /></span>
            <span className="brand-text"><span>{t('brand')}</span><span className="brand-tagline">{t('brandTagline')}</span></span>
          </Link>
          <div className="nav-utilities">
            <div className="appearance-control">
              <Icon name={mode === 'system' ? 'monitor' : mode === 'dark' ? 'moon' : 'sun'} size={17} />
              <select aria-label={t('appearance')} value={mode} onChange={(event) => setMode(event.target.value as ThemeMode)}>
                <option value="system">{t('themeSystem')}</option>
                <option value="light">{t('themeLight')}</option>
                <option value="dark">{t('themeDark')}</option>
              </select>
            </div>
            <button type="button" className="language-toggle" lang={nextLanguage} dir={nextLanguage === 'ar' ? 'rtl' : 'ltr'}
              aria-label={t(language === 'ar' ? 'switchToEnglish' : 'switchToArabic')}
              onClick={() => setLanguage(nextLanguage)}>
              <Icon name="globe" size={17} />{t('switchLanguage')}
            </button>
            {auth.status === 'authenticated' && <button type="button" className="nav-link auth-logout" onClick={auth.logout}><Icon name="logout" size={18} /><span>{t('authSignOut')}</span></button>}
          </div>
          <nav className={'site-nav' + (auth.status === 'authenticated' ? ' site-nav--authenticated' : '')} aria-label={t('mainNavigation')}>
            <div className="nav-links">
              {auth.status === 'authenticated' ? <>
                <div className="workspace-primary-links" role="group" aria-label={t('workspacePrimaryNavigation')}>
                  <NavLink end to="/app" className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}><Icon name="home" size={19} />{t('home')}</NavLink>
                  <NavLink to="/app/recipients" className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}><Icon name="people" size={19} />{t('workspacePeople')}</NavLink>
                  <NavLink end to="/app/notes/new" className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}><Icon name="edit" size={19} />{t('workspaceNotes')}</NavLink>
                  <Link to="/app/notes" aria-current={isHistoryRoute(pathname) ? 'page' : undefined}
                    className={`nav-link${isHistoryRoute(pathname) ? ' active' : ''}`}>
                    <Icon name="history" size={19} />{t('workspaceHistory')}
                  </Link>
                </div>
                <div className="workspace-secondary-links" role="group" aria-label={t('workspaceSecondaryNavigation')}>
                  <NavLink to="/app/notes/plans" className={({ isActive }) => `${isActive ? 'active ' : ''}nav-link workspace-secondary-link`}><Icon name="shield" size={18} />{t('plansTitle')}</NavLink>
                  <NavLink end to="/app/settings" className={({ isActive }) => `${isActive ? 'active ' : ''}nav-link workspace-secondary-link`}><Icon name="info" size={18} />{t('settingsNavigation')}</NavLink>
                </div>
              </> : <>
                <NavLink end to="/" className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}>{t('home')}</NavLink>
                <NavLink to="/login" className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}>{t('signIn')}</NavLink>
              </>}
            </div>
          </nav>
        </div>
      </header>}
      <main id="main-content" ref={mainRef} tabIndex={-1} className={`main-content${pathname === '/app' ? ' main-content--dashboard' : ''}`}>
        {auth.status === 'authenticated' ? <ByokProvider>{children}</ByokProvider> : children}
      </main>
      <footer className="site-footer">
        <div className="container footer-inner">
          <span className="footer-brand"><Icon name="leaf" size={18} />{t('footer')}</span>
          <span className="footer-note">{t('note')}</span>
        </div>
      </footer>
    </div>
  );
}

export function AppLayout() {
  const { t, language, setLanguage } = useI18n();
  const auth = useAuth();
  const { mode, setMode } = useTheme();
  const client = useApiClient();
  const recipientsApi = useMemo(() => createRecipientsApi(client), [client]);
  const location = useLocation();
  const pathname = normalizePathname(location.pathname);
  const navigate = useNavigate();
  const [search, setSearch] = useSearchParams();
  const [recipients, setRecipients] = useState<RecipientState>({ status: 'idle' });
  const [recipientRequested, setRecipientRequested] = useState(false);
  const [recipientRevision, setRecipientRevision] = useState(0);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [mobile, setMobile] = useState(false);
  const drawerRef = useRef<HTMLDivElement>(null);
  const menuButtonRef = useRef<HTMLButtonElement>(null);
  const accountRef = useRef<HTMLDetailsElement>(null);
  const nextLanguage = language === 'en' ? 'ar' : 'en';
  const rawDate = search.get('date');
  const date = rawDate && isNoteDate(rawDate) ? rawDate : localToday();
  const recipientRoute = pathname.match(/^\/app\/recipients\/([^/]+)(\/(?:edit|notes|summaries|plans))?$/);
  const routeRecipientId = recipientRoute && isRecipientId(recipientRoute[1]) ? recipientRoute[1] : '';
  const queryRecipientRoutes = ['/app/notes', '/app/notes/history', '/app/notes/summary', '/app/notes/plans'];
  const canChooseRecipient = pathname === '/app/recipients' || !!routeRecipientId || queryRecipientRoutes.includes(pathname);
  const selectedRecipientId = routeRecipientId || search.get('recipientId') || '';
  const noteCreateTarget = `/app/notes/new?${new URLSearchParams({
    ...(selectedRecipientId ? { recipientId: selectedRecipientId } : {}), date,
  }).toString()}`;

  useEffect(() => {
    if (!canChooseRecipient) { setRecipients({ status: 'idle' }); return; }
    if (!recipientRequested) return;
    const controller = new AbortController();
    setRecipients({ status: 'loading' });
    void recipientsApi.list(controller.signal, 'ALL').then(
      (data) => { if (!controller.signal.aborted) setRecipients({ status: 'ready', data }); },
      (error: unknown) => { if (!controller.signal.aborted) setRecipients({ status: 'error', error }); },
    );
    return () => controller.abort();
  }, [canChooseRecipient, recipientRequested, recipientsApi, recipientRevision]);

  useEffect(() => {
    const media = typeof window.matchMedia === 'function' ? window.matchMedia('(min-width: 769px)') : null;
    if (!media) return;
    const updateViewport = (event: MediaQueryListEvent) => {
      setMobile(!event.matches);
      if (event.matches) setDrawerOpen(false);
    };
    media.addEventListener('change', updateViewport);
    setMobile(!media.matches);
    return () => media.removeEventListener('change', updateViewport);
  }, []);

  useEffect(() => {
    if (!drawerOpen) return;
    drawerRef.current?.querySelector<HTMLElement>('button, a[href]')?.focus();
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => { document.body.style.overflow = previousOverflow; };
  }, [drawerOpen]);

  useEffect(() => {
    if (pathname) setDrawerOpen(false);
    if (accountRef.current) accountRef.current.open = false;
  }, [pathname]);

  const updateDate = (value: string) => {
    const next = new URLSearchParams(search);
    if (isNoteDate(value)) next.set('date', value);
    else next.delete('date');
    setSearch(next);
  };

  const changeRecipient = (recipientId: string) => {
    if (pathname === '/app/recipients') {
      if (recipientId) navigate({
        pathname: `/app/recipients/${encodeURIComponent(recipientId)}`,
        search: location.search,
        hash: location.hash,
      });
      return;
    }
    if (routeRecipientId && recipientId) {
      navigate({
        pathname: `/app/recipients/${encodeURIComponent(recipientId)}${recipientRoute?.[2] ?? ''}`,
        search: location.search,
        hash: location.hash,
      });
      return;
    }
    setSearch((current) => {
      const next = new URLSearchParams(current);
      if (recipientId) next.set('recipientId', recipientId);
      else next.delete('recipientId');
      return next;
    });
  };

  const notesActive = pathname === '/app/notes' || (pathname.startsWith('/app/notes/')
    && !['/app/notes/summary', '/app/notes/plans'].includes(pathname))
    || /^\/app\/recipients\/[^/]+\/notes$/.test(pathname);
  const closeDrawer = (restoreFocus: boolean) => {
    setDrawerOpen(false);
    if (restoreFocus) menuButtonRef.current?.focus();
  };
  const trapDrawerFocus = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key === 'Escape') {
      event.preventDefault();
      closeDrawer(true);
      return;
    }
    if (event.key !== 'Tab') return;
    const focusable = Array.from(drawerRef.current?.querySelectorAll<HTMLElement>(
      'button:not([disabled]), a[href], input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])',
    ) ?? []);
    if (!focusable.length) { event.preventDefault(); return; }
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (event.shiftKey && (document.activeElement === first || !drawerRef.current?.contains(document.activeElement))) {
      event.preventDefault(); last.focus();
    } else if (!event.shiftKey && (document.activeElement === last || !drawerRef.current?.contains(document.activeElement))) {
      event.preventDefault(); first.focus();
    }
  };

  return <div className="app-layout">
    {mobile && drawerOpen && <div className="app-drawer-backdrop" aria-hidden="true" onClick={() => closeDrawer(true)} />}
    <div id="app-sidebar-panel" ref={drawerRef} className={`app-sidebar-shell${drawerOpen ? ' app-sidebar-shell--open' : ''}`}
      role={mobile && drawerOpen ? 'dialog' : undefined} aria-modal={mobile && drawerOpen ? true : undefined}
      aria-label={mobile && drawerOpen ? t('appNavigation') : undefined} onKeyDown={mobile && drawerOpen ? trapDrawerFocus : undefined}>
      <aside className="app-sidebar">
        <Link className="app-sidebar-brand" to="/app" onClick={() => closeDrawer(true)}>
          <span className="brand-mark"><Icon name="leaf" size={24} /></span>
          <span className="app-sidebar-brand-text"><strong>{t('brand')}</strong><span>{t('brandTagline')}</span></span>
        </Link>
        <nav className="app-sidebar-nav" aria-label={t('appNavigation')}>
          <NavLink end to="/app" title={t('appOverview')} className={({ isActive }) => `app-nav-link${isActive ? ' active' : ''}`}
            onClick={() => closeDrawer(true)}><Icon name="home" /><span>{t('appOverview')}</span></NavLink>
          <NavLink to="/app/recipients" title={t('appRecipients')} className={({ isActive }) => `app-nav-link${isActive ? ' active' : ''}`}
            onClick={() => closeDrawer(true)}><Icon name="people" /><span>{t('appRecipients')}</span></NavLink>
          <Link to={noteCreateTarget} state={{ backgroundLocation: location }} title={t('notesCreate')}
            className="app-nav-link" onClick={() => closeDrawer(true)}>
            <Icon name="edit" /><span>{t('notesCreate')}</span>
          </Link>
          <Link to="/app/notes" title={t('appNotes')} aria-current={notesActive ? 'page' : undefined}
            className={`app-nav-link${notesActive ? ' active' : ''}`} onClick={() => closeDrawer(true)}>
            <Icon name="history" /><span>{t('appNotes')}</span>
          </Link>
          <NavLink to="/app/notes/summary" title={t('appSummaries')} className={({ isActive }) => `app-nav-link${isActive ? ' active' : ''}`}
            onClick={() => closeDrawer(true)}><Icon name="journal" /><span>{t('appSummaries')}</span></NavLink>
          <NavLink to="/app/notes/plans" title={t('appPlans')} className={({ isActive }) => `app-nav-link${isActive ? ' active' : ''}`}
            onClick={() => closeDrawer(true)}><Icon name="shield" /><span>{t('appPlans')}</span></NavLink>
        </nav>
        {mobile && <button type="button" className="app-sidebar-close" onClick={() => closeDrawer(true)}>
          <Icon name="close" />{t('appCloseNavigation')}
        </button>}
      </aside>
    </div>

    <div className="app-main">
      <header className="app-topbar">
        <button ref={menuButtonRef} type="button" className="app-menu-trigger" aria-label={t('appOpenNavigation')}
          aria-expanded={drawerOpen} aria-controls="app-sidebar-panel" onClick={() => setDrawerOpen(true)}>
          <Icon name="menu" />
        </button>
        <div className="app-date-control" dir="ltr">
          <label className="app-visually-hidden" htmlFor="app-date">{t('appDateLabel')}</label>
          <Icon name="calendar" />
          <input id="app-date" type="date" dir="ltr" value={date} onChange={(event) => updateDate(event.target.value)} />
        </div>
        <Link className="button button--secondary app-create-note" to={noteCreateTarget} state={{ backgroundLocation: location }}>
          <Icon name="edit" />{t('notesCreate')}
        </Link>
        {canChooseRecipient && <div className="app-recipient-control">
          <label className="app-visually-hidden" htmlFor="app-recipient">{t('appRecipientLabel')}</label>
          <Icon name="people" />
          <select id="app-recipient" dir="auto" value={selectedRecipientId}
            disabled={recipients.status === 'ready' && !recipients.data.length}
            aria-describedby={recipients.status === 'error' ? 'app-recipient-error' : undefined}
            onFocus={() => { if (recipients.status === 'idle') setRecipientRequested(true); }}
            onChange={(event) => changeRecipient(event.target.value)}>
            <option value="">{recipients.status === 'loading' ? t('appLoadingRecipients')
              : recipients.status === 'ready' && !recipients.data.length ? t('appNoRecipients') : t('appChooseRecipient')}</option>
            {selectedRecipientId && recipients.status === 'ready' && !recipients.data.some((recipient) => recipient.id === selectedRecipientId)
              && <option value={selectedRecipientId}>{t('appRecipientUnavailable')}</option>}
            {recipients.status === 'ready' && recipients.data.map((recipient) => <option key={recipient.id} value={recipient.id}>
              {recipient.name}{recipient.lifecycleStatus === 'ARCHIVED' ? ` — ${t('recipientArchived')}`
                : recipient.lifecycleStatus === 'LEGACY_INACTIVE_REVIEW' ? ` — ${t('recipientNeedsReview')}` : ''}
            </option>)}
          </select>
          {recipients.status === 'error' && <span id="app-recipient-error" className="app-recipient-error" role="alert">
            {t(recipientErrorMessage(recipients.error))}
            <button type="button" onClick={() => setRecipientRevision((revision) => revision + 1)}>{t('retry')}</button>
          </span>}
        </div>}
        <button type="button" className="language-toggle app-language-toggle" lang={nextLanguage} dir={nextLanguage === 'ar' ? 'rtl' : 'ltr'}
          aria-label={t(language === 'ar' ? 'switchToEnglish' : 'switchToArabic')} onClick={() => setLanguage(nextLanguage)}>
          <Icon name="globe" />{t('switchLanguage')}
        </button>
        <details ref={accountRef} className="app-account">
          <summary aria-label={t('appAccountMenu')}><Icon name="user" /><span>{t('appAccountMenu')}</span></summary>
          <div className="app-account-panel">
            <label htmlFor="app-appearance">{t('appearance')}</label>
            <select id="app-appearance" value={mode} onChange={(event) => setMode(event.target.value as ThemeMode)}>
              <option value="system">{t('themeSystem')}</option>
              <option value="light">{t('themeLight')}</option>
              <option value="dark">{t('themeDark')}</option>
            </select>
            <Link to="/app/settings" aria-current={pathname === '/app/settings' ? 'page' : undefined}
              onClick={() => { if (accountRef.current) accountRef.current.open = false; }}>
              <Icon name="info" />{t('settingsNavigation')}
            </Link>
            <button type="button" onClick={auth.logout}><Icon name="logout" />{t('authSignOut')}</button>
          </div>
        </details>
      </header>
      <div className="app-view"><Outlet /></div>
    </div>
  </div>;
}
