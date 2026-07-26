package com.profetai.industrymap.helper;

import com.profetai.industrymap.enums.IdentifierType;
import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.model.Company;
import com.profetai.industrymap.model.CompanyIdentifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompanyReferencesTest {

    private final Company tsmc = Company.builder()
            .id(1L).normalizedName("台積電").displayName("台積電").build();

    @Test
    @DisplayName("公司有主要識別碼時對外識別應為該代號")
    void of_withPrimaryIdentifier_shouldReturnIdentifierValue() {
        // Given：台積電同時有 TWSE 2330（主要）與 NYSE TSM
        CompanyIdentifier twse = identifier(IdentifierType.TWSE, "2330", true, ReviewStatus.VERIFIED);
        CompanyIdentifier nyse = identifier(IdentifierType.NYSE, "TSM", false, ReviewStatus.VERIFIED);

        // When
        String reference = CompanyReferences.of(tsmc, List.of(nyse, twse));

        // Then
        assertEquals("2330", reference);
    }

    @Test
    @DisplayName("公司無任何識別碼時對外識別應退回正規化名稱")
    void of_withoutIdentifier_shouldFallBackToNormalizedName() {
        assertEquals("台積電", CompanyReferences.of(tsmc, List.of()));
    }

    @Test
    @DisplayName("只有非主要識別碼時對外識別應退回正規化名稱")
    void of_withoutPrimaryIdentifier_shouldFallBackToNormalizedName() {
        CompanyIdentifier nyse = identifier(IdentifierType.NYSE, "TSM", false, ReviewStatus.VERIFIED);

        assertEquals("台積電", CompanyReferences.of(tsmc, List.of(nyse)));
    }

    @Test
    @DisplayName("主要識別碼已被駁回時對外識別應退回正規化名稱")
    void of_rejectedPrimaryIdentifier_shouldFallBackToNormalizedName() {
        // 已駁回的識別碼不外露，對外識別也不該引用它，否則拿回來查詢會查不到
        CompanyIdentifier rejected = identifier(IdentifierType.TWSE, "2330", true, ReviewStatus.REJECTED);

        assertEquals("台積電", CompanyReferences.of(tsmc, List.of(rejected)));
    }

    @Test
    @DisplayName("識別碼清單為 null 時對外識別應退回正規化名稱")
    void of_nullIdentifiers_shouldFallBackToNormalizedName() {
        assertEquals("台積電", CompanyReferences.of(tsmc, null));
    }

    private CompanyIdentifier identifier(IdentifierType type, String value, boolean primary, ReviewStatus status) {
        return CompanyIdentifier.builder()
                .company(tsmc)
                .identifierType(type)
                .identifierValue(value)
                .isPrimary(primary)
                .reviewStatus(status)
                .build();
    }
}
