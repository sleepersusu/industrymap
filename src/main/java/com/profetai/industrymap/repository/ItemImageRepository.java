package com.profetai.industrymap.repository;

import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.model.ItemImage;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ItemImageRepository extends JpaRepository<ItemImage, Long> {

    /**
     * 節點的圖片清單，依視角標籤再 id 排序。
     *
     * <p>排序固定是刻意的：任一筆被審核後清單順序不該無故改變，
     * 前端的圖片切換器也才不會在重新整理後跳位。</p>
     *
     * <p>刻意不加 {@code @EntityGraph}：本查詢只用得到圖片自身的欄位，
     * 路徑上的節點呼叫端早已持有，熱區另以一次查詢一併撈齊（見
     * {@code ItemHotspotRepository#findByItemImageIdInAndReviewStatusIn}）。</p>
     */
    List<ItemImage> findByItemIdAndReviewStatusInOrderByViewLabelAscIdAsc(
            Long itemId, Collection<ReviewStatus> reviewStatuses);

    /** 自然鍵（節點 + 視角標籤）：建立時的重複檢查與審核端點的定位共用同一條 */
    Optional<ItemImage> findByItemIdAndViewLabel(Long itemId, String viewLabel);

    /**
     * 取圖片並一併載入所屬節點，供建立熱區使用。
     *
     * <p>熱區的自然鍵是「節點 + 視角標籤 + 位置標籤」，而批次建立要在交易外把它組進回應——
     * {@code item} 是 LAZY 關聯，交易結束後才碰它會拋 {@code LazyInitializationException}。
     * 靠全域批次抓取兜不住這件事（那只在同一個 session 內有效），因此顯性寫在查詢上。</p>
     */
    @EntityGraph(attributePaths = "item")
    Optional<ItemImage> findWithItemById(Long id);
}
