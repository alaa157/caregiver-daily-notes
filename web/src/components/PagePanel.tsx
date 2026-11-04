import { useId, type ReactNode } from 'react';
import { Icon, type IconName } from './Icon';

export function PagePanel({ icon, eyebrow, title, description, children, aside }: {
  icon: IconName;
  eyebrow: string;
  title: string;
  description: string;
  children?: ReactNode;
  aside?: ReactNode;
}) {
  const titleId = useId();
  return (
    <section className={`page-panel container${aside ? ' page-panel--split' : ''}`} aria-labelledby={titleId}>
      {aside && <div className="panel-aside">{aside}</div>}
      <div className="panel-card">
        <span className="panel-icon"><Icon name={icon} size={30} /></span>
        <p className="eyebrow">{eyebrow}</p>
        <h1 id={titleId}>{title}</h1>
        <p className="panel-description">{description}</p>
        {children && <div className="panel-actions">{children}</div>}
      </div>
    </section>
  );
}
