import type { ReactNode } from 'react';

const glyphs = {
  'arrow-right': <path d="M5 12h14m-6-6 6 6-6 6" />,
  'arrow-left': <path d="M19 12H5m6-6-6 6 6 6" />,
  globe: <><circle cx="12" cy="12" r="9" /><path d="M3 12h18M12 3c5 5 5 13 0 18-5-5-5-13 0-18Z" /></>,
  leaf: <><path d="M20 4c-9-2-16 3-16 9a6 6 0 0 0 6 6c6 0 11-6 10-15Z" /><path d="m4 21 12-12" /></>,
  journal: <><rect x="5" y="3" width="15" height="18" rx="3" /><path d="M9 3v18M3 7h4m-4 5h4m-4 5h4m6-9h4m-4 4h4" /></>,
  shield: <><path d="m12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6l8-3Z" /><path d="m8 12 3 3 5-6" /></>,
  info: <><circle cx="12" cy="12" r="9" /><path d="M12 11v6m0-10v.01" /></>,
  'alert-circle': <><circle cx="12" cy="12" r="9" /><path d="M12 7v6m0 4v.01" /></>,
  inbox: <><path d="M4 4h16l2 11v5H2v-5L4 4Z" /><path d="M2 15h6l2 3h4l2-3h6" /></>,
  refresh: <path d="M20 7a9 9 0 1 0 1 8M20 3v5h-5" />,
  lock: <><rect x="5" y="10" width="14" height="11" rx="2" /><path d="M8 10V7a4 4 0 0 1 8 0v3m-4 5v2" /></>,
} satisfies Record<string, ReactNode>;

export type IconName = keyof typeof glyphs;

/** Decorative icons always accompany a text label or an accessible name. */
export function Icon({ name, size = 20, className = '' }: {
  name: IconName;
  size?: number;
  className?: string;
}) {
  return (
    <svg className={`icon ${className}`} width={size} height={size} viewBox="0 0 24 24"
      fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round"
      strokeLinejoin="round" aria-hidden="true" focusable="false">
      {glyphs[name]}
    </svg>
  );
}
