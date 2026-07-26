package com.profetai.industrymap.repository;

import com.profetai.industrymap.model.MarketShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 市佔率查詢。所有手寫查詢一律以 native SQL 寫在本層，service 只負責呼叫；
 * enum 以字串傳入，避免 native 查詢的列舉繫結依賴 ordinal。
 */
public interface MarketShareRepository extends JpaRepository<MarketShare, Long> {

    /**
     * 排名查詢：固定帶 item + 期間 + 地區 + 口徑四個維度，依百分比降冪。
     * 對應索引 idx_market_share_ranking。
     */
    @Query(value = """
            SELECT ms.* FROM market_share ms
            WHERE ms.item_id = :itemId
              AND ms.period_type = :periodType
              AND ms.period_value = :periodValue
              AND ms.region = :region
              AND ms.metric = :metric
              AND ms.review_status IN (:reviewStatuses)
            ORDER BY ms.share_percent DESC
            """, nativeQuery = true)
    List<MarketShare> findRanking(@Param("itemId") Long itemId,
                                  @Param("periodType") String periodType,
                                  @Param("periodValue") String periodValue,
                                  @Param("region") String region,
                                  @Param("metric") String metric,
                                  @Param("reviewStatuses") Collection<String> reviewStatuses);

    /**
     * 判斷同一來源是否已寫過同一組維度。
     * source_detail 可為 null，且 NULL 在 SQL 中互不相等，因此兩邊都以 COALESCE 正規化後比對——
     * 這與 uk_market_share_dimensions_source 索引的定義一致。
     */
    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM market_share ms
                WHERE ms.company_id = :companyId
                  AND ms.item_id = :itemId
                  AND ms.period_type = :periodType
                  AND ms.period_value = :periodValue
                  AND ms.region = :region
                  AND ms.metric = :metric
                  AND COALESCE(ms.source_detail, '') = COALESCE(:sourceDetail, '')
            )
            """, nativeQuery = true)
    boolean existsSameDimensionsFromSameSource(@Param("companyId") Long companyId,
                                               @Param("itemId") Long itemId,
                                               @Param("periodType") String periodType,
                                               @Param("periodValue") String periodValue,
                                               @Param("region") String region,
                                               @Param("metric") String metric,
                                               @Param("sourceDetail") String sourceDetail);

    /**
     * 以自然鍵（六個維度 + 來源）定位單筆市佔率，供審核端點使用（design D1）。
     * 比對條件與 uk_market_share_dimensions_source 索引一致，因此至多命中一筆。
     */
    @Query(value = """
            SELECT ms.* FROM market_share ms
            WHERE ms.company_id = :companyId
              AND ms.item_id = :itemId
              AND ms.period_type = :periodType
              AND ms.period_value = :periodValue
              AND ms.region = :region
              AND ms.metric = :metric
              AND COALESCE(ms.source_detail, '') = COALESCE(:sourceDetail, '')
            """, nativeQuery = true)
    Optional<MarketShare> findByDimensionsAndSource(@Param("companyId") Long companyId,
                                                    @Param("itemId") Long itemId,
                                                    @Param("periodType") String periodType,
                                                    @Param("periodValue") String periodValue,
                                                    @Param("region") String region,
                                                    @Param("metric") String metric,
                                                    @Param("sourceDetail") String sourceDetail);
}
