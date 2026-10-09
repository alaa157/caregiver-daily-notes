# Shared Components

All sizes are px on web and dp on Android. Use token values from `tokens.json`; do not introduce local color, spacing, type, or radius values.

## AppBar
- Size: 48 high.
- Typography: `title` style; textPrimary.
- Colors: background/surface according to screen surface.
- Radius: none.
- States: default; optional back button flips direction in RTL; optional trailing action. Interactive controls meet the 48 minimum target.

## PrimaryButton
- Size: 48 high; 56 high for full-width form buttons.
- Colors: primary fill, surface label; primaryPressed while pressed.
- Typography: label style, weight 600.
- Radius: md.
- States: default, pressed, disabled at 40% opacity, submitting (spinner only inside the button), success where applicable. Minimum touch target 48.

## SecondaryButton
- Size: same as PrimaryButton (48 high; 56 for full-width form buttons).
- Colors: surface background, border outline, textPrimary text.
- Radius: md.
- States: default, pressed, disabled. Minimum touch target 48.

## DangerOutlineButton
- Size: same as SecondaryButton.
- Colors: surface background, danger outline and danger text.
- Radius: md.
- States: default, pressed, disabled. Use for sign out only; do not use clinical alarm styling.

## Card
- Size: content-driven, with md (16) padding.
- Colors: surface background, textPrimary primary text, textSecondary secondary text, border for dividers.
- Radius: 14 (`component.cardRadius`).
- Elevation: `elevation.card`.
- States: default; selected/active states use token-defined styling only.

## Pill
- Size: 28 high.
- Colors: neutral uses surface/border/textPrimary; info uses info; warning uses warning; danger uses danger. Text/background combinations must meet 4.5:1 contrast.
- Radius: pill.
- Typography: label style.
- States: neutral, info, warning, danger. Do not use color alone to convey meaning; include a label.

## Avatar
- Size: 40 × 40 circle.
- Colors: primarySoft fill, primary text.
- Radius: pill.
- Content: initials only, no photos or illustrations.
- States: default; handle absent initials with a neutral fallback icon, never a person illustration.

## TextField
- Size: 52 high.
- Colors: surface background, border outline, textPrimary input, textSecondary hint, danger error text.
- Radius: md.
- Layout: label above the field.
- States: idle, focused, filled, disabled, error. Error text appears below the field and is provided through copy keys.

## MultilineField
- Size: minimum 120 high.
- Colors and radius: same as TextField.
- Direction: auto, supporting Arabic, English, or mixed text.
- States: idle, focused, filled, disabled, error.

## ChoiceRow
- Size: each option at least 48 high.
- Colors: neutral surface/border/textPrimary; selected primarySoft fill and primary outline.
- Radius: md.
- Content: option label plus check icon for selected state; never rely on color alone.
- States: unselected, selected, disabled.

## PainScale
- Size: values 0–10; each tap target at least 48 wide and 48 high. Wrap into two rows on narrow screens.
- Colors: border/surface for neutral, primarySoft and primary for selected. Danger may be used for the defined safety preview only.
- Radius: sm.
- States: unselected, selected, disabled; selected value is indicated with a check or other non-color cue.

## AlertSafety
- Size: content-driven with md padding.
- Colors: danger border; danger at 10% background opacity.
- Radius: md.
- Content: icon and exact copy supplied by backend via the relevant `alert.redFlag` copy key when applicable.
- States: active; cannot be dismissed while the flag is active. Do not infer or alter safety meaning.

## AlertInfo
- Size: content-driven with md padding.
- Colors: info border/accent and info at 10% background opacity.
- Radius: md.
- Content: informational copy, including `plan.disclaimer`.
- States: visible; no dismiss behavior unless separately specified by the screen.

## BottomNav
- Size: 72 high.
- Colors: primary for active item; textSecondary for inactive item; surface background; border divider.
- Radius: none.
- Content: five items—home, people, history, saved, settings—with labels always visible.
- States: active and inactive; layout and item order mirror correctly in RTL.

## FAB
- Size: extended button; height at least 48.
- Colors: primary fill, surface text/icon.
- Radius: pill.
- Content: icon plus label.
- States: default, pressed, disabled. Use only on Home and Care recipients.

## Skeleton
- Size: matches the content being loaded.
- Colors: neutral blocks using border.
- Radius: sm or md to match the placeholder shape.
- States: loading only. No full-screen spinners; a spinner is allowed only inside buttons.

## EmptyState
- Size: content-driven, centered within available content.
- Colors: textPrimary title, textSecondary supporting sentence, primary action.
- Radius: none.
- Content: icon without people, title, one sentence, primary action.
- States: empty.

## ErrorState
- Size: content-driven, inline where possible.
- Colors: textPrimary/textSecondary and border; danger only for error semantics as appropriate.
- Radius: md when contained.
- Content: `state.error` message and `action.retry` button.
- States: error; retry returns to loading or the relevant prior state.

## TimelineItem
- Size: content-driven with md padding.
- Colors: surface card, border divider, textPrimary/textSecondary.
- Radius: md.
- Elevation: `elevation.card`.
- Content: caption date label and note summary.
- States: default; tap opens note detail. Interactive target at least 48 high.
