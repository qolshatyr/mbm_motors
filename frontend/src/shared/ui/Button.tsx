import type { ButtonHTMLAttributes } from 'react';
export function Button({ primary = false, className = '', ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { primary?: boolean }) {
  return <button className={`button ${primary ? 'button-primary' : ''} ${className}`} {...props} />;
}
