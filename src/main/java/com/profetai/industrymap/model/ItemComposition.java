package com.profetai.industrymap.model;

import com.profetai.industrymap.enums.Necessity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * part-of 組成關係（design D3）：多對多且節點全站共用，
 * PCB 同時掛在主機板、汽車、家電之下時仍是同一筆 item 資料。
 *
 * <p>循環偵測在 service 層執行（design D10），DB 只擋自我循環。</p>
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "item_composition")
public class ItemComposition extends ProvenanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parent_item_id", nullable = false)
    private Item parentItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "child_item_id", nullable = false)
    private Item childItem;

    /** 必要性：品類層的組成並非總是成立（單速腳踏車沒有變速器） */
    @Enumerated(EnumType.STRING)
    @Column(name = "necessity", nullable = false, length = 32)
    private Necessity necessity;
}
