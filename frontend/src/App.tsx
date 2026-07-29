import { useCallback, useEffect, useState } from 'react';
import { ExplodedView } from './components/ExplodedView';
import { SupplierPanel } from './components/SupplierPanel';
import {
  fetchEndProducts,
  fetchItemImages,
  fetchSuppliers,
  type Hotspot,
  type Item,
  type ItemImage,
  type Supplier,
} from './lib/api';
import './App.css';

/**
 * 產業地圖的進入點：選一個終端成品 → 看它的爆炸圖 → 點零件 → 看供應公司。
 *
 * 成品清單來自 `GET /api/products`，呼叫端因此不需要事先知道任何內部 id。
 */
export default function App() {
  const [products, setProducts] = useState<Item[]>([]);
  const [productId, setProductId] = useState<number | null>(null);
  const [images, setImages] = useState<ItemImage[]>([]);
  const [hotspot, setHotspot] = useState<Hotspot | null>(null);
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [loadingSuppliers, setLoadingSuppliers] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchEndProducts()
      .then((page) => {
        setProducts(page.content);
        setProductId((current) => current ?? page.content[0]?.id ?? null);
      })
      .catch((cause: Error) => setError(cause.message));
  }, []);

  useEffect(() => {
    if (productId === null) {
      return;
    }
    // 換成品時把下鑽結果一併清掉，否則畫面會留著上一台車的零件
    setHotspot(null);
    setSuppliers([]);
    fetchItemImages(productId)
      .then(setImages)
      .catch((cause: Error) => setError(cause.message));
  }, [productId]);

  const selectHotspot = useCallback((selected: Hotspot) => {
    setHotspot(selected);
    setLoadingSuppliers(true);
    setError(null);
    fetchSuppliers(selected.childItemId)
      .then(setSuppliers)
      .catch((cause: Error) => setError(cause.message))
      .finally(() => setLoadingSuppliers(false));
  }, []);

  return (
    <div className="app">
      <header className="app-header">
        <h1>產業地圖</h1>
        <select
          value={productId ?? ''}
          onChange={(event) => setProductId(Number(event.target.value))}
          aria-label="選擇終端成品"
        >
          {products.map((product) => (
            <option key={product.id} value={product.id}>
              {product.displayName}
            </option>
          ))}
        </select>
      </header>

      {error && <p className="error banner">{error}</p>}

      <main>
        <section className="canvas">
          {images.length === 0 ? (
            <p className="hint">這個成品還沒有掛任何圖片。</p>
          ) : (
            images.map((image) => (
              <ExplodedView
                key={image.id}
                image={image}
                selectedHotspotId={hotspot?.id ?? null}
                onSelect={selectHotspot}
              />
            ))
          )}
        </section>

        <SupplierPanel
          hotspot={hotspot}
          suppliers={suppliers}
          loading={loadingSuppliers}
          error={error}
        />
      </main>
    </div>
  );
}
