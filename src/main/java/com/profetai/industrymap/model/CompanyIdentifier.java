package com.profetai.industrymap.model;

import com.profetai.industrymap.enums.IdentifierType;
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
 * 公司代號（design D5）：台積電同時有 TWSE 2330 與 NYSE TSM，
 * 而 SRAM 一個都沒有——因此代號是獨立實體而非 company 上的欄位。
 *
 * <p>{@code isPrimary} 每家公司至多一筆，日後接股價時直接取這筆，明確知道要抓哪個市場。</p>
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "company_identifier")
public class CompanyIdentifier extends ProvenanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(name = "identifier_type", nullable = false, length = 32)
    private IdentifierType identifierType;

    /** 代號值，與類型合併後全域唯一 */
    @Column(name = "identifier_value", nullable = false, length = 64)
    private String identifierValue;

    /** 是否為該公司的主要識別碼，每家公司至多一筆 */
    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary;
}
