package com.profetai.industrymap.repository;

import com.profetai.industrymap.model.Item;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long> {

    Optional<Item> findByNormalizedName(String normalizedName);

    boolean existsByNormalizedName(String normalizedName);

    /** 查某品類的細分類型（is-a 下層），與組成關係分開回傳 */
    java.util.List<Item> findByParentCategoryId(Long parentCategoryId);
}
