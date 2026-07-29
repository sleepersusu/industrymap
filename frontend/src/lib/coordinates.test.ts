import { describe, expect, it } from 'vitest';
import { toRelativePoint } from './coordinates';

describe('toRelativePoint', () => {
  it('以圖片邊界把螢幕座標換算成 0–1 相對座標，並捨入到小數四位', () => {
    const point = toRelativePoint(150, 75, {
      left: 100,
      top: 50,
      width: 300,
      height: 200,
    });

    expect(point).toEqual({ x: 0.1667, y: 0.125 });
  });

  it('把圖片外的座標限制在邊界內', () => {
    expect(
      toRelativePoint(20, 400, {
        left: 100,
        top: 50,
        width: 200,
        height: 100,
      }),
    ).toEqual({ x: 0, y: 1 });
  });

  it('圖片寬高為零時回傳有限的邊界值，不讓 NaN 進入 polygon', () => {
    const point = toRelativePoint(100, 50, {
      left: 100,
      top: 50,
      width: 0,
      height: 0,
    });

    expect(point).toEqual({ x: 0, y: 0 });
    expect(Number.isNaN(point.x)).toBe(false);
    expect(Number.isNaN(point.y)).toBe(false);
  });
});
