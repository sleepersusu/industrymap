import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  createItemHotspot,
  fetchComponents,
  fetchItemImages,
  toSvgPoints,
  unwrap,
} from './api';

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('toSvgPoints', () => {
  it('把 0–1 的相對座標串成 SVG polygon 的 points 字串', () => {
    const points = toSvgPoints([
      { x: 0.1, y: 0.2 },
      { x: 0.5, y: 0.2 },
      { x: 0.5, y: 0.6 },
    ]);

    expect(points).toBe('0.1,0.2 0.5,0.2 0.5,0.6');
  });

  it('不做任何縮放——viewBox 為 0 0 1 1，相對座標即是繪圖座標', () => {
    // 這正是後端把座標定成相對比例的用意：換圖或換裝置都不必重算
    expect(toSvgPoints([{ x: 0, y: 0 }, { x: 1, y: 0 }, { x: 1, y: 1 }])).toBe('0,0 1,0 1,1');
  });
});

describe('unwrap', () => {
  it('成功時取出 data', () => {
    expect(unwrap({ success: true, data: [1, 2], message: null })).toEqual([1, 2]);
  });

  it('失敗時以後端訊息拋錯，不讓 null 悄悄流進畫面', () => {
    // 後端錯誤一律帶 message（查無此節點、座標不合法…），吞掉它畫面只會變成空白
    expect(() => unwrap({ success: false, data: null, message: '查無此品類節點：99' }))
      .toThrowError('查無此品類節點：99');
  });

  it('失敗且沒有訊息時仍要拋錯', () => {
    expect(() => unwrap({ success: false, data: null, message: null })).toThrowError();
  });
});

describe('熱區編輯 API', () => {
  it('編輯模式查圖片時明確要求包含草稿', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      json: () => Promise.resolve({ success: true, data: [], message: null }),
    });
    vi.stubGlobal('fetch', fetchMock);

    await fetchItemImages(12, true);

    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/items/12/images?includeDrafts=true',
    );
  });

  it('只取得成品下一層的品類節點', async () => {
    const data = { itemId: 12, displayName: '自行車', children: [] };
    const fetchMock = vi.fn().mockResolvedValue({
      json: () => Promise.resolve({ success: true, data, message: null }),
    });
    vi.stubGlobal('fetch', fetchMock);

    await expect(fetchComponents(12)).resolves.toEqual(data);
    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/products/12/components?depth=1',
    );
  });

  it('以指定格式建立人工熱區，並保留後端 409 訊息原文', async () => {
    const input = {
      itemImageId: 8,
      childItemId: 21,
      positionLabel: '前煞車',
      polygon: [
        { x: 0.1, y: 0.2 },
        { x: 0.3, y: 0.2 },
        { x: 0.2, y: 0.4 },
      ],
      provenance: { sourceType: 'MANUAL' as const },
    };
    const fetchMock = vi.fn().mockResolvedValue({
      json: () =>
        Promise.resolve({
          success: false,
          data: null,
          message: '位置標籤「前煞車」已存在',
        }),
    });
    vi.stubGlobal('fetch', fetchMock);

    await expect(createItemHotspot(input)).rejects.toThrow('位置標籤「前煞車」已存在');
    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/item-hotspots',
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(input),
      },
    );
  });
});
