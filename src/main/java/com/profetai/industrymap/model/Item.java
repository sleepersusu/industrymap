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
 * 產業地圖的節點：產品與零件合併為單一遞迴實體（design D1），每個節點代表一個品類（design D2）。
 *
 * <p>part-of 組成關係另存於 {@link ItemComposition}；本類別的 {@code parentCategory}
 * 只表達 is-a 細分類型（車用 PCB is-a PCB），兩者刻意分離（design D4）。</p>
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "item")
public class Item extends ProvenanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 正規化名稱，全站唯一，由 NameNormalizer 產生 */
    @Column(name = "normalized_name", nullable = false, length = 255)
    private String normalizedName;

    /** 顯示用名稱，保留使用者輸入的原始寫法 */
    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    /** 是否為終端成品（腳踏車為是、PCB 為否），供反向查詢判斷回溯終點 */
    @Column(name = "is_end_product", nullable = false)
    private boolean isEndProduct;

    /** is-a 上層品類，至多一個；null 表示本身即為頂層品類 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_category_id")
    private Item parentCategory;
}
