name: Calm Clinical Warmth
colors:
  surface: '#f2fbff'
  surface-dim: '#d0dce1'
  surface-bright: '#f2fbff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#eaf6fb'
  surface-container: '#e4f0f5'
  surface-container-high: '#deeaef'
  surface-container-highest: '#d8e4ea'
  on-surface: '#121d21'
  on-surface-variant: '#3f4947'
  inverse-surface: '#273236'
  inverse-on-surface: '#e7f3f8'
  outline: '#6f7978'
  outline-variant: '#bec9c7'
  surface-tint: '#0b6a65'
  primary: '#00514d'
  on-primary: '#ffffff'
  primary-container: '#0e6b66'
  on-primary-container: '#9ae9e2'
  inverse-primary: '#86d4ce'
  secondary: '#2c6863'
  on-secondary: '#ffffff'
  secondary-container: '#afebe5'
  on-secondary-container: '#306c67'
  tertiary: '#1c4a78'
  on-tertiary: '#ffffff'
  tertiary-container: '#386291'
  on-tertiary-container: '#c5ddff'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#a2f1ea'
  primary-fixed-dim: '#86d4ce'
  on-primary-fixed: '#00201e'
  on-primary-fixed-variant: '#00504c'
  secondary-fixed: '#b2eee8'
  secondary-fixed-dim: '#96d2cc'
  on-secondary-fixed: '#00201e'
  on-secondary-fixed-variant: '#0b4f4b'
  tertiary-fixed: '#d2e4ff'
  tertiary-fixed-dim: '#a1c9fe'
  on-tertiary-fixed: '#001c37'
  on-tertiary-fixed-variant: '#1b4976'
  background: '#f2fbff'
  on-background: '#121d21'
  surface-variant: '#d8e4ea'
typography:
  display:
    fontFamily: Inter
    fontSize: 28px
    fontWeight: '700'
    lineHeight: 36px
    letterSpacing: -0.02em
  title:
    fontFamily: Inter
    fontSize: 22px
    fontWeight: '700'
    lineHeight: 28px
    letterSpacing: -0.01em
  section:
    fontFamily: Inter
    fontSize: 17px
    fontWeight: '600'
    lineHeight: 24px
  body:
    fontFamily: Inter
    fontSize: 15px
    fontWeight: '400'
    lineHeight: 22px
  label:
    fontFamily: Inter
    fontSize: 13px
    fontWeight: '500'
    lineHeight: 18px
    letterSpacing: 0.01em
  caption:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 16px
  gutter-tablet: 24px
  margin: 16px
  margin-tablet: 24px
  space-xs: 4px
  space-sm: 8px
  space-md: 16px
  space-lg: 24px
  space-xl: 32px

