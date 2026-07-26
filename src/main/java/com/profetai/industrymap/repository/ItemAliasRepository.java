package com.profetai.industrymap.repository;

import com.profetai.industrymap.model.ItemAlias;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ItemAliasRepository extends JpaRepository<ItemAlias, Long> {

    /** 以正規化別名反查節點，是「寫入前先比對、命中則沿用既有節點」的入口 */
    Optional<ItemAlias> findByNormalizedAlias(String normalizedAlias);

    boolean existsByNormalizedAlias(String normalizedAlias);

    List<ItemAlias> findByItemId(Long itemId);
}
