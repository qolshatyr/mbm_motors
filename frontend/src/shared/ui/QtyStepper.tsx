import { useEffect, useState } from 'react';

const valid = (value: string) => /^\d+$/.test(value) && Number(value) >= 1 && Number(value) <= 999;

/** − / number / + control. Typed values commit on Enter or blur. */
export function QtyStepper({ value, label, disabled, onChange, min = 1, size = 'md', canIncrease = true }: { value: number; label: string; disabled?: boolean; onChange: (next: number) => void; min?: number; size?: 'sm' | 'md' | 'lg'; canIncrease?: boolean }) {
  const [draft, setDraft] = useState(String(value));
  useEffect(() => setDraft(String(value)), [value]);
  function commit() {
    if (draft === String(value)) return;
    if (valid(draft)) onChange(Number(draft)); else setDraft(String(value));
  }
  return <div className={`stepper stepper-${size}`} role="group" aria-label={label}>
    <button type="button" aria-label={min === 0 && value <= 1 ? 'Убрать из корзины' : 'Уменьшить количество'} disabled={disabled || value <= min} onClick={() => onChange(value - 1)}>−</button>
    <input aria-label="Количество" type="text" inputMode="numeric" autoComplete="off" pattern="[0-9]*" maxLength={3} value={draft} disabled={disabled}
      onChange={(event) => setDraft(event.target.value.replace(/\D/g, ''))}
      onBlur={commit}
      onKeyDown={(event) => { if (event.key === 'Enter') { event.preventDefault(); commit(); } if (event.key === 'Escape') setDraft(String(value)); }} />
    <button type="button" aria-label="Увеличить количество" disabled={disabled || value >= 999 || !canIncrease} onClick={() => onChange(value + 1)}>+</button>
  </div>;
}
