package com.profetai.industrymap.support;

import com.profetai.industrymap.enums.CompanyRole;
import com.profetai.industrymap.enums.Necessity;
import com.profetai.industrymap.enums.PeriodType;
import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.enums.ShareMetric;
import com.profetai.industrymap.enums.SourceType;
import com.profetai.industrymap.model.Company;
import com.profetai.industrymap.model.CompanyItemRole;
import com.profetai.industrymap.model.Item;
import com.profetai.industrymap.model.ItemComposition;
import com.profetai.industrymap.model.MarketShare;
import com.profetai.industrymap.repository.CompanyItemRoleRepository;
import com.profetai.industrymap.repository.CompanyRepository;
import com.profetai.industrymap.repository.ItemCompositionRepository;
import com.profetai.industrymap.repository.ItemRepository;
import com.profetai.industrymap.repository.MarketShareRepository;
import com.profetai.industrymap.service.item.ItemCompositionService;
import com.profetai.industrymap.service.supply.CompanyItemRoleService;
import com.profetai.industrymap.service.supply.MarketShareService;
import com.profetai.industrymap.util.NameNormalizer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 對外查詢的資料庫往返次數必須與結果筆數脫鉤。
 *
 * <p>N+1 是靜默劣化：資料量小時完全看不出來，等到看得出來時已經是線上問題，
 * 而且沒有任何東西會在它長回來時攔住。這支測試就是那個攔住的東西。</p>
 *
 * <p><b>斷言刻意寫成「兩種扇出的 SQL 筆數相同」，而不是「不得超過 N 筆」</b>：
 * 後者綁的是某個當下的實作細節，任何無關的調整（多一次存在性檢查、少一次批次載入）
 * 都會讓它紅。反覆誤報的守衛會被停用，於是真的長回線性成長時反而沒人發現。
 * 「與扇出無關」則直接對應要守的性質本身——多回 17 筆資料不該多送 17 次查詢。</p>
 *
 * <p><b>大扇出刻意設在批次抓取的設定值（50）之上</b>。這一點是這支測試有沒有用的關鍵：
 * 若兩種扇出都小於批次值，全域的批次抓取會把兩者都壓成一次 {@code IN} 查詢，於是
 * 「筆數相同」在**有沒有 {@code @EntityGraph} 都成立**——守衛看起來是綠的，實際上什麼也沒守。
 * 扇出跨過批次值之後，只有真的 join fetch 才會恆定，靠批次兜底的會退回 {@code ceil(n/50)} 筆。</p>
 *
 * <p>唯一的例外是 {@link #findRanking_queryCount_shouldNotGrowWithFanout}，理由見該處。</p>
 */
class QueryFanoutTest extends AbstractPostgresWebIntegrationTest {

    private static final int SMALL_FANOUT = 3;

    /** 大於 {@code default_batch_fetch_size}（50）：批次抓取兜不住，只有 join fetch 才恆定 */
    private static final int LARGE_FANOUT = 60;

    /** 小於批次值，供無法 join fetch 的 native query 使用；語意見使用處 */
    private static final int BATCHED_LARGE_FANOUT = 20;

    private static final String RUN_ID = Long.toHexString(System.nanoTime());
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private ItemCompositionRepository itemCompositionRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private CompanyItemRoleRepository companyItemRoleRepository;
    @Autowired
    private MarketShareRepository marketShareRepository;
    @Autowired
    private ItemCompositionService itemCompositionService;
    @Autowired
    private CompanyItemRoleService companyItemRoleService;
    @Autowired
    private MarketShareService marketShareService;

    private Statistics statistics;

    @BeforeEach
    void enableStatistics() {
        // 執行期開啟，不動 application.properties——那會讓本測試獨佔一個 Spring context
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
    }

    @Test
    @DisplayName("查組成關係的 SQL 筆數不得隨組成數成長")
    void findCompositions_queryCount_shouldNotGrowWithFanout() {
        Item small = itemWithComponents(SMALL_FANOUT);
        Item large = itemWithComponents(LARGE_FANOUT);

        assertConstantQueryCount("findCompositions",
                () -> itemCompositionService.findCompositions(small.getId(), false),
                () -> itemCompositionService.findCompositions(large.getId(), false));
    }

    @Test
    @DisplayName("展開組成樹的 SQL 筆數不得隨下層零件數成長")
    void expandTree_queryCount_shouldNotGrowWithFanout() {
        Item small = itemWithComponents(SMALL_FANOUT);
        Item large = itemWithComponents(LARGE_FANOUT);

        assertConstantQueryCount("expandTree",
                () -> itemCompositionService.expandTree(small.getId(), 1, null, false),
                () -> itemCompositionService.expandTree(large.getId(), 1, null, false));
    }

    @Test
    @DisplayName("反向查終端成品的 SQL 筆數不得隨可達成品數成長")
    void findReachableEndProducts_queryCount_shouldNotGrowWithFanout() {
        Item small = partUnderProducts(SMALL_FANOUT);
        Item large = partUnderProducts(LARGE_FANOUT);

        assertConstantQueryCount("findReachableEndProducts",
                () -> itemCompositionService.findReachableEndProducts(small.getId(), false),
                () -> itemCompositionService.findReachableEndProducts(large.getId(), false));
    }

    @Test
    @DisplayName("查供應公司的 SQL 筆數不得隨供應商數成長")
    void findSuppliers_queryCount_shouldNotGrowWithFanout() {
        Item small = partWithSuppliers(SMALL_FANOUT);
        Item large = partWithSuppliers(LARGE_FANOUT);

        assertConstantQueryCount("findSuppliers",
                () -> companyItemRoleService.findSuppliers(small.getId(), null, false),
                () -> companyItemRoleService.findSuppliers(large.getId(), null, false));
    }

    /**
     * 市佔率排名是 native query，Hibernate 不會對它套用 {@code @EntityGraph}，公司只能靠批次抓取補撈，
     * 因此筆數是 {@code ceil(n/50)} 而非恆定——扇出跨過批次值必然多一筆。
     *
     * <p>所以這一格<b>刻意</b>把大扇出留在批次值以下：它守得住的是「沒有逐筆載入」
     * （真的長回 N+1 時是 3 → 23，照樣紅），守不住「不依賴批次大小」。
     * 要讓它與其他四格一樣強，得把這支查詢改寫成 JPQL 並 join fetch；那會偏離
     * 「手寫查詢一律 native」的既有慣例，屬另一個決定，不在本次夾帶。</p>
     */
    @Test
    @DisplayName("查市佔率排名的 SQL 筆數不得隨排名筆數成長")
    void findRanking_queryCount_shouldNotGrowWithFanout() {
        Item small = partWithMarketShares(SMALL_FANOUT);
        Item large = partWithMarketShares(BATCHED_LARGE_FANOUT);

        assertConstantQueryCount("findRanking", BATCHED_LARGE_FANOUT,
                () -> marketShareService.findRanking(
                        small.getId(), PeriodType.YEAR, "2024", "global", ShareMetric.REVENUE, false),
                () -> marketShareService.findRanking(
                        large.getId(), PeriodType.YEAR, "2024", "global", ShareMetric.REVENUE, false));
    }

    private void assertConstantQueryCount(String label, Runnable smallFanout, Runnable largeFanout) {
        assertConstantQueryCount(label, LARGE_FANOUT, smallFanout, largeFanout);
    }

    private void assertConstantQueryCount(String label, int largeFanout,
                                          Runnable smallFanoutCall, Runnable largeFanoutCall) {
        long onSmall = countStatements(smallFanoutCall);
        long onLarge = countStatements(largeFanoutCall);

        assertEquals(onSmall, onLarge, () -> String.format("""
                %s 的 SQL 筆數隨結果筆數成長：扇出 %d 送 %d 筆、扇出 %d 送 %d 筆。
                多回幾筆資料就多送幾次查詢，這是 N+1。常見成因與對策：
                - 逐筆初始化 LAZY 關聯 → 衍生查詢加 @EntityGraph（join fetch，永遠一筆）
                - 只靠全域 default_batch_fetch_size 兜底 → 扇出超過批次值就退回 ceil(n/批次) 筆，
                  這支測試的大扇出刻意設在批次值之上，就是為了讓這種情況也紅
                - 迴圈中逐節點查詢 → 走訪交給資料庫（遞迴 CTE），不要把資料庫當逐筆讀取的儲存體
                """, label, SMALL_FANOUT, onSmall, largeFanout, onLarge));
    }

    /** 清空 persistence context 再量，否則第一級快取會讓第二次呼叫看起來不需要查詢 */
    private long countStatements(Runnable action) {
        entityManager.flush();
        entityManager.clear();
        statistics.clear();
        action.run();
        return statistics.getPrepareStatementCount();
    }

    // ---------------------------------------------------------------------
    // fixture
    // ---------------------------------------------------------------------

    /** 一個終端成品，底下掛 fanout 個零件 */
    private Item itemWithComponents(int fanout) {
        Item product = item(true);
        for (int i = 0; i < fanout; i++) {
            composition(product, item(false));
        }
        return product;
    }

    /** 一個零件，掛在 fanout 個不同的終端成品之下 */
    private Item partUnderProducts(int fanout) {
        Item part = item(false);
        for (int i = 0; i < fanout; i++) {
            composition(item(true), part);
        }
        return part;
    }

    /** 一個零件，有 fanout 家供應商 */
    private Item partWithSuppliers(int fanout) {
        Item part = item(false);
        for (int i = 0; i < fanout; i++) {
            role(company(), part);
        }
        return part;
    }

    /** 一個零件，同一切面有 fanout 筆市佔率 */
    private Item partWithMarketShares(int fanout) {
        Item part = item(false);
        for (int i = 0; i < fanout; i++) {
            marketShare(company(), part);
        }
        return part;
    }

    private String uniqueName(String label) {
        return FIXTURE_PREFIX + "fanout" + RUN_ID + label + SEQUENCE.incrementAndGet();
    }

    private Item item(boolean endProduct) {
        String displayName = uniqueName("item");
        return itemRepository.saveAndFlush(Item.builder()
                .normalizedName(NameNormalizer.normalize(displayName))
                .displayName(displayName)
                .isEndProduct(endProduct)
                .sourceType(SourceType.MANUAL)
                .reviewStatus(ReviewStatus.VERIFIED)
                .build());
    }

    private void composition(Item parent, Item child) {
        itemCompositionRepository.saveAndFlush(ItemComposition.builder()
                .parentItem(parent)
                .childItem(child)
                .necessity(Necessity.STANDARD)
                .sourceType(SourceType.MANUAL)
                .reviewStatus(ReviewStatus.VERIFIED)
                .build());
    }

    private Company company() {
        String displayName = uniqueName("company");
        return companyRepository.saveAndFlush(Company.builder()
                .normalizedName(NameNormalizer.normalize(displayName))
                .displayName(displayName)
                .country("TW")
                .isPublic(true)
                .sourceType(SourceType.MANUAL)
                .reviewStatus(ReviewStatus.VERIFIED)
                .build());
    }

    private void role(Company company, Item item) {
        companyItemRoleRepository.saveAndFlush(CompanyItemRole.builder()
                .company(company)
                .item(item)
                .companyRole(CompanyRole.MANUFACTURE)
                .sourceType(SourceType.MANUAL)
                .reviewStatus(ReviewStatus.VERIFIED)
                .build());
    }

    private void marketShare(Company company, Item item) {
        marketShareRepository.saveAndFlush(MarketShare.builder()
                .company(company)
                .item(item)
                .periodType(PeriodType.YEAR)
                .periodValue("2024")
                .region("global")
                .metric(ShareMetric.REVENUE)
                .sharePercent(BigDecimal.valueOf(1))
                .sourceType(SourceType.MANUAL)
                .reviewStatus(ReviewStatus.VERIFIED)
                .build());
    }
}
