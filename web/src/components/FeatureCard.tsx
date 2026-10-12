import { Icon, type IconName } from './Icon';

export function FeatureCard({ icon, title, description, status, available = false }: {
  icon: IconName;
  title: string;
  description: string;
  status: string;
  available?: boolean;
}) {
  return (
    <article className="feature-card">
      <div className="feature-card-top">
        <span className="feature-icon"><Icon name={icon} size={24} /></span>
        <span className={`feature-status${available ? ' feature-status--available' : ''}`}>
          {available && <span className="status-dot" aria-hidden="true" />}{status}
        </span>
      </div>
      <h3>{title}</h3>
      <p>{description}</p>
    </article>
  );
}
