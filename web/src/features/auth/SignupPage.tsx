import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { Button, ButtonLink } from '../../components/Button';
import { FieldError } from '../../components/Feedback';
import { Icon } from '../../components/Icon';
import { PagePanel } from '../../components/PagePanel';
import { CareIllustration } from '../../components/CareIllustration';
import { useI18n } from '../../i18n/I18nProvider';
import type { MessageKey } from '../../i18n/translations';
import { ApiError } from '../../lib/api';
import { safeReturnPath } from '../../routes/authRedirect';
import { useAuth } from './AuthProvider';
import './auth.css';

type FieldErrors = { email?: MessageKey; password?: MessageKey };

function errorMessage(error: unknown): MessageKey {
  if (error instanceof ApiError) {
    if (error.status === 422 || error.status === 400) return 'signupValidationError';
    if (error.status === 429) return 'authRateLimited';
    if (error.status === 403) return 'authAccessDenied';
  }
  return error instanceof TypeError ? 'authNetworkError' : 'authServiceError';
}

export function SignupPage() {
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
    try { await auth.register(normalizedEmail, password, controller.signal); }
    catch (failure) {
      if (!controller.signal.aborted) { setError(errorMessage(failure)); setPassword(''); setShowPassword(false); }
    } finally {
      if (!controller.signal.aborted) setPending(false);
      if (request.current === controller) request.current = null;
    }
  };

  return (
    <PagePanel icon="user" eyebrow={t('authWelcome')} title={t('signupTitle')} description={t('signupDescription')}
      aside={<><CareIllustration /><h2>{t('authAsideTitle')}</h2><p>{t('authAsideDescription')}</p></>}>
      <form className="auth-form" aria-label={t('signupTitle')} noValidate onSubmit={submit}>
        {error && <div ref={errorRef} className="auth-error" role="alert" tabIndex={-1}><Icon name="alert-circle" size={20} /><p>{t(error)}</p></div>}
        <div className="auth-field">
          <label htmlFor="signup-email">{t('authEmail')}</label>
          <input ref={emailRef} id="signup-email" name="email" type="email" autoComplete="username" inputMode="email" dir="ltr"
            autoCapitalize="none" spellCheck={false} required value={email} readOnly={pending}
            aria-invalid={Boolean(errors.email)} aria-describedby={errors.email ? 'signup-email-error' : undefined}
            onChange={(event) => { setEmail(event.target.value); setErrors((current) => ({ ...current, email: undefined })); }} />
          {errors.email && <FieldError id="signup-email-error" message={t(errors.email)} />}
        </div>
        <div className="auth-field">
          <label htmlFor="signup-password">{t('authPassword')}</label>
          <div className="auth-password-row">
            <input ref={passwordRef} id="signup-password" name="password" type={showPassword ? 'text' : 'password'} autoComplete="new-password"
              required value={password} readOnly={pending} dir="ltr" aria-invalid={Boolean(errors.password)}
              aria-describedby={errors.password ? 'signup-password-error' : undefined}
              onChange={(event) => { setPassword(event.target.value); setErrors((current) => ({ ...current, password: undefined })); }} />
            <button className="auth-password-toggle" type="button" aria-controls="signup-password" aria-pressed={showPassword}
              disabled={pending} onClick={() => setShowPassword((shown) => !shown)}>{t(showPassword ? 'authHidePassword' : 'authShowPassword')}</button>
          </div>
          {errors.password && <FieldError id="signup-password-error" message={t(errors.password)} />}
        </div>
        <Button type="submit" className="auth-submit" disabled={pending}>
          {pending && <span className="spinner" aria-hidden="true" />}{t(pending ? 'signupSubmitting' : 'signupSubmit')}
          {!pending && <Icon name="arrow-right" className="directional-icon" />}
        </Button>
        <span className="auth-progress" role="status" aria-live="polite">{pending ? t('signupSubmitting') : ''}</span>
        <p className="auth-session-note"><Icon name="shield" size={16} />{t('authSessionNote')}</p>
      </form>
      <p className="auth-switch">{t('signupHaveAccount')} <ButtonLink variant="text" to="/login">{t('signupSignIn')}</ButtonLink></p>
      <ButtonLink variant="text" to="/"><Icon name="arrow-left" className="directional-icon" />{t('returnHome')}</ButtonLink>
    </PagePanel>
  );
}
