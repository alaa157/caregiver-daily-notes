import { Link } from 'react-router-dom';
import type { LandingCopy } from './landingCopy';
import heroPhoto from './hero-caregiver.webp';

function ShieldIcon({ size = 15 }: { size?: number }) {
  return (
    <svg aria-hidden="true" fill="none" focusable="false" height={size} stroke="currentColor" strokeWidth="1.7" viewBox="0 0 24 24" width={size}>
      <path d="M12 3 5 6v5c0 5 3.3 8.4 7 10 3.7-1.6 7-5 7-10V6l-7-3Z" />
      <path d="m9 12 2 2 4-4" />
    </svg>
  );
}

function ArrowIcon() {
  return (
    <svg aria-hidden="true" fill="none" focusable="false" height="15" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24" width="15">
      <path d="M5 12h14M13 6l6 6-6 6" />
    </svg>
  );
}

/** The full-bleed photographic hero and its three proof points. */
export function LandingHero({ c }: { c: LandingCopy }) {
  return (
    <section className="hero" id="top">
      <div className="hero-background">
        <div className="hero-photo-wrap">
          <img
            alt={c.heroPhotoAlt}
            className="hero-photo"
            decoding="async"
            fetchPriority="high"
            loading="eager"
            src={heroPhoto}
          />
        </div>
      </div>
      <div className="container">
        <div className="hero-grid">
          <div className="hero-copy">
            <p className="eyebrow">{c.eyebrow}</p>
            <h1><span>{c.heroTitle}</span> <span className="hero-title-accent">{c.heroAccent}</span></h1>
            <p className="hero-description">{c.heroBody}</p>
            <div className="hero-actions">
              <Link className="button button-primary" to="/signup">
                <span>{c.getStarted}</span>
                <ArrowIcon />
              </Link>
              <Link className="button button-secondary" to="/login">{c.signIn}</Link>
            </div>
            <p className="hero-small-note"><ShieldIcon /><span>{c.heroTrust}</span></p>
          </div>
        </div>
        <div className="hero-proof">
          <div className="proof-item">
            <span className="proof-icon">
              <svg aria-hidden="true" fill="none" focusable="false" height="18" stroke="currentColor" strokeWidth="1.7" viewBox="0 0 24 24" width="18">
                <path d="M7 3v3m10-3v3M4 9h16M5 5h14a1 1 0 0 1 1 1v13H4V6a1 1 0 0 1 1-1Z" />
                <path d="m8 14 2 2 5-5" />
              </svg>
            </span>
            <span className="proof-text"><strong>{c.proofOne}</strong><span>{c.proofOneSub}</span></span>
          </div>
          <div className="proof-item">
            <span className="proof-icon">
              <svg aria-hidden="true" fill="none" focusable="false" height="18" stroke="currentColor" strokeWidth="1.7" viewBox="0 0 24 24" width="18">
                <path d="M4 19V5m0 14h17" />
                <path d="m7 15 4-4 3 2 5-6" />
              </svg>
            </span>
            <span className="proof-text"><strong>{c.proofTwo}</strong><span>{c.proofTwoSub}</span></span>
          </div>
          <div className="proof-item">
            <span className="proof-icon"><ShieldIcon size={18} /></span>
            <span className="proof-text"><strong>{c.proofThree}</strong><span>{c.proofThreeSub}</span></span>
          </div>
        </div>
      </div>
    </section>
  );
}
