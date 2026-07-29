import type { HotspotPoint } from './api';

interface ImageRect {
  left: number;
  top: number;
  width: number;
  height: number;
}

const clamp = (value: number) => Math.min(1, Math.max(0, value));
const round = (value: number) => Math.round(value * 10_000) / 10_000;

/**
 * 以圖片本身而非外層容器換算座標，避免 caption 或其他版面高度讓熱區位移。
 *
 * 圖片尚未排版完成時寬高可能為零；此時回到左上角比產生 NaN 安全，
 * 因為 NaN 一旦送進 SVG 或後端便很難指出是哪次點擊造成。
 */
export function toRelativePoint(clientX: number, clientY: number, rect: ImageRect): HotspotPoint {
  const x = rect.width === 0 ? 0 : (clientX - rect.left) / rect.width;
  const y = rect.height === 0 ? 0 : (clientY - rect.top) / rect.height;

  return {
    x: round(clamp(x)),
    y: round(clamp(y)),
  };
}
