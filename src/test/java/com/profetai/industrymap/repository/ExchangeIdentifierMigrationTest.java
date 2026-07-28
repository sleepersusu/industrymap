package com.profetai.industrymap.repository;

import com.profetai.industrymap.enums.CompanyRole;
import com.profetai.industrymap.enums.IdentifierType;
import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.enums.SourceType;
import com.profetai.industrymap.model.Company;
import com.profetai.industrymap.model.CompanyAlias;
import com.profetai.industrymap.model.CompanyIdentifier;
import com.profetai.industrymap.model.CompanyItemRole;
import com.profetai.industrymap.model.Item;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 驗證交易所識別碼轉換 migration（{@code V2026_07_28_100100}）的 SQL 行為。
 *
 * <p>Flyway 在 context 啟動時就已跑完，之後無法再對同一支 migration 重跑，因此這裡改為
 * 對測試自建的 fixture 套用同一份 SQL 檔——驗的是那段 SQL 本身，而不是某個環境的既有資料。
 * 這也是 migration 條件用交易所前綴（{@code LIKE 'HKEX:%'}）而非寫死精確值的原因：
 * 寫死值的話，乾淨的 Testcontainer 上沒有資料可斷言，共用開發資料庫上 fixture 轉換後
 * 又會與已轉正的真實資料撞唯一鍵。</p>
 */
class ExchangeIdentifierMigrationTest extends AbstractPostgresIntegrationTest {

    private static final Path MIGRATION_FILE = Path.of(
            "src/main/resources/db/migration/V2026_07_28_100100__normalize_exchange_identifiers.sql");

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private CompanyAliasRepository companyAliasRepository;
    @Autowired
    private CompanyIdentifierRepository companyIdentifierRepository;
    @Autowired
    private CompanyItemRoleRepository companyItemRoleRepository;
    @Autowired
    private ItemRepository itemRepository;

    @Test
    @DisplayName("轉換後應可用純代號查到原公司，交易所前綴移入類型")
    void runMigration_otherWithExchangePrefix_shouldBecomeTypedIdentifier() {
        Company lenovo = companyRepository.saveAndFlush(company("聯想"));
        companyIdentifierRepository.saveAndFlush(
                identifier(lenovo, IdentifierType.OTHER, "HKEX:" + FIXTURE_PREFIX + "0992"));

        runMigration();

        List<CompanyIdentifier> found = companyIdentifierRepository.findByIdentifierValue(FIXTURE_PREFIX + "0992");
        assertAll(
                () -> assertEquals(1, found.size()),
                () -> assertEquals(lenovo.getId(), found.get(0).getCompany().getId()),
                () -> assertEquals(IdentifierType.HKEX, found.get(0).getIdentifierType()));
    }

    @Test
    @DisplayName("法蘭克福交易所前綴同樣應轉為對應類型")
    void runMigration_frankfurtPrefix_shouldBecomeTypedIdentifier() {
        Company infineon = companyRepository.saveAndFlush(company("Infineon"));
        companyIdentifierRepository.saveAndFlush(
                identifier(infineon, IdentifierType.OTHER, "FSE:" + FIXTURE_PREFIX + "IFX"));

        runMigration();

        List<CompanyIdentifier> found = companyIdentifierRepository.findByIdentifierValue(FIXTURE_PREFIX + "IFX");
        assertAll(
                () -> assertEquals(1, found.size()),
                () -> assertEquals(infineon.getId(), found.get(0).getCompany().getId()),
                () -> assertEquals(IdentifierType.FSE, found.get(0).getIdentifierType()));
    }

    @Test
    @DisplayName("轉換不應動到公司名稱、別名、供應角色與識別碼的審核狀態")
    void runMigration_convertedIdentifier_shouldLeaveCompanyDataUntouched() {
        Company foxconnInterconnect = companyRepository.saveAndFlush(company("鴻騰精密科技"));
        Item connector = itemRepository.saveAndFlush(item("連接器"));
        companyAliasRepository.saveAndFlush(alias(foxconnInterconnect, "foxconn interconnect"));
        companyItemRoleRepository.saveAndFlush(role(foxconnInterconnect, connector));
        companyIdentifierRepository.saveAndFlush(
                identifier(foxconnInterconnect, IdentifierType.OTHER, "HKEX:" + FIXTURE_PREFIX + "6088"));

        runMigration();

        CompanyIdentifier converted = companyIdentifierRepository
                .findByIdentifierValue(FIXTURE_PREFIX + "6088").get(0);
        Company reloaded = companyRepository.findById(foxconnInterconnect.getId()).orElseThrow();
        assertAll(
                () -> assertEquals("鴻騰精密科技", reloaded.getDisplayName()),
                () -> assertEquals(FIXTURE_PREFIX + "鴻騰精密科技", reloaded.getNormalizedName()),
                () -> assertEquals(1, companyAliasRepository.findByCompanyId(reloaded.getId()).size()),
                () -> assertEquals(1, companyItemRoleRepository
                        .findByItemIdAndReviewStatusIn(connector.getId(), EnumSet.allOf(ReviewStatus.class)).size()),
                () -> assertEquals(ReviewStatus.VERIFIED, converted.getReviewStatus()));
    }

