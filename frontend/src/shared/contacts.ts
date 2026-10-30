/** Public contacts for site access and the warehouse. Change them here only. */
export const pickupText = 'Самовывоз со склада MBM, с. Райымбек, Карасайский район';

export type SalesContact = { name: string; text: string; tel: string; whatsapp: string };

export const salesContacts: SalesContact[] = [
  { name: 'Бирлес', text: '+7 701 585 0748', tel: 'tel:+77015850748', whatsapp: 'https://wa.me/77015850748' },
  { name: 'Мурат', text: '+7 777 724 7078', tel: 'tel:+77777247078', whatsapp: 'https://wa.me/77777247078' },
];

/** Warehouse point 43.182987, 76.751679 (2GIS: с. Райымбек, Карасайский район); no street address. */
export const warehouseMapUrl = 'https://www.google.com/maps/search/?api=1&query=43.182987%2C76.751679';
