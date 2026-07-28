package com.profetai.industrymap.repository;

import com.profetai.industrymap.enums.CompanyRole;
import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.enums.SourceType;
import com.profetai.industrymap.model.Company;
import com.profetai.industrymap.model.CompanyAlias;
import com.profetai.industrymap.model.CompanyItemRole;
import com.profetai.industrymap.model.Item;
import com.profetai.industrymap.util.NameNormalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 實際對 PostgreSQL 執行 {@link CompanyRepository} 的公司列表查詢。
 *
 * <p>這支查詢的易錯處都只有真的送到資料庫才驗得出來：別名比對造成的重複、
 * count 與內容是否用同一組條件、null 參數在 native SQL 下的型別繫結、
 * 以及國別比對的大小寫處理。</p>
 *
 * <p>沒有 Docker 時測試跑在共用的開發資料庫上，整張 company 表已有真實資料，
 * 因此所有斷言一律限定在本測試自建的 fixture 上（名稱帶 {@code FIXTURE_PREFIX}），
 * 不對「總共幾家公司」這種全表數字下斷言。</p>
 */
class CompanyListingNativeQueryTest extends AbstractPostgresIntegrationTest {

    private static final Set<String> VERIFIED_ONLY = Set.of(ReviewStatus.VERIFIED.name());
    private static final Set<String> WITH_DRAFTS =
            Set.of(ReviewStatus.VERIFIED.name(), ReviewStatus.DRAFT.name());
    /** 別名的可外露範圍：實體只擋 REJECTED，不跟著 includeDrafts 走 */
    private static final Set<String> EXPOSABLE =
            Set.of(ReviewStatus.VERIFIED.name(), ReviewStatus.DRAFT.name());
    private static final int LIMIT = 50;

    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private CompanyAliasRepository companyAliasRepository;
    @Autowired
    private CompanyItemRoleRepository companyItemRoleRepository;
    @Autowired
    private ItemRepository itemRepository;

    @Test
    @DisplayName("以名稱關鍵字查詢應同時比對公司名稱與別名")
    void findCompanies_nameKeyword_shouldMatchNameAndAlias() {
        // Given：一家以名稱命中、一家只以別名命中
        Company kmc = verifiedCompany("桂盟國際", "TW", true);
        Company tsmc = verifiedCompany("台積電", "TW", true);
        alias(tsmc, "tsmc");

        // When
        List<Company> byName = find(pattern("桂盟"));
        List<Company> byAlias = find(pattern("tsmc"));

        // Then
        assertAll(
                () -> assertEquals(List.of(kmc.getId()), ids(byName)),
                () -> assertEquals(List.of(tsmc.getId()), ids(byAlias)));
    }

    @Test
    @DisplayName("關鍵字同時命中名稱與多個別名時該公司只出現一次，總筆數也不得重複計算")
    void findCompanies_nameAndMultipleAliasesMatch_shouldNotDuplicate() {
        // Given：名稱與兩個別名都含同一關鍵字——join 而未去重時這家會出現三次
        Company company = verifiedCompany("聯發科技", "TW", true);
        alias(company, "聯發科技股份有限公司");
        alias(company, "聯發科技集團");

        String pattern = pattern("聯發科技");

        // When
        List<Company> found = find(pattern);
        long total = countMatching(pattern);

        // Then：內容與總筆數都必須只算一次，否則前端的「共 N 家」與頁數全錯
        assertAll(
                () -> assertEquals(List.of(company.getId()), ids(found)),
                () -> assertEquals(1L, total));
    }

    @Test
    @DisplayName("關鍵字大小寫與全形差異應命中同一家公司")
    void findCompanies_caseAndWidthVariants_shouldMatchSameCompany() {
        Company company = verifiedCompany("WiFi模組廠", "TW", false);

        assertAll(
                () -> assertEquals(List.of(company.getId()), ids(find(pattern("wifi模組廠")))),
                () -> assertEquals(List.of(company.getId()), ids(find(pattern("ＷｉＦｉ模組廠")))));
    }

