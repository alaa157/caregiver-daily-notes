import type { ButtonHTMLAttributes } from 'react';
import { Link, type LinkProps } from 'react-router-dom';

type Variant = 'primary' | 'secondary' | 'text';

function buttonClass(variant: Variant, className: string) {
  return `button button--${variant} ${className}`.trim();
}

export function Button({ variant = 'primary', className = '', type = 'button', ...props }:
  ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant }) {
  return <button className={buttonClass(variant, className)} type={type} {...props} />;
}

export function ButtonLink({ variant = 'primary', className = '', ...props }:
  LinkProps & { variant?: Variant }) {
  return <Link className={buttonClass(variant, className)} {...props} />;
}
