package com.profetai.industrymap.repository;

import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.enums.SourceType;
import com.profetai.industrymap.model.Item;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 實際對 PostgreSQL 執行 {@link ItemRepository} 的終端成品列表 native SQL。
 *
 * <p>service 層的單元測試把這兩支方法 mock 掉，因此「只回終端成品」「審核範圍」
 * 「LIKE 比對」「LIMIT/OFFSET 搭配穩定排序不漏筆」這些只存在於 SQL 裡的行為，
 * 只有真的送到資料庫才驗得出來。</p>
 *
 * <p>所有 fixture 的正規化名稱共用同一段關鍵字，查詢一律帶該關鍵字——
 * 沒有 Docker 時整合測試跑在共用的本機開發資料庫上，不這樣做總筆數會被既有資料汙染。</p>
 */
class ItemEndProductNativeQueryTest extends AbstractPostgresIntegrationTest {

    private static final String KEYWORD = "listingfixture";
    private static final String PATTERN = "%" + KEYWORD + "%";
    private static final Set<String> VERIFIED_ONLY = Set.of(ReviewStatus.VERIFIED.name());
    private static final Set<String> WITH_DRAFTS =
            Set.of(ReviewStatus.VERIFIED.name(), ReviewStatus.DRAFT.name());

    @Autowired
    private ItemRepository itemRepository;

    @Test
    @DisplayName("列表應只回終端成品，不含零件節點")
    void findEndProducts_mixedNodes_shouldReturnOnlyEndProducts() {
        // Given：一個終端成品與一個零件節點，兩者名稱都含同一段關鍵字
        Item bike = save(endProduct("AAA bike", ReviewStatus.VERIFIED));
        save(component("BBB derailleur", ReviewStatus.VERIFIED));

        List<Item> found = itemRepository.findEndProducts(VERIFIED_ONLY, PATTERN, 10, 0);

        assertAll(
                () -> assertEquals(1, found.size()),
                () -> assertEquals(bike.getId(), found.get(0).getId()),
                () -> assertEquals(1L, itemRepository.countEndProducts(VERIFIED_ONLY, PATTERN)));
    }

    @Test
    @DisplayName("預設只回已驗證，指定納入草稿時才含草稿")
    void findEndProducts_draftScope_shouldFollowRequestedStatuses() {
        save(endProduct("AAA verified", ReviewStatus.VERIFIED));
        save(endProduct("BBB draft", ReviewStatus.DRAFT));

        assertAll(
                () -> assertEquals(1, itemRepository.findEndProducts(VERIFIED_ONLY, PATTERN, 10, 0).size()),
                () -> assertEquals(2, itemRepository.findEndProducts(WITH_DRAFTS, PATTERN, 10, 0).size()));
    }

    @Test
    @DisplayName("已駁回的節點在任何審核範圍下都不得回傳")
    void findEndProducts_rejected_shouldNeverBeReturned() {
        save(endProduct("AAA rejected", ReviewStatus.REJECTED));

        assertAll(
                () -> assertTrue(itemRepository.findEndProducts(VERIFIED_ONLY, PATTERN, 10, 0).isEmpty()),
                () -> assertTrue(itemRepository.findEndProducts(WITH_DRAFTS, PATTERN, 10, 0).isEmpty()),
                () -> assertEquals(0L, itemRepository.countEndProducts(WITH_DRAFTS, PATTERN)));
    }

    @Test
    @DisplayName("名稱關鍵字應只回名稱包含該關鍵字的終端成品")
    void findEndProducts_nameKeyword_shouldFilterByContains() {
        Item matched = save(endProduct("AAA bike", ReviewStatus.VERIFIED));
        save(endProduct("BBB scooter", ReviewStatus.VERIFIED));

        // fixture 的正規化名稱形如 it-listingfixture-aaabike，因此比對得到 aaabike 這段
        List<Item> found = itemRepository.findEndProducts(VERIFIED_ONLY, "%aaabike%", 10, 0);

        assertAll(
                () -> assertEquals(1, found.size()),
                () -> assertEquals(matched.getId(), found.get(0).getId()));
    }

    @Test
    @DisplayName("以相同條件翻頁時兩頁不得重疊，合併後不得遺漏")
    void findEndProducts_paging_shouldNotOverlapOrLoseRows() {
        // Given：三筆同名前綴的終端成品，display_name 只差一個字母，排序需靠 id 之外的穩定鍵釘住
        save(endProduct("AAA product", ReviewStatus.VERIFIED));
        save(endProduct("BBB product", ReviewStatus.VERIFIED));
        save(endProduct("CCC product", ReviewStatus.VERIFIED));

        List<Long> firstPage = idsOf(itemRepository.findEndProducts(VERIFIED_ONLY, PATTERN, 2, 0));
        List<Long> secondPage = idsOf(itemRepository.findEndProducts(VERIFIED_ONLY, PATTERN, 2, 2));

        assertAll(
                () -> assertEquals(3L, itemRepository.countEndProducts(VERIFIED_ONLY, PATTERN)),
                () -> assertEquals(2, firstPage.size()),
                () -> assertEquals(1, secondPage.size()),
                () -> assertTrue(firstPage.stream().noneMatch(secondPage::contains), "兩頁不得重疊"),
                () -> assertEquals(3, Set.copyOf(concat(firstPage, secondPage)).size(), "合併後不得遺漏"));
    }

    @Test
    @DisplayName("終端成品被改為非終端成品後應不再出現於列表")
    void findEndProducts_afterFlaggedAsNonEndProduct_shouldDisappear() {
        Item motherboard = save(endProduct("AAA motherboard", ReviewStatus.VERIFIED));
        assertEquals(1, itemRepository.findEndProducts(VERIFIED_ONLY, PATTERN, 10, 0).size());

        motherboard.setEndProduct(false);
        save(motherboard);

        assertTrue(itemRepository.findEndProducts(VERIFIED_ONLY, PATTERN, 10, 0).isEmpty());
    }

    @Test
    @DisplayName("修正後退回草稿的節點重新審核為已驗證後應重新出現於預設查詢")
    void findEndProducts_afterReverify_shouldReappearInDefaultScope() {
        Item bike = save(endProduct("AAA bike", ReviewStatus.DRAFT));
        assertTrue(itemRepository.findEndProducts(VERIFIED_ONLY, PATTERN, 10, 0).isEmpty());

        bike.setReviewStatus(ReviewStatus.VERIFIED);
        save(bike);

        assertEquals(1, itemRepository.findEndProducts(VERIFIED_ONLY, PATTERN, 10, 0).size());
    }

    private List<Long> idsOf(List<Item> items) {
        return items.stream().map(Item::getId).toList();
    }

    private List<Long> concat(List<Long> first, List<Long> second) {
        return java.util.stream.Stream.concat(first.stream(), second.stream()).toList();
    }

    private Item save(Item item) {
        return itemRepository.saveAndFlush(item);
    }

    private Item endProduct(String displayName, ReviewStatus reviewStatus) {
        return node(displayName, reviewStatus, true);
    }

    private Item component(String displayName, ReviewStatus reviewStatus) {
        return node(displayName, reviewStatus, false);
    }

    private Item node(String displayName, ReviewStatus reviewStatus, boolean endProduct) {
        return Item.builder()
                .normalizedName(FIXTURE_PREFIX + KEYWORD + "-" + displayName.toLowerCase().replace(" ", ""))
                .displayName(displayName)
                .isEndProduct(endProduct)
                .reviewStatus(reviewStatus)
                .sourceType(SourceType.MANUAL)
                .build();
    }
}
