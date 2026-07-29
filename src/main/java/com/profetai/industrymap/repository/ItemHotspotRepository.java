package com.profetai.industrymap.repository;

import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.model.ItemHotspot;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ItemHotspotRepository extends JpaRepository<ItemHotspot, Long> {

    /**
     * 一次撈齊多張圖片的熱區（design D6）：讀取端點回巢狀結構，逐張圖各查一次就是 N+1。
     *
     * <p>{@code @EntityGraph} 一併載入熱區指向的節點（design D9）：回應要讀該節點的名稱，
     * 過濾它的審核狀態更是一定要讀到節點本身，而 {@code Item} 的 {@code @Id} 標在欄位上，
     * Hibernate 無法在代理上攔截識別碼 getter——連取 id 都會逐筆觸發初始化。
     * 寫在查詢上而非只靠全域批次抓取：join fetch 永遠一次查詢，不受批次大小影響。</p>
     *
     * <p>排序固定為位置標籤再 id，理由同 {@link ItemImageRepository} 的圖片排序。</p>
     */
    @EntityGraph(attributePaths = "childItem")
    List<ItemHotspot> findByItemImageIdInAndReviewStatusInOrderByPositionLabelAscIdAsc(
            Collection<Long> itemImageIds, Collection<ReviewStatus> reviewStatuses);

    /** 自然鍵的後半（一張圖上的位置標籤唯一）：建立時的重複檢查與審核端點的定位共用同一條 */
    Optional<ItemHotspot> findByItemImageIdAndPositionLabel(Long itemImageId, String positionLabel);

    /**
     * 取熱區並一併載入所屬圖片與所指向的節點，供修正流程使用。
     *
     * <p>回應要帶所指向節點的名稱，而<b>組裝發生在交易外</b>（controller）：兩個關聯都是 LAZY，
     * 且 {@code @Id} 標在欄位上——連讀主鍵都會觸發初始化，交易結束後才碰就是
     * {@code LazyInitializationException}。全域批次抓取兜不住這件事（它只在同一個 session 內有效），
     * 因此顯性寫在查詢上。</p>
     *
     * <p>不改用 {@code findById} 再於 service 內先讀一次來初始化：那種寫法一旦有人重構掉那行
     * 「看起來沒有作用」的存取就會靜默失效，而失效的形式是線上 500。</p>
     */
    @EntityGraph(attributePaths = {"itemImage", "childItem"})
    Optional<ItemHotspot> findWithAssociationsById(Long id);
}
