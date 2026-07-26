package com.profetai.industrymap.payloads.review;

import com.profetai.industrymap.enums.ReviewStatus;

/**
 * 「非草稿狀態必須提供審核者」這條規則，單筆與批次請求共用同一份判斷，
 * 避免兩邊各寫一次而日後只改到其中一邊。
 */
final class ReviewerRequirement {

    private ReviewerRequirement() {
    }

    /**
     * @param targetStatus 目標狀態；為 null 時交給 {@code @NotNull} 回報，這裡不重複報錯
     */
    static boolean isSatisfied(ReviewStatus targetStatus, String reviewer) {
        if (targetStatus == null || targetStatus == ReviewStatus.DRAFT) {
            return true;
        }
        return reviewer != null && !reviewer.isBlank();
    }
}
