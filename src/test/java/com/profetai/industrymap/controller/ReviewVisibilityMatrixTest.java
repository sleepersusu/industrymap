package com.profetai.industrymap.controller;

import com.jayway.jsonpath.JsonPath;
import com.profetai.industrymap.enums.CompanyRole;
import com.profetai.industrymap.enums.IdentifierType;
import com.profetai.industrymap.enums.Necessity;
import com.profetai.industrymap.enums.PeriodType;
import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.enums.ShareMetric;
import com.profetai.industrymap.enums.SourceType;
import com.profetai.industrymap.model.Company;
import com.profetai.industrymap.model.CompanyAlias;
import com.profetai.industrymap.model.CompanyIdentifier;
import com.profetai.industrymap.model.CompanyItemRole;
import com.profetai.industrymap.model.HotspotPoint;
import com.profetai.industrymap.model.Item;
import com.profetai.industrymap.model.ItemAlias;
import com.profetai.industrymap.model.ItemComposition;
import com.profetai.industrymap.model.ItemHotspot;
import com.profetai.industrymap.model.ItemImage;
import com.profetai.industrymap.model.MarketShare;
import com.profetai.industrymap.repository.CompanyAliasRepository;
import com.profetai.industrymap.repository.CompanyIdentifierRepository;
import com.profetai.industrymap.repository.CompanyItemRoleRepository;
import com.profetai.industrymap.repository.CompanyRepository;
import com.profetai.industrymap.repository.ItemAliasRepository;
import com.profetai.industrymap.repository.ItemCompositionRepository;
import com.profetai.industrymap.repository.ItemHotspotRepository;
import com.profetai.industrymap.repository.ItemImageRepository;
import com.profetai.industrymap.repository.ItemRepository;
import com.profetai.industrymap.repository.MarketShareRepository;
import com.profetai.industrymap.support.AbstractPostgresWebIntegrationTest;
import com.profetai.industrymap.util.NameNormalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 「已駁回的資料不得外露、也不得影響結果」的集中驗證，以<b>端點 × 可觸及的內容表</b>矩陣驅動。
 *
 * <p>這條規則已經漏過三次，每次的層次還不一樣：漏過濾主查詢表（識別碼）、漏過濾子查詢的表（別名）、
 * 漏過濾關係所指向的實體（供應角色指向的品類節點）。三次都是事後由 code review 抓到，
 * 因為可見性只由各端點自己的測試零散覆蓋，沒有一個統一的執行點。</p>
 *
 * <p>本測試把規則變成會紅的 build：矩陣逐格驗證，另有一道覆蓋率守衛掃出所有對外 GET 端點，
 * 未登記進矩陣即 fail。守衛只能強制「有登記」，強制不了「登記正確」——這是刻意的取捨，
 * 但至少讓遺漏從「靜默發生」變成「必須主動說謊」。</p>
 *
 * <p>刻意<b>不</b>驗證草稿語意：各端點對草稿的取捨不同（{@code ReviewScopes} 明確區分關係與實體），
 * 混進來會讓這組測試變成規格重述而非防呆。每格也只斷言「該筆已駁回資料及其影響不出現」，
 * 不順便斷言回應的其他內容，否則端點一改這組測試就整批紅而被停用。</p>
 *
 * <p>fixture 名稱一律帶 {@code FIXTURE_PREFIX} 與本次執行的識別碼，斷言限定在自建資料上，
 * 不對全表數字下斷言——沒有 Docker 時測試跑在共用的開發資料庫上。</p>
 */
class ReviewVisibilityMatrixTest extends AbstractPostgresWebIntegrationTest {

    /**
     * 守衛的掃描範圍：本專案<b>根 package</b> 底下的所有 handler。
     *
     * <p>刻意不用路徑前綴（{@code /api}）也不用 {@code .controller} 子 package：兩者都有同一個對稱的漏洞——
     * 掛在 {@code /api} 以外路徑的端點、或宣告在 {@code controller} 以外 package 的端點
     * （例如日後的 {@code web.PatentController}）會被靜默放行，而那正是本測試要防的失效方式。
     * 以根 package 判定則涵蓋本專案寫的每一支 handler，springdoc 與 actuator 因不在此 package 而自然排除。</p>
     */
    private static final String APPLICATION_PACKAGE = "com.profetai.industrymap";

    /** 失敗訊息一律附上規則出處，紅掉的人不必先讀測試程式碼才知道要做什麼 */
    private static final String RULE_HINT = """
            見 .claude/rules/architecture.md「審核狀態過濾是查詢的預設義務」的五問清單：
            (1) 主查詢的表 (2) join／子查詢的表 (3) 關係指向的實體 (4) 該欄位屬關係還是實體
            (5) 過濾必須發生在計數、分頁與組裝錯誤訊息之前。""";

    private static final String LIST_END_PRODUCTS = "GET /api/products";
    private static final String COMPONENT_TREE = "GET /api/products/{id}/components";
    private static final String RESOLVE_ITEM = "GET /api/items";
    private static final String GET_ITEM = "GET /api/items/{id}";
    private static final String LIST_COMPOSITIONS = "GET /api/items/{id}/compositions";
    private static final String REACHABLE_END_PRODUCTS = "GET /api/items/{id}/end-products";
    private static final String LIST_SUPPLIERS = "GET /api/items/{id}/suppliers";
    private static final String MARKET_SHARE_RANKING = "GET /api/items/{id}/market-share";
    private static final String LIST_ITEM_IMAGES = "GET /api/items/{id}/images";
    private static final String LIST_COMPANIES = "GET /api/companies";
    private static final String GET_COMPANY = "GET /api/companies/{code}";
    private static final String LIST_COMPANY_ITEMS = "GET /api/companies/{code}/items";

    /** 帶 review_status 的十張內容表 */
    private enum ContentTable {
        ITEM, ITEM_ALIAS, ITEM_COMPOSITION, COMPANY, COMPANY_ALIAS, COMPANY_IDENTIFIER, COMPANY_ITEM_ROLE,
        MARKET_SHARE, ITEM_IMAGE, ITEM_HOTSPOT
    }

    /** 過濾可能漏掉的三個層次（design Context）；三次實際缺陷各佔一種 */
    private enum Layer {

        MAIN_TABLE("主查詢的表"),
        JOINED_TABLE("join／子查詢或另一次查詢所及的表"),
        REFERENCED_ENTITY("關係所指向的實體");

        private final String label;

