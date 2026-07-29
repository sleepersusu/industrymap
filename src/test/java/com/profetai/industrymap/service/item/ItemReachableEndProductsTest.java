package com.profetai.industrymap.service.item;

import com.profetai.industrymap.enums.Necessity;
import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.enums.SourceType;
import com.profetai.industrymap.model.Item;
import com.profetai.industrymap.model.ItemComposition;
import com.profetai.industrymap.repository.ItemCompositionRepository;
import com.profetai.industrymap.repository.ItemRepository;
import com.profetai.industrymap.support.AbstractPostgresWebIntegrationTest;
import com.profetai.industrymap.util.NameNormalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 反向回溯終端成品的走訪語意，逐條對真實 PostgreSQL 驗證。
 *
 * <p>這些語意原本由 {@code ItemCompositionServiceTest} 以 mock repository 驗證，其中一支甚至用
 * {@code verify(..., never())} 斷言「不曾查詢已駁回祖先的上層」——那驗的是實作互動而非行為，
 * 正是 {@code testing.md} 禁止的寫法，也正是它擋住重構的原因：走訪一旦交給資料庫，
 * 那些測試不只會壞，而且再也測不到任何邏輯。</p>
 *
 * <p>因此搬到真實資料庫，只斷言結果。走訪由 Java 迴圈或由 SQL 完成，這組測試都必須成立——
 * 它是重構的安全網，不是實作的複述。</p>
 *
 * <p>用完整 context 的基底而非 {@code @DataJpaTest}：要驗的是 service 對外的行為，
 * 不是單一支 repository 查詢。</p>
 */
class ItemReachableEndProductsTest extends AbstractPostgresWebIntegrationTest {

    private static final String RUN_ID = Long.toHexString(System.nanoTime());
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private ItemCompositionRepository itemCompositionRepository;
    @Autowired
    private ItemCompositionService itemCompositionService;

    @Test
    @DisplayName("沿組成關係向上多層回溯，只回傳終端成品，中間節點不列入")
    void findReachableEndProducts_multiLevel_shouldReturnOnlyEndProducts() {
        // Given：PCB → 主機板 → PC，主機板不是終端成品；PCB 另外直接掛在汽車之下
        Item pcb = part();
        Item motherboard = part();
        Item pc = product();
        Item car = product();
        composition(motherboard, pcb, ReviewStatus.VERIFIED);
        composition(pc, motherboard, ReviewStatus.VERIFIED);
        composition(car, pcb, ReviewStatus.VERIFIED);

        List<Item> endProducts = itemCompositionService.findReachableEndProducts(pcb.getId(), false);

        assertAll(
                () -> assertEquals(sorted(car.getId(), pc.getId()), sortedIds(endProducts)),
                () -> assertFalse(ids(endProducts).contains(motherboard.getId())));
    }

    @Test
    @DisplayName("只沿可見的組成關係向上，已駁回的關係不通行")
    void findReachableEndProducts_rejectedComposition_shouldNotBeTraversed() {
        Item pcb = part();
        Item reachable = product();
        Item blocked = product();
        composition(reachable, pcb, ReviewStatus.VERIFIED);
        composition(blocked, pcb, ReviewStatus.REJECTED);

        List<Item> endProducts = itemCompositionService.findReachableEndProducts(pcb.getId(), false);

        assertEquals(List.of(reachable.getId()), sortedIds(endProducts));
    }

    @Test
    @DisplayName("已駁回的上層節點不得列入結果")
    void findReachableEndProducts_rejectedAncestor_shouldBeExcluded() {
        Item pcb = part();
        Item pc = product();
        Item rejectedCar = product(ReviewStatus.REJECTED);
        composition(pc, pcb, ReviewStatus.VERIFIED);
        composition(rejectedCar, pcb, ReviewStatus.VERIFIED);

        List<Item> endProducts = itemCompositionService.findReachableEndProducts(pcb.getId(), false);

        assertEquals(List.of(pc.getId()), sortedIds(endProducts));
    }

    @Test
    @DisplayName("已駁回的上層節點不得作為繼續向上的起點")
    void findReachableEndProducts_rejectedAncestor_shouldNotBeTraversedThrough() {
        // Given：唯一到得了 topProduct 的路徑必須經過已駁回的中間節點——
        // 節點被駁回代表它本身不成立，經由它往上的路徑同樣不可信
        Item pcb = part();
        Item rejectedMiddle = part(ReviewStatus.REJECTED);
        Item topProduct = product();
        composition(rejectedMiddle, pcb, ReviewStatus.VERIFIED);
        composition(topProduct, rejectedMiddle, ReviewStatus.VERIFIED);

        List<Item> endProducts = itemCompositionService.findReachableEndProducts(pcb.getId(), false);

        assertTrue(endProducts.isEmpty(),
                "經由已駁回節點才到得了的終端成品不得列入，實際回傳：" + ids(endProducts));
    }

