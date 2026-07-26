package com.profetai.industrymap.model;

import com.profetai.industrymap.enums.PeriodType;
import com.profetai.industrymap.enums.ShareMetric;
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

import java.math.BigDecimal;

/**
 * 市佔率（design D7）：必帶期間、地區、口徑三個維度，缺一則數字沒有意義。
 *
 * <p>刻意不外鍵至 {@link CompanyItemRole}——市佔率資料常先於角色關係到達。
 * 唯一鍵含來源，讓互相衝突的數值並存，由使用者判斷。</p>
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "market_share")
public class MarketShare extends ProvenanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 16)
    private PeriodType periodType;

    /** 期間值，搭配 periodType 使用：YEAR 為 "2024"、QUARTER 為 "2024Q3" */
    @Column(name = "period_value", nullable = false, length = 16)
    private String periodValue;

    /** 地區，例：全球、台灣 */
    @Column(name = "region", nullable = false, length = 64)
    private String region;

    /** 口徑：營收或出貨量 */
    @Enumerated(EnumType.STRING)
    @Column(name = "metric", nullable = false, length = 16)
    private ShareMetric metric;

    /** 市佔百分比，0–100 */
    @Column(name = "share_percent", nullable = false, precision = 6, scale = 3)
    private BigDecimal sharePercent;
}
