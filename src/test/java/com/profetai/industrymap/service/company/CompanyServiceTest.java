package com.profetai.industrymap.service.company;

import com.profetai.industrymap.enums.IdentifierType;
import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.enums.SourceType;
import com.profetai.industrymap.exceptions.ServerException;
import com.profetai.industrymap.model.Company;
import com.profetai.industrymap.model.CompanyAlias;
import com.profetai.industrymap.model.CompanyIdentifier;
import com.profetai.industrymap.payloads.ProvenanceRequest;
import com.profetai.industrymap.payloads.company.CreateCompanyAliasRequest;
import com.profetai.industrymap.payloads.company.CreateCompanyRequest;
import com.profetai.industrymap.payloads.company.CreateIdentifierRequest;
import com.profetai.industrymap.repository.CompanyAliasRepository;
import com.profetai.industrymap.repository.CompanyIdentifierRepository;
import com.profetai.industrymap.repository.CompanyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.hibernate.LazyInitializationException;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private CompanyAliasRepository companyAliasRepository;

    @Mock
    private CompanyIdentifierRepository companyIdentifierRepository;

    @InjectMocks
    private CompanyService companyService;

    private final Company tsmc = Company.builder().id(1L).normalizedName("台積電").displayName("台積電").build();
    private final Company kmc = Company.builder().id(4L).normalizedName("桂盟國際").displayName("桂盟國際").build();

    @Test
    @DisplayName("建立沒有任何識別碼的未上市公司應成功")
    void create_privateCompanyWithoutIdentifier_shouldPersist() {
        // Given：SRAM 是美國私人公司，沒有任何交易所代號
        when(companyRepository.existsByNormalizedName("sram")).thenReturn(false);
        when(companyRepository.save(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Company created = companyService.create(CreateCompanyRequest.builder()
                .displayName("SRAM")
                .country("US")
                .provenance(manualProvenance())
                .build());

        // Then
        assertAll(
                () -> assertEquals("sram", created.getNormalizedName()),
                () -> assertEquals("SRAM", created.getDisplayName()),
                () -> assertEquals("US", created.getCountry()),
                () -> assertFalse(created.isPublic()),
                () -> verify(companyIdentifierRepository, never()).save(any(CompanyIdentifier.class)));
    }

    @Test
    @DisplayName("建立公司時正規化名稱重複應拋出 409 ServerException")
    void create_duplicatedNormalizedName_shouldThrowConflict() {
        when(companyRepository.existsByNormalizedName("台積電")).thenReturn(true);

        ServerException ex = assertThrows(ServerException.class, () -> companyService.create(
                CreateCompanyRequest.builder().displayName("台積電").provenance(manualProvenance()).build()));

        assertAll(
                () -> assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus()),
                () -> verify(companyRepository, never()).save(any(Company.class)));
    }

    @Test
    @DisplayName("識別碼的（類型, 代號值）已被其他公司登記時應拋出 409 ServerException")
    void addIdentifier_duplicatedTypeAndValue_shouldThrowConflict() {
        when(companyRepository.findById(1L)).thenReturn(Optional.of(tsmc));
        when(companyIdentifierRepository.existsByIdentifierTypeAndIdentifierValue(IdentifierType.TWSE, "2330"))
                .thenReturn(true);

        ServerException ex = assertThrows(ServerException.class,
                () -> companyService.addIdentifier(1L, identifierRequest(IdentifierType.TWSE, "2330", false)));

        assertAll(
                () -> assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus()),
                () -> verify(companyIdentifierRepository, never()).save(any(CompanyIdentifier.class)));
    }

    @Test
    @DisplayName("同一公司登記第二筆主要識別碼時應拋出 409 ServerException")
    void addIdentifier_secondPrimaryIdentifier_shouldThrowConflict() {
        when(companyRepository.findById(1L)).thenReturn(Optional.of(tsmc));
        when(companyIdentifierRepository.existsByIdentifierTypeAndIdentifierValue(IdentifierType.NYSE, "TSM"))
                .thenReturn(false);
        when(companyIdentifierRepository.existsByCompanyIdAndIsPrimaryTrue(1L)).thenReturn(true);

        ServerException ex = assertThrows(ServerException.class,
                () -> companyService.addIdentifier(1L, identifierRequest(IdentifierType.NYSE, "TSM", true)));

        assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
    }

    @Test
    @DisplayName("同一公司登記第二筆非主要識別碼應成功，多地掛牌屬正常情況")
    void addIdentifier_secondNonPrimaryIdentifier_shouldPersist() {
        when(companyRepository.findById(1L)).thenReturn(Optional.of(tsmc));
        when(companyIdentifierRepository.existsByIdentifierTypeAndIdentifierValue(IdentifierType.NYSE, "TSM"))
                .thenReturn(false);
        when(companyIdentifierRepository.save(any(CompanyIdentifier.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CompanyIdentifier created =
                companyService.addIdentifier(1L, identifierRequest(IdentifierType.NYSE, "TSM", false));

        assertAll(
                () -> assertEquals(IdentifierType.NYSE, created.getIdentifierType()),
                () -> assertEquals("TSM", created.getIdentifierValue()),
                () -> assertFalse(created.isPrimary()),
                () -> assertEquals(tsmc, created.getCompany()));
    }

    @Test
    @DisplayName("以交易所代號 2330 查詢應回傳對應公司")
    void getByIdentifierValue_existingCode_shouldReturnCompany() {
        when(companyIdentifierRepository.findByIdentifierValue("2330")).thenReturn(List.of(
                CompanyIdentifier.builder().company(tsmc).identifierType(IdentifierType.TWSE)
                        .identifierValue("2330").isPrimary(true).build()));
        when(companyRepository.findById(1L)).thenReturn(Optional.of(tsmc));

        Company found = companyService.getByIdentifierValue("2330");

        assertEquals(tsmc, found);
    }

    @Test
    @DisplayName("以代號查詢應回傳可直接讀取欄位的公司，而非識別碼上未初始化的關聯")
    void getByIdentifierValue_existingCode_shouldReturnInitializedCompanyNotLazyProxy() {
        // Given：識別碼上的 company 是 LAZY 關聯，交易結束後讀欄位會炸
        // （open-in-view 為 false，呼叫端在交易外組裝回應）
        CompanyIdentifier identifier = CompanyIdentifier.builder().company(uninitializedProxyOf(kmc))
                .identifierType(IdentifierType.TWSE).identifierValue("5306").isPrimary(true).build();
        when(companyIdentifierRepository.findByIdentifierValue("5306")).thenReturn(List.of(identifier));
        when(companyRepository.findById(4L)).thenReturn(Optional.of(kmc));

        // When
        Company found = companyService.getByIdentifierValue("5306");

        // Then：回傳的公司必須能在交易外讀取欄位
        assertAll(
                () -> assertEquals("桂盟國際", found.getDisplayName()),
                () -> assertEquals("桂盟國際", found.getNormalizedName()));
    }

    @Test
    @DisplayName("以路徑識別（代號）查詢時同樣應回傳可直接讀取欄位的公司")
    void getByReference_matchingIdentifier_shouldReturnInitializedCompanyNotLazyProxy() {
        CompanyIdentifier identifier = CompanyIdentifier.builder().company(uninitializedProxyOf(kmc))
                .identifierType(IdentifierType.TWSE).identifierValue("5306").isPrimary(true).build();
        when(companyIdentifierRepository.findByIdentifierValue("5306")).thenReturn(List.of(identifier));
        when(companyRepository.findById(4L)).thenReturn(Optional.of(kmc));

        assertEquals("桂盟國際", companyService.getByReference("5306").getDisplayName());
    }

    /** 模擬 Hibernate 的未初始化代理：只有主鍵讀得到，其餘欄位一律拋 LazyInitializationException */
    private Company uninitializedProxyOf(Company company) {
        Company proxy = mock(Company.class);
        when(proxy.getId()).thenReturn(company.getId());
        lenient().when(proxy.getDisplayName())
                .thenThrow(new LazyInitializationException("Could not initialize proxy - no session"));
        lenient().when(proxy.getNormalizedName())
                .thenThrow(new LazyInitializationException("Could not initialize proxy - no session"));
        return proxy;
    }

    @Test
    @DisplayName("以不存在的代號查詢應拋出 404 ServerException")
    void getByIdentifierValue_unknownCode_shouldThrowNotFound() {
        when(companyIdentifierRepository.findByIdentifierValue("9999")).thenReturn(List.of());

        ServerException ex = assertThrows(ServerException.class, () -> companyService.getByIdentifierValue("9999"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
    }

    @Test
    @DisplayName("公司別名與另一家公司的正規化名稱衝突時應拋出 409 ServerException")
    void addAlias_aliasCollidesWithAnotherCompanyName_shouldThrowConflict() {
        Company mediatek = Company.builder().id(2L).normalizedName("聯發科").displayName("聯發科").build();
        when(companyRepository.findById(1L)).thenReturn(Optional.of(tsmc));
        when(companyRepository.findByNormalizedName("聯發科")).thenReturn(Optional.of(mediatek));

        ServerException ex = assertThrows(ServerException.class,
                () -> companyService.addAlias(1L, aliasRequest("聯發科")));

        assertAll(
                () -> assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus()),
                () -> verify(companyAliasRepository, never()).save(any(CompanyAlias.class)));
    }

    @Test
    @DisplayName("以已登記的公司別名查詢應回傳對應公司")
    void resolveByName_matchingAlias_shouldReturnAliasedCompany() {
        when(companyRepository.findByNormalizedName("tsmc")).thenReturn(Optional.empty());
        when(companyAliasRepository.findByNormalizedAlias("tsmc"))
                .thenReturn(Optional.of(CompanyAlias.builder().company(tsmc).normalizedAlias("tsmc").build()));

        assertEquals(tsmc, companyService.resolveByName("TSMC").orElse(null));
    }

    @Test
    @DisplayName("以路徑識別查詢時代號優先，查無代號則退回正規化名稱")
    void getByReference_notAnIdentifier_shouldFallBackToNormalizedName() {
        // Given：SRAM 沒有任何代號，只能以名稱定位
        Company sram = Company.builder().id(3L).normalizedName("sram").displayName("SRAM").build();
        // 代號以原字串比對（代號的大小寫有意義），名稱則走正規化
        when(companyIdentifierRepository.findByIdentifierValue("SRAM")).thenReturn(List.of());
        when(companyRepository.findByNormalizedName("sram")).thenReturn(Optional.of(sram));

        assertEquals(sram, companyService.getByReference("SRAM"));
    }

    @Test
    @DisplayName("路徑識別既非代號也非公司名稱時應拋出 404 ServerException")
    void getByReference_unknownReference_shouldThrowNotFound() {
        when(companyIdentifierRepository.findByIdentifierValue("nobody")).thenReturn(List.of());
        when(companyRepository.findByNormalizedName("nobody")).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ServerException.class, () -> companyService.getByReference("nobody")).getHttpStatus());
    }

    @Test
    @DisplayName("對外依代號取得公司時已駁回的公司應視為查無而回 404")
    void getVisibleByReference_rejectedCompany_shouldThrowNotFound() {
        Company rejected = Company.builder().id(9L).normalizedName("空殼公司").displayName("空殼公司")
                .reviewStatus(ReviewStatus.REJECTED).build();
        when(companyIdentifierRepository.findByIdentifierValue("9999")).thenReturn(List.of());
        when(companyRepository.findByNormalizedName("9999")).thenReturn(Optional.of(rejected));

        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ServerException.class,
                        () -> companyService.getVisibleByReference("9999")).getHttpStatus());
    }

    @Test
    @DisplayName("對外依代號取得公司時草稿公司仍應取得")
    void getVisibleByReference_draftCompany_shouldReturnCompany() {
        when(companyIdentifierRepository.findByIdentifierValue("台積電")).thenReturn(List.of());
        when(companyRepository.findByNormalizedName("台積電")).thenReturn(Optional.of(tsmc));

        assertEquals(tsmc, companyService.getVisibleByReference("台積電"));
    }

    @Test
    @DisplayName("對外列出公司識別碼時已駁回的識別碼不得出現")
    void findVisibleIdentifiers_rejectedIdentifier_shouldBeExcluded() {
        CompanyIdentifier valid = CompanyIdentifier.builder()
                .id(1L).company(tsmc).identifierType(IdentifierType.TWSE).identifierValue("2330").build();
        CompanyIdentifier rejected = CompanyIdentifier.builder()
                .id(2L).company(tsmc).identifierType(IdentifierType.TWSE).identifierValue("0000")
                .reviewStatus(ReviewStatus.REJECTED).build();
        when(companyIdentifierRepository.findByCompanyId(1L)).thenReturn(List.of(valid, rejected));

        assertEquals(List.of(valid), companyService.findVisibleIdentifiers(1L));
    }

    @Test
    @DisplayName("取得公司對外識別時有主要代號應回代號")
    void referenceOf_withPrimaryIdentifier_shouldReturnIdentifierValue() {
        CompanyIdentifier primary = CompanyIdentifier.builder()
                .id(1L).company(tsmc).identifierType(IdentifierType.TWSE).identifierValue("2330")
                .isPrimary(true).build();
        when(companyIdentifierRepository.findByCompanyId(1L)).thenReturn(List.of(primary));

        assertEquals("2330", companyService.referenceOf(tsmc));
    }

    @Test
    @DisplayName("取得公司對外識別時無識別碼應退回正規化名稱")
    void referenceOf_withoutIdentifier_shouldFallBackToNormalizedName() {
        when(companyIdentifierRepository.findByCompanyId(1L)).thenReturn(List.of());

        assertEquals("台積電", companyService.referenceOf(tsmc));
    }

    @Test
    @DisplayName("批次取得對外識別時應一次查主要識別碼，有代號者回代號、無代號者回名稱")
    void referencesOf_mixedCompanies_shouldResolveEachInSingleQuery() {
        // Given：台積電有主要代號，桂盟沒有登記任何識別碼
        CompanyIdentifier primary = CompanyIdentifier.builder()
                .id(1L).company(tsmc).identifierType(IdentifierType.TWSE).identifierValue("2330")
                .isPrimary(true).build();
        when(companyIdentifierRepository.findByCompanyIdInAndIsPrimaryTrue(Set.of(1L, 4L)))
                .thenReturn(List.of(primary));

        // When：同一家公司出現兩次（同一零件的兩個角色），不應重複查詢
        Map<Long, String> references = companyService.referencesOf(List.of(tsmc, kmc, tsmc));

        // Then
        assertAll(
                () -> assertEquals(2, references.size()),
                () -> assertEquals("2330", references.get(1L)),
                () -> assertEquals("桂盟國際", references.get(4L)));
    }

    @Test
    @DisplayName("有主要代號的公司，其對外識別應可直接用於依代號查詢")
    void referenceOf_withPrimaryIdentifier_shouldBeQueryableByReference() {
        // Given
        CompanyIdentifier primary = CompanyIdentifier.builder()
                .id(1L).company(tsmc).identifierType(IdentifierType.TWSE).identifierValue("2330")
                .isPrimary(true).build();
        when(companyIdentifierRepository.findByCompanyId(1L)).thenReturn(List.of(primary));
        when(companyIdentifierRepository.findByIdentifierValue("2330")).thenReturn(List.of(primary));
        when(companyRepository.findById(1L)).thenReturn(Optional.of(tsmc));

        // When：把回應裡的對外識別原封不動拿去查詢
        String reference = companyService.referenceOf(tsmc);

        // Then
        assertEquals(tsmc, companyService.getByReference(reference));
    }

    @Test
    @DisplayName("無識別碼的公司，其對外識別（正規化名稱）同樣應可直接用於查詢")
    void referenceOf_withoutIdentifier_shouldBeQueryableByReference() {
        when(companyIdentifierRepository.findByCompanyId(4L)).thenReturn(List.of());
        when(companyIdentifierRepository.findByIdentifierValue("桂盟國際")).thenReturn(List.of());
        when(companyRepository.findByNormalizedName("桂盟國際")).thenReturn(Optional.of(kmc));

        String reference = companyService.referenceOf(kmc);

        assertEquals(kmc, companyService.getByReference(reference));
    }

    @Test
    @DisplayName("批次取得對外識別時輸入為空應直接回空 Map 不查資料庫")
    void referencesOf_emptyInput_shouldReturnEmptyMapWithoutQuery() {
        assertAll(
                () -> assertTrue(companyService.referencesOf(List.of()).isEmpty()),
                () -> verify(companyIdentifierRepository, never()).findByCompanyIdInAndIsPrimaryTrue(any()));
    }

    private CreateIdentifierRequest identifierRequest(IdentifierType type, String value, boolean primary) {
        return CreateIdentifierRequest.builder()
                .identifierType(type)
                .identifierValue(value)
                .primary(primary)
                .provenance(manualProvenance())
                .build();
    }

    private CreateCompanyAliasRequest aliasRequest(String alias) {
        return CreateCompanyAliasRequest.builder().alias(alias).provenance(manualProvenance()).build();
    }

    private ProvenanceRequest manualProvenance() {
        return ProvenanceRequest.builder().sourceType(SourceType.MANUAL).build();
    }
}
