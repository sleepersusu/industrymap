import type { Hotspot, Supplier } from '../lib/api';

interface Props {
  hotspot: Hotspot | null;
  suppliers: Supplier[];
  loading: boolean;
  error: string | null;
}

const ROLE_LABELS: Record<string, string> = {
  DESIGN: '設計',
  MANUFACTURE: '製造',
  ASSEMBLY: '代工組裝',
  BRAND: '品牌',
  PACKAGING_TESTING: '封測',
};

/**
 * 點選熱區後的下鑽結果：這個零件由哪些公司供應。
 *
 * 空清單與「還沒選」是兩種不同的狀態，畫面必須分得出來——後端對「這個零件沒有供應商」
 * 回的是空陣列而非 404，若兩者都顯示成一片空白，使用者會以為是壞掉。
 */
export function SupplierPanel({ hotspot, suppliers, loading, error }: Props) {
  if (!hotspot) {
    return (
      <aside className="panel">
        <p className="hint">點圖上的任一個零件，看它由哪些公司供應。</p>
      </aside>
    );
  }

  return (
    <aside className="panel">
      <header>
        <h2>{hotspot.childDisplayName}</h2>
        {/* 位置標籤與零件名稱刻意分開顯示：前煞車與後煞車是同一個品類節點，
            只印零件名稱的話，使用者會以為自己點錯了 */}
        <p className="position">圖上位置：{hotspot.positionLabel}</p>
      </header>

      {loading && <p className="hint">查詢中…</p>}
      {error && <p className="error">{error}</p>}

      {!loading && !error && suppliers.length === 0 && (
        <p className="hint">這個零件目前沒有登錄任何供應公司。</p>
      )}

      {!loading && !error && suppliers.length > 0 && (
        <ul className="suppliers">
          {suppliers.map((supplier) => (
            <li key={`${supplier.companyReference}-${supplier.companyRole}`}>
              <span className="name">{supplier.companyName}</span>
              <span className="role">{ROLE_LABELS[supplier.companyRole] ?? supplier.companyRole}</span>
              <span className="code">{supplier.companyReference}</span>
            </li>
          ))}
        </ul>
      )}
    </aside>
  );
}
