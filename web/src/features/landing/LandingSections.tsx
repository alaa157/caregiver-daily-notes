import { Link } from 'react-router-dom';
import type { LandingCopy } from './landingCopy';

const APK = 'https://github.com/alaa157/caregiver-daily-notes/releases/download/apk-latest/app-minified.apk';

function InfoIcon({ size = 14 }: { size?: number }) {
  return (
    <svg aria-hidden="true" fill="none" focusable="false" height={size} stroke="currentColor" strokeWidth="1.7" viewBox="0 0 24 24" width={size}>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 11v5m0-8h.01" />
    </svg>
  );
}

/** All content sections between the hero and the footer, in prototype order. */
export function LandingSections({ c }: { c: LandingCopy }) {
  return (
    <>
      <section className="section details-section" id="daily-notes">
        <div className="container details-layout">
          <div>
            <p className="section-kicker">{c.detailsKicker}</p>
            <h2 className="section-heading">{c.detailsTitle}</h2>
            <p className="section-intro">{c.detailsBody}</p>
            <blockquote className="details-quote">{c.detailsQuote}</blockquote>
          </div>
          <div className="detail-grid">
            <article className="detail-card">
              <span className="detail-icon">
                <svg aria-hidden="true" fill="none" focusable="false" height="18" stroke="currentColor" strokeWidth="1.7" viewBox="0 0 24 24" width="18">
                  <circle cx="12" cy="12" r="9" />
                  <path d="M8.5 14.5s1.2 1.5 3.5 1.5 3.5-1.5 3.5-1.5M9 9h.01M15 9h.01" />
                </svg>
              </span>
              <h3>{c.mood}</h3><p>{c.moodSub}</p>
            </article>
            <article className="detail-card">
              <span className="detail-icon">
                <svg aria-hidden="true" fill="none" focusable="false" height="18" stroke="currentColor" strokeWidth="1.7" viewBox="0 0 24 24" width="18">
                  <path d="M4 11h16M6 11l2 8h8l2-8M8 11V7a4 4 0 0 1 8 0v4" />
                  <path d="M12 3v2" />
                </svg>
              </span>
              <h3>{c.appetite}</h3><p>{c.appetiteSub}</p>
            </article>
            <article className="detail-card">
              <span className="detail-icon">
                <svg aria-hidden="true" fill="none" focusable="false" height="18" stroke="currentColor" strokeWidth="1.7" viewBox="0 0 24 24" width="18">
                  <path d="M3 13a9 9 0 1 1 18 0M5 13h14M12 4v3" />
                  <path d="M12 13 16 9" />
                </svg>
              </span>
              <h3>{c.sleep}</h3><p>{c.sleepSub}</p>
            </article>
            <article className="detail-card">
              <span className="detail-icon">
                <svg aria-hidden="true" fill="none" focusable="false" height="18" stroke="currentColor" strokeWidth="1.7" viewBox="0 0 24 24" width="18">
                  <circle cx="12" cy="5" r="2.5" />
                  <path d="m9 21 1-7-3 2-2-3m10 8-1-7 3 2 2-3M9 10l3 2 3-2" />
                </svg>
              </span>
              <h3>{c.mobility}</h3><p>{c.mobilitySub}</p>
            </article>
            <article className="detail-card">
              <span className="detail-icon">
                <svg aria-hidden="true" fill="none" focusable="false" height="18" stroke="currentColor" strokeWidth="1.7" viewBox="0 0 24 24" width="18">
                  <path d="M8 4h8a4 4 0 0 1 0 8H8a4 4 0 0 1 0-8Z" />
                  <path d="m8 12 8 8M16 12l-8 8" />
                </svg>
              </span>
              <h3>{c.medication}</h3><p>{c.medicationSub}</p>
            </article>
            <article className="detail-card">
              <span className="detail-icon">
                <svg aria-hidden="true" fill="none" focusable="false" height="18" stroke="currentColor" strokeWidth="1.7" viewBox="0 0 24 24" width="18">
                  <path d="M12 3 21 19H3L12 3Z" />
                  <path d="M12 9v4m0 3h.01" />
                </svg>
              </span>
              <h3>{c.painFalls}</h3><p>{c.painFallsSub}</p>
            </article>
          </div>
        </div>
      </section>

      <section className="section workflow-section" id="how">
        <div className="container">
          <div className="workflow-head">
            <div><p className="section-kicker">{c.howKicker}</p><h2 className="section-heading">{c.howTitle}</h2></div>
            <p className="section-intro">{c.howBody}</p>
          </div>
          <div className="workflow-grid">
            <article className="workflow-card">
              <span className="workflow-number">01</span>
              <h3>{c.stepOneTitle}</h3>
              <p>{c.stepOneBody}</p>
              <div className="workflow-art">
                <div className="mini-note one"><i /><i /><i /></div>
                <div className="mini-note two"><i /><i /><i /></div>
              </div>
            </article>
            <article className="workflow-card">
              <span className="workflow-number">02</span>
              <h3>{c.stepTwoTitle}</h3>
              <p>{c.stepTwoBody}</p>
              <div className="timeline-art"><span /><span /><span /></div>
            </article>
            <article className="workflow-card">
              <span className="workflow-number">03</span>
              <h3>{c.stepThreeTitle}</h3>
              <p>{c.stepThreeBody}</p>
              <div className="plan-art"><b /><i /><i /><i /></div>
            </article>
          </div>
        </div>
      </section>

      <section className="section sample-section">
        <div className="container sample-layout">
          <div className="sample-copy">
            <p className="section-kicker">{c.summaryKicker}</p>
            <h2 className="section-heading">{c.summaryTitle}</h2>
            <p className="section-intro">{c.summaryBody}</p>
            <Link className="button button-primary" to="/signup">
              <span>{c.tryWorkspace}</span>
              <svg aria-hidden="true" fill="none" focusable="false" height="15" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24" width="15">
                <path d="M5 12h14M13 6l6 6-6 6" />
              </svg>
            </Link>
          </div>
          <div aria-label="Illustrative seven-day summary sample" className="summary-card landing-screen">
            <div className="summary-head">
              <div><small>{c.sampleSummary}</small><h3>{c.sevenDay}</h3></div>
              <span className="summary-period">{c.sevenDays}</span>
            </div>
            <p className="summary-insight">{c.summaryExample}</p>
            <blockquote className="source-quote">
              <span>{c.quoteExample}</span>
              <span className="source-label">{c.sourceLabel}</span>
            </blockquote>
            <div className="summary-tags"><span>{c.tagGrounded}</span><span>{c.tagUnclear}</span><span>{c.tagReview}</span></div>
            <div className="summary-foot"><InfoIcon /><span>{c.summaryFoot}</span></div>
          </div>
        </div>
      </section>

      <section className="section safety-section" id="safety">
        <div className="container">
          <div>
            <p className="section-kicker">{c.safetyKicker}</p>
            <h2 className="section-heading">{c.safetyTitle}</h2>
            <p className="section-intro">{c.safetyBody}</p>
          </div>
          <div className="safety-grid">
            <article className="safety-card">
              <svg aria-hidden="true" fill="none" focusable="false" height="22" stroke="currentColor" strokeWidth="1.6" viewBox="0 0 24 24" width="22">
                <path d="M12 3 5 6v5c0 5 3.3 8.4 7 10 3.7-1.6 7-5 7-10V6l-7-3Z" /><path d="m9 12 2 2 4-4" />
              </svg>
              <h3>{c.safetyOneTitle}</h3><p>{c.safetyOneBody}</p>
            </article>
            <article className="safety-card">
              <svg aria-hidden="true" fill="none" focusable="false" height="22" stroke="currentColor" strokeWidth="1.6" viewBox="0 0 24 24" width="22">
                <path d="M8 4h8a4 4 0 0 1 0 8H8a4 4 0 0 1 0-8Z" /><path d="m8 12 8 8m0-8-8 8" />
              </svg>
              <h3>{c.safetyTwoTitle}</h3><p>{c.safetyTwoBody}</p>
            </article>
            <article className="safety-card">
              <svg aria-hidden="true" fill="none" focusable="false" height="22" stroke="currentColor" strokeWidth="1.6" viewBox="0 0 24 24" width="22">
                <path d="M4 5h16v14H4z" /><path d="M8 9h8M8 13h5" /><path d="m14 17 2 2 4-4" />
              </svg>
              <h3>{c.safetyThreeTitle}</h3><p>{c.safetyThreeBody}</p>
            </article>
            <article className="safety-card">
              <svg aria-hidden="true" fill="none" focusable="false" height="22" stroke="currentColor" strokeWidth="1.6" viewBox="0 0 24 24" width="22">
                <path d="M12 3v18M5 8h14M7 16h10" /><circle cx="12" cy="12" r="9" />
              </svg>
              <h3>{c.safetyFourTitle}</h3><p>{c.safetyFourBody}</p>
            </article>
            <article className="safety-card">
              <svg aria-hidden="true" fill="none" focusable="false" height="22" stroke="currentColor" strokeWidth="1.6" viewBox="0 0 24 24" width="22">
                <path d="M12 3 5 6v5c0 5 3.3 8.4 7 10 3.7-1.6 7-5 7-10V6l-7-3Z" /><path d="M9 12h6" />
              </svg>
              <h3>{c.safetyFiveTitle}</h3><p>{c.safetyFiveBody}</p>
            </article>
            <article className="safety-card">
              <svg aria-hidden="true" fill="none" focusable="false" height="22" stroke="currentColor" strokeWidth="1.6" viewBox="0 0 24 24" width="22">
                <rect height="10" rx="2" width="14" x="5" y="10" /><path d="M8 10V7a4 4 0 0 1 8 0v3m-4 4v2" />
              </svg>
              <h3>{c.safetySixTitle}</h3><p>{c.safetySixBody}</p>
            </article>
          </div>
          <div className="safety-bottom">
            <InfoIcon size={17} />
            <span><strong>{c.aiNoteTitle}</strong> <span>{c.aiNoteBody}</span></span>
          </div>
        </div>
      </section>

      <section className="section language-section" id="languages">
        <div className="container language-layout">
          <div>
            <p className="section-kicker">{c.languageKicker}</p>
            <h2 className="section-heading">{c.languageTitle}</h2>
            <p className="section-intro">{c.languageBody}</p>
            <div className="language-note">
              <svg aria-hidden="true" fill="none" focusable="false" height="17" stroke="currentColor" strokeWidth="1.7" viewBox="0 0 24 24" width="17">
                <path d="M4 5h16v14H4zM8 9h8M8 13h5" />
              </svg>
              <span>{c.languageFoot}</span>
            </div>
          </div>
          <div className="language-cards">
            <article className="language-card" lang="en">
              <span className="language-pill">ENGLISH · LTR</span>
              <h3>Today's note</h3>
              <p>Sleep was restful. Appetite was fair. Enjoyed a quiet morning and a cup of tea.</p>
            </article>
            <article className="language-card ar-card" dir="rtl" lang="ar">
              <span className="language-pill">العربية · RTL</span>
              <h3>ملاحظة اليوم</h3>
              <p>كان النوم جيدًا، والشهية مقبولة. استمتعنا بصباح هادئ وكوب من الشاي.</p>
            </article>
          </div>
        </div>
      </section>

      <section className="notice-section">
        <div className="container">
          <aside className="project-notice">
            <span className="notice-mark"><InfoIcon size={19} /></span>
            <div><h3>{c.projectTitle}</h3><p>{c.projectBody}</p></div>
          </aside>
        </div>
      </section>

      <section className="final-cta">
        <div className="container">
          <div className="cta-panel">
            <div><h2>{c.ctaTitle}</h2><p>{c.ctaBody}</p></div>
            <div className="cta-actions">
              <Link className="button button-primary" to="/signup">{c.ctaCreate}</Link>
              <a className="button button-secondary" href={APK} rel="noopener noreferrer" target="_blank">
                {c.ctaApk}
                <span className="visually-hidden">{c.opensNewTab}</span>
              </a>
            </div>
          </div>
        </div>
      </section>
    </>
  );
}
