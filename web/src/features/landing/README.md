# Public landing page

`LandingRoute.tsx` owns the `/` route presentation. English and Arabic visitor copy lives in its `copy` resources, selected through the existing `I18nProvider`; direction, document language, and language persistence remain owned by that provider. RTL layout uses logical properties and the shared design tokens.

The current illustrations are CSS-rendered synthetic UI mockups rather than captured application screenshots. To replace them with real screenshots, capture synthetic accounts only with the existing Playwright setup at desktop/mobile widths in both languages, sanitize the outputs, optimize them as local assets, and update `MockScreen` with meaningful localized alt text.

Run the component test with `npm test -- LandingRoute.test.tsx`. Run browser and accessibility suites with `npm run test:e2e`.
