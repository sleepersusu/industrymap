package com.profetai.industrymap.payloads.review;

/**
 * 「內部 id 與自然鍵擇一提供」這條規則，單筆請求與批次項目共用同一份判斷，
 * 避免兩邊各寫一次而日後只改到其中一邊。
 */
final class ReviewTargetLocation {

    private ReviewTargetLocation() {
    }

    /**
     * 自然鍵物件本身存在但欄位全空，等同沒有提供定位資訊；哪些欄位對哪個類型有效
     * 由解析元件依 targetType 判斷（design D2），這裡只回答「有沒有給任何線索」。
     */
    static boolean isLocatable(Long targetId, ReviewTargetKey naturalKey) {
        return targetId != null || hasAnyField(naturalKey);
    }

    /** sourceDetail 刻意不計入：它可為 null 且單獨存在時定位不到任何一筆 */
    private static boolean hasAnyField(ReviewTargetKey key) {
        if (key == null) {
            return false;
        }
        return key.getName() != null
                || key.getCompanyCode() != null
                || key.getParentItemId() != null
                || key.getChildItemId() != null
                || key.getItemId() != null
                || key.getIdentifierType() != null
                || key.getIdentifierValue() != null
                || key.getCompanyRole() != null
                || key.getPeriodType() != null
                || key.getPeriodValue() != null
                || key.getRegion() != null
                || key.getMetric() != null;
    }
}
