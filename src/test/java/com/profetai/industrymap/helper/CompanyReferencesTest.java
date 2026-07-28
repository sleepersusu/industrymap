package com.profetai.industrymap.helper;

import com.profetai.industrymap.enums.IdentifierType;
import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.model.Company;
import com.profetai.industrymap.model.CompanyIdentifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyReferencesTest {

    private final Company tsmc = Company.builder()
            .id(1L).normalizedName("台積電").displayName("台積電").build();

    @Test
    @DisplayName("公司有主要識別碼時對外識別應為交易所限定形式")
    void of_withPrimaryIdentifier_shouldReturnQualifiedReference() {
        // Given：台積電同時有 TWSE 2330（主要）與 NYSE TSM
        CompanyIdentifier twse = identifier(IdentifierType.TWSE, "2330", true, ReviewStatus.VERIFIED);
        CompanyIdentifier nyse = identifier(IdentifierType.NYSE, "TSM", false, ReviewStatus.VERIFIED);

        // When
        String reference = CompanyReferences.of(tsmc, List.of(nyse, twse));

        // Then：代號只在發行它的交易所內唯一，識別值必須把交易所帶上
        assertEquals("TWSE:2330", reference);
    }

    @Test
    @DisplayName("非交易所類型的主要識別碼同樣走限定形式")
    void of_nonExchangePrimaryIdentifier_shouldAlsoBeQualified() {
        CompanyIdentifier taxId = identifier(IdentifierType.TAX_ID, "12345678", true, ReviewStatus.VERIFIED);

        assertEquals("TAX_ID:12345678", CompanyReferences.of(tsmc, List.of(taxId)));
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

    @Test
    @DisplayName("限定形式應拆解成識別碼類型與代號值")
    void parse_qualifiedReference_shouldSplitIntoTypeAndValue() {
        CompanyReferences.Qualified qualified = CompanyReferences.parse("TWSE:2330").orElseThrow();

        assertAll(
                () -> assertEquals(IdentifierType.TWSE, qualified.getIdentifierType()),
                () -> assertEquals("2330", qualified.getIdentifierValue()));
    }

    @Test
    @DisplayName("冒號前非合法識別碼類型時不得視為限定形式")
    void parse_unknownPrefix_shouldNotBeQualified() {
        // 識別碼值本身可能含冒號（OTHER 底下的 ISIN 之類），誤判成限定形式會查無資料
        assertTrue(CompanyReferences.parse("ISIN:DE0006231004").isEmpty());
    }

    @Test
    @DisplayName("不含冒號的裸代號不得視為限定形式")
    void parse_bareIdentifierValue_shouldNotBeQualified() {
        assertTrue(CompanyReferences.parse("2330").isEmpty());
    }

    @Test
    @DisplayName("代號值本身含冒號時只切第一個冒號")
    void parse_valueContainingColon_shouldSplitOnFirstColonOnly() {
        CompanyReferences.Qualified qualified = CompanyReferences.parse("OTHER:ISIN:DE0006231004").orElseThrow();

        assertAll(
                () -> assertEquals(IdentifierType.OTHER, qualified.getIdentifierType()),
                () -> assertEquals("ISIN:DE0006231004", qualified.getIdentifierValue()));
    }

    @Test
    @DisplayName("僅有類型而無代號值時不得視為限定形式")
    void parse_missingValue_shouldNotBeQualified() {
        assertTrue(CompanyReferences.parse("TWSE:").isEmpty());
    }

    @Test
    @DisplayName("null 輸入不得視為限定形式")
    void parse_null_shouldNotBeQualified() {
        assertTrue(CompanyReferences.parse(null).isEmpty());
    }

    @Test
    @DisplayName("組裝出的限定形式應能原封不動拆解回原本的類型與值")
    void parse_qualifyOutput_shouldRoundTrip() {
        // 組裝與拆解成對，分開寫會漂移，因此以來回轉換驗證兩者對齊（design D5）
        String reference = CompanyReferences.qualify(IdentifierType.HKEX, "0992");
        CompanyReferences.Qualified qualified = CompanyReferences.parse(reference).orElseThrow();

        assertAll(
                () -> assertEquals("HKEX:0992", reference),
                () -> assertEquals(IdentifierType.HKEX, qualified.getIdentifierType()),
                () -> assertEquals("0992", qualified.getIdentifierValue()));
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
