package com.profetai.industrymap.repository;

import com.profetai.industrymap.enums.IdentifierType;
import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.enums.SourceType;
import com.profetai.industrymap.model.Company;
import com.profetai.industrymap.model.CompanyIdentifier;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 驗證代號解析用的兩支查詢會連同所屬公司一起載入。
 *
 * <p>{@code CompanyIdentifier.company} 是 LAZY 關聯，而 {@link Company} 的 {@code @Id} 標在欄位上
 * （field access），Hibernate 因此無法在代理上攔截識別碼 getter——對未初始化的代理呼叫
 * {@code getId()} 會觸發初始化，每筆識別碼多打一次 SELECT。代號解析本來就一定要用到公司本體，
 * 所以查詢直接把公司撈齊，而不是讓代理逐筆自己去補。</p>
 *
 * <p>斷言前必須 {@code clear()}：fixture 剛存完時公司仍是 managed 實體，關聯會直接解析到它本身
 * 而不是代理，不清掉的話這個測試在修好之前就已經是綠的。</p>
 */
class CompanyIdentifierFetchTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private CompanyIdentifierRepository companyIdentifierRepository;

    @Test
    @DisplayName("以代號值查詢時所屬公司應已載入，不留下未初始化的代理")
    void findByIdentifierValue_shouldFetchCompanyEagerly() {
        // Given：一筆識別碼，並清空持久化上下文讓查詢真的重新載入
        Company tsmc = givenCompanyWithIdentifier("台積電", IdentifierType.TWSE, "2330");
        entityManager.clear();

        // When
        List<CompanyIdentifier> found = companyIdentifierRepository.findByIdentifierValue(FIXTURE_PREFIX + "2330");

        // Then
        assertAll(
                () -> assertEquals(1, found.size()),
                () -> assertTrue(Hibernate.isInitialized(found.get(0).getCompany()),
                        "公司應隨識別碼一起載入，否則讀主鍵就會多一次 SELECT"),
                () -> assertEquals(tsmc.getId(), found.get(0).getCompany().getId()));
    }

    @Test
    @DisplayName("以限定形式查詢時所屬公司同樣應已載入")
    void findByIdentifierTypeAndIdentifierValue_shouldFetchCompanyEagerly() {
        Company lenovo = givenCompanyWithIdentifier("聯想", IdentifierType.HKEX, "0992");
        entityManager.clear();

        CompanyIdentifier found = companyIdentifierRepository
                .findByIdentifierTypeAndIdentifierValue(IdentifierType.HKEX, FIXTURE_PREFIX + "0992")
                .orElseThrow();

        assertAll(
                () -> assertTrue(Hibernate.isInitialized(found.getCompany()),
                        "公司應隨識別碼一起載入，否則讀主鍵就會多一次 SELECT"),
                () -> assertEquals(lenovo.getId(), found.getCompany().getId()));
    }

    @Test
    @DisplayName("載入的公司應可直接讀審核狀態，供解析時過濾已駁回的公司")
    void findByIdentifierValue_shouldExposeCompanyReviewStatus() {
        // Given：已駁回的公司——解析代號時要靠這個狀態把它排除在候選之外
        Company mistaken = companyRepository.saveAndFlush(Company.builder()
                .normalizedName(FIXTURE_PREFIX + "誤建公司")
                .displayName("誤建公司")
                .sourceType(SourceType.MANUAL)
                .reviewStatus(ReviewStatus.REJECTED)
                .build());
        companyIdentifierRepository.saveAndFlush(identifier(mistaken, IdentifierType.TWSE, "9999"));
        entityManager.clear();

        List<CompanyIdentifier> found = companyIdentifierRepository.findByIdentifierValue(FIXTURE_PREFIX + "9999");

        assertEquals(ReviewStatus.REJECTED, found.get(0).getCompany().getReviewStatus());
    }

    private Company givenCompanyWithIdentifier(String displayName, IdentifierType type, String value) {
        Company company = companyRepository.saveAndFlush(Company.builder()
                .normalizedName(FIXTURE_PREFIX + displayName)
                .displayName(displayName)
                .sourceType(SourceType.MANUAL)
                .reviewStatus(ReviewStatus.VERIFIED)
                .build());
        companyIdentifierRepository.saveAndFlush(identifier(company, type, value));
        return company;
    }

    private CompanyIdentifier identifier(Company company, IdentifierType type, String value) {
        return CompanyIdentifier.builder()
                .company(company)
                .identifierType(type)
                .identifierValue(FIXTURE_PREFIX + value)
                .isPrimary(true)
                .sourceType(SourceType.MANUAL)
                .reviewStatus(ReviewStatus.VERIFIED)
                .build();
    }
}
