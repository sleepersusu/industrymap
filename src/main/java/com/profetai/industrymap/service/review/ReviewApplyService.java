package com.profetai.industrymap.service.review;

import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.enums.ReviewTargetType;
import com.profetai.industrymap.exceptions.ServerException;
import com.profetai.industrymap.model.ProvenanceEntity;
import com.profetai.industrymap.payloads.review.ReviewResultResponse;
import com.profetai.industrymap.payloads.review.ReviewTargetKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 單筆審核的交易邊界：查目標 → 套用審核 → 寫回。
 *
 * <p>審核邏輯本身沿用 {@link ReviewService}，本層只負責把它接上實體查找與持久化。
 * 交易切在「一筆」上，讓批次審核能逐筆獨立成功或失敗（design D2）。</p>
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class ReviewApplyService {

    private final ReviewLookupService reviewLookupService;
    private final ReviewService reviewService;
    private final NaturalKeyResolver naturalKeyResolver;

    /**
     * 以內部 id 套用單筆審核。既有呼叫端與測試沿用這個入口（design D1：id 定位不移除）。
     *
     * @throws ServerException 查無目標（404）、轉為已驗證／已駁回卻未提供審核者（400）
     */
    @Transactional
    public ReviewResultResponse apply(ReviewTargetType targetType, Long targetId,
                                      ReviewStatus targetStatus, String reviewer) {

        return applyResolved(targetType, targetId, targetStatus, reviewer);
    }

    /**
     * 以內部 id 或自然鍵套用單筆審核。
     *
     * <p>兩者同時提供時以 id 為準——id 是精確定位，自然鍵還要再查一次，
     * 兩者衝突時回錯誤只會讓呼叫端多一種要處理的失敗情況（design D1）。</p>
     *
     * @throws ServerException 兩種定位都未提供或自然鍵欄位不足（400）、查無目標（404）、
     *                         轉為已驗證／已駁回卻未提供審核者（400）
     */
    @Transactional
    public ReviewResultResponse apply(ReviewTargetType targetType, Long targetId, ReviewTargetKey naturalKey,
                                      ReviewStatus targetStatus, String reviewer) {

        if (targetId == null && naturalKey == null) {
            throw new ServerException("必須提供目標識別碼或自然鍵：" + targetType, HttpStatus.BAD_REQUEST);
        }
        Long resolvedId = targetId != null ? targetId : naturalKeyResolver.resolveId(targetType, naturalKey);
        return applyResolved(targetType, resolvedId, targetStatus, reviewer);
    }

    private ReviewResultResponse applyResolved(ReviewTargetType targetType, Long targetId,
                                               ReviewStatus targetStatus, String reviewer) {

        ProvenanceEntity target = reviewLookupService.getTarget(targetType, targetId);
        ProvenanceEntity reviewed = reviewService.applyReview(target, targetStatus, reviewer);
        ProvenanceEntity saved = reviewLookupService.save(targetType, reviewed);

        log.info("審核完成 targetType={} targetId={} status={}", targetType, targetId, saved.getReviewStatus());
        return ReviewResultResponse.from(targetType, targetId, saved);
    }
}
