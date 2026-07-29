package com.profetai.industrymap.repository;

import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.model.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long> {

    Optional<Item> findByNormalizedName(String normalizedName);

    /** 查某品類的細分類型（is-a 下層），與組成關係分開回傳 */
    List<Item> findByParentCategoryId(Long parentCategoryId);

    /**
     * 該節點是否存在<b>且可外露</b>。
     *
     * <p>對外查詢用來讓已駁回的節點與不存在的節點表現完全一致：兩者都不成立，
     * 呼叫端因此無法從狀態碼或回應內容反推出一筆對外不存在的資料。
     * 傳入 {@code ReviewScopes.exposableStatuses()}，狀態集合的規則不在此重寫第二份。</p>
     */
    boolean existsByIdAndReviewStatusIn(Long id, Collection<ReviewStatus> reviewStatuses);

    /**
     * 終端成品列表（design D6）。
     *
     * <p>排序固定 {@code display_name} 再 {@code id}：正規化名稱雖是唯一鍵，但排序用的是
     * display_name 本身，資料庫定序規則下可能有「不同字串但比較結果相等」的情況，
     * 少了 id 這個 tie-break，翻頁就會在頁與頁之間漏或重複資料。</p>
     *
     * <p>名稱關鍵字以「正規化後的 LIKE 樣式」傳入（不過濾時傳 {@code %}）：
     * 樣式組裝留在呼叫端，是為了讓參數在 SQL 裡只與 {@code normalized_name} 直接比較，
     * 由欄位提供型別上下文，不必為字串串接另外加 CAST。</p>
     */
    @Query(value = """
            SELECT i.* FROM item i
            WHERE i.is_end_product = TRUE
              AND i.review_status IN (:reviewStatuses)
              AND i.normalized_name LIKE :namePattern
            ORDER BY i.display_name, i.id
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<Item> findEndProducts(@Param("reviewStatuses") Collection<String> reviewStatuses,
                               @Param("namePattern") String namePattern,
                               @Param("limit") int limit,
                               @Param("offset") long offset);

    /** 終端成品列表的總筆數，過濾條件與 {@link #findEndProducts} 一致 */
    @Query(value = """
            SELECT COUNT(*) FROM item i
            WHERE i.is_end_product = TRUE
              AND i.review_status IN (:reviewStatuses)
              AND i.normalized_name LIKE :namePattern
            """, nativeQuery = true)
    long countEndProducts(@Param("reviewStatuses") Collection<String> reviewStatuses,
                          @Param("namePattern") String namePattern);
}
