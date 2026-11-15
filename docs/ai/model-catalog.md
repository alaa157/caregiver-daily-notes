# AI model catalog and data-use rules

This document is the single source for the data-use rules behind the AI
model catalogs in both apps. The web and Android work must quote it
rather than re-deriving the rules from code comments.

## Data-use rules

1. For Gemini, data use follows the API key's billing tier, not the
   model. A free-tier key may have its content used by Google to
   improve its products. A key from a project with billing enabled does
   not.
2. An OpenRouter model whose id ends in `:free` keeps its host's data
   policy, and some hosts train on what they receive.
3. Paid models are listed first. No free model is a default or a
   recommendation.
4. A custom model id must match `^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$`.
5. Both apps must show the notices below, and must require
   acknowledgement before sending notes to a provider in the Gemini
   and free-host cases above.

## Notices

### Gemini

- English: Google may use content sent with a free-tier key to improve
  its products. A key from a project with billing turned on is not used
  this way. The model you pick does not change this.
- العربية: قد تستخدم Google المحتوى المُرسل بمفتاح من الفئة المجانية
  لتحسين منتجاتها. لا يحدث هذا مع مفتاح من مشروع مفعّل فيه الفوترة.
  اختيار النموذج لا يغيّر ذلك.

### Free host

- English: A free model keeps its host's data policy, and some hosts
  train on what they receive.
- العربية: يحتفظ النموذج المجاني بسياسة بيانات مضيفه، وبعض المضيفين
  يدرّبون نماذجهم على ما يتلقونه.

### Acknowledgement

- English: I understand that my care notes may be used by the provider
  to improve its products.
- العربية: أفهم أن مقدّم الخدمة قد يستخدم ملاحظات الرعاية لتحسين
  منتجاته.

## Where the catalogs live

- Web: `web/src/features/ai/modelCatalog.ts`
- Android: `android/app/src/main/java/com/caregiver/mobile/ai/AiModelCatalog.kt`

A model id present in both catalogs must carry the same data-use
treatment.
