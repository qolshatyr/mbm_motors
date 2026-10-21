import type { Classification, Product, Store } from '../shared/api/catalog';

export const demoStores: Store[] = [{ id: 'dddddddddddddddddddd0003', name: 'СКЛАД (демо)' }];
export const demoStore = demoStores[0];

export interface DemoProduct extends Omit<Product, 'price' | 'canAdd' | 'availableQuantity' | 'brand'> { price: number; stock: number; classification: Classification }
// Prototype assortment from the v2 design, photos from the v4 design. Prices and stock are illustrative only.
const rows: [string, string, string, number, number][] = [
  ['Амортизатор передний левый', '54651-2T000', '0004812', 42800, 6],
  ['Амортизатор передний правый', '54661-2T000', '0004813', 42800, 4],
  ['Стойка амортизатора в сборе', '54610-2T000', '0004790', 61500, 2],
  ['Колодки тормозные передние', '58101-D7A00', '0002204', 18900, 14],
  ['Фильтр масляный', '26300-35505', '0000117', 2900, 86],
  ['Фильтр воздушный', '28113-D3300', '0000342', 6400, 23],
  ['Фильтр салонный', '97133-D1000', '0000389', 5200, 31],
  ['Свеча зажигания', '18849-11051', '0001055', 3100, 120],
  ['Ремень приводной поликлиновой', '25212-2G710', '0001630', 9800, 7],
  ['Стойка стабилизатора передняя', '54830-D3000', '0004911', 7600, 12],
  ['Масло моторное 5W-30, 4 л', '05100-00451', '0003001', 24500, 40],
  ['Рычаг передний нижний левый', '54500-D3000', '0004950', 38200, 0],
  ['Датчик ABS передний правый', '95670-D3000', '0005402', 0, 3],
  ['Диск тормозной передний', '51712-D7000', '0002250', 27400, 8],
];
// Section, subsection, brand and fitting cars per row, in the shape of the CloudShop tags.
const tags: [string, string, string, [string, string[]][]][] = [
  ['Ходовая часть', 'Амортизаторы и опоры', 'Mobis', [['Hyundai', ['Tucson']], ['Kia', ['Sportage']]]],
  ['Ходовая часть', 'Амортизаторы и опоры', 'Mobis', [['Hyundai', ['Tucson']], ['Kia', ['Sportage']]]],
  ['Ходовая часть', 'Амортизаторы и опоры', 'Mando', [['Hyundai', ['Sonata']]]],
  ['Тормозная система', 'Колодки', 'Hyundai/Kia', [['Hyundai', ['Sonata']], ['Kia', ['Optima']]]],
  ['Фильтры и обслуживание', 'Масляные фильтры', 'Hyundai/Kia', [['Hyundai', ['Tucson', 'Elantra']], ['Kia', ['Rio']]]],
  ['Фильтры и обслуживание', 'Воздушные фильтры', 'Parts-Mall', [['Hyundai', ['Tucson']]]],
  ['Фильтры и обслуживание', 'Салонные фильтры', 'Sangsin', [['Hyundai', ['Tucson']], ['Kia', ['Sportage']]]],
  ['Электрика и освещение', 'Зажигание', 'Hyundai/Kia', [['Kia', ['Rio']], ['Hyundai', ['Accent']]]],
  ['Двигатель', 'ГРМ и приводные ремни', 'Mobis', [['Hyundai', ['Elantra']]]],
  ['Ходовая часть', 'Стойки и втулки стабилизатора', 'Sangsin', [['Hyundai', ['Elantra']], ['Kia', ['Cerato']]]],
  ['Масла и технические жидкости', 'Моторные масла', 'Hyundai/Kia', []],
  ['Ходовая часть', 'Рычаги и шаровые', 'Mobis', [['Hyundai', ['Elantra']]]],
  ['Электрика и освещение', 'Датчики', 'Mobis', [['Hyundai', ['Elantra']]]],
  ['Тормозная система', 'Диски и барабаны', 'Mando', [['Hyundai', ['Sonata']], ['Kia', ['Optima']]]],
];
export const money = (value: number) => `${value.toLocaleString('ru-RU')} ₸`;
export const demoProducts: DemoProduct[] = rows.map(([name, sku, code, price, stock], index) => ({
  id: (index + 1).toString(16).padStart(24, '0'), name, sku, code, unit: 'шт.', imageUrl: `${import.meta.env.BASE_URL}demo-images/p${String(index + 1).padStart(2, '0')}.jpg`,
  priceText: price > 0 ? money(price) : 'Цена уточняется', stockText: stock > 0 ? `В наличии: ${stock} шт.` : 'Нет в наличии', price, stock,
  classification: { section: tags[index][0], subsection: tags[index][1], brands: [tags[index][2]], fitment: tags[index][3].map(([make, models]) => ({ make, models })) },
}));
export const demoPartner = { login: 'demo', company: 'СТО «Демо Сервис»', contactName: 'Ерлан', phone: '+77000000000' };
