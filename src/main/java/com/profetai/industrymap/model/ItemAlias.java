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
 * 零件同義詞（design D9）：WiFi 模組／無線網卡／WLAN Module 應指向同一節點，
 * 否則節點碎裂會讓跨產業查詢與市佔率失真。
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "item_alias")
public class ItemAlias extends ProvenanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    /** 正規化別名，全域唯一 */
    @Column(name = "normalized_alias", nullable = false, length = 255)
    private String normalizedAlias;

    /** 顯示用別名，保留原始寫法 */
    @Column(name = "display_alias", nullable = false, length = 255)
    private String displayAlias;
}
