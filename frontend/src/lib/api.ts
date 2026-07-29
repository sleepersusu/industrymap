/**
 * 後端 API 的型別與存取層。
 *
 * 後端所有成功回應都包在 `ServerResponse<T>` 裡，錯誤則帶 `message` 與非 2xx 狀態碼。
 * 這裡把拆封集中在一處，畫面只拿得到 data 或例外，不必每支呼叫各判斷一次。
 */

const API_BASE = import.meta.env.VITE_API_BASE ?? 'http://localhost:8080';

export interface ServerResponse<T> {
  success: boolean;
  data: T | null;
  message: string | null;
}

/** 熱區多邊形的一個頂點，座標為相對於圖片寬高的比例（0–1） */
export interface HotspotPoint {
  x: number;
  y: number;
}

export interface Hotspot {
  id: number;
  itemImageId: number;
  /** 這塊區域對應的品類節點，點擊後由此往下查供應公司 */
  childItemId: number;
  childDisplayName: string;
  /** 同一張圖上多個熱區可指向同一節點（前煞車／後煞車），靠這個標籤區分 */
  positionLabel: string;
  polygon: HotspotPoint[];
  reviewStatus: string;
}

export interface ItemImage {
  id: number;
  itemId: number;
  viewLabel: string;
  /** 物件儲存的 URL，後端不存二進位 */
  storageKey: string;
  widthPx: number | null;
  heightPx: number | null;
  reviewStatus: string;
  hotspots: Hotspot[];
}

export interface Supplier {
  /** 公司顯示名稱；欄位名是 companyName，不是 displayName */
  companyName: string;
  /** 對外識別：主要代號的交易所限定形式（TWSE:2330），未上市公司為正規化名稱 */
  companyReference: string;
  companyRole: string;
  reviewStatus: string;
  sourceType: string;
}

export interface Item {
  id: number;
  displayName: string;
  normalizedName: string;
  endProduct: boolean;
  reviewStatus: string;
}

export interface ComponentNode {
  itemId: number;
  displayName: string;
  children: ComponentNode[];
}

export interface CreateHotspotInput {
  itemImageId: number;
  childItemId: number;
  positionLabel: string;
  polygon: HotspotPoint[];
  provenance: {
    sourceType: 'MANUAL';
  };
}

/**
 * 取出 data；失敗時以後端訊息拋錯。
 *
 * 刻意不回傳 null：後端的錯誤訊息是有意義的（查無此節點、座標不合法），
 * 吞掉它畫面只會變成一片空白，除錯時完全看不出發生什麼事。
 */
export function unwrap<T>(response: ServerResponse<T>): T {
  if (!response.success || response.data === null) {
    throw new Error(response.message ?? 'API 回應失敗且未提供訊息');
  }
  return response.data;
}

/**
 * 把相對座標串成 SVG `<polygon>` 的 points。
 *
 * 不做任何縮放：`<svg viewBox="0 0 1 1">` 之下相對座標本身就是繪圖座標，
 * 圖片放大縮小、換裝置都不需要重算——這正是後端把座標定成 0–1 比例的用意。
 */
export function toSvgPoints(polygon: HotspotPoint[]): string {
  return polygon.map((point) => `${point.x},${point.y}`).join(' ');
}

async function get<T>(path: string): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`);
  const body = (await response.json()) as ServerResponse<T>;
  return unwrap(body);
}

async function post<T>(path: string, input: unknown): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  });
  const body = (await response.json()) as ServerResponse<T>;
  return unwrap(body);
}

/** 品類節點的圖片與其熱區——一次呼叫拿完，畫一張可互動的圖不需要第二次往返 */
export function fetchItemImages(itemId: number, includeDrafts = false): Promise<ItemImage[]> {
  const query = includeDrafts ? '?includeDrafts=true' : '';
  return get<ItemImage[]>(`/api/items/${itemId}/images${query}`);
}

/** 某個零件的供應公司；查無資料時後端回空陣列而非 404 */
export function fetchSuppliers(itemId: number): Promise<Supplier[]> {
  return get<Supplier[]>(`/api/items/${itemId}/suppliers`);
}

export function fetchItem(itemId: number): Promise<Item> {
  return get<Item>(`/api/items/${itemId}`);
}

/** 終端成品列表——地圖的進入點，呼叫端不需事先知道任何 id */
export function fetchEndProducts(): Promise<{ content: Item[] }> {
  return get<{ content: Item[] }>('/api/products?size=20');
}

/** 編輯器只需要下一層候選，限制 depth 可避免為一個下拉選單載入整棵品類樹 */
export function fetchComponents(itemId: number): Promise<ComponentNode> {
  return get<ComponentNode>(`/api/products/${itemId}/components?depth=1`);
}

/** 建立熱區集中走存取層，才能完整保留後端（例如 409）的可修正訊息 */
export function createItemHotspot(input: CreateHotspotInput): Promise<Hotspot> {
  return post<Hotspot>('/api/item-hotspots', input);
}
