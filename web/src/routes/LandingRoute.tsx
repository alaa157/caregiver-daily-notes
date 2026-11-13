import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useI18n } from '../i18n/I18nProvider';
import { useAuthStatus } from '../features/auth/AuthProvider';
import { Icon } from '../components/Icon';
import '../features/landing/landing.css';

const copy = {
  en: {
    nav: ['How it works', 'Safety', 'Languages', 'For reviewers'],
    hero: 'A clearer picture of each day, in a minute.', sub: 'Write down what you notice. Review a simple summary and a care suggestion you stay in control of.', sign: 'Sign in', open: 'Open app', apk: 'Download Android APK', disclaimer: 'Care-support suggestions generated from your notes. Not medical advice.',
    how: 'How it works', steps: [['Record daily notes', 'Add a few details about the day, in your own words.'], ['Review a summary', 'See a 7, 14, or 30-day picture of your notes.'], ['Choose what happens next', 'Review a suggested plan. Accept it, edit it, or dismiss it.']],
    record: 'What you can record', recordBody: 'Mood, appetite, sleep, mobility, medication taken, pain, falls, and free-text notes in Arabic, English, or both. Notes are never overwritten; corrections are added as separate addendum notes.', safety: 'Safety comes first', safetyItems: ['Red flags such as falls, high pain, repeated missed medication, or several days of poor appetite come from fixed rules—not AI.', 'AI cannot add, remove, or change medications or doses.', 'Each observation links back to your own notes and supporting quotes.', 'Unclear or conflicting information stays marked as uncertain.', 'Saving a note never depends on AI being available.', 'Structured recipient profile fields such as names and dates of birth are excluded from AI requests. Free-text notes are sent as written and may contain identifying details.', 'You decide whether to accept, edit, or dismiss each suggested plan; previous versions are kept.', 'Only you can see your recipients, enforced by the server.'],
    languages: 'Arabic and English, built in', langBody: 'The whole experience supports right-to-left Arabic and mixed Arabic, English, and numbers. Switch languages whenever you need.', ar: 'Arabic', en: 'English', apps: 'Web and Android', web: 'Web app', android: 'Android app', same: 'Both use the same account and data.', notice: 'This is a capstone project. It uses synthetic data only, is not medical advice, and must not be used with real patient data.', reviewers: 'For reviewers', test: 'How it is tested', testing: 'Unit, decision-table, and state-transition tests; Testcontainers integration with a fake LLM client; golden datasets and metamorphic tests; Playwright with axe accessibility scans at 1280, 768, 390, and 320px in both languages.', stack: ['Java 21', 'Spring Boot 3', 'PostgreSQL (Supabase)', 'Redis (Upstash)', 'React + TypeScript', 'React Native', 'Playwright', 'GitHub Actions'], repo: 'GitHub repository', architecture: 'Architecture', releases: 'Releases', backend: 'The demo backend is started on request.',
  },
  ar: {
    nav: ['كيف يعمل', 'السلامة', 'اللغات', 'للمراجعين'],
    hero: 'سجّل تفاصيل يومك بسهولة، واطّلع على ملخص واضح.', sub: 'دوّن ما تلاحظه، ثم راجع ملخصاً واقتراحاً للرعاية مع بقاء القرار لك.', sign: 'تسجيل الدخول', open: 'فتح التطبيق', apk: 'تنزيل تطبيق أندرويد', disclaimer: 'اقتراحات للمساندة في الرعاية تستند إلى ملاحظاتك. ليست نصيحة طبية.',
    how: 'كيف يعمل', steps: [['دوّن ملاحظاتك اليومية', 'أضف تفاصيل بسيطة عن اليوم بكلماتك.'], ['راجع ملخصاً', 'اطّلع على ملخص لملاحظاتك خلال 7 أو 14 أو 30 يوماً.'], ['اختر الخطوة التالية', 'راجع خطة مقترحة، ثم اقبلها أو عدّلها أو ارفضها.']],
    record: 'ما الذي يمكنك تدوينه؟', recordBody: 'المزاج، والشهية، والنوم، والحركة، وتناول الدواء، والألم، والسقوط، وملاحظات حرة بالعربية أو الإنجليزية أو بكلتيهما. لا تُستبدل الملاحظات؛ تُضاف التصحيحات كملاحظات ملحقة منفصلة.', safety: 'السلامة أولاً', safetyItems: ['تُحدَّد التنبيهات مثل السقوط والألم الشديد وتكرار نسيان الدواء وضعف الشهية لعدة أيام بقواعد ثابتة، لا بالذكاء الاصطناعي.', 'لا يستطيع الذكاء الاصطناعي إضافة الأدوية أو حذفها أو تغييرها أو تغيير جرعاتها.', 'يرتبط كل استنتاج بملاحظاتك والاقتباسات الداعمة له.', 'تبقى المعلومات غير الواضحة أو المتضاربة موسومة بأنها غير مؤكدة.', 'حفظ الملاحظة لا يعتمد على عمل الذكاء الاصطناعي.', 'لا تُضمَّن بيانات الملف الشخصي المنظمة، مثل الأسماء وتواريخ الميلاد، في طلبات الذكاء الاصطناعي. أما الملاحظات المكتوبة بحرية فتُرسل كما كُتبت وقد تتضمن معلومات تكشف الهوية.', 'أنت تقرر قبول كل خطة مقترحة أو تعديلها أو رفضها، مع الاحتفاظ بالإصدارات السابقة.', 'لا يرى بيانات الأشخاص الذين تتابعهم سواك، ويُطبَّق ذلك على الخادم.'],
    languages: 'العربية والإنجليزية، من البداية', langBody: 'يدعم التطبيق الاتجاه من اليمين إلى اليسار والنصوص المختلطة بالعربية والإنجليزية والأرقام. يمكنك تبديل اللغة متى شئت.', ar: 'العربية', en: 'الإنجليزية', apps: 'الويب وأندرويد', web: 'تطبيق الويب', android: 'تطبيق أندرويد', same: 'يستخدم التطبيقان الحساب والبيانات نفسيهما.', notice: 'هذا مشروع تخرّج يستخدم بيانات اصطناعية فقط. ليس نصيحة طبية، ويجب عدم استخدامه مع بيانات مرضى حقيقية.', reviewers: 'للمراجعين', test: 'كيف نختبره؟', testing: 'اختبارات الوحدة وجداول القرار وانتقالات الحالة؛ اختبارات تكامل Testcontainers بعميل لغوي وهمي؛ مجموعات بيانات مرجعية واختبارات التحوّل؛ واختبارات Playwright مع فحوصات axe للإتاحة عند 1280 و768 و390 و320 بكسل باللغتين.', stack: ['Java 21', 'Spring Boot 3', 'PostgreSQL (Supabase)', 'Redis (Upstash)', 'React + TypeScript', 'React Native', 'Playwright', 'GitHub Actions'], repo: 'مستودع GitHub', architecture: 'المعمارية', releases: 'الإصدارات', backend: 'يُشغَّل الخادم التجريبي عند الطلب.',
  },
} as const;

