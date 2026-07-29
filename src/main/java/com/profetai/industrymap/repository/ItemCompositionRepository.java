package com.profetai.industrymap.repository;

import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.model.Item;
import com.profetai.industrymap.model.ItemComposition;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ItemCompositionRepository extends JpaRepository<ItemComposition, Long> {

    /** 由上層查下層：組成樹展開與循環偵測都走這條 */
    List<ItemComposition> findByParentItemId(Long parentItemId);

    /**
     * 對外查詢用：兩端節點一併載入（{@code @EntityGraph}）。
     *
     * <p>{@code parentItem} / {@code childItem} 都是 LAZY 關聯，而 {@code Item} 的 {@code @Id}
     * 標在欄位上，Hibernate 無法在代理上攔截識別碼 getter——組成關係的回應連「只取兩端的 id」
     * 都會逐筆觸發初始化，過濾兩端節點的審核狀態更是一定要讀到節點本身。兩端本來就一定用得到，
     * 一次撈齊即可。</p>
     *
     * <p>寫在查詢上而非只靠全域的批次抓取設定：join fetch 永遠一次查詢，不受批次大小影響。</p>
     */
    @EntityGraph(attributePaths = {"parentItem", "childItem"})
    List<ItemComposition> findByParentItemIdAndReviewStatusIn(Long parentItemId, Collection<ReviewStatus> reviewStatuses);

    /** 由下層查上層：反向查詢終端成品 */
    List<ItemComposition> findByChildItemId(Long childItemId);

    /** 兩端節點一併載入，理由同 {@link #findByParentItemIdAndReviewStatusIn} */
    @EntityGraph(attributePaths = {"parentItem", "childItem"})
    List<ItemComposition> findByChildItemIdAndReviewStatusIn(Long childItemId, Collection<ReviewStatus> reviewStatuses);

    /**
     * 由零件向上回溯所有可達的終端成品，走訪由資料庫一次完成。
     *
     * <p>取代逐節點往返的 Java BFS：那個寫法每經過一個節點就送一次查詢，
     * 查詢次數隨圖的大小線性成長（實測扇出 20 時 22 筆 SQL）。走訪本來就是資料庫的工作。</p>
     *
     * <p>語意逐條對應原本的 BFS，一條都不能少：</p>
     * <ul>
     *   <li>只沿可見的組成關係向上——{@code c.review_status IN (:reviewStatuses)}，
     *       跟著呼叫端的 {@code includeDrafts} 走（關係）</li>
     *   <li>已駁回的上層節點不列入結果，<b>也不作為繼續向上的起點</b>——
     *       {@code p.review_status IN (:exposableStatuses)}（實體，只擋 REJECTED）。
     *       兩件事在這裡收斂成同一個 join 條件：被濾掉的列不會進入下一輪遞迴，
     *       這是簡化不是省略</li>
     *   <li>已走訪的節點不重複展開——{@code UNION}（非 {@code UNION ALL}）去重，
     *       與原本的 visited 集合等價，既有資料若含循環時同樣會終止</li>
     *   <li>起點自身不列入——anchor 從直接上層起算，但那只擋得住無循環的資料：
     *       循環會把路徑繞回起點，於是起點自己也成為「可達的上層」。因此結果另外排除
     *       {@code i.id <> :itemId}，對應原本 BFS 預先把起點放進 visited 的那一行。
     *       只排在結果而不排在走訪上是刻意的：經由起點再往上走，走到的仍是起點的上層，
     *       那些節點 anchor 已經涵蓋，不會多也不會少</li>
     * </ul>
     *
     * <p>刻意不設深度上限：終止性已由去重保證，加上限只會讓超出的終端成品靜默消失，
     * 呼叫端看到的是「這顆零件沒裝進那些產品」，與事實相反。</p>
     *
     * <p>{@code is_end_product} 的過濾只套在<b>結果</b>上，不套在走訪上：
     * 終端成品仍可能是別的產品的零件（主機板 → PC），走訪必須繼續往上。</p>
     */
    @Query(value = """
            WITH RECURSIVE reachable(item_id) AS (
                SELECT c.parent_item_id
                FROM item_composition c
                JOIN item p ON p.id = c.parent_item_id
                WHERE c.child_item_id = :itemId
                  AND c.review_status IN (:reviewStatuses)
                  AND p.review_status IN (:exposableStatuses)

                UNION

                SELECT c.parent_item_id
                FROM reachable r
                JOIN item_composition c ON c.child_item_id = r.item_id
                JOIN item p ON p.id = c.parent_item_id
                WHERE c.review_status IN (:reviewStatuses)
                  AND p.review_status IN (:exposableStatuses)
            )
            SELECT i.* FROM item i
            WHERE i.id IN (SELECT item_id FROM reachable)
              AND i.id <> :itemId
              AND i.is_end_product = TRUE
            ORDER BY i.display_name, i.id
            """, nativeQuery = true)
    List<Item> findReachableEndProducts(@Param("itemId") Long itemId,
                                        @Param("reviewStatuses") Collection<String> reviewStatuses,
                                        @Param("exposableStatuses") Collection<String> exposableStatuses);

    boolean existsByParentItemIdAndChildItemId(Long parentItemId, Long childItemId);

    /** 以自然鍵（上層 + 下層）定位單筆關係，供審核端點使用（design D1） */
    Optional<ItemComposition> findByParentItemIdAndChildItemId(Long parentItemId, Long childItemId);
}
