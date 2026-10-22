import { Link } from 'react-router-dom';
import type { Product } from '../../shared/api/catalog';
import { AddControl, PartImage, productHref, stockTone } from './AddControl';

export function ProductRow({ product, storeId, inCart }: { product: Product; storeId: string; inCart: number }) {
  const unknown = product.price === null || product.price <= 0;
  return <li className="product-row">
    <Link to={productHref(product.id)} tabIndex={-1} aria-hidden="true" className="product-row-media"><PartImage src={product.imageUrl} className="part-image-row" /></Link>
    <div className="product-row-copy">
      <p className="product-codes">{product.sku ? <span className="code">{product.sku}</span> : <span>Артикул не указан</span>}{product.code && <span>Код {product.code}</span>}{product.brand && <span className="product-brand">{product.brand}</span>}</p>
      <h3><Link to={productHref(product.id)}>{product.name}</Link></h3>
      <p className={`stock ${stockTone(product)}`}>{product.stockText}</p>
    </div>
    <div className="product-row-buy">
      <p className={`price ${unknown ? 'price-unknown' : ''}`}>{unknown ? <>Цена уточняется</> : <><span className="sr-only">Оптовая цена: </span>{product.priceText}{product.unit && <span className="price-unit"> / {product.unit}</span>}</>}</p>
      <AddControl product={product} storeId={storeId} inCart={inCart} />
    </div>
  </li>;
}

export function ProductTile({ product, storeId, inCart }: { product: Product; storeId: string; inCart: number }) {
  const unknown = product.price === null || product.price <= 0;
  return <li className="product-tile">
    <Link to={productHref(product.id)} tabIndex={-1} aria-hidden="true"><PartImage src={product.imageUrl} className="part-image-tile" /></Link>
    <p className="product-codes">{product.sku ? <span className="code">{product.sku}</span> : <span>Артикул не указан</span>}</p>
    <h3><Link to={productHref(product.id)}>{product.name}</Link></h3>
    <p className={`stock ${stockTone(product)}`}>{product.stockText}</p>
    <div className="product-tile-buy"><p className={`price ${unknown ? 'price-unknown' : ''}`}>{unknown ? 'Цена уточняется' : product.priceText}</p><AddControl size="sm" product={product} storeId={storeId} inCart={inCart} /></div>
  </li>;
}
