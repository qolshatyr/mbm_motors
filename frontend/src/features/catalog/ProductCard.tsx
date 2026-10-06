import type { Product } from '../../shared/api/catalog';

export function ProductCard({ product, store }: { product: Product; store: string }) {
  const href = `/catalog/${encodeURIComponent(product.id)}?store=${encodeURIComponent(store)}`;
  return <article className="product-card">
    <img src={product.imageUrl ?? '/images/part-placeholder.svg'} alt="" className="product-image" loading="lazy" width="88" height="88" onError={(event) => { if (event.currentTarget.src.endsWith('/images/part-placeholder.svg')) { event.currentTarget.style.visibility = 'hidden'; } else { event.currentTarget.src = '/images/part-placeholder.svg'; } }} />
    <div className="product-copy"><p className="sku">{product.sku || 'Артикул не указан'}</p><h3><a href={href}>{product.name}</a></h3>{product.code && <p className="product-code">Код: {product.code}</p>}
      <div className="product-summary"><strong>{product.priceText}</strong><span>{product.stockText}</span></div>
    </div>
  </article>;
}