        Layer(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    private static final Map<String, EndpointCoverage> MATRIX = buildMatrix();

    /**
     * 一定不存在的節點 id，用來對照「已駁回的節點必須與不存在的節點表現完全一致」。
     *
     * <p>不寫死預期的狀態碼是刻意的：各端點對「查無此節點」的既有處理本來就不同
     * （公司列表回 404、供應商與市佔率回空清單）。要守的不是某個狀態碼，
     * 而是<b>兩者不可區分</b>——只要有差異，呼叫端就能反推出一筆對外不存在的資料確實存在。</p>
     */
    private static final long MISSING_ITEM_ID = Long.MAX_VALUE;

    /** 同一次執行內的 fixture 識別，避免與開發資料庫殘留的資料撞正規化名稱唯一鍵 */
    private static final String RUN_ID = Long.toHexString(System.nanoTime());
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;
    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private ItemAliasRepository itemAliasRepository;
    @Autowired
    private ItemCompositionRepository itemCompositionRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private CompanyAliasRepository companyAliasRepository;
    @Autowired
    private CompanyIdentifierRepository companyIdentifierRepository;
    @Autowired
    private CompanyItemRoleRepository companyItemRoleRepository;
    @Autowired
    private MarketShareRepository marketShareRepository;
    @Autowired
    private ItemImageRepository itemImageRepository;
    @Autowired
    private ItemHotspotRepository itemHotspotRepository;

    /**
     * 端點可觸及的內容表，以 repository 的實際 SQL 與 service 的組裝流程為準，不以端點名稱推測。
     */
    private static Map<String, EndpointCoverage> buildMatrix() {
        Map<String, EndpointCoverage> matrix = new LinkedHashMap<>();

        matrix.put(LIST_END_PRODUCTS, EndpointCoverage.of(
                cell(ContentTable.ITEM, Layer.MAIN_TABLE, "終端成品列表的主查詢表")));

        matrix.put(COMPONENT_TREE, EndpointCoverage.of(
                cell(ContentTable.ITEM, Layer.MAIN_TABLE, "路徑指名的根節點"),
                cell(ContentTable.ITEM_COMPOSITION, Layer.MAIN_TABLE, "組成樹沿 part-of 邊展開"),
                cell(ContentTable.ITEM, Layer.REFERENCED_ENTITY, "邊所指向的下層節點")));

        matrix.put(RESOLVE_ITEM, EndpointCoverage.of(
                cell(ContentTable.ITEM, Layer.MAIN_TABLE, "以正規化名稱解析節點"),
                cell(ContentTable.ITEM_ALIAS, Layer.JOINED_TABLE, "名稱查不到時改以別名解析")));

        matrix.put(GET_ITEM, EndpointCoverage.of(
                cell(ContentTable.ITEM, Layer.MAIN_TABLE, "單筆節點")));

        matrix.put(LIST_COMPOSITIONS, EndpointCoverage.of(
                cell(ContentTable.ITEM, Layer.MAIN_TABLE, "路徑指名的節點"),
                cell(ContentTable.ITEM_COMPOSITION, Layer.MAIN_TABLE, "本節點上下兩個方向的組成關係"),
                cell(ContentTable.ITEM, Layer.REFERENCED_ENTITY, "關係另一端的節點")));

        matrix.put(REACHABLE_END_PRODUCTS, EndpointCoverage.of(
                cell(ContentTable.ITEM, Layer.MAIN_TABLE, "路徑指名的起點節點"),
                cell(ContentTable.ITEM_COMPOSITION, Layer.MAIN_TABLE, "沿 part-of 邊向上回溯"),
                cell(ContentTable.ITEM, Layer.REFERENCED_ENTITY, "回溯路徑上的上層節點")));

        matrix.put(LIST_SUPPLIERS, EndpointCoverage.of(
                cell(ContentTable.ITEM, Layer.MAIN_TABLE, "路徑指名的零件節點"),
                cell(ContentTable.COMPANY_ITEM_ROLE, Layer.MAIN_TABLE, "零件的供應角色"),
                cell(ContentTable.COMPANY, Layer.REFERENCED_ENTITY, "角色所指向的公司"),
                cell(ContentTable.COMPANY_IDENTIFIER, Layer.JOINED_TABLE, "組出公司對外識別時另查主要識別碼")));

        matrix.put(MARKET_SHARE_RANKING, EndpointCoverage.of(
                cell(ContentTable.ITEM, Layer.MAIN_TABLE, "路徑指名的零件節點"),
                cell(ContentTable.MARKET_SHARE, Layer.MAIN_TABLE, "市佔率排名的主查詢表"),
                cell(ContentTable.COMPANY, Layer.REFERENCED_ENTITY, "市佔率所指向的公司"),
                cell(ContentTable.COMPANY_IDENTIFIER, Layer.JOINED_TABLE, "組出公司對外識別時另查主要識別碼")));

        matrix.put(LIST_ITEM_IMAGES, EndpointCoverage.of(
                cell(ContentTable.ITEM, Layer.MAIN_TABLE, "路徑指名的節點"),
                cell(ContentTable.ITEM_IMAGE, Layer.MAIN_TABLE, "該節點的圖片清單"),
                cell(ContentTable.ITEM_HOTSPOT, Layer.JOINED_TABLE, "本頁圖片的熱區一次撈齊後巢狀組進回應"),
                cell(ContentTable.ITEM, Layer.REFERENCED_ENTITY, "熱區所指向的品類節點")));

        matrix.put(LIST_COMPANIES, EndpointCoverage.of(
                cell(ContentTable.COMPANY, Layer.MAIN_TABLE, "公司列表的主查詢表"),
                cell(ContentTable.ITEM, Layer.MAIN_TABLE, "itemId 參數指名的品類節點（存在性檢查）"),
                cell(ContentTable.COMPANY_ALIAS, Layer.JOINED_TABLE, "名稱關鍵字比對的 EXISTS 子查詢"),
                cell(ContentTable.COMPANY_ITEM_ROLE, Layer.JOINED_TABLE, "itemId／companyRole 過濾的 EXISTS 子查詢"),
                cell(ContentTable.ITEM, Layer.REFERENCED_ENTITY, "供應角色所指向的品類節點"),
                cell(ContentTable.COMPANY_IDENTIFIER, Layer.JOINED_TABLE, "本頁公司的識別碼一次撈齊後組進回應")));

        matrix.put(GET_COMPANY, EndpointCoverage.of(
                cell(ContentTable.COMPANY, Layer.MAIN_TABLE, "解析結果的公司本體"),
                cell(ContentTable.COMPANY_IDENTIFIER, Layer.MAIN_TABLE, "代號解析，以及回應中的識別碼清單")));

        matrix.put(LIST_COMPANY_ITEMS, EndpointCoverage.of(
                cell(ContentTable.COMPANY_IDENTIFIER, Layer.MAIN_TABLE, "路徑的代號解析"),
                cell(ContentTable.COMPANY, Layer.MAIN_TABLE, "解析結果的公司本體"),
                cell(ContentTable.COMPANY_ITEM_ROLE, Layer.MAIN_TABLE, "該公司的供應角色"),
                cell(ContentTable.ITEM, Layer.REFERENCED_ENTITY, "角色所指向的品類節點")));

        return Map.copyOf(matrix);
    }

    // ---------------------------------------------------------------------
    // 2. 覆蓋率守衛：新端點不登記就過不了 build
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("每個對外 GET 端點都必須登記進可見性矩陣")
    void everyGetEndpoint_shouldBeRegisteredInMatrix() {
        Set<String> exposed = exposedGetEndpoints();

        List<String> unregistered = exposed.stream().filter(endpoint -> !MATRIX.containsKey(endpoint)).toList();
        List<String> stale = MATRIX.keySet().stream().filter(endpoint -> !exposed.contains(endpoint)).sorted().toList();

        assertAll(
                () -> assertTrue(unregistered.isEmpty(),
                        "以下對外 GET 端點未登記進可見性矩陣，無法確認它有沒有擋掉已駁回資料：\n"
                                + String.join("\n", unregistered) + "\n" + RULE_HINT),
                () -> assertTrue(stale.isEmpty(),
                        "以下端點登記在矩陣中卻已不存在，請一併移除登記：\n" + String.join("\n", stale)));
    }

    @Test
    @DisplayName("登記為不觸及任何內容表的端點必須寫明理由")
    void matrixEntryWithoutContentTable_shouldStateReason() {
        List<String> unexplained = MATRIX.entrySet().stream()
                .filter(entry -> entry.getValue().cells().isEmpty())
                .filter(entry -> entry.getValue().noContentTableReason().isBlank())
                .map(Map.Entry::getKey)
                .toList();

        assertTrue(unexplained.isEmpty(),
                "以下端點登記為不觸及任何內容表卻未寫明理由；可以宣告「這裡不需要過濾」，但不得留白：\n"
                        + String.join("\n", unexplained) + "\n" + RULE_HINT);
    }

    // ---------------------------------------------------------------------
    // 3. 直接外露：已駁回的資料本身不得出現在回應中
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("已駁回的識別碼不得列進公司的單筆回應")
    void getCompanyByCode_rejectedIdentifier_shouldNotListIt() throws Exception {
        // Given：公司與主要識別碼都已驗證，另有一筆被駁回的誤建識別碼
        Company company = company(ReviewStatus.VERIFIED);
        String primaryValue = identifierValue();
        String rejectedValue = identifierValue();
        identifier(company, IdentifierType.TWSE, primaryValue, true, ReviewStatus.VERIFIED);
        identifier(company, IdentifierType.TPEX, rejectedValue, false, ReviewStatus.REJECTED);

        // When
        String body = okBody("/api/companies/TWSE:" + primaryValue);

        // Then
        assertFalse(body.contains(rejectedValue),
                failure(GET_COMPANY, ContentTable.COMPANY_IDENTIFIER, Layer.MAIN_TABLE));
    }

    @Test
    @DisplayName("已駁回的品類節點不得出現在終端成品列表，也不得計入總筆數")
    void listEndProducts_rejectedItem_shouldNotAppearNorBeCounted() throws Exception {
        Item rejected = item(ReviewStatus.REJECTED, true);

        String body = okBody("/api/products?name=" + rejected.getDisplayName());

        assertAll(
                () -> assertFalse(body.contains(rejected.getDisplayName()),
                        failure(LIST_END_PRODUCTS, ContentTable.ITEM, Layer.MAIN_TABLE)),
                () -> assertEquals(0, (int) JsonPath.read(body, "$.data.totalElements"),
                        failure(LIST_END_PRODUCTS, ContentTable.ITEM, Layer.MAIN_TABLE)));
    }

    @Test
    @DisplayName("已駁回的品類節點不得經名稱解析取得")
    void resolveItemByName_rejectedItem_shouldReturnNotFound() throws Exception {
        Item rejected = item(ReviewStatus.REJECTED, false);

        MockHttpServletResponse response = fetch("/api/items?name=" + rejected.getDisplayName());

        assertEquals(HttpStatus.NOT_FOUND.value(), response.getStatus(),
                failure(RESOLVE_ITEM, ContentTable.ITEM, Layer.MAIN_TABLE));
    }

    @Test
    @DisplayName("已駁回的品類節點不得經 id 取得")
    void getItemById_rejectedItem_shouldReturnNotFound() throws Exception {
        Item rejected = item(ReviewStatus.REJECTED, false);

        MockHttpServletResponse response = fetch("/api/items/" + rejected.getId());

        assertEquals(HttpStatus.NOT_FOUND.value(), response.getStatus(),
                failure(GET_ITEM, ContentTable.ITEM, Layer.MAIN_TABLE));
    }

    @Test
    @DisplayName("已駁回的組成關係不得出現在組成樹")
    void getComponents_rejectedComposition_shouldNotAppear() throws Exception {
        Item product = item(ReviewStatus.VERIFIED, true);
        Item part = item(ReviewStatus.VERIFIED, false);
        composition(product, part, ReviewStatus.REJECTED);

        String body = okBody("/api/products/" + product.getId() + "/components?depth=3");

        assertFalse(body.contains(part.getDisplayName()),
                failure(COMPONENT_TREE, ContentTable.ITEM_COMPOSITION, Layer.MAIN_TABLE));
    }

    @Test
    @DisplayName("已駁回的組成關係不得出現在節點的組成關係清單")
    void getCompositions_rejectedComposition_shouldNotAppear() throws Exception {
        Item product = item(ReviewStatus.VERIFIED, true);
        Item part = item(ReviewStatus.VERIFIED, false);
        composition(product, part, ReviewStatus.REJECTED);

        String body = okBody("/api/items/" + product.getId() + "/compositions");

        assertFalse(childItemIds(body).contains(String.valueOf(part.getId())),
                failure(LIST_COMPOSITIONS, ContentTable.ITEM_COMPOSITION, Layer.MAIN_TABLE));
    }

    @Test
    @DisplayName("已駁回的組成關係不得讓零件回溯到終端成品")
    void getEndProducts_rejectedComposition_shouldNotReachProduct() throws Exception {
        Item product = item(ReviewStatus.VERIFIED, true);
        Item part = item(ReviewStatus.VERIFIED, false);
        composition(product, part, ReviewStatus.REJECTED);

        String body = okBody("/api/items/" + part.getId() + "/end-products");

        assertFalse(body.contains(product.getDisplayName()),
                failure(REACHABLE_END_PRODUCTS, ContentTable.ITEM_COMPOSITION, Layer.MAIN_TABLE));
    }

    @Test
    @DisplayName("已駁回的供應角色不得出現在零件的供應公司清單")
    void getSuppliers_rejectedRole_shouldNotAppear() throws Exception {
        Item part = item(ReviewStatus.VERIFIED, false);
        Company company = company(ReviewStatus.VERIFIED);
        role(company, part, CompanyRole.MANUFACTURE, ReviewStatus.REJECTED);

        String body = okBody("/api/items/" + part.getId() + "/suppliers");

        assertFalse(body.contains(company.getDisplayName()),
                failure(LIST_SUPPLIERS, ContentTable.COMPANY_ITEM_ROLE, Layer.MAIN_TABLE));
    }

    @Test
    @DisplayName("已駁回的市佔率不得出現在排名")
    void getMarketShare_rejectedShare_shouldNotAppear() throws Exception {
        Item part = item(ReviewStatus.VERIFIED, false);
        Company company = company(ReviewStatus.VERIFIED);
        marketShare(company, part, ReviewStatus.REJECTED);

        String body = okBody(marketShareUri(part));

        assertFalse(body.contains(company.getDisplayName()),
                failure(MARKET_SHARE_RANKING, ContentTable.MARKET_SHARE, Layer.MAIN_TABLE));
    }

    @Test
    @DisplayName("已駁回的圖片不得出現在節點的圖片清單，其熱區也一併不得出現")
    void getItemImages_rejectedImage_shouldNotAppearWithItsHotspots() throws Exception {
        Item bike = item(ReviewStatus.VERIFIED, true);
        Item brake = item(ReviewStatus.VERIFIED, false);
        ItemImage rejectedImage = image(bike, ReviewStatus.REJECTED);
        String positionLabel = hotspot(rejectedImage, brake, ReviewStatus.VERIFIED);

        String body = okBody("/api/items/" + bike.getId() + "/images");

        assertAll(
                () -> assertFalse(body.contains(rejectedImage.getStorageKey()),
                        failure(LIST_ITEM_IMAGES, ContentTable.ITEM_IMAGE, Layer.MAIN_TABLE)),
                () -> assertFalse(body.contains(positionLabel),
                        failure(LIST_ITEM_IMAGES, ContentTable.ITEM_IMAGE, Layer.MAIN_TABLE)));
    }

    @Test
    @DisplayName("已駁回的熱區不得出現在圖片的熱區清單")
    void getItemImages_rejectedHotspot_shouldNotAppear() throws Exception {
        Item bike = item(ReviewStatus.VERIFIED, true);
        Item brake = item(ReviewStatus.VERIFIED, false);
        String positionLabel = hotspot(image(bike, ReviewStatus.VERIFIED), brake, ReviewStatus.REJECTED);

        String body = okBody("/api/items/" + bike.getId() + "/images");

        assertFalse(body.contains(positionLabel),
                failure(LIST_ITEM_IMAGES, ContentTable.ITEM_HOTSPOT, Layer.JOINED_TABLE));
    }

    @Test
    @DisplayName("已駁回的公司不得出現在公司列表，也不得計入總筆數")
    void listCompanies_rejectedCompany_shouldNotAppearNorBeCounted() throws Exception {
        Company rejected = company(ReviewStatus.REJECTED);

        String body = okBody("/api/companies?name=" + rejected.getDisplayName());

        assertAll(
                () -> assertFalse(body.contains(rejected.getDisplayName()),
                        failure(LIST_COMPANIES, ContentTable.COMPANY, Layer.MAIN_TABLE)),
                () -> assertEquals(0, (int) JsonPath.read(body, "$.data.totalElements"),
                        failure(LIST_COMPANIES, ContentTable.COMPANY, Layer.MAIN_TABLE)));
    }

    @Test
    @DisplayName("已駁回的識別碼不得列進公司列表的回應")
    void listCompanies_rejectedIdentifier_shouldNotListIt() throws Exception {
        Company company = company(ReviewStatus.VERIFIED);
        String rejectedValue = identifierValue();
        identifier(company, IdentifierType.TWSE, identifierValue(), true, ReviewStatus.VERIFIED);
        identifier(company, IdentifierType.TPEX, rejectedValue, false, ReviewStatus.REJECTED);

        String body = okBody("/api/companies?name=" + company.getDisplayName());

        assertFalse(body.contains(rejectedValue),
                failure(LIST_COMPANIES, ContentTable.COMPANY_IDENTIFIER, Layer.JOINED_TABLE));
    }

    // ---------------------------------------------------------------------
    // 4. 間接影響：已駁回的資料不得改變結果
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("已駁回的公司別名不得使公司被搜尋到")
    void listCompanies_rejectedAlias_shouldNotMakeCompanySearchable() throws Exception {
        Company company = company(ReviewStatus.VERIFIED);
        String alias = companyAlias(company, ReviewStatus.REJECTED);

        String body = okBody("/api/companies?name=" + alias);

        assertFalse(body.contains(company.getDisplayName()),
                failure(LIST_COMPANIES, ContentTable.COMPANY_ALIAS, Layer.JOINED_TABLE));
    }

    @Test
    @DisplayName("已駁回的品類別名不得使節點被解析到")
    void resolveItemByName_rejectedAlias_shouldReturnNotFound() throws Exception {
        Item item = item(ReviewStatus.VERIFIED, false);
        String alias = itemAlias(item, ReviewStatus.REJECTED);

        MockHttpServletResponse response = fetch("/api/items?name=" + alias);

        assertEquals(HttpStatus.NOT_FOUND.value(), response.getStatus(),
                failure(RESOLVE_ITEM, ContentTable.ITEM_ALIAS, Layer.JOINED_TABLE));
    }

    @Test
    @DisplayName("已駁回的識別碼不得使合法的裸代號查詢變成 409")
    void getCompanyByCode_rejectedIdentifierSharingBareCode_shouldStillResolve() throws Exception {
        // Given：兩家公司共用同一個裸代號值，但其中一筆識別碼已被駁回——對外只剩一個合法解
        String bareCode = identifierValue();
        Company legitimate = company(ReviewStatus.VERIFIED);
        Company mistaken = company(ReviewStatus.VERIFIED);
        identifier(legitimate, IdentifierType.TWSE, bareCode, true, ReviewStatus.VERIFIED);
        identifier(mistaken, IdentifierType.TPEX, bareCode, true, ReviewStatus.REJECTED);

        MockHttpServletResponse response = fetch("/api/companies/" + bareCode);

        assertEquals(HttpStatus.OK.value(), response.getStatus(),
                failure(GET_COMPANY, ContentTable.COMPANY_IDENTIFIER, Layer.MAIN_TABLE));
    }

    @Test
    @DisplayName("已駁回的識別碼不得被寫進 409 的候選清單")
    void getCompanyByCode_ambiguousBareCode_shouldNotListRejectedCandidate() throws Exception {
        // Given：三家共用裸代號，其中一筆識別碼已駁回——照訊息重送必須會成功，
        // 因此候選清單不得含那一筆
        String bareCode = identifierValue();
        identifier(company(ReviewStatus.VERIFIED), IdentifierType.TWSE, bareCode, true, ReviewStatus.VERIFIED);
        identifier(company(ReviewStatus.VERIFIED), IdentifierType.TPEX, bareCode, true, ReviewStatus.VERIFIED);
        identifier(company(ReviewStatus.VERIFIED), IdentifierType.NASDAQ, bareCode, true, ReviewStatus.REJECTED);

        MockHttpServletResponse response = fetch("/api/companies/" + bareCode);

        assertAll(
                () -> assertEquals(HttpStatus.CONFLICT.value(), response.getStatus()),
                () -> assertFalse(body(response).contains(IdentifierType.NASDAQ.name() + ":" + bareCode),
                        failure(GET_COMPANY, ContentTable.COMPANY_IDENTIFIER, Layer.MAIN_TABLE)));
    }

    @Test
    @DisplayName("已駁回的公司不得使合法的裸代號查詢變成 409")
    void getCompanyByCode_rejectedCompanySharingBareCode_shouldStillResolve() throws Exception {
        // Given：識別碼兩筆都有效，但其中一家公司已被駁回——它對外不存在，不該造成歧義
        String bareCode = identifierValue();
        Company legitimate = company(ReviewStatus.VERIFIED);
        Company rejected = company(ReviewStatus.REJECTED);
        identifier(legitimate, IdentifierType.TWSE, bareCode, true, ReviewStatus.VERIFIED);
        identifier(rejected, IdentifierType.TPEX, bareCode, true, ReviewStatus.VERIFIED);

        MockHttpServletResponse response = fetch("/api/companies/" + bareCode);

        assertEquals(HttpStatus.OK.value(), response.getStatus(),
                failure(GET_COMPANY, ContentTable.COMPANY, Layer.MAIN_TABLE));
    }

    @Test
    @DisplayName("已駁回的公司不得經代號取得")
    void getCompanyByCode_rejectedCompany_shouldReturnNotFound() throws Exception {
        Company rejected = company(ReviewStatus.REJECTED);
        String value = identifierValue();
        identifier(rejected, IdentifierType.TWSE, value, true, ReviewStatus.VERIFIED);

        MockHttpServletResponse response = fetch("/api/companies/TWSE:" + value);

        assertEquals(HttpStatus.NOT_FOUND.value(), response.getStatus(),
                failure(GET_COMPANY, ContentTable.COMPANY, Layer.MAIN_TABLE));
    }

    // ---------------------------------------------------------------------
    // 5. 關係指向的實體：關係的審核狀態與它指向的實體的審核狀態是兩回事
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("已駁回的供應角色不得使公司出現在公司列表")
    void listCompanies_rejectedRole_shouldNotSurfaceCompany() throws Exception {
        Item part = item(ReviewStatus.VERIFIED, false);
        Company company = company(ReviewStatus.VERIFIED);
        role(company, part, CompanyRole.ASSEMBLY, ReviewStatus.REJECTED);

        String body = okBody("/api/companies?itemId=" + part.getId());

        assertFalse(body.contains(company.getDisplayName()),
                failure(LIST_COMPANIES, ContentTable.COMPANY_ITEM_ROLE, Layer.JOINED_TABLE));
    }

    @Test
    @DisplayName("供應角色指向已駁回的品類節點時不得使公司出現在角色查詢")
    void listCompanies_roleOnRejectedItem_shouldNotSurfaceCompany() throws Exception {
        // 審核逐列套用，節點被駁回時掛在它下面的角色未必一併駁回——「節點已駁回、角色仍有效」是可達狀態
        Item rejectedItem = item(ReviewStatus.REJECTED, false);
        Company company = company(ReviewStatus.VERIFIED);
        role(company, rejectedItem, CompanyRole.ASSEMBLY, ReviewStatus.VERIFIED);

        String body = okBody("/api/companies?companyRole=ASSEMBLY&name=" + company.getDisplayName());

        assertFalse(body.contains(company.getDisplayName()),
                failure(LIST_COMPANIES, ContentTable.ITEM, Layer.REFERENCED_ENTITY));
    }

    @Test
    @DisplayName("組成關係指向已駁回的下層節點時整枝不得出現在組成樹")
    void getComponents_edgeToRejectedChild_shouldSkipBranch() throws Exception {
        Item product = item(ReviewStatus.VERIFIED, true);
        Item rejectedPart = item(ReviewStatus.REJECTED, false);
        composition(product, rejectedPart, ReviewStatus.VERIFIED);

        String body = okBody("/api/products/" + product.getId() + "/components?depth=3");

        assertFalse(body.contains(rejectedPart.getDisplayName()),
                failure(COMPONENT_TREE, ContentTable.ITEM, Layer.REFERENCED_ENTITY));
    }

    @Test
    @DisplayName("組成關係指向已駁回的節點時不得出現在組成關係清單")
    void getCompositions_edgeToRejectedItem_shouldNotAppear() throws Exception {
        Item product = item(ReviewStatus.VERIFIED, true);
        Item rejectedPart = item(ReviewStatus.REJECTED, false);
        composition(product, rejectedPart, ReviewStatus.VERIFIED);

        String body = okBody("/api/items/" + product.getId() + "/compositions");

        assertFalse(childItemIds(body).contains(String.valueOf(rejectedPart.getId())),
                failure(LIST_COMPOSITIONS, ContentTable.ITEM, Layer.REFERENCED_ENTITY));
    }

    @Test
    @DisplayName("組成關係指向已駁回的上層節點時不得出現在可達的終端成品")
    void getEndProducts_edgeToRejectedProduct_shouldNotAppear() throws Exception {
        Item rejectedProduct = item(ReviewStatus.REJECTED, true);
        Item part = item(ReviewStatus.VERIFIED, false);
        composition(rejectedProduct, part, ReviewStatus.VERIFIED);

        String body = okBody("/api/items/" + part.getId() + "/end-products");

        assertFalse(body.contains(rejectedProduct.getDisplayName()),
                failure(REACHABLE_END_PRODUCTS, ContentTable.ITEM, Layer.REFERENCED_ENTITY));
    }

    @Test
    @DisplayName("供應角色指向已駁回的公司時不得出現在供應公司清單")
    void getSuppliers_roleOnRejectedCompany_shouldNotAppear() throws Exception {
        Item part = item(ReviewStatus.VERIFIED, false);
        Company rejectedCompany = company(ReviewStatus.REJECTED);
        role(rejectedCompany, part, CompanyRole.MANUFACTURE, ReviewStatus.VERIFIED);

        String body = okBody("/api/items/" + part.getId() + "/suppliers");

        assertFalse(body.contains(rejectedCompany.getDisplayName()),
                failure(LIST_SUPPLIERS, ContentTable.COMPANY, Layer.REFERENCED_ENTITY));
    }

    @Test
    @DisplayName("市佔率指向已駁回的公司時不得出現在排名")
    void getMarketShare_shareOnRejectedCompany_shouldNotAppear() throws Exception {
        Item part = item(ReviewStatus.VERIFIED, false);
        Company rejectedCompany = company(ReviewStatus.REJECTED);
        marketShare(rejectedCompany, part, ReviewStatus.VERIFIED);

        String body = okBody(marketShareUri(part));

        assertFalse(body.contains(rejectedCompany.getDisplayName()),
                failure(MARKET_SHARE_RANKING, ContentTable.COMPANY, Layer.REFERENCED_ENTITY));
    }

    @Test
    @DisplayName("熱區指向已駁回的品類節點時不得出現在圖片的熱區清單")
    void getItemImages_hotspotOnRejectedItem_shouldNotAppear() throws Exception {
        // 審核逐列套用：節點被駁回時畫在圖上的熱區未必一併駁回。少了這一格，
        // 使用者點下去會下鑽到一個對外根本不存在的節點
        Item bike = item(ReviewStatus.VERIFIED, true);
        Item rejectedPart = item(ReviewStatus.REJECTED, false);
        String positionLabel = hotspot(image(bike, ReviewStatus.VERIFIED), rejectedPart, ReviewStatus.VERIFIED);

        String body = okBody("/api/items/" + bike.getId() + "/images");

        assertAll(
                () -> assertFalse(body.contains(positionLabel),
                        failure(LIST_ITEM_IMAGES, ContentTable.ITEM, Layer.REFERENCED_ENTITY)),
                () -> assertFalse(body.contains(rejectedPart.getDisplayName()),
                        failure(LIST_ITEM_IMAGES, ContentTable.ITEM, Layer.REFERENCED_ENTITY)));
    }

    @Test
    @DisplayName("已駁回的主要識別碼不得成為供應公司的對外識別")
    void getSuppliers_rejectedPrimaryIdentifier_shouldNotBecomeReference() throws Exception {
        // 對外識別是呼叫端回頭查詢用的鍵，帶上一個不外露的識別碼等於給出一個查不回公司的值
        Item part = item(ReviewStatus.VERIFIED, false);
        Company company = company(ReviewStatus.VERIFIED);
        String rejectedValue = identifierValue();
        identifier(company, IdentifierType.TWSE, rejectedValue, true, ReviewStatus.REJECTED);
        role(company, part, CompanyRole.MANUFACTURE, ReviewStatus.VERIFIED);

        String body = okBody("/api/items/" + part.getId() + "/suppliers");

        assertFalse(body.contains(rejectedValue),
                failure(LIST_SUPPLIERS, ContentTable.COMPANY_IDENTIFIER, Layer.JOINED_TABLE));
    }

    @Test
    @DisplayName("已駁回的主要識別碼不得成為市佔率排名中公司的對外識別")
    void getMarketShare_rejectedPrimaryIdentifier_shouldNotBecomeReference() throws Exception {
        Item part = item(ReviewStatus.VERIFIED, false);
        Company company = company(ReviewStatus.VERIFIED);
        String rejectedValue = identifierValue();
        identifier(company, IdentifierType.TWSE, rejectedValue, true, ReviewStatus.REJECTED);
        marketShare(company, part, ReviewStatus.VERIFIED);

        String body = okBody(marketShareUri(part));

        assertFalse(body.contains(rejectedValue),
                failure(MARKET_SHARE_RANKING, ContentTable.COMPANY_IDENTIFIER, Layer.JOINED_TABLE));
    }

    @Test
    @DisplayName("已駁回的識別碼不得成為公司零件清單的定位手段")
    void getCompanyItems_rejectedIdentifier_shouldReturnNotFound() throws Exception {
        Company company = company(ReviewStatus.VERIFIED);
        String rejectedValue = identifierValue();
        identifier(company, IdentifierType.TWSE, rejectedValue, true, ReviewStatus.REJECTED);

        MockHttpServletResponse response = fetch("/api/companies/TWSE:" + rejectedValue + "/items");

        assertEquals(HttpStatus.NOT_FOUND.value(), response.getStatus(),
                failure(LIST_COMPANY_ITEMS, ContentTable.COMPANY_IDENTIFIER, Layer.MAIN_TABLE));
    }

    @Test
    @DisplayName("已駁回的公司不得取得其零件清單")
    void getCompanyItems_rejectedCompany_shouldReturnNotFound() throws Exception {
        Company rejected = company(ReviewStatus.REJECTED);
        String value = identifierValue();
        identifier(rejected, IdentifierType.TWSE, value, true, ReviewStatus.VERIFIED);

        MockHttpServletResponse response = fetch("/api/companies/TWSE:" + value + "/items");

        assertEquals(HttpStatus.NOT_FOUND.value(), response.getStatus(),
                failure(LIST_COMPANY_ITEMS, ContentTable.COMPANY, Layer.MAIN_TABLE));
    }

    @Test
    @DisplayName("已駁回的供應角色不得使零件出現在公司的零件清單")
    void getCompanyItems_rejectedRole_shouldNotAppear() throws Exception {
        Company company = company(ReviewStatus.VERIFIED);
        Item part = item(ReviewStatus.VERIFIED, false);
        role(company, part, CompanyRole.MANUFACTURE, ReviewStatus.REJECTED);

        String body = okBody("/api/companies/" + company.getNormalizedName() + "/items");

        assertFalse(body.contains(part.getDisplayName()),
                failure(LIST_COMPANY_ITEMS, ContentTable.COMPANY_ITEM_ROLE, Layer.MAIN_TABLE));
    }

    @Test
    @DisplayName("供應角色指向已駁回的品類節點時不得出現在公司的零件清單")
    void getCompanyItems_roleOnRejectedItem_shouldNotAppear() throws Exception {
        // 審核逐列套用：節點被駁回時掛在它下面的角色未必一併駁回，
        // 少了這一格，公司頁會列出一個對外根本不存在、點下去必然 404 的零件
        Company company = company(ReviewStatus.VERIFIED);
        Item rejectedPart = item(ReviewStatus.REJECTED, false);
        role(company, rejectedPart, CompanyRole.MANUFACTURE, ReviewStatus.VERIFIED);

        String body = okBody("/api/companies/" + company.getNormalizedName() + "/items");

        assertFalse(body.contains(rejectedPart.getDisplayName()),
                failure(LIST_COMPANY_ITEMS, ContentTable.ITEM, Layer.REFERENCED_ENTITY));
    }

    // ---------------------------------------------------------------------
    // 5(續). 路徑指名的節點本身已駁回時，端點不得把它當成存在的資源回傳
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("指名已駁回的節點展開組成樹應回 404")
    void getComponents_rejectedRootItem_shouldReturnNotFound() throws Exception {
        Item rejected = item(ReviewStatus.REJECTED, true);

        MockHttpServletResponse response = fetch("/api/products/" + rejected.getId() + "/components");

        assertEquals(HttpStatus.NOT_FOUND.value(), response.getStatus(),
                failure(COMPONENT_TREE, ContentTable.ITEM, Layer.MAIN_TABLE));
    }

    @Test
    @DisplayName("指名已駁回的節點查組成關係應回 404")
    void getCompositions_rejectedRootItem_shouldReturnNotFound() throws Exception {
        Item rejected = item(ReviewStatus.REJECTED, false);

        MockHttpServletResponse response = fetch("/api/items/" + rejected.getId() + "/compositions");

        assertEquals(HttpStatus.NOT_FOUND.value(), response.getStatus(),
                failure(LIST_COMPOSITIONS, ContentTable.ITEM, Layer.MAIN_TABLE));
    }

    @Test
    @DisplayName("指名已駁回的節點反向查終端成品應回 404")
    void getEndProducts_rejectedRootItem_shouldReturnNotFound() throws Exception {
        Item rejected = item(ReviewStatus.REJECTED, false);

        MockHttpServletResponse response = fetch("/api/items/" + rejected.getId() + "/end-products");

        assertEquals(HttpStatus.NOT_FOUND.value(), response.getStatus(),
                failure(REACHABLE_END_PRODUCTS, ContentTable.ITEM, Layer.MAIN_TABLE));
    }

    @Test
    @DisplayName("指名已駁回的節點查圖片應回 404")
    void getItemImages_rejectedRootItem_shouldReturnNotFound() throws Exception {
        Item rejected = item(ReviewStatus.REJECTED, true);

        MockHttpServletResponse response = fetch("/api/items/" + rejected.getId() + "/images");

        assertEquals(HttpStatus.NOT_FOUND.value(), response.getStatus(),
                failure(LIST_ITEM_IMAGES, ContentTable.ITEM, Layer.MAIN_TABLE));
    }

    @Test
    @DisplayName("指名已駁回的節點查供應公司，表現必須與查無此節點完全一致")
    void getSuppliers_rejectedItem_shouldBeIndistinguishableFromMissingItem() throws Exception {
        Item rejected = item(ReviewStatus.REJECTED, false);
        Company company = company(ReviewStatus.VERIFIED);
        role(company, rejected, CompanyRole.MANUFACTURE, ReviewStatus.VERIFIED);

        MockHttpServletResponse onRejected = fetch("/api/items/" + rejected.getId() + "/suppliers");
        MockHttpServletResponse onMissing = fetch("/api/items/" + MISSING_ITEM_ID + "/suppliers");

        assertAll(
                () -> assertEquals(onMissing.getStatus(), onRejected.getStatus(),
                        failure(LIST_SUPPLIERS, ContentTable.ITEM, Layer.MAIN_TABLE)),
                () -> assertFalse(body(onRejected).contains(company.getDisplayName()),
                        failure(LIST_SUPPLIERS, ContentTable.ITEM, Layer.MAIN_TABLE)));
    }

    @Test
    @DisplayName("指名已駁回的節點查市佔率排名，表現必須與查無此節點完全一致")
    void getMarketShare_rejectedItem_shouldBeIndistinguishableFromMissingItem() throws Exception {
        Item rejected = item(ReviewStatus.REJECTED, false);
        Company company = company(ReviewStatus.VERIFIED);
        marketShare(company, rejected, ReviewStatus.VERIFIED);

        MockHttpServletResponse onRejected = fetch(marketShareUri(rejected.getId()));
        MockHttpServletResponse onMissing = fetch(marketShareUri(MISSING_ITEM_ID));

        assertAll(
                () -> assertEquals(onMissing.getStatus(), onRejected.getStatus(),
                        failure(MARKET_SHARE_RANKING, ContentTable.ITEM, Layer.MAIN_TABLE)),
                () -> assertFalse(body(onRejected).contains(company.getDisplayName()),
                        failure(MARKET_SHARE_RANKING, ContentTable.ITEM, Layer.MAIN_TABLE)));
    }

    @Test
    @DisplayName("以已駁回的節點過濾公司列表，表現必須與查無此節點完全一致")
    void listCompanies_rejectedItemFilter_shouldBeIndistinguishableFromMissingItem() throws Exception {
        Item rejected = item(ReviewStatus.REJECTED, false);
        Company company = company(ReviewStatus.VERIFIED);
        role(company, rejected, CompanyRole.MANUFACTURE, ReviewStatus.VERIFIED);

        MockHttpServletResponse onRejected = fetch("/api/companies?itemId=" + rejected.getId());
        MockHttpServletResponse onMissing = fetch("/api/companies?itemId=" + MISSING_ITEM_ID);

        assertAll(
                () -> assertEquals(onMissing.getStatus(), onRejected.getStatus(),
                        failure(LIST_COMPANIES, ContentTable.ITEM, Layer.MAIN_TABLE)),
                () -> assertFalse(body(onRejected).contains(company.getDisplayName()),
                        failure(LIST_COMPANIES, ContentTable.ITEM, Layer.MAIN_TABLE)));
    }

    // ---------------------------------------------------------------------
    // 守衛的實作
    // ---------------------------------------------------------------------

    /** 取出本專案 controller 上所有會回應 GET 的路徑樣式 */
    private Set<String> exposedGetEndpoints() {
        return requestMappingHandlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry -> entry.getValue().getBeanType().getPackageName().startsWith(APPLICATION_PACKAGE))
                .filter(entry -> respondsToGet(entry.getKey()))
                .flatMap(entry -> patternsOf(entry.getKey()).stream())
                .map(pattern -> "GET " + pattern)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /** 未指定 method 的對應會吃下所有動詞，GET 也在其中，因此一併納入 */
    private static boolean respondsToGet(RequestMappingInfo info) {
        Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
        return methods.isEmpty() || methods.contains(RequestMethod.GET);
    }

    private static Set<String> patternsOf(RequestMappingInfo info) {
        if (info.getPathPatternsCondition() != null) {
            return info.getPathPatternsCondition().getPatternValues();
        }
        return info.getPatternsCondition().getPatterns();
    }

    // ---------------------------------------------------------------------
    // 矩陣的表達方式
    // ---------------------------------------------------------------------

    private static Cell cell(ContentTable table, Layer layer, String note) {
        return new Cell(table, layer, note);
    }

    /**
     * 產生某一格的失敗訊息，並順帶確認該格真的登記在矩陣裡——
     * 斷言與矩陣因此互相綁定，矩陣不會退化成沒人看的裝飾。
     */
    private static String failure(String endpoint, ContentTable table, Layer layer) {
        EndpointCoverage coverage = MATRIX.get(endpoint);
        if (coverage == null) {
            return fail(endpoint + " 未登記進可見性矩陣");
        }
        Cell matched = coverage.cells().stream()
                .filter(candidate -> candidate.table() == table && candidate.layer() == layer)
                .findFirst()
                .orElseGet(() -> fail(endpoint + " 的可見性矩陣未登記「" + table + " × " + layer.label() + "」這一格"));

        return endpoint + " 沒有擋住已駁回的 " + table + "（層次：" + matched.layer().label() + "；"
                + matched.note() + "）\n" + RULE_HINT;
    }

    /** 矩陣中的一格：某端點對某張內容表，在某個層次上的過濾義務 */
    private static final class Cell {

        private final ContentTable table;
        private final Layer layer;
        private final String note;

        private Cell(ContentTable table, Layer layer, String note) {
            this.table = table;
            this.layer = layer;
            this.note = note;
        }

        ContentTable table() {
            return table;
        }

        Layer layer() {
            return layer;
        }

        String note() {
            return note;
        }
    }

    /**
     * 一個端點的登記內容。
     *
     * <p>確實不觸及任何內容表時可登記為空，但必須以 {@link #none(String)} 附上理由——
     * 可以宣告「這裡不需要過濾」，不可以省略登記。</p>
     */
    private static final class EndpointCoverage {

        private final List<Cell> cells;
        private final String noContentTableReason;

        private EndpointCoverage(List<Cell> cells, String noContentTableReason) {
            this.cells = cells;
            this.noContentTableReason = noContentTableReason;
        }

        static EndpointCoverage of(Cell... cells) {
            return new EndpointCoverage(List.of(cells), "");
        }

        @SuppressWarnings("unused") // 目前每個端點都觸及內容表；保留登記形式，讓日後不必為此改結構
        static EndpointCoverage none(String reason) {
            return new EndpointCoverage(List.of(), reason);
        }

        List<Cell> cells() {
            return cells;
        }

        String noContentTableReason() {
            return noContentTableReason;
        }
    }

    // ---------------------------------------------------------------------
    // 請求與回應
    // ---------------------------------------------------------------------

    private MockHttpServletResponse fetch(String uri) throws Exception {
        return mockMvc.perform(get(uri)).andReturn().getResponse();
    }

    private String body(MockHttpServletResponse response) throws Exception {
        return response.getContentAsString(StandardCharsets.UTF_8);
    }

    /**
     * 取回應內容，並先確認請求真的被服務到。
     *
     * <p>「不得出現」的斷言在錯誤回應之下天生成立——查詢條件一改名，端點回 400，
     * 整組矩陣會在完全沒執行到該端點的情況下全綠，還宣稱那幾格驗過了。
     * 狀態碼是這組斷言成立的<b>前提</b>，不是順帶多驗的回應內容。</p>
     */
    private String okBody(String uri) throws Exception {
        MockHttpServletResponse response = fetch(uri);
        assertEquals(HttpStatus.OK.value(), response.getStatus(),
                "端點未回 200，「已駁回資料不得出現」的斷言不成立：" + uri + " → " + body(response));
        return body(response);
    }

    private List<String> childItemIds(String body) {
        List<Object> raw = JsonPath.read(body, "$.data[*].childItemId");
        return raw.stream().map(String::valueOf).toList();
    }

    private String marketShareUri(Item item) {
        return marketShareUri(item.getId());
    }

    private String marketShareUri(long itemId) {
        return "/api/items/" + itemId + "/market-share"
                + "?periodType=YEAR&periodValue=2024&region=global&metric=REVENUE";
    }

    // ---------------------------------------------------------------------
    // fixture
    // ---------------------------------------------------------------------

    /** fixture 名稱：前綴隔離共用開發資料庫的真實資料，執行識別與流水號避免撞正規化名稱唯一鍵 */
    private String uniqueName(String label) {
        return FIXTURE_PREFIX + "rvm" + RUN_ID + "-" + label + "-" + SEQUENCE.incrementAndGet();
    }

    private String identifierValue() {
        return "RVM" + RUN_ID + SEQUENCE.incrementAndGet();
    }

    private Item item(ReviewStatus reviewStatus, boolean endProduct) {
        String displayName = uniqueName(endProduct ? "product" : "part");
        return itemRepository.saveAndFlush(Item.builder()
                .normalizedName(NameNormalizer.normalize(displayName))
                .displayName(displayName)
                .isEndProduct(endProduct)
                .sourceType(SourceType.MANUAL)
                .reviewStatus(reviewStatus)
                .build());
    }

    /** @return 可直接當查詢關鍵字的別名原文 */
    private String itemAlias(Item item, ReviewStatus reviewStatus) {
        String alias = uniqueName("itemalias");
        itemAliasRepository.saveAndFlush(ItemAlias.builder()
                .item(item)
                .normalizedAlias(NameNormalizer.normalize(alias))
                .displayAlias(alias)
                .sourceType(SourceType.MANUAL)
                .reviewStatus(reviewStatus)
                .build());
        return alias;
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

    private Company company(ReviewStatus reviewStatus) {
        String displayName = uniqueName("company");
        return companyRepository.saveAndFlush(Company.builder()
                .normalizedName(NameNormalizer.normalize(displayName))
                .displayName(displayName)
                .country("TW")
                .isPublic(true)
                .sourceType(SourceType.MANUAL)
                .reviewStatus(reviewStatus)
                .build());
    }

    /** @return 可直接當查詢關鍵字的別名原文 */
    private String companyAlias(Company company, ReviewStatus reviewStatus) {
        String alias = uniqueName("companyalias");
        companyAliasRepository.saveAndFlush(CompanyAlias.builder()
                .company(company)
                .normalizedAlias(NameNormalizer.normalize(alias))
                .displayAlias(alias)
                .sourceType(SourceType.MANUAL)
                .reviewStatus(reviewStatus)
                .build());
        return alias;
    }

    private void identifier(Company company, IdentifierType identifierType, String identifierValue,
                            boolean primary, ReviewStatus reviewStatus) {
        companyIdentifierRepository.saveAndFlush(CompanyIdentifier.builder()
                .company(company)
                .identifierType(identifierType)
                .identifierValue(identifierValue)
                .isPrimary(primary)
                .sourceType(SourceType.MANUAL)
                .reviewStatus(reviewStatus)
                .build());
    }

    private void role(Company company, Item item, CompanyRole companyRole, ReviewStatus reviewStatus) {
        companyItemRoleRepository.saveAndFlush(CompanyItemRole.builder()
                .company(company)
                .item(item)
                .companyRole(companyRole)
                .sourceType(SourceType.MANUAL)
                .reviewStatus(reviewStatus)
                .build());
    }

    private ItemImage image(Item item, ReviewStatus reviewStatus) {
        return itemImageRepository.saveAndFlush(ItemImage.builder()
                .item(item)
                .viewLabel(uniqueName("view"))
                .storageKey("https://cdn.example.com/" + uniqueName("image") + ".png")
                .sourceType(SourceType.MANUAL)
                .reviewStatus(reviewStatus)
                .build());
    }

    /** @return 可在回應中比對的位置標籤 */
    private String hotspot(ItemImage image, Item childItem, ReviewStatus reviewStatus) {
        String positionLabel = uniqueName("position");
        itemHotspotRepository.saveAndFlush(ItemHotspot.builder()
                .itemImage(image)
                .childItem(childItem)
                .positionLabel(positionLabel)
                .polygon(List.of(new HotspotPoint(0.1, 0.1), new HotspotPoint(0.2, 0.1), new HotspotPoint(0.2, 0.2)))
                .sourceType(SourceType.MANUAL)
                .reviewStatus(reviewStatus)
                .build());
        return positionLabel;
    }

    private void marketShare(Company company, Item item, ReviewStatus reviewStatus) {
        marketShareRepository.saveAndFlush(MarketShare.builder()
                .company(company)
                .item(item)
                .periodType(PeriodType.YEAR)
                .periodValue("2024")
                .region("global")
                .metric(ShareMetric.REVENUE)
                .sharePercent(BigDecimal.valueOf(10))
                .sourceType(SourceType.MANUAL)
                .reviewStatus(reviewStatus)
                .build());
    }
}