    @Test
    @DisplayName("關鍵字位於名稱中間時同樣應命中")
    void findCompanies_keywordInMiddleOfName_shouldMatch() {
        // 前面幾支測試的關鍵字都落在名稱開頭（fixture 前綴使然），
        // 這支刻意讓關鍵字夾在中間，確認比對不是只在開頭成立
        Company company = verifiedCompany("台灣中綴標記工業", "TW", true);

        List<Company> found = companyRepository.findCompanies(
                VERIFIED_ONLY, "%" + NameNormalizer.normalizeOrEmpty("中綴標記") + "%",
                null, null, null, null, VERIFIED_ONLY, EXPOSABLE, LIMIT, 0);

        // 樣式未帶 fixture 前綴，開發資料庫可能有其他資料一併命中，因此只斷言包含關係
        assertTrue(ids(found).contains(company.getId()));
    }

    @Test
    @DisplayName("已駁回的別名不得成為搜尋鍵")
    void findCompanies_rejectedAlias_shouldNotMatch() {
        // Given：公司本身已驗證，但有一筆被審核者駁回的錯誤別名
        Company company = verifiedCompany("駁回別名公司壬", "TW", true);
        alias(company, "壬字誤植別名", ReviewStatus.REJECTED);

        // When：以那個被駁回的別名搜尋
        List<Company> found = find(pattern("壬字誤植別名"));

        // Then：被明確判定為錯誤的別名不該還能把公司找出來
        assertFalse(ids(found).contains(company.getId()));
    }

    @Test
    @DisplayName("草稿別名仍應可搜到已驗證公司")
    void findCompanies_draftAlias_shouldStillMatch() {
        // 別名是查找路徑而非回應內容，最終回傳的仍是已驗證公司；
        // 擋掉草稿別名只會讓剛匯入的資料變難找（ReviewScopes：實體只擋 REJECTED）
        Company company = verifiedCompany("草稿別名公司癸", "TW", true);
        alias(company, "癸字草稿別名", ReviewStatus.DRAFT);

        List<Company> found = find(pattern("癸字草稿別名"));

        assertTrue(ids(found).contains(company.getId()));
    }

    @Test
    @DisplayName("依國別過濾應不分大小寫")
    void findCompanies_countryFilter_shouldBeCaseInsensitive() {
        Company taiwanese = verifiedCompany("公司甲台灣", "TW", true);
        verifiedCompany("公司甲美國", "US", true);

        List<Company> lowerCase = companyRepository.findCompanies(
                VERIFIED_ONLY, pattern("公司甲"), "tw", null, null, null, VERIFIED_ONLY, EXPOSABLE, LIMIT, 0);
        List<Company> upperCase = companyRepository.findCompanies(
                VERIFIED_ONLY, pattern("公司甲"), "TW", null, null, null, VERIFIED_ONLY, EXPOSABLE, LIMIT, 0);

        assertAll(
                () -> assertEquals(List.of(taiwanese.getId()), ids(lowerCase)),
                () -> assertEquals(ids(upperCase), ids(lowerCase)));
    }

    @Test
    @DisplayName("依公開發行狀態過濾應分別回傳上市與非上市公司")
    void findCompanies_publicCompanyFilter_shouldSplitByFlag() {
        Company listed = verifiedCompany("公司乙上市", "TW", true);
        Company unlisted = verifiedCompany("公司乙未上市", "TW", false);

        List<Company> onlyPublic = companyRepository.findCompanies(
                VERIFIED_ONLY, pattern("公司乙"), null, true, null, null, VERIFIED_ONLY, EXPOSABLE, LIMIT, 0);
        List<Company> onlyPrivate = companyRepository.findCompanies(
                VERIFIED_ONLY, pattern("公司乙"), null, false, null, null, VERIFIED_ONLY, EXPOSABLE, LIMIT, 0);

        assertAll(
                () -> assertEquals(List.of(listed.getId()), ids(onlyPublic)),
                () -> assertEquals(List.of(unlisted.getId()), ids(onlyPrivate)));
    }

