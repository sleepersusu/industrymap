import { describe, expect, it } from 'vitest';
import { toSvgPoints, unwrap } from './api';

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
