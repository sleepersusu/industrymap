import { useRef, type MouseEvent } from 'react';
import type { Hotspot, HotspotPoint, ItemImage } from '../lib/api';
import { toSvgPoints } from '../lib/api';
import { toRelativePoint } from '../lib/coordinates';

interface Props {
  image: ItemImage;
  selectedHotspotId: number | null;
  onSelect: (hotspot: Hotspot) => void;
  editing: boolean;
  draftPoints: HotspotPoint[];
  onAddPoint: (point: HotspotPoint) => void;
  onComplete: () => void;
}

/**
 * 爆炸圖與其熱區。
 *
 * 熱區以 SVG 疊在圖片上，`viewBox="0 0 1 1"` 讓後端的 0–1 相對座標直接當繪圖座標用，
 * 不必知道圖片實際尺寸，也不需在視窗縮放時重算。`preserveAspectRatio="none"` 讓 SVG
 * 跟著 `<img>` 一起被拉伸，兩者的形變才會一致。
 *
 * 同一個 `childItemId` 可能出現多次（前煞車／後煞車），因此 key 用熱區自己的 id，
 * 不能用 childItemId。
 */
export function ExplodedView({
  image,
  selectedHotspotId,
  onSelect,
  editing,
  draftPoints,
  onAddPoint,
  onComplete,
}: Props) {
  const imageRef = useRef<HTMLImageElement>(null);

  const addPoint = (event: MouseEvent<SVGSVGElement>) => {
    if (!editing || event.detail > 1 || !imageRef.current) {
      return;
    }
    onAddPoint(toRelativePoint(event.clientX, event.clientY, imageRef.current.getBoundingClientRect()));
  };

  return (
    <figure className="exploded-view">
      {/* img 與 svg 必須共用一個定位容器，caption 留在外面——
          否則 svg 的 inset:0 會連 caption 的高度一起蓋進去，熱區整體被往下拉伸 */}
      <div className="stage">
        <img ref={imageRef} src={image.storageKey} alt={image.viewLabel} />
        <svg
          viewBox="0 0 1 1"
          preserveAspectRatio="none"
          role="group"
          aria-label={editing ? '熱區編輯畫布' : '零件熱區'}
          className={editing ? 'editing' : ''}
          onClick={addPoint}
          onDoubleClick={editing ? onComplete : undefined}
        >
          {image.hotspots.map((hotspot) => (
            <polygon
              key={hotspot.id}
              className={hotspot.id === selectedHotspotId ? 'hotspot selected' : 'hotspot'}
              points={toSvgPoints(hotspot.polygon)}
              onClick={(event) => {
                event.stopPropagation();
                if (!editing) {
                  onSelect(hotspot);
                }
              }}
              role="button"
              tabIndex={0}
              aria-label={`${hotspot.positionLabel}（${hotspot.childDisplayName}）`}
              onKeyDown={(event) => {
                if (event.key === 'Enter' || event.key === ' ') {
                  event.preventDefault();
                  onSelect(hotspot);
                }
              }}
            >
              <title>
                {hotspot.positionLabel} → {hotspot.childDisplayName}
              </title>
            </polygon>
          ))}
          {draftPoints.length > 0 && (
            <>
              <polyline className="draft-line" points={toSvgPoints(draftPoints)} />
              {draftPoints.map((point, index) => (
                <circle
                  key={`${index}-${point.x}-${point.y}`}
                  className="draft-point"
                  cx={point.x}
                  cy={point.y}
                  r="0.009"
                />
              ))}
            </>
          )}
        </svg>
      </div>
      <figcaption>
        {image.viewLabel}．{image.hotspots.length} 個熱區
        {image.reviewStatus !== 'VERIFIED' && <span className="badge">{image.reviewStatus}</span>}
      </figcaption>
    </figure>
  );
}