    @Test
    @DisplayName("未指定的條件應視為不過濾")
    void findCompanies_nullFilters_shouldNotFilter() {
        Company listed = verifiedCompany("混合公司丙", "TW", true);
        Company unlisted = verifiedCompany("混合公司丁", "US", false);

        // When：country、publicCompany、itemId、companyRole 全部不指定
        List<Company> found = find(pattern("混合公司"));

        // Then：國別與公開發行狀態相反的兩家都要回來（排序另有測試涵蓋，這裡不綁定順序）
        assertEquals(List.of(listed.getId(), unlisted.getId()).stream().sorted().toList(),
                ids(found).stream().sorted().toList());
    }

    @Test
    @DisplayName("依零件過濾應只回傳對該零件有供應角色的公司，且多重角色不重複")
    void findCompanies_itemFilter_shouldReturnDistinctSuppliers() {
        // Given：甲對該零件同時有製造與封測兩個角色，乙無任何角色
        Item pcb = itemRepository.saveAndFlush(item("印刷電路板"));
        Company supplier = verifiedCompany("供應商甲", "TW", true);
        Company outsider = verifiedCompany("供應商乙", "TW", true);
        role(supplier, pcb, CompanyRole.MANUFACTURE, ReviewStatus.VERIFIED);
        role(supplier, pcb, CompanyRole.ASSEMBLY, ReviewStatus.VERIFIED);

        // When
        List<Company> found = companyRepository.findCompanies(
                VERIFIED_ONLY, "%", null, null, pcb.getId(), null, VERIFIED_ONLY, EXPOSABLE, LIMIT, 0);
        long total = companyRepository.countCompanies(
                VERIFIED_ONLY, "%", null, null, pcb.getId(), null, VERIFIED_ONLY, EXPOSABLE);

        // Then：兩個角色只能讓公司出現一次
        assertAll(
                () -> assertEquals(List.of(supplier.getId()), ids(found)),
                () -> assertEquals(1L, total),
                () -> assertFalse(ids(found).contains(outsider.getId())));
    }

    @Test
    @DisplayName("零件併用角色過濾時應只回傳具有該角色的公司")
    void findCompanies_itemAndRoleFilter_shouldNarrowByRole() {
        Item pcb = itemRepository.saveAndFlush(item("軟性電路板"));
        Company manufacturer = verifiedCompany("製造商戊", "TW", true);
        Company assembler = verifiedCompany("組裝廠戊", "TW", true);
        role(manufacturer, pcb, CompanyRole.MANUFACTURE, ReviewStatus.VERIFIED);
        role(assembler, pcb, CompanyRole.ASSEMBLY, ReviewStatus.VERIFIED);

        List<Company> found = companyRepository.findCompanies(
                VERIFIED_ONLY, "%", null, null, pcb.getId(), CompanyRole.ASSEMBLY.name(),
                VERIFIED_ONLY, EXPOSABLE, LIMIT, 0);

        assertEquals(List.of(assembler.getId()), ids(found));
    }

    @Test
    @DisplayName("只指定角色而未指定零件時，應回傳對任何零件具有該角色的公司")
    void findCompanies_roleOnly_shouldReturnSuppliersAcrossAllItems() {
        // Given：子丑各對不同零件具有代工組裝角色，寅只有製造角色
        Item boardA = itemRepository.saveAndFlush(item("角色測試零件子"));
        Item boardB = itemRepository.saveAndFlush(item("角色測試零件丑"));
        Company assemblerA = verifiedCompany("角色公司子", "TW", true);
        Company assemblerB = verifiedCompany("角色公司丑", "TW", true);
        Company manufacturer = verifiedCompany("角色公司寅", "TW", true);
        role(assemblerA, boardA, CompanyRole.ASSEMBLY, ReviewStatus.VERIFIED);
        role(assemblerB, boardB, CompanyRole.ASSEMBLY, ReviewStatus.VERIFIED);
        role(manufacturer, boardA, CompanyRole.MANUFACTURE, ReviewStatus.VERIFIED);

        // When：不指定零件，只問「有哪些代工組裝廠」
        List<Company> found = findByRole(pattern("角色公司"), CompanyRole.ASSEMBLY);

        // Then：跨零件彙總，只做製造的那家不得入列
        assertAll(
                () -> assertEquals(List.of(assemblerA.getId(), assemblerB.getId()),
                        ids(found).stream().sorted().toList()),
                () -> assertFalse(ids(found).contains(manufacturer.getId())));
    }

