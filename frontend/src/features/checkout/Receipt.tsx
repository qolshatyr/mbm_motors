import { useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router-dom';
import { fetchReceipt, forgetPendingKey, readPendingKey } from '../../shared/api/checkout';
import { ApiError } from '../../shared/api/http';
import { Icon } from '../../shared/ui/Icon';
import { Button } from '../../shared/ui/Button';
import { Notice } from '../../shared/ui/Feedback';
import { pickupText } from '../../shared/contacts';

export function Receipt() {
  const [params] = useSearchParams();
  const key = params.get('key') || '';
  const validKey = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(key);
  const result = useQuery({ queryKey: ['receipt', key], queryFn: ({ signal }) => fetchReceipt(key, signal), enabled: validKey, retry: false, refetchOnWindowFocus: false });
  useEffect(() => { if (result.isSuccess && readPendingKey() === key) forgetPendingKey(); }, [result.isSuccess, key]);
  return <><h1 className="page-title">Результат оформления</h1>{!validKey ? <Notice error>Ссылка на заявку неверна. Вернитесь к оформлению, чтобы проверить результат.</Notice> : result.isPending ? <div className="loading-state" role="status">Проверяем заявку…</div> : result.isError ? <Notice error><p>{result.error instanceof ApiError && result.error.status === 404 ? 'Заявка не найдена в текущей сессии. Возможно, сессия истекла или заявка ещё не сохранена.' : 'Не удалось проверить заявку. Повторите проверку после восстановления соединения.'}</p><Button onClick={() => void result.refetch()}>Проверить ещё раз</Button></Notice> : <section className="receipt-panel" aria-labelledby="receipt-title"><div className="receipt-heading"><span className="receipt-status-mark" aria-hidden="true"><Icon name="check" /></span><h2 id="receipt-title">{(import.meta.env.MODE === 'demo' && import.meta.env.VITE_DEMO === 'true') ? 'Демонстрация оформления завершена' : 'Заявка сохранена'}</h2></div><p>{result.data.message}</p><dl className="receipt-details"><div><dt>Номер заявки</dt><dd>{result.data.number}</dd></div><div><dt>Получение</dt><dd>{pickupText}</dd></div><div><dt>Количество товаров</dt><dd>{result.data.itemCount}</dd></div><div><dt>Сумма</dt><dd>{result.data.totalText}</dd></div></dl><p className="flow-note">{(import.meta.env.MODE === 'demo' && import.meta.env.VITE_DEMO === 'true') ? 'Это тестовый результат в вашей вкладке. Магазин его не получает; контакты не сохраняются.' : 'Статус заявки виден в разделе «Мои заявки». Продавец свяжется с вами по WhatsApp или телефону, подтвердит наличие и итоговую сумму; до подтверждения товар не резервируется.'}</p></section>}<div className="receipt-actions"><Link className="button button-primary" to="/search">Продолжить покупки</Link><Link className="button" to="/account">Мои заявки</Link></div></>;
}
