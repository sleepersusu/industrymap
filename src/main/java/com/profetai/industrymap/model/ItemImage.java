package com.profetai.industrymap.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * 品類節點的圖片（design D4）：爆炸圖、側視圖等，一個節點可有多張，以視角標籤區分。
 *
 * <p>只存物件儲存的位置（key 或 URL），二進位不進資料庫。本次不做上傳端點，
 * 先接受外部已存在的位置，讓資料層與讀取路徑先跑通。</p>
 *
 * <p>自然鍵為 {@code (item, viewLabel)}（design D3），對外定位全程不需要內部 id。</p>
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "item_image")
public class ItemImage extends ProvenanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    /** 視角標籤（爆炸圖、側視圖），自然鍵的一半，非空 */
    @Column(name = "view_label", nullable = false, length = 64)
    private String viewLabel;

    /** 物件儲存的 key 或 URL */
    @Column(name = "storage_key", nullable = false, length = 1024)
    private String storageKey;

    /** 原圖寬（像素），供前端估算版面；座標本身一律是相對比例，與此無關 */
    @Column(name = "width_px")
    private Integer widthPx;

    /** 原圖高（像素），用途同 {@link #widthPx} */
    @Column(name = "height_px")
    private Integer heightPx;
}