const APK = 'https://github.com/alaa157/caregiver-daily-notes/releases/download/v0.1.1/app-minified.apk';
const repo = 'https://github.com/alaa157/caregiver-daily-notes';

function MockScreen({ rtl = false }: { rtl?: boolean }) {
  return <div className="landing-screen" dir={rtl ? 'rtl' : 'ltr'} aria-hidden="true"><div className="screen-top"><span /><span /><span /></div><strong>{rtl ? 'ملاحظات اليوم' : 'Today’s notes'}</strong><small>{rtl ? 'الثلاثاء، ١٢ نوفمبر' : 'Tuesday, 12 November'}</small><div className="screen-field">{rtl ? 'المزاج' : 'Mood'}<b>{rtl ? 'هادئ' : 'Calm'}</b></div><div className="screen-field">{rtl ? 'النوم' : 'Sleep'}<b>{rtl ? 'جيد' : 'Good'}</b></div><div className="screen-field">{rtl ? 'ملاحظات' : 'Notes'}<i /></div><span className="screen-save">{rtl ? 'حفظ الملاحظة' : 'Save note'}</span></div>;
}

export function LandingRoute() {
  const { language, setLanguage } = useI18n();
  const authStatus = useAuthStatus();
  const c = copy[language];
  const { t } = useI18n();
  const [menuOpen, setMenuOpen] = useState(false);
  const cta = authStatus === 'authenticated' ? c.open : c.sign;
  const target = authStatus === 'authenticated' ? '/app' : '/login';
  return <div className="landing-page">
    <header className="landing-header"><Link className="landing-brand" to="/">Caregiver Daily Notes</Link><button className="landing-menu" aria-expanded={menuOpen} aria-controls="landing-nav" onClick={() => setMenuOpen(!menuOpen)}>{menuOpen ? '×' : '☰'}<span className="visually-hidden">{language === 'ar' ? 'القائمة' : 'Menu'}</span></button><nav id="landing-nav" aria-label={language === 'ar' ? 'التنقل الرئيسي' : 'Main navigation'} className={menuOpen ? 'is-open' : ''}>{c.nav.map((label, i) => <a key={label} href={['#how-it-works', '#safety', '#languages', '#reviewers'][i]} onClick={() => setMenuOpen(false)}>{label}</a>)}<button
      className="language-toggle"
      aria-label={language === 'ar' ? 'EN — Switch to English' : 'AR — التبديل إلى العربية'}
      onClick={() => setLanguage(language === 'ar' ? 'en' : 'ar')}
    >{language === 'ar' ? 'EN' : 'AR'}</button><Link className="button button--primary" to={target}>{cta}</Link></nav></header>
    <div className="landing-content">
      <section className="landing-hero"><div><p className="eyebrow">Caregiver Daily Notes</p><h1>{c.hero}</h1><p>{c.sub}</p><div className="landing-ctas"><Link className="button button--primary" to={target}>{cta}</Link><a className="button button--secondary" href={APK}>{c.apk}</a></div><p className="landing-disclaimer">{c.disclaimer}</p></div><MockScreen rtl={language === 'ar'} /></section>
      <section id="how-it-works" className="landing-section"><h2>{c.how}</h2><div className="landing-grid">{c.steps.map(([title, text], i) => <article className="landing-card" key={title}><span className="step-number">{i + 1}</span><h3>{title}</h3><p>{text}</p></article>)}</div><article className="landing-card"><h3>{c.record}</h3><p>{c.recordBody}</p></article></section>
      <section id="safety" className="landing-section landing-safety"><h2>{c.safety}</h2><ul>{c.safetyItems.map(text => <li key={text}><Icon name="shield" />{text}</li>)}</ul></section>
      <section id="languages" className="landing-section"><h2>{c.languages}</h2><p>{c.langBody}</p><div className="landing-grid language-screens"><figure><MockScreen rtl /><figcaption>{c.ar}</figcaption></figure><figure><MockScreen /><figcaption>{c.en}</figcaption></figure></div></section>
      <section className="landing-section"><h2>{c.apps}</h2><div className="landing-grid"><article className="landing-card"><h3>{c.web}</h3><Link className="button button--primary" to={target}>{cta}</Link></article><article className="landing-card"><h3>{c.android}</h3><a className="button button--secondary" href={APK}>{c.apk}</a><p>{c.same}</p></article></div></section>
      <aside className="landing-notice"><Icon name="info" />{c.notice}</aside>
      <section id="reviewers" className="landing-section"><h2>{c.reviewers}</h2><div className="landing-badges">{c.stack.map(item => <span key={item}>{item}</span>)}</div><h3>{c.test}</h3><p>{c.testing}</p><p>{c.backend}</p><div className="landing-links"><a href={repo}>{c.repo}</a><a href={`${repo}/blob/main/ARCHITECTURE.md`}>{c.architecture}</a><a href={`${repo}/releases`}>{c.releases}</a></div></section>
    </div><footer className="landing-footer"><span>Caregiver Daily Notes · {t('note')}</span><a href={repo}>{c.repo}</a><button className="language-toggle" onClick={() => setLanguage(language === 'ar' ? 'en' : 'ar')}>{language === 'ar' ? 'EN' : 'AR'}</button></footer>
  </div>;
}
