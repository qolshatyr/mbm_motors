import { Link } from 'react-router-dom';

const categories = [
  { name: 'Фильтры', query: 'фильтр', image: 'filter', caption: 'Воздух, масло, салон' },
  { name: 'Тормозная система', query: 'тормоз', image: 'brake', caption: 'Диски и колодки' },
  { name: 'Подвеска', query: 'амортизатор', image: 'suspension', caption: 'Опоры и амортизаторы' },
  { name: 'Свет и электрика', query: 'фара', image: 'light', caption: 'Фары и лампы' },
  { name: 'Масла и жидкости', query: 'масло', image: 'oil', caption: 'Для обслуживания' },
  { name: 'Кузовные детали', query: 'бампер', image: 'body', caption: 'Детали кузова' },
];

export function DemoCategories() {
  return <section className="demo-categories" aria-labelledby="category-heading"><div className="demo-section-heading"><h2 id="category-heading">Что ищем сегодня?</h2><span>Разделы демонстрационного каталога</span></div><div className="demo-category-grid">{categories.map((category) => <Link key={category.image} to={`/?category=${category.image}`} className="demo-category"><img src={`/demo-images/${category.image}.svg`} alt="" width="150" height="110" /><div><h3>{category.name}</h3><p>{category.caption}</p></div></Link>)}</div></section>;
}

export function DemoGuide() {
  return <section className="demo-guide"><div><h2>От поиска до заявки</h2><p>Попробуйте весь сценарий покупки — без регистрации.</p></div><ol><li><strong>Найдите деталь</strong><span>Введите название или демонстрационный артикул.</span></li><li><strong>Выберите точку</strong><span>Откройте карточку и сравните пример цены и наличия.</span></li><li><strong>Оформите заявку</strong><span>Добавьте товар в корзину и проверьте форму.</span></li></ol><p className="demo-disclaimer">Это прототип. Цены, остатки и точки условные. Заявки не отправляются в магазин.</p></section>;
}

export function DemoInformation({ page }: { page: 'purchase' | 'pickup' | 'about' }) {
  const content = {
    purchase: { title: 'Как оформить заявку', lead: 'Понятный путь от нужной детали до обращения в магазин.', body: 'Найдите товар по названию или артикулу. В карточке выберите одну демонстрационную точку, добавьте нужное количество в корзину и перейдите к оформлению.', note: 'В рабочей версии менеджер подтверждает цену, наличие и возможность выдачи. Правила подтверждения, оплаты и резервирования ещё согласуются с владельцем магазина.' },
    pickup: { title: 'Самовывоз', lead: 'Выбор места получения — до оформления заявки.', body: 'В прототипе доступны условные точки А, Б и склад. Они показывают, как покупатель сможет сравнивать наличие и цены. Это не реальные адреса MBM Motors.', note: 'Адреса, часы работы, контакты и выдача товаров со склада появятся после согласования с клиентом.' },
    about: { title: 'MBM Motors', lead: 'Магазин автозапчастей с удобным поиском и прозрачным оформлением.', body: 'Этот сайт показывает будущий интерфейс: каталог, поиск, карточку детали, корзину и заявку. Демонстрационные иллюстрации не являются фотографиями конкретных товаров.', note: 'Реальные реквизиты, контакты, ассортимент и условия покупки будут добавлены после подтверждения владельцем. Подбор по автомобилю и VIN не включён без достоверной базы совместимости.' },
  }[page];
  return <section className="demo-information"><h1>{content.title}</h1><p className="demo-lead">{content.lead}</p><p>{content.body}</p><div className="notice">{content.note}</div><Link to="/" className="button button-primary">Перейти в каталог</Link>{page === 'purchase' && <DemoGuide />}</section>;
}

export function DemoFooter() {
  return <div className="demo-footer-grid"><div><strong>MBM Motors</strong><p>Автозапчасти. Поиск. Выбор. Заявка.</p></div><nav aria-label="Информация о магазине"><Link to="/how-to-order">Как оформить заявку</Link><Link to="/pickup">Самовывоз</Link><Link to="/about">О магазине</Link></nav><p>Демонстрационный вариант для согласования.<br />Рабочие контакты и условия будут добавлены после утверждения.</p></div>;
}
