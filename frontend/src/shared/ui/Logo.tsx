// "Formal 1c" logo from the design project: a hex nut replaces the "B" in MBM.
export function NutMark({ className }: { className?: string }) {
  return <svg className={className} viewBox="0 0 100 100" aria-hidden="true" focusable="false"><path d="M50 4 L90 27 L90 73 L50 96 L10 73 L10 27 Z" fill="currentColor" stroke="currentColor" strokeWidth="4" strokeLinejoin="round" /><circle cx="50" cy="50" r="18" className="logo-nut-hole" /></svg>;
}

export function Logo({ tagline = true }: { tagline?: boolean }) {
  return <span className="logo">
    <span className="logo-word" aria-hidden="true"><span>M</span><NutMark className="logo-nut" /><span>M</span></span>
    <span className="logo-rule" aria-hidden="true" />
    <span className="logo-text"><span>AUTOMOTORS</span>{tagline && <span>АВТОЗАПЧАСТИ</span>}</span>
  </span>;
}
