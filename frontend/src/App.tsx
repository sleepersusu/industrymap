import { useCallback, useEffect, useState } from 'react';
import { ExplodedView } from './components/ExplodedView';
import { SupplierPanel } from './components/SupplierPanel';
import {
  fetchEndProducts,
  fetchComponents,
  fetchItemImages,
  fetchSuppliers,
  createItemHotspot,
  type ComponentNode,
  type Hotspot,
  type HotspotPoint,
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
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState<{ imageId: number; points: HotspotPoint[] } | null>(null);
  const [pending, setPending] = useState<{ imageId: number; points: HotspotPoint[] } | null>(null);
  const [components, setComponents] = useState<ComponentNode[]>([]);
  const [positionLabel, setPositionLabel] = useState('');
  const [childItemId, setChildItemId] = useState<number | null>(null);
  const [editorMessage, setEditorMessage] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    fetchEndProducts()
      .then((page) => {
        setProducts(page.content);
        setProductId((current) => current ?? page.content[0]?.id ?? null);
      })
      .catch((cause: Error) => setError(cause.message));
  }, []);

  const loadImages = useCallback((itemId: number, includeDrafts: boolean) => {
    setError(null);
    return fetchItemImages(itemId, includeDrafts)
      .then(setImages)
      .catch((cause: Error) => setError(cause.message));
  }, []);

  useEffect(() => {
    if (productId === null) {
      return;
    }
    // 換成品時把下鑽結果一併清掉，否則畫面會留著上一台車的零件
    setHotspot(null);
    setSuppliers([]);
    setDraft(null);
    setPending(null);
    void loadImages(productId, editing);
  }, [productId, editing, loadImages]);

  useEffect(() => {
    if (!editing || productId === null) {
      setComponents([]);
      return;
    }
    fetchComponents(productId)
      .then((root) => {
        setComponents(root.children);
        setChildItemId((current) =>
          root.children.some((child) => child.itemId === current)
            ? current
            : root.children[0]?.itemId ?? null,
        );
      })
      .catch((cause: Error) => setEditorMessage(cause.message));
  }, [editing, productId]);

  const completeDraft = useCallback(() => {
    if (!draft || draft.points.length < 3) {
      setEditorMessage('多邊形至少需要三個頂點，請再點選圖片。');
      return;
    }
    setPending(draft);
    setDraft(null);
    setEditorMessage(null);
  }, [draft]);

  useEffect(() => {
    if (!editing || pending) {
      return;
    }
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Enter') {
        event.preventDefault();
        completeDraft();
      } else if (event.key === 'Escape') {
        setDraft(null);
        setEditorMessage(null);
      } else if (event.key === 'Backspace') {
        event.preventDefault();
        setDraft((current) =>
          current ? { ...current, points: current.points.slice(0, -1) } : null,
        );
        setEditorMessage(null);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [completeDraft, editing, pending]);

  const selectHotspot = useCallback((selected: Hotspot) => {
    setHotspot(selected);
    setLoadingSuppliers(true);
    setError(null);
    fetchSuppliers(selected.childItemId)
      .then(setSuppliers)
      .catch((cause: Error) => setError(cause.message))
      .finally(() => setLoadingSuppliers(false));
  }, []);

  const addPoint = (imageId: number, point: HotspotPoint) => {
    setPending(null);
    setEditorMessage(null);
    setDraft((current) => ({
      imageId,
      points: current?.imageId === imageId ? [...current.points, point] : [point],
    }));
  };

  const submitHotspot = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!pending || childItemId === null || positionLabel.trim() === '') {
      setEditorMessage('位置標籤與對應的品類節點都是必填欄位。');
      return;
    }
    setSaving(true);
    setEditorMessage(null);
    try {
      await createItemHotspot({
        itemImageId: pending.imageId,
        childItemId,
        positionLabel: positionLabel.trim(),
        polygon: pending.points,
        provenance: { sourceType: 'MANUAL' },
      });
      setPending(null);
      setPositionLabel('');
      if (productId !== null) {
        await loadImages(productId, true);
      }
    } catch (cause) {
      setEditorMessage(cause instanceof Error ? cause.message : String(cause));
    } finally {
      setSaving(false);
    }
  };

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
        <label className="editor-toggle">
          <input
            type="checkbox"
            checked={editing}
            onChange={(event) => {
              setEditing(event.target.checked);
              setDraft(null);
              setPending(null);
              setEditorMessage(null);
            }}
          />
          編輯熱區
        </label>
      </header>

      {error && <p className="error banner">{error}</p>}

      <main>
        <section className="canvas">
          {editing && (
            <div className="editor-help">
              點圖片新增頂點；Enter 或雙擊完成；Esc 取消；Backspace 移除上一點。
              {editorMessage && <p className="error">{editorMessage}</p>}
            </div>
          )}
          {images.length === 0 ? (
            <p className="hint">這個成品還沒有掛任何圖片。</p>
          ) : (
            images.map((image) => (
              <ExplodedView
                key={image.id}
                image={image}
                selectedHotspotId={hotspot?.id ?? null}
                onSelect={selectHotspot}
                editing={editing}
                draftPoints={draft?.imageId === image.id ? draft.points : []}
                onAddPoint={(point) => addPoint(image.id, point)}
                onComplete={completeDraft}
              />
            ))
          )}
          {pending && (
            <form className="hotspot-form" onSubmit={submitHotspot}>
              <h2>新增熱區</h2>
              <label>
                位置標籤
                <input
                  required
                  value={positionLabel}
                  onChange={(event) => setPositionLabel(event.target.value)}
                  placeholder="例如：前煞車"
                />
              </label>
              <label>
                對應的品類節點
                <select
                  required
                  value={childItemId ?? ''}
                  onChange={(event) => setChildItemId(Number(event.target.value))}
                >
                  {components.map((component) => (
                    <option key={component.itemId} value={component.itemId}>
                      {component.displayName}
                    </option>
                  ))}
                </select>
              </label>
              <div className="form-actions">
                <button type="button" onClick={() => setPending(null)}>取消</button>
                <button type="submit" disabled={saving || components.length === 0}>
                  {saving ? '儲存中…' : '儲存熱區'}
                </button>
              </div>
            </form>
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
