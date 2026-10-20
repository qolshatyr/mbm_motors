import type { Product, Store } from '../shared/api/catalog';

export const demoStores: Store[] = [{ id: 'dddddddddddddddddddd0003', name: 'СКЛАД (демо)' }];
export const demoStore = demoStores[0];

type Image = 'filter' | 'brake' | 'suspension' | 'light' | 'oil' | 'body';
export interface DemoProduct extends Omit<Product, 'price' | 'canAdd' | 'availableQuantity'> { price: number; stock: number }
// Prototype assortment from the v2 design. Prices and stock are illustrative only.
const rows: [string, string, string, Image, number, number][] = [
  ['Амортизатор передний левый', '54651-2T000', '0004812', 'suspension', 42800, 6],
  ['Амортизатор передний правый', '54661-2T000', '0004813', 'suspension', 42800, 4],
  ['Стойка амортизатора в сборе', '54610-2T000', '0004790', 'suspension', 61500, 2],
  ['Колодки тормозные передние', '58101-D7A00', '0002204', 'brake', 18900, 14],
  ['Фильтр масляный', '26300-35505', '0000117', 'filter', 2900, 86],
  ['Фильтр воздушный', '28113-D3300', '0000342', 'filter', 6400, 23],
  ['Фильтр салонный', '97133-D1000', '0000389', 'filter', 5200, 31],
  ['Свеча зажигания', '18849-11051', '0001055', 'light', 3100, 120],
  ['Ремень приводной поликлиновой', '25212-2G710', '0001630', 'body', 9800, 7],
  ['Стойка стабилизатора передняя', '54830-D3000', '0004911', 'suspension', 7600, 12],
  ['Масло моторное 5W-30, 4 л', '05100-00451', '0003001', 'oil', 24500, 40],
  ['Рычаг передний нижний левый', '54500-D3000', '0004950', 'suspension', 38200, 0],
  ['Датчик ABS передний правый', '95670-D3000', '0005402', 'light', 0, 3],
  ['Диск тормозной передний', '51712-D7000', '0002250', 'brake', 27400, 8],
];
export const money = (value: number) => `${value.toLocaleString('ru-RU')} ₸`;
export const demoProducts: DemoProduct[] = rows.map(([name, sku, code, image, price, stock], index) => ({
  id: (index + 1).toString(16).padStart(24, '0'), name, sku, code, unit: 'шт.', imageUrl: `${import.meta.env.BASE_URL}demo-images/${image}.svg`,
  priceText: price > 0 ? money(price) : 'Цена уточняется', stockText: stock > 0 ? `В наличии: ${stock} шт.` : 'Нет в наличии', price, stock,
}));
export const demoPartner = { login: 'demo', company: 'СТО «Демо Сервис»', contactName: 'Ерлан', phone: '+77000000000' };
