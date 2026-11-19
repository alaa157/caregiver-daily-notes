import type { Language } from '../../i18n/translations';

/** Landing-scoped bilingual copy, ported verbatim from the prototype. */
export interface LandingCopy {
  skip: string;
  brandTag: string;
  navHow: string;
  navNotes: string;
  navSafety: string;
  navLanguages: string;
  signIn: string;
  eyebrow: string;
  heroTitle: string;
  heroAccent: string;
  heroBody: string;
  getStarted: string;
  heroTrust: string;
  photoCaption: string;
  sampleNote: string;
  today: string;
  mood: string;
  calm: string;
  appetite: string;
  fair: string;
  sleep: string;
  restful: string;
  mobility: string;
  usual: string;
  sampleFreeText: string;
  proofOne: string;
  proofOneSub: string;
  proofTwo: string;
  proofTwoSub: string;
  proofThree: string;
  proofThreeSub: string;
  detailsKicker: string;
  detailsTitle: string;
  detailsBody: string;
  detailsQuote: string;
  moodSub: string;
  appetiteSub: string;
  sleepSub: string;
  mobilitySub: string;
  medication: string;
  medicationSub: string;
  painFalls: string;
  painFallsSub: string;
  howKicker: string;
  howTitle: string;
  howBody: string;
  stepOneTitle: string;
  stepOneBody: string;
  stepTwoTitle: string;
  stepTwoBody: string;
  stepThreeTitle: string;
  stepThreeBody: string;
  summaryKicker: string;
  summaryTitle: string;
  summaryBody: string;
  tryWorkspace: string;
  sampleSummary: string;
  sevenDay: string;
  sevenDays: string;
  summaryExample: string;
  quoteExample: string;
  sourceLabel: string;
  tagGrounded: string;
  tagUnclear: string;
  tagReview: string;
  summaryFoot: string;
  safetyKicker: string;
  safetyTitle: string;
  safetyBody: string;
  safetyOneTitle: string;
  safetyOneBody: string;
  safetyTwoTitle: string;
  safetyTwoBody: string;
  safetyThreeTitle: string;
  safetyThreeBody: string;
  safetyFourTitle: string;
  safetyFourBody: string;
  safetyFiveTitle: string;
  safetyFiveBody: string;
  safetySixTitle: string;
  safetySixBody: string;
  aiNoteTitle: string;
  aiNoteBody: string;
  languageKicker: string;
  languageTitle: string;
  languageBody: string;
  languageFoot: string;
  projectTitle: string;
  projectBody: string;
  ctaTitle: string;
  ctaBody: string;
  ctaCreate: string;
  ctaApk: string;
  footerCopy: string;
  repoLink: string;
  releasesLink: string;
  switchLabel: string;
  switchAria: string;
  menuOpen: string;
  menuClose: string;
  brand: string;
  brandAria: string;
  heroPhotoAlt: string;
  opensNewTab: string;
  navAria: string;
}

