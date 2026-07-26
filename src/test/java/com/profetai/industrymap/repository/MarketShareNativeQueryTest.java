package com.profetai.industrymap.repository;

import com.profetai.industrymap.enums.PeriodType;
import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.enums.ShareMetric;
import com.profetai.industrymap.enums.SourceType;
import com.profetai.industrymap.model.Company;
import com.profetai.industrymap.model.Item;
import com.profetai.industrymap.model.MarketShare;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 實際對 PostgreSQL 執行 {@link MarketShareRepository} 的兩支 native SQL。
 *
 * <p>單元測試都是 mock 這兩支方法，繫結方式（IN 展開集合、enum 以字串傳入、
 * {@code SELECT EXISTS} 映射成 boolean、{@code COALESCE} 與唯一索引定義是否一致）
 * 只有真的送到資料庫才驗得出來。</p>
 */
class MarketShareNativeQueryTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private MarketShareRepository marketShareRepository;

    @Test
    @DisplayName("排名查詢應依百分比降冪回傳，且只含指定審核狀態")
    void findRanking_multipleCompanies_shouldReturnSortedAndScopedRows() {
        // Given：同一零件三筆市佔率，其中一筆為草稿
        Item derailleur = itemRepository.saveAndFlush(item("變速器"));
        MarketShare leader = marketShareRepository.saveAndFlush(
                verified(company("shimano"), derailleur, new BigDecimal("70.000"), "報告 A"));
        marketShareRepository.saveAndFlush(
                verified(company("sram"), derailleur, new BigDecimal("20.000"), "報告 A"));
        MarketShare draft = marketShare(company("campagnolo"), derailleur, new BigDecimal("90.000"), "報告 A");
        marketShareRepository.saveAndFlush(draft);

        // When：預設範圍只取已驗證
        List<MarketShare> verifiedOnly = marketShareRepository.findRanking(
                derailleur.getId(), "YEAR", "2024", "全球", "REVENUE", Set.of(ReviewStatus.VERIFIED.name()));
        List<MarketShare> includingDrafts = marketShareRepository.findRanking(
                derailleur.getId(), "YEAR", "2024", "全球", "REVENUE",
                Set.of(ReviewStatus.VERIFIED.name(), ReviewStatus.DRAFT.name()));

        // Then：草稿那筆百分比最高，納入後應排第一，可證明審核範圍確實有作用
        assertAll(
                () -> assertEquals(2, verifiedOnly.size()),
                () -> assertEquals(leader.getId(), verifiedOnly.get(0).getId()),
                () -> assertEquals(new BigDecimal("20.000"), verifiedOnly.get(1).getSharePercent()),
                () -> assertEquals(3, includingDrafts.size()),
                () -> assertEquals(new BigDecimal("90.000"), includingDrafts.get(0).getSharePercent()));
    }

    @Test
    @DisplayName("排名查詢的其他維度不符時應回傳空清單")
    void findRanking_differentRegion_shouldReturnEmptyList() {
        Item derailleur = itemRepository.saveAndFlush(item("變速器"));
        marketShareRepository.saveAndFlush(
                verified(company("shimano"), derailleur, new BigDecimal("70.000"), "報告 A"));

        assertTrue(marketShareRepository.findRanking(
                derailleur.getId(), "YEAR", "2024", "台灣", "REVENUE",
                Set.of(ReviewStatus.VERIFIED.name())).isEmpty());
    }

    @Test
    @DisplayName("同來源重複判斷應命中，換一個來源則不算重複")
    void existsSameDimensionsFromSameSource_shouldDedupeBySourceDetail() {
        Company shimano = company("shimano");
        Item derailleur = itemRepository.saveAndFlush(item("變速器"));
        marketShareRepository.saveAndFlush(verified(shimano, derailleur, new BigDecimal("70.000"), "報告 A"));

        assertAll(
                () -> assertTrue(marketShareRepository.existsSameDimensionsFromSameSource(
                        shimano.getId(), derailleur.getId(), "YEAR", "2024", "全球", "REVENUE", "報告 A")),
                () -> assertFalse(marketShareRepository.existsSameDimensionsFromSameSource(
                        shimano.getId(), derailleur.getId(), "YEAR", "2024", "全球", "REVENUE", "報告 B")));
    }

    @Test
    @DisplayName("來源明細為 null 時應以 COALESCE 正規化後比對，與唯一索引一致")
    void existsSameDimensionsFromSameSource_nullSourceDetail_shouldMatchViaCoalesce() {
        // Given：一筆沒有來源明細的資料；SQL 的 NULL 互不相等，沒有 COALESCE 就會漏判
        Company shimano = company("shimano");
        Item derailleur = itemRepository.saveAndFlush(item("變速器"));
        marketShareRepository.saveAndFlush(verified(shimano, derailleur, new BigDecimal("70.000"), null));

        assertAll(
                () -> assertTrue(marketShareRepository.existsSameDimensionsFromSameSource(
                        shimano.getId(), derailleur.getId(), "YEAR", "2024", "全球", "REVENUE", null)),
                () -> assertFalse(marketShareRepository.existsSameDimensionsFromSameSource(
                        shimano.getId(), derailleur.getId(), "YEAR", "2024", "全球", "REVENUE", "報告 A")));
    }

    private Company company(String normalizedName) {
        return companyRepository.saveAndFlush(Company.builder()
                .normalizedName(normalizedName)
                .displayName(normalizedName)
                .sourceType(SourceType.MANUAL)
                .reviewStatus(ReviewStatus.VERIFIED)
                .build());
    }

    private Item item(String normalizedName) {
        return Item.builder()
                .normalizedName(normalizedName)
                .displayName(normalizedName)
                .sourceType(SourceType.MANUAL)
                .reviewStatus(ReviewStatus.VERIFIED)
                .build();
    }

    private MarketShare verified(Company company, Item item, BigDecimal sharePercent, String sourceDetail) {
        MarketShare marketShare = marketShare(company, item, sharePercent, sourceDetail);
        marketShare.setReviewStatus(ReviewStatus.VERIFIED);
        return marketShare;
    }

    private MarketShare marketShare(Company company, Item item, BigDecimal sharePercent, String sourceDetail) {
        return MarketShare.builder()
                .company(company)
                .item(item)
                .periodType(PeriodType.YEAR)
                .periodValue("2024")
                .region("全球")
                .metric(ShareMetric.REVENUE)
                .sharePercent(sharePercent)
                .sourceType(SourceType.EXTERNAL)
                .sourceDetail(sourceDetail)
                .build();
    }
}
