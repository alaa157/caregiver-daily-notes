import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { Button, ButtonLink } from '../../components/Button';
import { FieldError } from '../../components/Feedback';
import { Icon } from '../../components/Icon';
import { PagePanel } from '../../components/PagePanel';
import { useI18n } from '../../i18n/I18nProvider';
import type { MessageKey } from '../../i18n/translations';
import { ApiError } from '../../lib/api';
import { safeReturnPath } from '../../routes/authRedirect';
import { useAuth } from './AuthProvider';
import './auth.css';

type FieldErrors = { email?: MessageKey; password?: MessageKey };
function errorMessage(error: unknown): MessageKey {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'authInvalidCredentials';
    if (error.status === 422 || error.status === 400) return 'authValidationError';
    if (error.status === 429) return 'authRateLimited';
    if (error.status === 403) return 'authAccessDenied';
  }
  return error instanceof TypeError ? 'authNetworkError' : 'authServiceError';
}

export function LoginPage() {
  const { t } = useI18n();
  const auth = useAuth();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [errors, setErrors] = useState<FieldErrors>({});
  const [error, setError] = useState<MessageKey | null>(null);
  const [pending, setPending] = useState(false);
  const emailRef = useRef<HTMLInputElement>(null);
  const passwordRef = useRef<HTMLInputElement>(null);
  const errorRef = useRef<HTMLDivElement>(null);
  const request = useRef<AbortController | null>(null);
  useEffect(() => () => { request.current?.abort(); }, []);
  useEffect(() => { if (error) errorRef.current?.focus(); }, [error]);

  if (auth.status === 'authenticated') {
    const from: unknown = (location.state as { from?: unknown } | null)?.from;
    return <Navigate to={safeReturnPath(from)} replace />;
  }
  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (request.current) return;
    const next: FieldErrors = {};
    const normalizedEmail = email.trim();
    if (!normalizedEmail) next.email = 'authEmailRequired';
    else if (normalizedEmail.length > 320 || !/^[^\s@]+@[^\s@]+$/.test(normalizedEmail)) next.email = 'authEmailInvalid';
    if (!password.trim()) next.password = 'authPasswordRequired';
    else if (new TextEncoder().encode(password).length > 72) next.password = 'authPasswordTooLong';
    setErrors(next);
    setError(null);
    if (next.email || next.password) {
      (next.email ? emailRef : passwordRef).current?.focus();
      return;
    }
    const controller = new AbortController();
    request.current = controller;
    setPending(true);
    try { await auth.login(normalizedEmail, password, controller.signal); }
    catch (failure) {
      if (!controller.signal.aborted) { setError(errorMessage(failure)); setPassword(''); setShowPassword(false); }
    } finally {
      if (!controller.signal.aborted) setPending(false);
      if (request.current === controller) request.current = null;
    }
  };
  return (
    <PagePanel icon="lock" eyebrow={t('authWelcome')} title={t('loginTitle')} description={t('loginDescription')}>
      {auth.reason === 'expired' && !error && <p className="notice" role="alert">{t('authSessionExpired')}</p>}
      {auth.reason === 'signedOut' && <p className="notice" role="status">{t('authSignedOut')}</p>}
      <form className="auth-form" aria-label={t('loginTitle')} noValidate onSubmit={submit}>
        {error && <div ref={errorRef} className="auth-error" role="alert" tabIndex={-1}><Icon name="alert-circle" size={20} /><p>{t(error)}</p></div>}
        <div className="auth-field">
          <label htmlFor="auth-email">{t('authEmail')}</label>
          <input ref={emailRef} id="auth-email" name="email" type="email" autoComplete="username" inputMode="email" dir="ltr"
            autoCapitalize="none" spellCheck={false} required value={email} readOnly={pending}
            aria-invalid={Boolean(errors.email)} aria-describedby={errors.email ? 'auth-email-error' : undefined}
            onChange={(event) => { setEmail(event.target.value); setErrors((current) => ({ ...current, email: undefined })); }} />
          {errors.email && <FieldError id="auth-email-error" message={t(errors.email)} />}
        </div>
        <div className="auth-field">
          <label htmlFor="auth-password">{t('authPassword')}</label>
          <div className="auth-password-row">
            <input ref={passwordRef} id="auth-password" name="password" type={showPassword ? 'text' : 'password'} autoComplete="current-password"
              required value={password} readOnly={pending} dir="ltr" aria-invalid={Boolean(errors.password)}
              aria-describedby={errors.password ? 'auth-password-error' : undefined}
              onChange={(event) => { setPassword(event.target.value); setErrors((current) => ({ ...current, password: undefined })); }} />
            <button className="auth-password-toggle" type="button" aria-controls="auth-password" aria-pressed={showPassword}
              disabled={pending} onClick={() => setShowPassword((shown) => !shown)}>{t(showPassword ? 'authHidePassword' : 'authShowPassword')}</button>
          </div>
          {errors.password && <FieldError id="auth-password-error" message={t(errors.password)} />}
        </div>
        <Button type="submit" className="auth-submit" disabled={pending} aria-describedby="auth-session-note">
          {pending && <span className="spinner" aria-hidden="true" />}{t(pending ? 'authSigningIn' : 'signIn')}
          {!pending && <Icon name="arrow-right" className="directional-icon" />}
        </Button>
        <span className="auth-progress" role="status" aria-live="polite">{pending ? t('authSigningIn') : ''}</span>
        <p id="auth-session-note" className="auth-session-note"><Icon name="shield" size={16} />{t('authSessionNote')}</p>
      </form>
      <ButtonLink variant="text" to="/"><Icon name="arrow-left" className="directional-icon" />{t('returnHome')}</ButtonLink>
    </PagePanel>
  );
}