export const landingCopy: Record<Language, LandingCopy> = {
  en: {
    skip: "Skip to content",
    brandTag: "A companion in every moment of care",
    navHow: "How it works",
    navNotes: "Daily notes",
    navSafety: "Safety",
    navLanguages: "Languages",
    signIn: "Sign in",
    eyebrow: "A little clarity, every day",
    heroTitle: "The little details are the story of",
    heroAccent: "care.",
    heroBody: "A calm place to record each day, revisit what you noticed, and review care-support suggestions — while the decisions stay yours.",
    getStarted: "Create your caregiver account",
    heroTrust: "Built for careful record-keeping. Not diagnosis or medical monitoring.",
    photoCaption: "The moments between the check-ins matter, too.",
    sampleNote: "SAMPLE DAILY NOTE",
    today: "Today",
    mood: "Mood",
    calm: "Calm",
    appetite: "Appetite",
    fair: "Fair",
    sleep: "Sleep",
    restful: "Restful",
    mobility: "Mobility",
    usual: "Usual",
    sampleFreeText: "A quiet morning and a good cup of tea.",
    proofOne: "Daily notes",
    proofOneSub: "Structured and in your words",
    proofTwo: "7, 14 or 30 days",
    proofTwoSub: "Summaries when you need them",
    proofThree: "Your decision, always",
    proofThreeSub: "Review, edit or dismiss suggestions",
    detailsKicker: "A place for the everyday",
    detailsTitle: "A day is made of details. Keep them together.",
    detailsBody: "From mood and appetite to sleep, mobility and the notes that do not fit in a checkbox — record what you observed in a way that feels natural.",
    detailsQuote: "“Not every important detail looks important in the moment.”",
    moodSub: "How the day felt",
    appetiteSub: "Food and appetite observations",
    sleepSub: "Rest and sleep quality",
    mobilitySub: "Movement and daily activity",
    medication: "Medication status",
    medicationSub: "Taken, missed or unknown",
    painFalls: "Pain and falls",
    painFallsSub: "Important observations to record",
    howKicker: "A simpler rhythm",
    howTitle: "From small observations to a clearer view.",
    howBody: "Rafeek brings the record, the review and the next decision into one calm, easy-to-follow flow.",
    stepOneTitle: "Write it down, your way.",
    stepOneBody: "Record structured details and add a note in Arabic, English, or both. Original notes stay intact; corrections are added separately.",
    stepTwoTitle: "Look back across days.",
    stepTwoBody: "Generate a 7, 14 or 30-day summary from your notes, with observations and trends you can review.",
    stepThreeTitle: "Choose the next step.",
    stepThreeBody: "Review a suggested care plan. Accept it, edit it first, or dismiss it. Previous plan versions remain available.",
    summaryKicker: "Context, not guesswork",
    summaryTitle: "A summary should show where it came from.",
    summaryBody: "AI-generated observations are linked to your notes and supporting quotes. Conflicting or unclear details stay marked as uncertain, so the record does not pretend to know more than it does.",
    tryWorkspace: "Explore the workspace",
    sampleSummary: "ILLUSTRATIVE SUMMARY",
    sevenDay: "A 7-day overview",
    sevenDays: "LAST 7 DAYS",
    summaryExample: "Across the notes recorded this week, appetite was described as lower on two days. Sleep was described as restful on most recorded days.",
    quoteExample: "“Ate a little less at lunch, but enjoyed tea later.”",
    sourceLabel: "Supporting note · illustrative sample",
    tagGrounded: "Linked to notes",
    tagUnclear: "Uncertainty preserved",
    tagReview: "Ready for your review",
    summaryFoot: "Example content only — not a clinical assessment.",
    safetyKicker: "Thoughtful by design",
    safetyTitle: "AI can assist. It does not take the wheel.",
    safetyBody: "Care-support suggestions are bounded by fixed rules, checked against your notes, and always left for you to review.",
    safetyOneTitle: "Red flags use fixed rules",
    safetyOneBody: "Signals such as falls, high pain or repeated missed medication are calculated by the backend — not invented by AI.",
    safetyTwoTitle: "Medication stays locked",
    safetyTwoBody: "AI cannot add, remove or change medications or doses in a saved plan.",
    safetyThreeTitle: "Evidence stays visible",
    safetyThreeBody: "Observations point back to note IDs and supporting text that the system validates.",
    safetyFourTitle: "Uncertainty stays visible",
    safetyFourBody: "Conflicting or unclear details are not turned into confident statements.",
    safetyFiveTitle: "Your notes still save",
    safetyFiveBody: "Saving a note never depends on AI being available.",
    safetySixTitle: "Access is caregiver-scoped",
    safetySixBody: "Server-side checks keep each caregiver's recipients and related records within their own account.",
    aiNoteTitle: "A note about AI:",
    aiNoteBody: "You choose the AI provider and supply your own API key. When you request a summary or plan, the note text is sent to that provider as written and may contain identifying details. This prototype uses synthetic examples only.",
    languageKicker: "Language should feel natural",
    languageTitle: "Arabic, English, or both in the same note.",
    languageBody: "Switch the interface whenever you need. Arabic uses a right-to-left layout, and notes can be written in Arabic, English, or a mix of the two.",
    languageFoot: "A matching experience in both languages — not an English screen with translated labels added later.",
    projectTitle: "Project demo — please read before using",
    projectBody: "Rafeek is a capstone project using synthetic data only. It is not a diagnostic or medical-monitoring system, is not medical advice, and must not be used with real patient or care-recipient data.",
    ctaTitle: "Make room for the details that matter.",
    ctaBody: "A thoughtful place to record, review and keep the next step in your hands.",
    ctaCreate: "Create an account",
    ctaApk: "Download Android APK",
    footerCopy: "Everyday care, brought together.",
    repoLink: "GitHub repository",
    releasesLink: "Android releases",
    switchLabel: "العربية",
    switchAria: "Switch to Arabic",
    menuOpen: "Open navigation",
    menuClose: "Close navigation",
    brand: "Rafeek",
    brandAria: "Rafeek home",
    heroPhotoAlt: "A caregiver sharing a quiet conversation with an older woman at home in a sunlit Egyptian apartment",
    opensNewTab: " (opens in a new tab)",
    navAria: "Main navigation",
  },
  ar: {
    skip: "انتقل إلى المحتوى",
    brandTag: "رفيقك في تفاصيل الرعاية",
    navHow: "كيف يعمل",
    navNotes: "الملاحظات اليومية",
    navSafety: "السلامة",
    navLanguages: "اللغات",
    signIn: "تسجيل الدخول",
    eyebrow: "قليل من الوضوح، كل يوم",
    heroTitle: "تفاصيل صغيرة ترسم صورة أكبر لـ",
    heroAccent: "الرعاية.",
    heroBody: "مساحة هادئة لتسجيل تفاصيل كل يوم، والعودة إلى ما لاحظته، ومراجعة اقتراحات الرعاية — مع بقاء القرار لك دائمًا.",
    getStarted: "إنشاء حساب لمقدم الرعاية",
    heroTrust: "لتوثيق الملاحظات بعناية، وليس للتشخيص أو المراقبة الطبية.",
    photoCaption: "حتى اللحظات بين المتابعات اليومية تستحق أن نتذكرها.",
    sampleNote: "مثال لملاحظة يومية",
    today: "اليوم",
    mood: "المزاج",
    calm: "هادئ",
    appetite: "الشهية",
    fair: "مقبولة",
    sleep: "النوم",
    restful: "جيد",
    mobility: "الحركة",
    usual: "معتادة",
    sampleFreeText: "صباح هادئ وكوب شاي لطيف.",
    proofOne: "ملاحظات يومية",
    proofOneSub: "بيانات منظمة وكلماتك أنت",
    proofTwo: "7 أو 14 أو 30 يومًا",
    proofTwoSub: "ملخصات عند الحاجة",
    proofThree: "القرار لك دائمًا",
    proofThreeSub: "راجع الاقتراح أو عدّله أو استبعده",
    detailsKicker: "مساحة لتفاصيل كل يوم",
    detailsTitle: "كل يوم مكوّن من تفاصيل. اجمعها في مكان واحد.",
    detailsBody: "من المزاج والشهية إلى النوم والحركة، وصولًا إلى الملاحظات التي لا تناسب خانة محددة — سجّل ما لاحظته بطريقتك الطبيعية.",
    detailsQuote: "«ليست كل التفاصيل المهمة واضحة الأهمية في لحظتها».",
    moodSub: "كيف كان شعور اليوم",
    appetiteSub: "ملاحظات الطعام والشهية",
    sleepSub: "الراحة وجودة النوم",
    mobilitySub: "الحركة والنشاط اليومي",
    medication: "حالة تناول الدواء",
    medicationSub: "تم تناوله أو فات أو غير معروف",
    painFalls: "الألم والسقوط",
    painFallsSub: "ملاحظات مهمة تستحق التسجيل",
    howKicker: "خطوات أبسط",
    howTitle: "من ملاحظات صغيرة إلى صورة أوضح.",
    howBody: "يجمع تطبيق ملاحظات الرعاية اليومية التسجيل والمراجعة واختيار الخطوة التالية في مسار هادئ وواضح.",
    stepOneTitle: "دوّن ما حدث بطريقتك.",
    stepOneBody: "سجّل التفاصيل المنظمة وأضف ملاحظة بالعربية أو الإنجليزية أو بكلتيهما. تبقى الملاحظات الأصلية كما هي، وتُضاف التصحيحات بشكل منفصل.",
    stepTwoTitle: "ارجع إلى الأيام الماضية.",
    stepTwoBody: "أنشئ ملخصًا لملاحظاتك خلال 7 أو 14 أو 30 يومًا، وراجع الملاحظات والاتجاهات الظاهرة فيها.",
    stepThreeTitle: "اختر الخطوة التالية.",
    stepThreeBody: "راجع خطة الرعاية المقترحة. اقبلها أو عدّلها أولًا أو استبعدها. وتظل الإصدارات السابقة محفوظة.",
    summaryKicker: "سياق واضح، لا تخمين",
    summaryTitle: "من المهم أن تعرف الملاحظة مصدرها.",
    summaryBody: "ترتبط الملاحظات التي ينشئها الذكاء الاصطناعي بملاحظاتك والاقتباسات الداعمة لها. وتظل التفاصيل المتضاربة أو غير الواضحة موصوفة بأنها غير مؤكدة، دون ادعاء معرفة ما لا نعرفه.",
    tryWorkspace: "استكشف مساحة العمل",
    sampleSummary: "مثال توضيحي لملخص",
    sevenDay: "نظرة على 7 أيام",
    sevenDays: "آخر 7 أيام",
    summaryExample: "في الملاحظات المسجلة هذا الأسبوع، وُصفت الشهية بأنها أقل من المعتاد في يومين، بينما وُصف النوم بأنه جيد في معظم الأيام المسجلة.",
    quoteExample: "«تناولت طعامًا أقل قليلًا وقت الغداء، لكنها استمتعت بالشاي لاحقًا».",
    sourceLabel: "ملاحظة داعمة · مثال توضيحي",
    tagGrounded: "مرتبط بالملاحظات",
    tagUnclear: "يحافظ على عدم اليقين",
    tagReview: "بانتظار مراجعتك",
    summaryFoot: "محتوى توضيحي فقط — وليس تقييمًا طبيًا.",
    safetyKicker: "السلامة جزء من التصميم",
    safetyTitle: "الذكاء الاصطناعي يساعد، لكنه لا يتخذ القرار.",
    safetyBody: "تخضع اقتراحات الرعاية لقواعد ثابتة، وتُراجع في ضوء ملاحظاتك، وتظل دائمًا بانتظار قرارك.",
    safetyOneTitle: "التنبيهات تعتمد على قواعد ثابتة",
    safetyOneBody: "تُحسب مؤشرات مثل السقوط والألم الشديد وتكرار تفويت الدواء بواسطة الخادم، لا بواسطة الذكاء الاصطناعي.",
    safetyTwoTitle: "الأدوية محمية من التغيير",
    safetyTwoBody: "لا يستطيع الذكاء الاصطناعي إضافة الأدوية أو حذفها أو تغييرها أو تعديل جرعاتها في الخطة المحفوظة.",
    safetyThreeTitle: "مصدر المعلومة يظل ظاهرًا",
    safetyThreeBody: "ترتبط الملاحظات بمعرّفات الملاحظات والنصوص الداعمة التي يتحقق النظام من وجودها.",
    safetyFourTitle: "المعلومات غير المؤكدة تبقى كذلك",
    safetyFourBody: "لا تتحول التفاصيل المتضاربة أو غير الواضحة إلى عبارات جازمة.",
    safetyFiveTitle: "حفظ الملاحظات لا يتوقف",
    safetyFiveBody: "لا يعتمد حفظ الملاحظة على توفر الذكاء الاصطناعي.",
    safetySixTitle: "الوصول مرتبط بحساب مقدم الرعاية",
    safetySixBody: "تضمن فحوصات الخادم بقاء بيانات كل مقدم رعاية والأشخاص الذين يتابعهم ضمن حسابه فقط.",
    aiNoteTitle: "تنبيه بخصوص الذكاء الاصطناعي:",
    aiNoteBody: "تختار مزود الذكاء الاصطناعي وتستخدم مفتاح API الخاص بك. عند طلب ملخص أو خطة، يُرسل نص الملاحظات إلى المزود الذي اخترته كما كُتب، وقد يتضمن معلومات تكشف الهوية. يستخدم هذا النموذج بيانات اصطناعية فقط.",
    languageKicker: "اكتب باللغة الأقرب إليك",
    languageTitle: "العربية أو الإنجليزية أو كلتاهما في الملاحظة نفسها.",
    languageBody: "بدّل لغة الواجهة متى شئت. يدعم التطبيق اتجاه الكتابة من اليمين إلى اليسار، ويمكن كتابة الملاحظات بالعربية أو الإنجليزية أو بمزيج منهما.",
    languageFoot: "تجربة متكاملة باللغتين، وليست واجهة إنجليزية أُضيفت إليها ترجمات لاحقًا.",
    projectTitle: "نسخة تجريبية للمشروع — اقرأ قبل الاستخدام",
    projectBody: "ملاحظات الرعاية اليومية مشروع تخرّج يستخدم بيانات اصطناعية فقط. ليس نظامًا للتشخيص أو المراقبة الطبية، ولا يقدم نصيحة طبية، ويجب عدم استخدامه مع بيانات مرضى أو مستفيدين حقيقية.",
    ctaTitle: "امنح التفاصيل المهمة مكانًا واضحًا.",
    ctaBody: "مساحة مدروسة للتسجيل والمراجعة، مع بقاء الخطوة التالية بين يديك.",
    ctaCreate: "إنشاء حساب",
    ctaApk: "تنزيل تطبيق أندرويد",
    footerCopy: "نجمع تفاصيل الرعاية اليومية في مكان واحد.",
    repoLink: "مستودع GitHub",
    releasesLink: "إصدارات أندرويد",
    switchLabel: "English",
    switchAria: "التبديل إلى الإنجليزية",
    menuOpen: "فتح قائمة التنقل",
    menuClose: "إغلاق قائمة التنقل",
    brand: "رفيق",
    brandAria: "الرئيسية لرفيق",
    heroPhotoAlt: "مقدمة رعاية تتحدث بهدوء مع سيدة مسنة في منزل مصري مضاء بضوء الشمس",
    opensNewTab: " (يفتح في علامة تبويب جديدة)",
    navAria: "التنقل الرئيسي",
  },
};