    @Test
    @DisplayName("只指定角色時總筆數應與內容一致")
    void findCompanies_roleOnly_countShouldMatchContent() {
        // 內容改了 count 沒改，清單會有資料卻顯示 0 筆
        Item board = itemRepository.saveAndFlush(item("角色計數零件卯"));
        Company assembler = verifiedCompany("角色計數公司卯", "TW", true);
        verifiedCompany("角色計數公司辰", "TW", true);
        role(assembler, board, CompanyRole.ASSEMBLY, ReviewStatus.VERIFIED);

        String namePattern = pattern("角色計數公司");
        List<Company> found = findByRole(namePattern, CompanyRole.ASSEMBLY);
        long total = countByRole(namePattern, CompanyRole.ASSEMBLY);

        assertAll(
                () -> assertEquals(List.of(assembler.getId()), ids(found)),
                () -> assertEquals(1L, total));
    }

    @Test
    @DisplayName("只指定角色時，同一公司對多個零件具有該角色仍只出現一次")
    void findCompanies_roleOnlyAcrossManyItems_shouldNotDuplicate() {
        // Given：同一家公司對三個零件都有代工組裝角色
        Company assembler = verifiedCompany("角色去重公司巳", "TW", true);
        for (String name : List.of("去重零件一", "去重零件二", "去重零件三")) {
            role(assembler, itemRepository.saveAndFlush(item(name)),
                    CompanyRole.ASSEMBLY, ReviewStatus.VERIFIED);
        }

        String namePattern = pattern("角色去重公司");
        List<Company> found = findByRole(namePattern, CompanyRole.ASSEMBLY);

        assertAll(
                () -> assertEquals(List.of(assembler.getId()), ids(found)),
                () -> assertEquals(1L, countByRole(namePattern, CompanyRole.ASSEMBLY)));
    }

    @Test
    @DisplayName("只指定角色時草稿角色不得讓公司出現在預設查詢中")
    void findCompanies_roleOnlyWithDraftRole_shouldNotSurfaceByDefault() {
        // 未指定零件時掃描範圍更大，草稿角色若被採計，「有哪些封測廠」會混入一批還沒審的猜測
        Item board = itemRepository.saveAndFlush(item("角色草稿零件午"));
        Company draftAssembler = verifiedCompany("角色草稿公司午", "TW", true);
        role(draftAssembler, board, CompanyRole.ASSEMBLY, ReviewStatus.DRAFT);

        List<Company> defaultScope = findByRole(pattern("角色草稿公司"), CompanyRole.ASSEMBLY);

        assertFalse(ids(defaultScope).contains(draftAssembler.getId()));
    }

    @Test
    @DisplayName("零件與角色併用時必須由同一筆供應角色滿足，不得由不同零件的不同角色分別命中")
    void findCompanies_itemAndRoleMustBeSatisfiedBySameRow() {
        // Given：未字公司對 A 零件有製造、對 B 零件有代工組裝——兩個條件各自都有人滿足，
        // 但沒有任何一筆角色同時滿足「A 零件」與「代工組裝」
        Item itemA = itemRepository.saveAndFlush(item("同列零件未A"));
        Item itemB = itemRepository.saveAndFlush(item("同列零件未B"));
        Company company = verifiedCompany("同列公司未", "TW", true);
        role(company, itemA, CompanyRole.MANUFACTURE, ReviewStatus.VERIFIED);
        role(company, itemB, CompanyRole.ASSEMBLY, ReviewStatus.VERIFIED);

        // When：查「A 零件 + 代工組裝」
        List<Company> found = companyRepository.findCompanies(
                VERIFIED_ONLY, pattern("同列公司未"), null, null, itemA.getId(),
                CompanyRole.ASSEMBLY.name(), VERIFIED_ONLY, EXPOSABLE, LIMIT, 0);

        // Then：拆成兩個 EXISTS 的寫法會讓這家誤中
        assertFalse(ids(found).contains(company.getId()));
    }