Brand & Style
This design system is tailored for healthcare aides, nurses, and residential caregivers documenting daily care under demanding physical and emotional conditions. It mediates the boundary between clinical precision and emotional decompression. The visual posture rejects both the sterile, intimidating aesthetics of legacy electronic health records (EHR) and the trivializing softness of generic lifestyle apps.
The aesthetic fuses Tactile Modernism with Native Android Material 3 ergonomics. It prioritizes grounding tactile cues, calm focus, and rapid glanceability: - Atmosphere: Reassuring, unhurried, clean, and warm. Designed to reduce cognitive fatigue during 12-hour shifts. - Bi-directional Integrity: Fully responsive to both Left-to-Right (LTR) and Right-to-Left (RTL) reading flows, respecting Arabic and Latin typographical balance equally. - Urgency Scaffolding: Medical alerts, pain indicators, and missed notes command immediate situational awareness through soft, tinted container tiers rather than glaring alarms.
Colors
The palette grounds the caregiver through deep organic teals, low-strain paper tones, and functionally differentiated status containers.
Core Canvas & Surfaces
Canvas (#F7F6F3): A warm limestone foundation engineered to avoid the harsh blue glare of #FFFFFF in low-light residential rooms and night shifts.
Surface (#FAF9F5): Elevated card surface providing subtle luminous contrast against the canvas.
Ink (#1F2A2E): Deep slate teal for high-legibility primary typography, meeting strict WCAG AAA contrast ratios.
Muted (#5B6B70): Calibrated secondary slate for metadata, timestamps, and input hints.
Borders:
Border (#C5CDD1): Defined structural divider for inputs and active outlines.
BorderSoft (#E3E7EA): Whisper-quiet bounding line for nested cards and modular sections.
Interactive Gradients & Primary Actions
Primary actions use a focused vertical gradient from Primary (#0E6B66) down to PrimaryDeep (#0A4F4B), providing a subtle tactile press-depth.
Clinical Status & Semantic Tiers
Semantic states always pair high-contrast foreground text with soft, desaturated containers to prevent eye fatigue while preserving immediate triage clarity: - Danger (Falls, Pain Scale > 7, Safety Warnings): - Text/Icon: #8B2B25 - Container: #F7E3E1 - Warning (Missed Shift Notes, Pending Vitals): - Text/Icon: #7A4B00 - Container: #FBF0D9 - Success (Logged Notes, Administered Medications): - Text/Icon: #1F5C38 - Container: #E3F1E8 - Info (AI Shift Summaries, Care Plan Insights): - Text/Icon: #24507E - Container: #E8F0F9
Typography
Typography is calibrated for instantaneous parsing while walking, holding equipment, or navigating split-second handoffs.
Typeface Selection: Inter (for Latin scripts) coupled with IBM Plex Sans Arabic (for Arabic locales). Both typefaces share clear counterforms, tall x-heights, and robust horizontal rhythms.
RTL & Script Parity: In Arabic locale builds, weights map 1:1 (700 Bold for Display/Title, 600 Semi-Bold for Section, 500 Medium for Label, 400 Regular for Body/Caption). Line-height metrics are extended dynamically by +2px on body and section tiers in native Android layout passes to comfortably accommodate Arabic ascenders and descenders without clipping.
Tabular Figures: All timestamps, blood pressure measurements, dosages, and pain metrics must strictly use OpenType tabular numerals (tnum) to maintain vertical scanning alignment across tabular lists.
Layout & Spacing
The layout model is anchored around single-hand Android ergonomic zones (thumb reachability) with an 8-point baseline grid.
Mobile Grid (Compact & Medium Android Targets)
Fluid Layout: Continuous screen filling within bounds.
Columns: 4 columns on standard mobile (< 600dp), 8 columns on foldables and compact tablets (600dp - 840dp).
Margins & Gutters: Outer margin is set to 16px (margin) on mobile screens, stepping up to 24px (margin-tablet) on tablets. Column gutters default to 16px.
Vertical Rhythm: Content groupings flow with space-md (16px) between interactive stacked cards and space-xl (32px) between macro patient sections (e.g., Vitals vs. Shift Notes).
Bi-Directional Layout Rules (LTR & RTL)
All layout definitions enforce Android logical padding and margins (paddingStart, paddingEnd, marginStart, marginEnd).
Icons indicating temporal progression (e.g., chevron navigation, shift handover steps) flip horizontally under RTL; clinical indicators (e.g., stethoscope, pulse wave icons) maintain fixed orientation.
The standard Android Floating Action Button (FAB) or Primary Note Trigger rests anchored at marginEnd: 16px and marginBottom: 16px.
Elevation & Depth
Visual depth avoids cold, exaggerated drop shadows, opting instead for tactile ambient resting states reminiscent of stacked clinical cards.
Level 0 (Flat Canvas): Resting surface #F7F6F3.
Level 1 (Card Resting): Surface #FAF9F5 bounded by a 1px BorderSoft (#E3E7EA) outline and an ambient, warm-tinted shadow:
box-shadow: 0px 1px 3px rgba(31, 42, 46, 0.04), 0px 2px 6px rgba(31, 42, 46, 0.03)
Level 2 (Active/Pressed Card, Modal Sheets):
box-shadow: 0px 3px 8px rgba(31, 42, 46, 0.08), 0px 1px 2px rgba(31, 42, 46, 0.04)
Level 3 (Floating Action Button & Urgent Alerts):
box-shadow: 0px 4px 12px rgba(14, 107, 102, 0.16), 0px 2px 4px rgba(31, 42, 46, 0.06)
No elevated element relies on shadow alone; a BorderSoft or semantic container border guarantees visual separation in direct sunlight or high-contrast screen modes.
Shapes
The geometric architecture pairs broad, comforting card corners with ergonomic touchpoints:
Cards & Summary Panels: 20px radius. Softens dense medical data into approachable modules.
Buttons & Input Fields: 14px radius. Aligns with standard finger pads for comfortable tapping.
Chips & Quick-Tags: 9999px (Full Pill). Differentiates transient, selectable items from structural forms.
Bottom Sheets & Modal Surfaces: Top corners at 24px radius; bottom corners at 0px.
Components
Buttons
Primary Action (Log Note, Submit Assessment):
Height: 52px (optimal for gloved or hasty touch interaction).
Background: Gradient from Primary (#0E6B66) down to PrimaryDeep (#0A4F4B).
Text: #FFFFFF, 15px Semi-Bold.
Corner Radius: 14px.
Elevation: Level 1 ambient teal drop.
Secondary (Add Sub-task, Attach Vitals):
Height: 48px.
Background: #FAF9F5.
Border: 1px solid #C5CDD1.
Text: Ink (#1F2A2E), 15px Medium.
Destructive/Safety Alert Action:
Height: 48px.
Background: DangerContainer (#F7E3E1).
Text: Danger (#8B2B25), 15px Semi-Bold.
Border: 1px solid rgba(139, 43, 37, 0.2).
Chips
Status & Category Pills:
Height: 32px.
Padding: 0 14px.
Radius: Pill (9999px).
Selected State: Background Primary (#0E6B66), Text #FFFFFF.
Unselected State: Background #FAF9F5, Border 1px solid #C5CDD1, Text Muted (#5B6B70).
Cards & Care Summaries
Daily Care Record Card:
Background: Surface (#FAF9F5).
Border: 1px solid #E3E7EA.
Radius: 20px.
Padding: 16px.
Layout: Top header displaying resident/patient name (section font), status chip at trailing corner, followed by body notes (body font) and timestamp metadata (caption font).
Input Fields & Observation Notes
Text Area & Voice Dictation Input:
Background: #FAF9F5.
Border: 1px solid #C5CDD1.
Focus State: 2px solid #0E6B66.
Corner Radius: 14px.
Padding: 14px 16px.
Typography: Body (15px/22px).
Placeholder: Muted (#5B6B70).
Checkboxes & Radio Controls
Minimum bounding touch target: 48x48px with a visual box of 22x22px (radius: 6px).
Active State: Fill Primary (#0E6B66) with check icon in #FFFFFF.
Inactive State: Fill transparent with 1.5px solid #C5CDD1.
Specialized Domain Components
Critical Safety Banner (Danger Tier):
Visual: Uninterrupted surface band (#F7E3E1), 1px solid #8B2B25, radius 16px, padding 14px 16px.
Content: Leading urgent alert icon (#8B2B25), followed by bold incident classification (e.g., "Fall Detected - Unwitnessed") and immediate escalation CTA.
AI Daily Summary Banner (Info Tier):
Surface: InfoContainer (#E8F0F9) with soft edge 1px solid rgba(36, 80, 126, 0.15).
Content: Header paired with sparkle icon (#24507E), translating raw nurse notes into structured handover digests.
Pain Level & Mood Slider Scale:
Touch target height: 56px.
Visual track with discrete segmented notches, shifting dynamically from Calm Teal (#0E6B66) across Warning Amber (#7A4B00) to High Pain Alert (#8B2B25)
