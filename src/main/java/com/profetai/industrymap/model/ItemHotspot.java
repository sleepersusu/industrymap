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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

/**
 * 圖片上的可點擊區域（design D2）：綁一張圖、指向一個品類節點，並帶位置標籤與多邊形座標。
 *
 * <p>位置標籤讓同一條組成關係在同一張圖上對應多個熱區（前煞車／後煞車）——
 * 「腳踏車有煞車」是組成關係的事實，不因車上有兩個煞車而變成兩筆；
 * 「左下角那塊是前煞車」是圖的事實，因此標籤屬於這裡而非 {@link ItemComposition}。</p>
 *
 * <p>自然鍵為 {@code (itemImage, positionLabel)}，<b>刻意不含 {@link #childItem}</b>（design D3）。</p>
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "item_hotspot")
public class ItemHotspot extends ProvenanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_image_id", nullable = false)
    private ItemImage itemImage;

    /** 這塊區域對應的品類節點；點擊後即由此往下查供應公司 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "child_item_id", nullable = false)
    private Item childItem;

    /** 位置標籤（前煞車／後煞車），自然鍵的一半，非空 */
    @Column(name = "position_label", nullable = false, length = 64)
    private String positionLabel;

    /**
     * 多邊形頂點集，至少三點，座標為 0–1 的相對比例（design D5）。
     *
     * <p>以 JSONB 持久化，走 Hibernate 內建的 JSON 對應（Jackson 已在 classpath 上），
     * 不另外引入 JSON 型別套件。點數與範圍 DB 表達不了，驗證在 payload 與 service 兩層。</p>
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "polygon", nullable = false, columnDefinition = "jsonb")
    private List<HotspotPoint> polygon;
}