    @Test
    @DisplayName("零件與角色皆不指定時不得因供應關係排除任何公司")
    void findCompanies_neitherItemNorRole_shouldNotFilterBySupply() {
        // 守衛的 AND / OR 優先序寫錯時，完全沒有供應角色的公司會被整個濾掉
        Company withoutAnyRole = verifiedCompany("角色公司申無", "TW", true);
        Company withRole = verifiedCompany("角色公司申有", "TW", true);
        role(withRole, itemRepository.saveAndFlush(item("申字零件")),
                CompanyRole.MANUFACTURE, ReviewStatus.VERIFIED);

        List<Company> found = find(pattern("角色公司申"));

        assertAll(
                () -> assertTrue(ids(found).contains(withoutAnyRole.getId())),
                () -> assertTrue(ids(found).contains(withRole.getId())));
    }

    @Test
    @DisplayName("依零件過濾時草稿角色不得讓公司出現在預設查詢中")
    void findCompanies_draftRole_shouldNotSurfaceCompanyByDefault() {
        // Given：公司本身已驗證，但它對該零件的供應角色只是草稿——
        // 若採計草稿角色，就會出現「公司已驗證但關係其實還沒審」的誤導結果
        Item sensor = itemRepository.saveAndFlush(item("影像感測器"));
        Company draftSupplier = verifiedCompany("草稿供應商己", "TW", true);
        role(draftSupplier, sensor, CompanyRole.MANUFACTURE, ReviewStatus.DRAFT);

        List<Company> defaultScope = companyRepository.findCompanies(
                VERIFIED_ONLY, "%", null, null, sensor.getId(), null, VERIFIED_ONLY, EXPOSABLE, LIMIT, 0);
        List<Company> withDrafts = companyRepository.findCompanies(
                WITH_DRAFTS, "%", null, null, sensor.getId(), null, WITH_DRAFTS, EXPOSABLE, LIMIT, 0);

        assertAll(
                () -> assertFalse(ids(defaultScope).contains(draftSupplier.getId())),
                () -> assertTrue(ids(withDrafts).contains(draftSupplier.getId())));
    }

    @Test
    @DisplayName("已駁回的公司在任何審核範圍下都不得出現")
    void findCompanies_rejectedCompany_shouldNeverAppear() {
        Company rejected = company("公司庚駁回", "TW", true, ReviewStatus.REJECTED);
        Company draft = company("公司庚草稿", "TW", true, ReviewStatus.DRAFT);

        List<Company> withDrafts = companyRepository.findCompanies(
                WITH_DRAFTS, pattern("公司庚"), null, null, null, null, WITH_DRAFTS, EXPOSABLE, LIMIT, 0);

        assertAll(
                () -> assertTrue(ids(withDrafts).contains(draft.getId())),
                () -> assertFalse(ids(withDrafts).contains(rejected.getId())));
    }

    @Test
    @DisplayName("條件併用時應回傳同時滿足全部條件的公司")
    void findCompanies_combinedFilters_shouldApplyAllConditions() {
        Item chain = itemRepository.saveAndFlush(item("鏈條"));
        Company match = verifiedCompany("鏈條台廠辛", "TW", true);
        Company wrongCountry = verifiedCompany("鏈條美廠辛", "US", true);
        Company noRole = verifiedCompany("鏈條無角色辛", "TW", true);
        role(match, chain, CompanyRole.MANUFACTURE, ReviewStatus.VERIFIED);
        role(wrongCountry, chain, CompanyRole.MANUFACTURE, ReviewStatus.VERIFIED);

        List<Company> found = companyRepository.findCompanies(
                VERIFIED_ONLY, pattern("鏈條"), "TW", true, chain.getId(), null, VERIFIED_ONLY, EXPOSABLE, LIMIT, 0);

        assertAll(
                () -> assertEquals(List.of(match.getId()), ids(found)),
                () -> assertFalse(ids(found).contains(noRole.getId())));
    }

