package com.profetai.industrymap.helper;

import com.profetai.industrymap.enums.ReviewStatus;

import java.util.EnumSet;
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

    /**
     * {@link #isExposable} 的字串集合版，供 native SQL 的 {@code IN} 使用。
     *
     * <p>與 {@link #visibleStatusNames} 的差別就是實體與關係的差別：關係的草稿取捨跟著呼叫端的
     * {@code includeDrafts} 走，實體則一律只擋 REJECTED。以補集導出而非列舉可外露的值，
     * 日後新增狀態時不會漏掉。</p>
     */
    public static Set<String> exposableStatusNames() {
        return EnumSet.complementOf(EnumSet.of(ReviewStatus.REJECTED)).stream()
                .map(Enum::name)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 實體本身是否可外露。
     *
     * <p>上面兩個方法過濾的是「關係」（組成、供應角色、市佔率）的狀態，但實體本身
     * （品類節點、公司、別名、識別碼）也有審核狀態：一筆已驗證的組成關係可能指向一個
     * 事後被駁回的節點，此時關係過得了 {@link #visibleStatuses}，節點卻不該外露。</p>
     *
     * <p>這裡只擋 REJECTED、不擋 DRAFT：草稿的取捨屬於各查詢自己的 {@code includeDrafts}
     * 語意，而「建立後立刻查回自己剛建的草稿」是既有且必要的行為。</p>
     */
    public static boolean isExposable(ReviewStatus status) {
        return status != ReviewStatus.REJECTED;
    }
}
