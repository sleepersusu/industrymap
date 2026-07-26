package com.profetai.industrymap.repository;

import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.model.ItemComposition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ItemCompositionRepository extends JpaRepository<ItemComposition, Long> {

    /** 由上層查下層：組成樹展開與循環偵測都走這條 */
    List<ItemComposition> findByParentItemId(Long parentItemId);

    List<ItemComposition> findByParentItemIdAndReviewStatusIn(Long parentItemId, Collection<ReviewStatus> reviewStatuses);

    /** 由下層查上層：反向查詢終端成品 */
    List<ItemComposition> findByChildItemId(Long childItemId);

    List<ItemComposition> findByChildItemIdAndReviewStatusIn(Long childItemId, Collection<ReviewStatus> reviewStatuses);

    boolean existsByParentItemIdAndChildItemId(Long parentItemId, Long childItemId);
}
