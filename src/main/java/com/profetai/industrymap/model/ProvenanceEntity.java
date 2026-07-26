package com.profetai.industrymap.model;

import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.enums.SourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 所有內容表共用的來源與審核欄位群（design D8）。
 *
 * <p>資料以 AI 生成初稿 + 人工審核方式進來，市佔率數字尤其是幻覺重災區；
 * 這組欄位若第一版沒有，之後補上等於既有資料全部無從追溯。以 {@code @MappedSuperclass}
 * 共用，避免八張表各自重複宣告。</p>
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@MappedSuperclass
public abstract class ProvenanceEntity {

    /** 來源類型，非空 */
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private SourceType sourceType;

    /** 來源明細：模型識別、報告名稱或 URL */
    @Column(name = "source_detail", length = 512)
    private String sourceDetail;

    /** 信心度 0–1，AI_GENERATED 必填 */
    @Column(name = "confidence", precision = 4, scale = 3)
    private BigDecimal confidence;

    /** 審核狀態，預設 DRAFT；@Builder.Default 讓 builder 建立的物件同樣吃到預設值 */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 32)
    private ReviewStatus reviewStatus = ReviewStatus.DRAFT;

    /** 審核者，狀態轉為 VERIFIED / REJECTED 時必填 */
    @Column(name = "reviewed_by", length = 128)
    private String reviewedBy;

    /** 審核時間 */
    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.reviewStatus == null) {
            this.reviewStatus = ReviewStatus.DRAFT;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
