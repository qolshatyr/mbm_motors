// "Formal 1c" logo from the design project: MBM with the "B" in brand red.
export function Logo({ tagline = true, dark = false }: { tagline?: boolean; dark?: boolean }) {
  return <span className={dark ? 'logo logo-dark' : 'logo'} role="img" aria-label="MBM AutoMotors">
    <span className="logo-word" aria-hidden="true"><span>M</span><span className="logo-b">B</span><span>M</span></span>
    <span className="logo-rule" aria-hidden="true" />
    <span className="logo-text" aria-hidden="true"><span>AUTOMOTORS</span>{tagline && <span>АВТОЗАПЧАСТИ</span>}</span>
  </span>;
}