    @Test
    @DisplayName("起點自身不列入結果，即使它本身就是終端成品")
    void findReachableEndProducts_startIsEndProduct_shouldNotIncludeItself() {
        // 主機板可以既是別人的零件、又是自己語境下的成品（design D1 的遞迴實體）
        Item motherboard = product();
        Item pc = product();
        composition(pc, motherboard, ReviewStatus.VERIFIED);

        List<Item> endProducts = itemCompositionService.findReachableEndProducts(motherboard.getId(), false);

        assertEquals(List.of(pc.getId()), sortedIds(endProducts));
    }

    @Test
    @DisplayName("起點位於循環中且自身是終端成品時，仍不得回傳自己")
    void findReachableEndProducts_cyclicStartIsEndProduct_shouldStillExcludeItself() {
        // Given：主機板與 PC 互為上下層（歷史資料或直接寫庫都繞得過 service 的循環偵測，
        // DB 只擋自我指向），且主機板自己就是終端成品。
        // 原本的 BFS 以 visited 預先放入起點，繞回來時直接跳過；走訪改由 SQL 完成後，
        // 「起點不列入」這條必須另外表達，否則循環會把起點自己送回去
        Item motherboard = product();
        Item pc = product();
        composition(pc, motherboard, ReviewStatus.VERIFIED);
        composition(motherboard, pc, ReviewStatus.VERIFIED);

        List<Item> endProducts = itemCompositionService.findReachableEndProducts(motherboard.getId(), false);

        assertEquals(List.of(pc.getId()), sortedIds(endProducts),
                "起點自身不得出現在自己的回溯結果中，實際回傳：" + ids(endProducts));
    }

    @Test
    @DisplayName("既有資料含循環時查詢仍應終止")
    void findReachableEndProducts_cyclicData_shouldTerminate() {
        // service 的寫入路徑會擋掉循環，但歷史資料或直接寫庫仍可能造成循環，
        // 查詢不得因此無限展開
        Item a = part();
        Item b = part();
        Item product = product();
        composition(b, a, ReviewStatus.VERIFIED);
        composition(a, b, ReviewStatus.VERIFIED);
        composition(product, b, ReviewStatus.VERIFIED);

        List<Item> endProducts = itemCompositionService.findReachableEndProducts(a.getId(), false);

        assertEquals(List.of(product.getId()), sortedIds(endProducts));
    }

    @Test
    @DisplayName("同一個終端成品經多條路徑可達時只回傳一次")
    void findReachableEndProducts_reachableViaMultiplePaths_shouldNotDuplicate() {
        // Given：PCB 經由兩個不同的中間節點都到得了同一台 PC
        Item pcb = part();
        Item boardA = part();
        Item boardB = part();
        Item pc = product();
        composition(boardA, pcb, ReviewStatus.VERIFIED);
        composition(boardB, pcb, ReviewStatus.VERIFIED);
        composition(pc, boardA, ReviewStatus.VERIFIED);
        composition(pc, boardB, ReviewStatus.VERIFIED);

        List<Item> endProducts = itemCompositionService.findReachableEndProducts(pcb.getId(), false);

        assertEquals(List.of(pc.getId()), sortedIds(endProducts));
    }

    // ---------------------------------------------------------------------
    // fixture
    // ---------------------------------------------------------------------

    private List<Long> ids(List<Item> items) {
        return items.stream().map(Item::getId).toList();
    }

    /** 排序後比對：走訪順序是實作細節，不該綁進斷言 */
    private List<Long> sortedIds(List<Item> items) {
        return ids(items).stream().sorted().toList();
    }

    private List<Long> sorted(Long... itemIds) {
        return List.of(itemIds).stream().sorted().toList();
    }

    private Item part() {
        return item(false, ReviewStatus.VERIFIED);
    }

    private Item part(ReviewStatus reviewStatus) {
        return item(false, reviewStatus);
    }

    private Item product() {
        return item(true, ReviewStatus.VERIFIED);
    }

    private Item product(ReviewStatus reviewStatus) {
        return item(true, reviewStatus);
    }

    private Item item(boolean endProduct, ReviewStatus reviewStatus) {
        String displayName = FIXTURE_PREFIX + "reach" + RUN_ID + SEQUENCE.incrementAndGet();
        return itemRepository.saveAndFlush(Item.builder()
                .normalizedName(NameNormalizer.normalize(displayName))
                .displayName(displayName)
                .isEndProduct(endProduct)
                .sourceType(SourceType.MANUAL)
                .reviewStatus(reviewStatus)
                .build());
    }

    private void composition(Item parent, Item child, ReviewStatus reviewStatus) {
        itemCompositionRepository.saveAndFlush(ItemComposition.builder()
                .parentItem(parent)
                .childItem(child)
                .necessity(Necessity.STANDARD)
                .sourceType(SourceType.MANUAL)
                .reviewStatus(reviewStatus)
                .build());
    }
}
