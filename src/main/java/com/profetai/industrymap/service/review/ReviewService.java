package com.profetai.industrymap.service.review;

import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.exceptions.ServerException;
import com.profetai.industrymap.model.ProvenanceEntity;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 審核狀態流轉（design D8）。八張內容表共用 {@link ProvenanceEntity}，
 * 因此審核邏輯也只寫一份，以泛型套用到任一內容實體。
 *
 * <p>已駁回的資料保留不刪除——刪掉就等於下次 AI 生成又會把同一筆錯誤資料寫回來。</p>
 */
@Log4j2
@Service
public class ReviewService {

    /**
     * 套用審核結果。
     *
     * @param targetStatus 目標狀態
     * @param reviewer     審核者；轉為 VERIFIED / REJECTED 時必填
     * @throws ServerException 未提供目標狀態，或轉為已驗證／已駁回卻未提供審核者（皆為 400）
     */
    public <T extends ProvenanceEntity> T applyReview(T entity, ReviewStatus targetStatus, String reviewer) {
        if (targetStatus == null) {
            throw new ServerException("必須提供審核狀態", HttpStatus.BAD_REQUEST);
        }

        if (targetStatus == ReviewStatus.DRAFT) {
            // 退回草稿代表「這筆還沒被審過」，殘留的審核紀錄會誤導後續判讀
            entity.setReviewStatus(ReviewStatus.DRAFT);
            entity.setReviewedBy(null);
            entity.setReviewedAt(null);
            return entity;
        }

        if (reviewer == null || reviewer.isBlank()) {
            throw new ServerException("標記為" + targetStatus + "時必須提供審核者", HttpStatus.BAD_REQUEST);
        }

        entity.setReviewStatus(targetStatus);
        entity.setReviewedBy(reviewer);
        entity.setReviewedAt(Instant.now());
        log.info("審核狀態更新 status={} reviewer={}", targetStatus, reviewer);
        return entity;
    }
}
