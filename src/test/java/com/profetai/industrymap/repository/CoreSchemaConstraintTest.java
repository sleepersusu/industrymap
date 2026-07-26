package com.profetai.industrymap.repository;

import com.profetai.industrymap.enums.CompanyRole;
import com.profetai.industrymap.enums.IdentifierType;
import com.profetai.industrymap.enums.Necessity;
import com.profetai.industrymap.enums.PeriodType;
import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.enums.ShareMetric;
import com.profetai.industrymap.enums.SourceType;
import com.profetai.industrymap.model.Company;
import com.profetai.industrymap.model.CompanyIdentifier;
import com.profetai.industrymap.model.CompanyItemRole;
import com.profetai.industrymap.model.Item;
import com.profetai.industrymap.model.ItemAlias;
import com.profetai.industrymap.model.ItemComposition;
import com.profetai.industrymap.model.MarketShare;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 驗證 Flyway migration 建立的唯一鍵與 check constraint 在真實 PostgreSQL 上確實生效。
 *
 * <p>service 層雖然也會擋這些情況，但那是「友善錯誤訊息」；DB 層的限制才是最後防線，
 * 尤其是日後批次匯入若繞過 service，只剩這層擋得住。</p>
 */
class CoreSchemaConstraintTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private ItemAliasRepository itemAliasRepository;
    @Autowired
    private ItemCompositionRepository itemCompositionRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private CompanyIdentifierRepository companyIdentifierRepository;
    @Autowired
    private CompanyItemRoleRepository companyItemRoleRepository;
    @Autowired
    private MarketShareRepository marketShareRepository;

    @Test
    @DisplayName("item 正規化名稱重複時 DB 應拒絕寫入")
    void saveItem_duplicatedNormalizedName_shouldViolateUniqueConstraint() {
        itemRepository.saveAndFlush(item("pcb", "PCB"));

        assertThrows(DataIntegrityViolationException.class,
                () -> itemRepository.saveAndFlush(item("pcb", "印刷電路板")));
    }

    @Test
    @DisplayName("item_alias 正規化別名重複時 DB 應拒絕寫入")
    void saveItemAlias_duplicatedNormalizedAlias_shouldViolateUniqueConstraint() {
        Item wifiModule = itemRepository.saveAndFlush(item("wifi模組", "WiFi 模組"));
        Item antenna = itemRepository.saveAndFlush(item("天線", "天線"));
        itemAliasRepository.saveAndFlush(alias(wifiModule, "無線網卡", "無線網卡"));

        assertThrows(DataIntegrityViolationException.class,
                () -> itemAliasRepository.saveAndFlush(alias(antenna, "無線網卡", "無線網卡")));
    }

    @Test
    @DisplayName("同一組 parent/child 的組成關係重複時 DB 應拒絕寫入")
    void saveComposition_duplicatedParentChildPair_shouldViolateUniqueConstraint() {
        Item bike = itemRepository.saveAndFlush(item("腳踏車", "腳踏車"));
        Item derailleur = itemRepository.saveAndFlush(item("變速器", "變速器"));
        itemCompositionRepository.saveAndFlush(composition(bike, derailleur, Necessity.COMMON));

        assertThrows(DataIntegrityViolationException.class,
                () -> itemCompositionRepository.saveAndFlush(composition(bike, derailleur, Necessity.OPTIONAL)));
    }

    @Test
    @DisplayName("組成關係指向自身時 DB 應拒絕寫入")
    void saveComposition_selfLoop_shouldViolateCheckConstraint() {
        Item bike = itemRepository.saveAndFlush(item("腳踏車", "腳踏車"));

        assertThrows(DataIntegrityViolationException.class,
                () -> itemCompositionRepository.saveAndFlush(composition(bike, bike, Necessity.STANDARD)));
    }

    @Test
    @DisplayName("company 正規化名稱重複時 DB 應拒絕寫入")
    void saveCompany_duplicatedNormalizedName_shouldViolateUniqueConstraint() {
        companyRepository.saveAndFlush(company("台積電", "台積電"));

        assertThrows(DataIntegrityViolationException.class,
                () -> companyRepository.saveAndFlush(company("台積電", "台灣積體電路製造")));
    }

    @Test
    @DisplayName("同一組（類型, 代號值）跨公司重複時 DB 應拒絕寫入")
    void saveIdentifier_duplicatedTypeAndValue_shouldViolateUniqueConstraint() {
        Company tsmc = companyRepository.saveAndFlush(company("台積電", "台積電"));
        Company other = companyRepository.saveAndFlush(company("聯發科", "聯發科"));
        companyIdentifierRepository.saveAndFlush(identifier(tsmc, IdentifierType.TWSE, "2330", true));

        assertThrows(DataIntegrityViolationException.class,
                () -> companyIdentifierRepository.saveAndFlush(identifier(other, IdentifierType.TWSE, "2330", false)));
    }

    @Test
    @DisplayName("多地掛牌的識別碼應並存，且主要識別碼可查得")
    void saveIdentifier_multipleExchangesForSameCompany_shouldCoexist() {
        Company tsmc = companyRepository.saveAndFlush(company("台積電", "台積電"));
        companyIdentifierRepository.saveAndFlush(identifier(tsmc, IdentifierType.TWSE, "2330", true));
        companyIdentifierRepository.saveAndFlush(identifier(tsmc, IdentifierType.NYSE, "TSM", false));

        assertEquals(2, companyIdentifierRepository.findByCompanyId(tsmc.getId()).size());
        assertEquals(FIXTURE_PREFIX + "2330", companyIdentifierRepository
                .findByCompanyIdAndIsPrimaryTrue(tsmc.getId()).orElseThrow().getIdentifierValue());
    }

    @Test
    @DisplayName("同一公司第二筆主要識別碼應被 partial unique index 擋下")
    void saveIdentifier_secondPrimaryForSameCompany_shouldViolateUniqueIndex() {
        Company tsmc = companyRepository.saveAndFlush(company("台積電", "台積電"));
        companyIdentifierRepository.saveAndFlush(identifier(tsmc, IdentifierType.TWSE, "2330", true));

        assertThrows(DataIntegrityViolationException.class,
                () -> companyIdentifierRepository.saveAndFlush(identifier(tsmc, IdentifierType.NYSE, "TSM", true)));
    }

    @Test
    @DisplayName("同公司同零件的不同角色應並存，同角色重複則 DB 拒絕寫入")
    void saveCompanyItemRole_sameCompanyAndItem_shouldAllowDistinctRolesOnly() {
        Company tsmc = companyRepository.saveAndFlush(company("台積電", "台積電"));
        Item chip = itemRepository.saveAndFlush(item("晶片", "晶片"));
        companyItemRoleRepository.saveAndFlush(role(tsmc, chip, CompanyRole.MANUFACTURE));
        companyItemRoleRepository.saveAndFlush(role(tsmc, chip, CompanyRole.PACKAGING_TESTING));

        assertThrows(DataIntegrityViolationException.class,
                () -> companyItemRoleRepository.saveAndFlush(role(tsmc, chip, CompanyRole.MANUFACTURE)));
    }

    @Test
    @DisplayName("市佔率百分比超出 0–100 時 DB 應拒絕寫入")
    void saveMarketShare_percentOutOfRange_shouldViolateCheckConstraint() {
        Company shimano = companyRepository.saveAndFlush(company("shimano", "Shimano"));
        Item derailleur = itemRepository.saveAndFlush(item("變速器", "變速器"));

        assertThrows(DataIntegrityViolationException.class,
                () -> marketShareRepository.saveAndFlush(
                        marketShare(shimano, derailleur, new BigDecimal("120.000"), "報告 A")));
    }

    @Test
    @DisplayName("不同來源的相同維度市佔率應並存，同來源重複則 DB 拒絕寫入")
    void saveMarketShare_sameDimensions_shouldDedupeBySourceOnly() {
        Company shimano = companyRepository.saveAndFlush(company("shimano", "Shimano"));
        Item derailleur = itemRepository.saveAndFlush(item("變速器", "變速器"));
        marketShareRepository.saveAndFlush(marketShare(shimano, derailleur, new BigDecimal("70.000"), "報告 A"));
        MarketShare fromSourceB =
                marketShareRepository.saveAndFlush(marketShare(shimano, derailleur, new BigDecimal("50.000"), "報告 B"));

        assertNotNull(fromSourceB.getId());
        assertThrows(DataIntegrityViolationException.class,
                () -> marketShareRepository.saveAndFlush(
                        marketShare(shimano, derailleur, new BigDecimal("72.000"), "報告 A")));
    }

    @Test
    @DisplayName("市佔率可在尚未建立角色關係時先行寫入")
    void saveMarketShare_withoutCompanyItemRole_shouldSucceed() {
        Company shimano = companyRepository.saveAndFlush(company("shimano", "Shimano"));
        Item derailleur = itemRepository.saveAndFlush(item("變速器", "變速器"));

        MarketShare saved =
                marketShareRepository.saveAndFlush(marketShare(shimano, derailleur, new BigDecimal("70.000"), "報告 A"));

        // 範圍限定在這個零件：整張表可能有其他資料（沒有 Docker 時測試跑在共用的開發資料庫上），
        // 這裡要證明的是「寫市佔率不會順手建出角色關係」，不是整張表為空
        assertNotNull(saved.getId());
        assertTrue(companyItemRoleRepository
                .findByItemIdAndReviewStatusIn(derailleur.getId(), EnumSet.allOf(ReviewStatus.class))
                .isEmpty());
    }

    private Item item(String normalizedName, String displayName) {
        return Item.builder()
                .normalizedName(FIXTURE_PREFIX + normalizedName)
                .displayName(displayName)
                .sourceType(SourceType.MANUAL)
                .build();
    }

    private ItemAlias alias(Item item, String normalizedAlias, String displayAlias) {
        return ItemAlias.builder()
                .item(item)
                .normalizedAlias(FIXTURE_PREFIX + normalizedAlias)
                .displayAlias(displayAlias)
                .sourceType(SourceType.MANUAL)
                .build();
    }

    private ItemComposition composition(Item parent, Item child, Necessity necessity) {
        return ItemComposition.builder()
                .parentItem(parent)
                .childItem(child)
                .necessity(necessity)
                .sourceType(SourceType.MANUAL)
                .build();
    }

    private Company company(String normalizedName, String displayName) {
        return Company.builder()
                .normalizedName(FIXTURE_PREFIX + normalizedName)
                .displayName(displayName)
                .sourceType(SourceType.MANUAL)
                .build();
    }

    private CompanyIdentifier identifier(Company company, IdentifierType type, String value, boolean primary) {
        return CompanyIdentifier.builder()
                .company(company)
                .identifierType(type)
                .identifierValue(FIXTURE_PREFIX + value)
                .isPrimary(primary)
                .sourceType(SourceType.MANUAL)
                .build();
    }

    private CompanyItemRole role(Company company, Item item, CompanyRole companyRole) {
        return CompanyItemRole.builder()
                .company(company)
                .item(item)
                .companyRole(companyRole)
                .sourceType(SourceType.MANUAL)
                .build();
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
