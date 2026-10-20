import type { Product, Store } from '../shared/api/catalog';

export const demoStores: Store[] = [
  { id: 'dddddddddddddddddddd0001', name: 'Демо · Точка А' },
  { id: 'dddddddddddddddddddd0002', name: 'Демо · Точка Б' },
  { id: 'dddddddddddddddddddd0003', name: 'Демо · Склад' },
];

export const demoCategories = [
  { id: 'filter', name: 'Фильтры', image: '/demo-images/filter.svg', query: 'фильтр' },
  { id: 'brake', name: 'Тормозная система', image: '/demo-images/brake.svg', query: 'тормозн' },
  { id: 'suspension', name: 'Подвеска', image: '/demo-images/suspension.svg', query: 'подвеск' },
  { id: 'light', name: 'Освещение', image: '/demo-images/light.svg', query: 'лампа' },
  { id: 'oil', name: 'Масла и жидкости', image: '/demo-images/oil.svg', query: 'масло' },
  { id: 'body', name: 'Кузовные детали', image: '/demo-images/body.svg', query: 'кузов' },
] as const;

type Category = typeof demoCategories[number]['id'];
export interface DemoProduct extends Product { category: Category; price: number; stocks: number[] }
const names: Record<Category, string[]> = {
  filter: ['Фильтр масляный', 'Фильтр воздушный', 'Фильтр салонный', 'Фильтр топливный'],
  brake: ['Колодки тормозные передние', 'Диск тормозной', 'Колодки тормозные задние', 'Шланг тормозной'],
  suspension: ['Амортизатор подвески', 'Рычаг подвески', 'Стойка стабилизатора подвески', 'Опора амортизатора подвески'],
  light: ['Фара головного света', 'Лампа габаритного света', 'Лампа противотуманная', 'Лампа сигнала торможения'],
  oil: ['Масло моторное 5W-30, 1 л', 'Масло моторное 5W-40, 4 л', 'Масло трансмиссионное, 1 л', 'Жидкость тормозная, 0,5 л'],
  body: ['Зеркало кузовное боковое', 'Крепление кузовного бампера', 'Решётка кузовная передняя', 'Накладка кузовного порога'],
};
const basePrices = [3500, 16500, 21500, 2400, 6500, 8900];
export const money = (value: number) => `${value.toLocaleString('ru-RU')} ₸`;
export const demoProducts: DemoProduct[] = demoCategories.flatMap((category, group) => names[category.id].map((name, index) => {
  const position = group * 4 + index + 1;
  const price = basePrices[group] + index * 900;
  return { id: position.toString(16).padStart(24, '0'), name, sku: `DEMO-${category.id.slice(0, 1).toUpperCase()}${String(position).padStart(3, '0')}`, code: `DEMO-${String(position).padStart(4, '0')}`, unit: 'шт.', priceText: money(price), stockText: 'Демонстрационный остаток', imageUrl: category.image, category: category.id, price, stocks: [position % 7 === 0 ? 0 : 4 + position % 8, position % 5 === 0 ? 0 : 2 + position % 5, 10 + position % 16] };
}));

export function demoOffer(product: DemoProduct, storeId: string) {
  const storeIndex = demoStores.findIndex((store) => store.id === storeId);
  return { price: product.price + (storeIndex === 1 ? 300 : 0), stock: product.stocks[storeIndex] ?? 0 };
}
