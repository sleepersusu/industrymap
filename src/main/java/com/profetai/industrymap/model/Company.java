package com.profetai.industrymap.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * 公司主檔。代號一律不放在本表——未上市公司（例：SRAM）必須能正常建立，
 * 且多地掛牌的公司會有多筆代號，改由 {@link CompanyIdentifier} 承載（design D5）。
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "company")
public class Company extends ProvenanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 正規化名稱，全站唯一 */
    @Column(name = "normalized_name", nullable = false, length = 255)
    private String normalizedName;

    /** 顯示用名稱 */
    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    /** 所屬國家 */
    @Column(name = "country", length = 64)
    private String country;

    /** 是否為公開發行公司 */
    @Column(name = "is_public", nullable = false)
    private boolean isPublic;
}
