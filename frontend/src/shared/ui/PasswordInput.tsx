import { useState, type InputHTMLAttributes } from 'react';
import { Icon } from './Icon';

/** Password field with a show/hide toggle so the user can check what they typed. */
export function PasswordInput(props: Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> & { id: string }) {
  const [visible, setVisible] = useState(false);
  return <div className="password-input">
    <input {...props} type={visible ? 'text' : 'password'} autoCapitalize="none" spellCheck={false} />
    <button type="button" className="password-toggle" onClick={() => setVisible((value) => !value)}
      aria-controls={props.id} aria-pressed={visible} aria-label={visible ? 'Скрыть пароль' : 'Показать пароль'} title={visible ? 'Скрыть пароль' : 'Показать пароль'}>
      <Icon name={visible ? 'eye-off' : 'eye'} />
    </button>
  </div>;
}
