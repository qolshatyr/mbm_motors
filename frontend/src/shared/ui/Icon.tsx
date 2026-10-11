import type { CSSProperties } from 'react';

type Name = 'search' | 'cart' | 'arrow' | 'filter' | 'close' | 'check' | 'box' | 'location' | 'minus' | 'plus';
const paths: Record<Name, string> = {
  search: 'M21 21l-5-5M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0',
  cart: 'M2 3h3l3 13h11l3-9H6M10 21h.01M18 21h.01',
  arrow: 'M4 12h16m-6-6 6 6-6 6',
  filter: 'M4 7h16M4 17h16M8 4v6M16 14v6',
  close: 'm6 6 12 12M6 18 18 6',
  check: 'm5 12 4 4L19 6',
  box: 'm3 7 9-5 9 5v10l-9 5-9-5V7m0 0 9 5 9-5M12 12v10M7 4l9 5',
  location: 'M20 10c0 6-8 12-8 12S4 16 4 10a8 8 0 1 1 16 0M15 10a3 3 0 1 1-6 0 3 3 0 0 1 6 0',
  minus: 'M5 12h14',
  plus: 'M5 12h14M12 5v14',
};
export function Icon({ name, className = '', style }: { name: Name; className?: string; style?: CSSProperties }) {
  return <svg className={`icon ${className}`} style={style} width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={paths[name]} /></svg>;
}
