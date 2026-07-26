package com.profetai.industrymap.helper;

import com.profetai.industrymap.enums.ReviewStatus;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 對外查詢的審核範圍（design D8）。
 *
 * <p>預設只回已驗證資料；草稿需呼叫端明確指定才納入；已駁回一律不外露——
 * 駁回的資料留著是為了避免同一筆錯誤被重複生成，不是為了給人看。
 * 集中在這裡是為了讓每個查詢用同一套規則，不會有某支 API 漏擋 REJECTED。</p>
 */
public final class ReviewScopes {

    private ReviewScopes() {
    }

    /** 供 JPA 衍生查詢使用 */
    public static Set<ReviewStatus> visibleStatuses(boolean includeDrafts) {
        return includeDrafts
                ? Set.of(ReviewStatus.VERIFIED, ReviewStatus.DRAFT)
                : Set.of(ReviewStatus.VERIFIED);
    }

    /** 供 native SQL 使用：native 查詢的列舉繫結以字串傳入較明確 */
    public static Set<String> visibleStatusNames(boolean includeDrafts) {
        return visibleStatuses(includeDrafts).stream().map(Enum::name).collect(Collectors.toUnmodifiableSet());
    }
}