    @Test
    @DisplayName("分頁應以穩定排序切頁，前後頁不重複也不漏")
    void findCompanies_paging_shouldSplitWithStableOrder() {
        Company first = verifiedCompany("分頁公司A", "TW", true);
        Company second = verifiedCompany("分頁公司B", "TW", true);
        Company third = verifiedCompany("分頁公司C", "TW", true);

        String pattern = pattern("分頁公司");
        List<Company> page0 = companyRepository.findCompanies(
                VERIFIED_ONLY, pattern, null, null, null, null, VERIFIED_ONLY, EXPOSABLE, 2, 0);
        List<Company> page1 = companyRepository.findCompanies(
                VERIFIED_ONLY, pattern, null, null, null, null, VERIFIED_ONLY, EXPOSABLE, 2, 2);

        assertAll(
                () -> assertEquals(2, page0.size()),
                () -> assertEquals(1, page1.size()),
                () -> assertEquals(List.of(first.getId(), second.getId()), ids(page0)),
                () -> assertEquals(List.of(third.getId()), ids(page1)),
                () -> assertEquals(3L, countMatching(pattern)));
    }

    @Test
    @DisplayName("無符合資料時應回空清單與總筆數 0")
    void findCompanies_noMatch_shouldReturnEmptyAndZero() {
        String pattern = pattern("不存在的公司名稱xyz");

        assertAll(
                () -> assertTrue(find(pattern).isEmpty()),
                () -> assertEquals(0L, countMatching(pattern)));
    }

    private List<Company> find(String namePattern) {
        return companyRepository.findCompanies(
                VERIFIED_ONLY, namePattern, null, null, null, null, VERIFIED_ONLY, EXPOSABLE, LIMIT, 0);
    }

    private List<Company> findByRole(String namePattern, CompanyRole companyRole) {
        return companyRepository.findCompanies(VERIFIED_ONLY, namePattern, null, null, null,
                companyRole.name(), VERIFIED_ONLY, EXPOSABLE, LIMIT, 0);
    }

    private long countByRole(String namePattern, CompanyRole companyRole) {
        return companyRepository.countCompanies(VERIFIED_ONLY, namePattern, null, null, null,
                companyRole.name(), VERIFIED_ONLY, EXPOSABLE);
    }

    private long countMatching(String namePattern) {
        return companyRepository.countCompanies(
                VERIFIED_ONLY, namePattern, null, null, null, null, VERIFIED_ONLY, EXPOSABLE);
    }

    private List<Long> ids(List<Company> companies) {
        return companies.stream().map(Company::getId).toList();
    }

    /**
     * 組出比對 {@code normalized_name} 的 LIKE 樣式，關鍵字走與正式流程相同的正規化規則
     * （全形轉半形、轉小寫、去標點），全形寫法才驗得出來會命中同一家公司。
     *
     * <p>另外綴上 {@code FIXTURE_PREFIX}：沒有 Docker 時測試跑在共用開發資料庫上，
     * 不限定範圍就會掃到真實公司資料。前綴本身不參與正規化，fixture 與樣式兩邊一致即可。</p>
     */
    private String pattern(String keyword) {
        return "%" + FIXTURE_PREFIX + NameNormalizer.normalizeOrEmpty(keyword) + "%";
    }

    private Company verifiedCompany(String displayName, String country, boolean isPublic) {
        return company(displayName, country, isPublic, ReviewStatus.VERIFIED);
    }

    private Company company(String displayName, String country, boolean isPublic, ReviewStatus reviewStatus) {
        return companyRepository.saveAndFlush(Company.builder()
                .normalizedName(FIXTURE_PREFIX + NameNormalizer.normalizeOrEmpty(displayName))
                .displayName(displayName)
                .country(country)
                .isPublic(isPublic)
                .sourceType(SourceType.MANUAL)
                .reviewStatus(reviewStatus)
                .build());
    }

    private void alias(Company company, String alias) {
        alias(company, alias, ReviewStatus.VERIFIED);
    }

    private void alias(Company company, String alias, ReviewStatus reviewStatus) {
        companyAliasRepository.saveAndFlush(CompanyAlias.builder()
                .company(company)
                .normalizedAlias(FIXTURE_PREFIX + NameNormalizer.normalizeOrEmpty(alias))
                .displayAlias(alias)
                .sourceType(SourceType.MANUAL)
                .reviewStatus(reviewStatus)
                .build());
    }

    private Item item(String name) {
        return Item.builder()
                .normalizedName(FIXTURE_PREFIX + NameNormalizer.normalizeOrEmpty(name))
                .displayName(name)
                .sourceType(SourceType.MANUAL)
                .reviewStatus(ReviewStatus.VERIFIED)
                .build();
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
}
