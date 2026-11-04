// Optional and unused by the web app; see ./DECISIONS.md#ds-10.
// OPTIONAL ONLY: Tailwind is not declared in the current web package.
// Use tokens.css as source of truth if Tailwind is adopted.
const tailwindTheme = {
  theme: {
    extend: {
      colors: {
        primary: 'var(--color-primary)', onPrimary: 'var(--color-on-primary)',
        primaryContainer: 'var(--color-primary-container)', onPrimaryContainer: 'var(--color-on-primary-container)',
        secondary: 'var(--color-secondary)', onSecondary: 'var(--color-on-secondary)',
        secondaryContainer: 'var(--color-secondary-container)', onSecondaryContainer: 'var(--color-on-secondary-container)',
        background: 'var(--color-background)', onBackground: 'var(--color-on-background)',
        surface: 'var(--color-surface)', onSurface: 'var(--color-on-surface)',
        surfaceVariant: 'var(--color-surface-variant)', onSurfaceVariant: 'var(--color-on-surface-variant)',
        outline: 'var(--color-outline)',
        caregiver: {
          primary: 'var(--caregiver-primary)', primarySoft: 'var(--caregiver-primary-soft)',
          ink: 'var(--caregiver-ink)', muted: 'var(--caregiver-muted)',
          border: 'var(--caregiver-border)', borderSoft: 'var(--caregiver-border-soft)',
          danger: 'var(--caregiver-danger)', dangerContainer: 'var(--caregiver-danger-container)',
          success: 'var(--caregiver-success)', successContainer: 'var(--caregiver-success-container)',
          info: 'var(--caregiver-info)', infoContainer: 'var(--caregiver-info-container)',
          warning: 'var(--caregiver-warning)', warningContainer: 'var(--caregiver-warning-container)'
        }
      },
      fontFamily: { app: ['IBM Plex Sans Arabic', 'sans-serif'], arabic: ['IBM Plex Sans Arabic', 'sans-serif'] },
      fontSize: {
        'display-large':['57px',{lineHeight:'64px',letterSpacing:'-.25px'}],
        'display-medium':['45px',{lineHeight:'52px'}], 'display-small':['36px',{lineHeight:'44px'}],
        'headline-large':['32px',{lineHeight:'40px'}], 'headline-medium':['28px',{lineHeight:'36px'}],
        'headline-small':['24px',{lineHeight:'32px'}], 'title-large':['22px',{lineHeight:'28px'}],
        'title-medium':['16px',{lineHeight:'24px',letterSpacing:'.15px'}], 'title-small':['14px',{lineHeight:'20px',letterSpacing:'.1px'}],
        'body-large':['16px',{lineHeight:'24px',letterSpacing:'.5px'}], 'body-medium':['14px',{lineHeight:'20px',letterSpacing:'.25px'}],
        'body-small':['12px',{lineHeight:'16px',letterSpacing:'.4px'}], 'label-large':['14px',{lineHeight:'20px',letterSpacing:'.1px'}],
        'label-medium':['12px',{lineHeight:'16px',letterSpacing:'.5px'}], 'label-small':['11px',{lineHeight:'16px',letterSpacing:'.5px'}]
      },
      spacing: { xs:'4px', sm:'8px', md:'16px', lg:'24px', xl:'32px', xxl:'48px',
        'card-gap':'12px', 'section-gap':'14px', 'screen-gutter':'16px', 'card-padding':'14px',
        'section-padding':'20px', 'alert-padding':'16px' },
      borderRadius: { control:'12px', card:'14px', popup:'16px', 'extra-large':'28px', pill:'999px' },
      minHeight: { 'touch-target':'48px', button:'48px', 'button-large':'56px', input:'52px' },
      height: { 'top-bar':'56px', 'bottom-nav':'64px', fab:'56px' },
      width: { 'icon-sm':'20px', 'icon-md':'24px', 'icon-lg':'30px' }
    }
  }
};
export default tailwindTheme;