    @Test
    @DisplayName("轉換完成後不應留下 OTHER 且值含交易所前綴的識別碼")
    void runMigration_afterConversion_shouldLeaveNoPrefixedOtherIdentifier() {
        Company lenovo = companyRepository.saveAndFlush(company("聯想"));
        Company infineon = companyRepository.saveAndFlush(company("Infineon"));
        companyIdentifierRepository.saveAndFlush(
                identifier(lenovo, IdentifierType.OTHER, "HKEX:" + FIXTURE_PREFIX + "0992"));
        companyIdentifierRepository.saveAndFlush(
                identifier(infineon, IdentifierType.OTHER, "FSE:" + FIXTURE_PREFIX + "IFX"));

        runMigration();

        // 範圍限定在本測試建的兩家公司：沒有 Docker 時測試跑在共用的開發資料庫上，整張表可能有
        // 其他合法的非交易所 OTHER 識別碼（值帶冒號並不違規），掃全表會把它們誤判成漏轉的資料
        Number remaining = (Number) entityManager.createNativeQuery("""
                        SELECT count(*) FROM company_identifier
                        WHERE company_id IN (:companyIds)
                          AND identifier_type = 'OTHER' AND identifier_value LIKE '%:%'""")
                .setParameter("companyIds", List.of(lenovo.getId(), infineon.getId()))
                .getSingleResult();
        assertEquals(0, remaining.intValue());
    }

    /**
     * 對當前交易套用 migration 檔的 SQL。Flyway 已在 context 啟動時執行過，
     * 無法重跑，因此直接讀檔執行——這樣測到的仍是實際會上線的那份 SQL。
     */
    private void runMigration() {
        entityManager.flush();
        for (String statement : readMigrationStatements()) {
            entityManager.createNativeQuery(statement).executeUpdate();
        }
        entityManager.clear();
    }

    private List<String> readMigrationStatements() {
        String sql;
        try {
            sql = Files.readString(MIGRATION_FILE, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("讀不到 migration 檔：" + MIGRATION_FILE, e);
        }
        String withoutComments = sql.lines()
                .filter(line -> !line.stripLeading().startsWith("--"))
                .reduce("", (acc, line) -> acc + line + "\n");
        return Arrays.stream(withoutComments.split(";"))
                .map(String::trim)
                .filter(statement -> !statement.isEmpty())
                .toList();
    }

    private Company company(String displayName) {
        return Company.builder()
                .normalizedName(FIXTURE_PREFIX + displayName)
                .displayName(displayName)
                .sourceType(SourceType.MANUAL)
                .build();
    }

    private Item item(String name) {
        return Item.builder()
                .normalizedName(FIXTURE_PREFIX + name)
                .displayName(name)
                .sourceType(SourceType.MANUAL)
                .build();
    }

    private CompanyAlias alias(Company company, String alias) {
        return CompanyAlias.builder()
                .company(company)
                .normalizedAlias(FIXTURE_PREFIX + alias)
                .displayAlias(alias)
                .sourceType(SourceType.MANUAL)
                .build();
    }

    private CompanyItemRole role(Company company, Item item) {
        return CompanyItemRole.builder()
                .company(company)
                .item(item)
                .companyRole(CompanyRole.MANUFACTURE)
                .sourceType(SourceType.MANUAL)
                .build();
    }

    /** 既有那 3 筆都是已審核通過的主要識別碼，fixture 比照，才驗得出轉換沒動到審核狀態 */
    private CompanyIdentifier identifier(Company company, IdentifierType type, String value) {
        return CompanyIdentifier.builder()
                .company(company)
                .identifierType(type)
                .identifierValue(value)
                .isPrimary(true)
                .sourceType(SourceType.MANUAL)
                .reviewStatus(ReviewStatus.VERIFIED)
                .reviewedBy("tester")
                .build();
    }
}
