import { Link } from 'react-router-dom';
import type { Product } from '../../shared/api/catalog';
import { Icon } from '../../shared/ui/Icon';

export function ProductCard({ product, store }: { product: Product; store: string }) {
  const href = `/products/${encodeURIComponent(product.id)}?store=${encodeURIComponent(store)}`;
  return <article className="product-card">
    <Link to={href} className="product-media" aria-label={`Посмотреть ${product.name}`} tabIndex={-1}>
      <img src={product.imageUrl ?? '/images/part-placeholder.svg'} alt="" className="product-image" loading="lazy" width="240" height="160" onError={(event) => { if (event.currentTarget.src.endsWith('/images/part-placeholder.svg')) { event.currentTarget.style.visibility = 'hidden'; } else { event.currentTarget.src = '/images/part-placeholder.svg'; } }} />
    </Link>
    <div className="product-copy"><div className="product-meta"><p className="sku">{product.sku || 'Артикул не указан'}</p>{product.code && <p className="product-code">Код: {product.code}</p>}</div><h3><Link to={href}>{product.name}</Link></h3>
      <div className="product-summary"><strong>{product.priceText}</strong><span>{product.stockText}</span></div>
      <Link to={href} className="product-card-action"><span>Цена и наличие</span><Icon name="arrow" /></Link>
    </div>
  </article>;
}
