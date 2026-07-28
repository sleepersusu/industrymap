package com.profetai.industrymap.service.company;

import com.profetai.industrymap.enums.IdentifierType;
import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.enums.SourceType;
import com.profetai.industrymap.exceptions.ServerException;
import com.profetai.industrymap.model.Company;
import com.profetai.industrymap.model.CompanyAlias;
import com.profetai.industrymap.model.CompanyIdentifier;
import com.profetai.industrymap.payloads.ProvenanceRequest;
import com.profetai.industrymap.payloads.PageResponse;
import com.profetai.industrymap.payloads.company.CompanyQuery;
import com.profetai.industrymap.payloads.company.CompanyResponse;
import com.profetai.industrymap.payloads.company.CreateCompanyAliasRequest;
import com.profetai.industrymap.payloads.company.CreateCompanyRequest;
import com.profetai.industrymap.payloads.company.CreateIdentifierRequest;
import com.profetai.industrymap.repository.CompanyAliasRepository;
import com.profetai.industrymap.repository.CompanyIdentifierRepository;
import com.profetai.industrymap.repository.CompanyRepository;
import com.profetai.industrymap.repository.ItemRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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

    @Mock
    private ItemRepository itemRepository;

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
    @DisplayName("以香港交易所類型與純代號登記識別碼應成功，交易所資訊不進入值")
    void addIdentifier_hongKongExchangeWithPlainCode_shouldPersist() {
        when(companyRepository.findById(1L)).thenReturn(Optional.of(tsmc));
        when(companyIdentifierRepository.existsByIdentifierTypeAndIdentifierValue(IdentifierType.HKEX, "0992"))
                .thenReturn(false);
        when(companyIdentifierRepository.save(any(CompanyIdentifier.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CompanyIdentifier created =
                companyService.addIdentifier(1L, identifierRequest(IdentifierType.HKEX, "0992", true));

        assertAll(
                () -> assertEquals(IdentifierType.HKEX, created.getIdentifierType()),
                () -> assertEquals("0992", created.getIdentifierValue()));
    }

    @Test
    @DisplayName("以法蘭克福交易所類型與純代號登記識別碼應成功，交易所資訊不進入值")
    void addIdentifier_frankfurtExchangeWithPlainCode_shouldPersist() {
        when(companyRepository.findById(1L)).thenReturn(Optional.of(tsmc));
        when(companyIdentifierRepository.existsByIdentifierTypeAndIdentifierValue(IdentifierType.FSE, "IFX"))
                .thenReturn(false);
        when(companyIdentifierRepository.save(any(CompanyIdentifier.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CompanyIdentifier created =
                companyService.addIdentifier(1L, identifierRequest(IdentifierType.FSE, "IFX", true));

        assertAll(
                () -> assertEquals(IdentifierType.FSE, created.getIdentifierType()),
                () -> assertEquals("IFX", created.getIdentifierValue()));
    }

    @Test
    @DisplayName("以交易所代號 2330 查詢應回傳對應公司")
    void getByIdentifierValue_existingCode_shouldReturnCompany() {
        when(companyIdentifierRepository.findByIdentifierValue("2330")).thenReturn(List.of(
                CompanyIdentifier.builder().company(tsmc).identifierType(IdentifierType.TWSE)
                        .identifierValue("2330").isPrimary(true).build()));

        Company found = companyService.getByIdentifierValue("2330");

        assertEquals(tsmc, found);
    }

    // 「回傳的公司必須能在交易外讀取欄位」原本靠 service 以主鍵重新載入來保證，並在此以 mock 代理模擬。
    // 該保證已移到 CompanyIdentifierRepository 的 @EntityGraph，改由 CompanyIdentifierFetchTest
    // 對真實 Hibernate 驗證關聯確實已初始化——mock 模擬代理行為證不了這件事，故不在此重複。

    @Test
    @DisplayName("以不存在的代號查詢應拋出 404 ServerException")
    void getByIdentifierValue_unknownCode_shouldThrowNotFound() {
        when(companyIdentifierRepository.findByIdentifierValue("9999")).thenReturn(List.of());

        ServerException ex = assertThrows(ServerException.class, () -> companyService.getByIdentifierValue("9999"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
    }

    @Test
    @DisplayName("以限定形式查詢時應只回傳該交易所的公司，不受同代號值的其他交易所影響")
    void getByReference_qualifiedReference_shouldResolveByExchange() {
        // Given：聯想在香港、另一家公司在上海，同樣持有代號值 0992
        Company lenovo = Company.builder().id(5L).normalizedName("聯想").displayName("聯想").build();
        when(companyIdentifierRepository.findByIdentifierTypeAndIdentifierValue(IdentifierType.HKEX, "0992"))
                .thenReturn(Optional.of(CompanyIdentifier.builder().company(lenovo)
                        .identifierType(IdentifierType.HKEX).identifierValue("0992").isPrimary(true).build()));

        // When / Then：限定形式走唯一鍵，不經過裸代號查詢
        assertAll(
                () -> assertEquals(lenovo, companyService.getByReference("HKEX:0992")),
                () -> verify(companyIdentifierRepository, never()).findByIdentifierValue("HKEX:0992"));
    }

    @Test
    @DisplayName("以裸代號查詢且僅單筆命中時仍應回傳該公司")
    void getByReference_bareCodeSingleMatch_shouldReturnCompany() {
        // 手冊與既有操作大量使用裸代號，單筆命中沒有歧義，維持可用
        when(companyIdentifierRepository.findByIdentifierValue("2330")).thenReturn(List.of(
                CompanyIdentifier.builder().company(tsmc).identifierType(IdentifierType.TWSE)
                        .identifierValue("2330").isPrimary(true).build()));

        assertEquals(tsmc, companyService.getByReference("2330"));
    }

    @Test
    @DisplayName("以裸代號查詢而多家公司命中時應拋 409 並列出候選限定形式")
    void getByReference_bareCodeMultipleMatches_shouldThrowConflictListingCandidates() {
        // Given：兩家公司在不同交易所持有相同代號值，裸代號不足以指定唯一一家
        Company lenovo = Company.builder().id(5L).normalizedName("聯想").displayName("聯想").build();
        Company sseListed = Company.builder().id(6L).normalizedName("某滬市公司").displayName("某滬市公司").build();
        when(companyIdentifierRepository.findByIdentifierValue("0992")).thenReturn(List.of(
                CompanyIdentifier.builder().company(lenovo).identifierType(IdentifierType.HKEX)
                        .identifierValue("0992").isPrimary(true).build(),
                CompanyIdentifier.builder().company(sseListed).identifierType(IdentifierType.SSE)
                        .identifierValue("0992").isPrimary(true).build()));

        // When
        ServerException ex = assertThrows(ServerException.class, () -> companyService.getByReference("0992"));

        // Then：MUST NOT 任選一筆；訊息須讓呼叫端知道該改用哪個限定形式
        assertAll(
                () -> assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus()),
                () -> assertTrue(ex.getMessage().contains("HKEX:0992")),
                () -> assertTrue(ex.getMessage().contains("SSE:0992")));
    }

    @Test
    @DisplayName("依代號查詢的多筆命中行為應與依路徑識別查詢一致")
    void getByIdentifierValue_multipleMatches_shouldThrowConflictLikeGetByReference() {
        // 兩個入口若各自為政，就會出現「同一個值在 A 端點報衝突、在 B 端點靜默回錯公司」
        Company lenovo = Company.builder().id(5L).normalizedName("聯想").displayName("聯想").build();
        Company sseListed = Company.builder().id(6L).normalizedName("某滬市公司").displayName("某滬市公司").build();
        when(companyIdentifierRepository.findByIdentifierValue("0992")).thenReturn(List.of(
                CompanyIdentifier.builder().company(lenovo).identifierType(IdentifierType.HKEX)
                        .identifierValue("0992").isPrimary(true).build(),
                CompanyIdentifier.builder().company(sseListed).identifierType(IdentifierType.SSE)
                        .identifierValue("0992").isPrimary(true).build()));

        ServerException ex =
                assertThrows(ServerException.class, () -> companyService.getByIdentifierValue("0992"));

        assertAll(
                () -> assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus()),
                () -> assertTrue(ex.getMessage().contains("HKEX:0992")),
                () -> assertTrue(ex.getMessage().contains("SSE:0992")));
    }

    @Test
    @DisplayName("同一家公司在兩個交易所持有相同代號值時，裸代號查詢不算歧義")
    void getByReference_sameCodeUnderTwoExchangesOfOneCompany_shouldNotConflict() {
        // Given：上櫃轉上市會保留同一組代號，同一家公司因此可同時有 TPEX 與 TWSE 的 6488
        Company kingYuan = Company.builder().id(9L).normalizedName("環球晶").displayName("環球晶").build();
        when(companyIdentifierRepository.findByIdentifierValue("6488")).thenReturn(List.of(
                CompanyIdentifier.builder().company(kingYuan).identifierType(IdentifierType.TPEX)
                        .identifierValue("6488").isPrimary(false).build(),
                CompanyIdentifier.builder().company(kingYuan).identifierType(IdentifierType.TWSE)
                        .identifierValue("6488").isPrimary(true).build()));

        // When / Then：歧義的定義是「指向幾家公司」，不是「有幾筆識別碼」
        assertEquals(kingYuan, companyService.getByReference("6488"));
    }

    @Test
    @DisplayName("已駁回的識別碼不得造成歧義，也不得出現在衝突訊息中")
    void getByReference_rejectedCollidingIdentifier_shouldResolveToExposableCompany() {
        // Given：台積電持有已驗證的 TWSE 2330，另有一筆被駁回的誤建資料撞到同一代號值
        Company mistaken = Company.builder().id(8L).normalizedName("誤建公司").displayName("誤建公司").build();
        when(companyIdentifierRepository.findByIdentifierValue("2330")).thenReturn(List.of(
                CompanyIdentifier.builder().company(tsmc).identifierType(IdentifierType.TWSE)
                        .identifierValue("2330").isPrimary(true).reviewStatus(ReviewStatus.VERIFIED).build(),
                CompanyIdentifier.builder().company(mistaken).identifierType(IdentifierType.SSE)
                        .identifierValue("2330").isPrimary(true).reviewStatus(ReviewStatus.REJECTED).build()));

        // When / Then：已駁回的識別碼不外露於任何回應，更不該讓合法公司查不到
        assertEquals(tsmc, companyService.getByReference("2330"));
    }

    @Test
    @DisplayName("以已駁回的識別碼查詢應視為查無，不得據此定位公司")
    void getByReference_onlyRejectedIdentifierMatches_shouldFallBackToNameLookup() {
        Company mistaken = Company.builder().id(8L).normalizedName("誤建公司").displayName("誤建公司").build();
        when(companyIdentifierRepository.findByIdentifierTypeAndIdentifierValue(IdentifierType.SSE, "2330"))
                .thenReturn(Optional.of(CompanyIdentifier.builder().company(mistaken)
                        .identifierType(IdentifierType.SSE).identifierValue("2330")
                        .reviewStatus(ReviewStatus.REJECTED).build()));
        when(companyRepository.findByNormalizedName("sse2330")).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ServerException.class,
                        () -> companyService.getByReference("SSE:2330")).getHttpStatus());
    }

    @Test
    @DisplayName("限定形式命中已駁回識別碼時不得改以整串當代號值查詢")
    void getByReference_qualifiedMatchRejected_shouldNotFallBackToLiteralValueLookup() {
        // Given：台積電的 TWSE 2330 已被駁回，另一家公司在 OTHER 底下把「TWSE:2330」整串登記為代號值
        // （值含冒號是本專案明文支援的用法）
        Company unrelated = Company.builder().id(7L).normalizedName("無關公司").displayName("無關公司").build();
        when(companyIdentifierRepository.findByIdentifierTypeAndIdentifierValue(IdentifierType.TWSE, "2330"))
                .thenReturn(Optional.of(CompanyIdentifier.builder().company(tsmc)
                        .identifierType(IdentifierType.TWSE).identifierValue("2330")
                        .reviewStatus(ReviewStatus.REJECTED).build()));
        lenient().when(companyIdentifierRepository.findByIdentifierValue("TWSE:2330")).thenReturn(List.of(
                CompanyIdentifier.builder().company(unrelated).identifierType(IdentifierType.OTHER)
                        .identifierValue("TWSE:2330").reviewStatus(ReviewStatus.VERIFIED).build()));
        lenient().when(companyRepository.findByNormalizedName("twse2330")).thenReturn(Optional.empty());

        // When / Then：呼叫端明確指名了交易所，該筆存在就以它為準；
        // 已駁回即視為查無，不可靜默改回一家與 TWSE 2330 無關的公司
        ServerException ex = assertThrows(ServerException.class,
                () -> companyService.getByReference("TWSE:2330"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
    }

    @Test
    @DisplayName("對外查詢時已駁回公司的識別碼不得造成歧義，寫入路徑則仍看得到")
    void getVisibleByReference_collidingWithRejectedCompany_shouldResolveToExposableCompany() {
        // Given：審核逐列套用，公司被駁回時其識別碼未必一併駁回——
        // 因此「公司已駁回、識別碼仍有效」是可達狀態
        Company mistaken = Company.builder().id(8L).normalizedName("誤建公司").displayName("誤建公司")
                .reviewStatus(ReviewStatus.REJECTED).build();
        when(companyIdentifierRepository.findByIdentifierValue("2330")).thenReturn(List.of(
                CompanyIdentifier.builder().company(tsmc).identifierType(IdentifierType.TWSE)
                        .identifierValue("2330").reviewStatus(ReviewStatus.VERIFIED).build(),
                CompanyIdentifier.builder().company(mistaken).identifierType(IdentifierType.SSE)
                        .identifierValue("2330").reviewStatus(ReviewStatus.VERIFIED).build()));

        // When / Then：對外只有一家公司存在，不構成歧義
        assertEquals(tsmc, companyService.getVisibleByReference("2330"));
    }

    @Test
    @DisplayName("唯一命中的識別碼屬於已駁回公司時應回 404，不得退回以代號值當名稱查詢")
    void getVisibleByReference_onlyMatchBelongsToRejectedCompany_shouldNotFallBackToNameLookup() {
        // Given：代號 2330 只命中一筆識別碼，其所屬公司已被駁回；
        // 另有一家公司的正規化名稱恰好就是「2330」（名稱本身沒有禁止數字）
        Company mistaken = Company.builder().id(8L).normalizedName("誤建公司").displayName("誤建公司")
                .reviewStatus(ReviewStatus.REJECTED).build();
        Company namedLikeCode = Company.builder().id(9L).normalizedName("2330").displayName("2330")
                .reviewStatus(ReviewStatus.VERIFIED).build();
        when(companyIdentifierRepository.findByIdentifierValue("2330")).thenReturn(List.of(
                CompanyIdentifier.builder().company(mistaken).identifierType(IdentifierType.TWSE)
                        .identifierValue("2330").reviewStatus(ReviewStatus.VERIFIED).build()));
        lenient().when(companyRepository.findByNormalizedName("2330")).thenReturn(Optional.of(namedLikeCode));

        // When / Then：單一命中時不得在解析階段就濾掉已駁回公司——濾掉會讓解析回空而退回名稱查詢，
        // 於是一個查得到代號的請求靜默回了一家與該代號無關的公司
        ServerException ex = assertThrows(ServerException.class,
                () -> companyService.getVisibleByReference("2330"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
    }

    @Test
    @DisplayName("寫入路徑仍須看見已駁回公司，否則駁回的公司無法被改回草稿")
    void getByReference_collidingWithRejectedCompany_shouldStillReportConflict() {
        // 審核端點以 companyCode 定位目標，若寫入路徑也濾掉已駁回公司，就再也無法把它改回來
        Company mistaken = Company.builder().id(8L).normalizedName("誤建公司").displayName("誤建公司")
                .reviewStatus(ReviewStatus.REJECTED).build();
        when(companyIdentifierRepository.findByIdentifierValue("2330")).thenReturn(List.of(
                CompanyIdentifier.builder().company(tsmc).identifierType(IdentifierType.TWSE)
                        .identifierValue("2330").reviewStatus(ReviewStatus.VERIFIED).build(),
                CompanyIdentifier.builder().company(mistaken).identifierType(IdentifierType.SSE)
                        .identifierValue("2330").reviewStatus(ReviewStatus.VERIFIED).build()));

        ServerException ex = assertThrows(ServerException.class, () -> companyService.getByReference("2330"));

        assertAll(
                () -> assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus()),
                () -> assertTrue(ex.getMessage().contains("SSE:2330")));
    }

    @Test
    @DisplayName("衝突訊息的候選清單不得列入已駁回的識別碼")
    void getByReference_ambiguousWithRejectedThird_shouldListOnlyExposableCandidates() {
        // Given：兩家可外露的公司撞號（真歧義），外加第三筆已駁回的同值識別碼
        Company lenovo = Company.builder().id(5L).normalizedName("聯想").displayName("聯想").build();
        Company sseListed = Company.builder().id(6L).normalizedName("某滬市公司").displayName("某滬市公司").build();
        Company mistaken = Company.builder().id(8L).normalizedName("誤建公司").displayName("誤建公司").build();
        when(companyIdentifierRepository.findByIdentifierValue("0992")).thenReturn(List.of(
                CompanyIdentifier.builder().company(lenovo).identifierType(IdentifierType.HKEX)
                        .identifierValue("0992").reviewStatus(ReviewStatus.VERIFIED).build(),
                CompanyIdentifier.builder().company(sseListed).identifierType(IdentifierType.SSE)
                        .identifierValue("0992").reviewStatus(ReviewStatus.VERIFIED).build(),
                CompanyIdentifier.builder().company(mistaken).identifierType(IdentifierType.SZSE)
                        .identifierValue("0992").reviewStatus(ReviewStatus.REJECTED).build()));

        // When
        ServerException ex = assertThrows(ServerException.class, () -> companyService.getByReference("0992"));

        // Then：照訊息重送必須成功，已駁回的候選重送會查無，因此不得列出
        assertAll(
                () -> assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus()),
                () -> assertTrue(ex.getMessage().contains("HKEX:0992")),
                () -> assertTrue(ex.getMessage().contains("SSE:0992")),
                () -> assertFalse(ex.getMessage().contains("SZSE:0992")));
    }

    @Test
    @DisplayName("代號值本身含冒號時不得被誤判為限定形式")
    void getByReference_valueContainingColon_shouldBeTreatedAsBareValue() {
        // Given：OTHER 類型底下登記了含冒號的值，冒號前的 ISIN 不是合法識別碼類型
        Company infineon = Company.builder().id(7L).normalizedName("英飛凌").displayName("英飛凌").build();
        when(companyIdentifierRepository.findByIdentifierValue("ISIN:DE0006231004")).thenReturn(List.of(
                CompanyIdentifier.builder().company(infineon).identifierType(IdentifierType.OTHER)
                        .identifierValue("ISIN:DE0006231004").isPrimary(false).build()));

        assertEquals(infineon, companyService.getByReference("ISIN:DE0006231004"));
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
    @DisplayName("取得公司對外識別時有主要代號應回該代號的限定形式")
    void referenceOf_withPrimaryIdentifier_shouldReturnQualifiedReference() {
        CompanyIdentifier primary = CompanyIdentifier.builder()
                .id(1L).company(tsmc).identifierType(IdentifierType.TWSE).identifierValue("2330")
                .isPrimary(true).build();
        when(companyIdentifierRepository.findByCompanyId(1L)).thenReturn(List.of(primary));

        assertEquals("TWSE:2330", companyService.referenceOf(tsmc));
    }

    @Test
    @DisplayName("取得公司對外識別時無識別碼應退回正規化名稱")
    void referenceOf_withoutIdentifier_shouldFallBackToNormalizedName() {
        when(companyIdentifierRepository.findByCompanyId(1L)).thenReturn(List.of());

        assertEquals("台積電", companyService.referenceOf(tsmc));
    }

    @Test
    @DisplayName("批次取得對外識別時應一次查主要識別碼，有代號者回限定形式、無代號者回名稱")
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
                () -> assertEquals("TWSE:2330", references.get(1L)),
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
        when(companyIdentifierRepository.findByIdentifierTypeAndIdentifierValue(IdentifierType.TWSE, "2330"))
                .thenReturn(Optional.of(primary));

        // When：把回應裡的對外識別原封不動拿去查詢
        String reference = companyService.referenceOf(tsmc);

        // Then
        assertEquals(tsmc, companyService.getByReference(reference));
    }

    @Test
    @DisplayName("跨交易所撞號時，兩家公司的對外識別應各自不同且各自查回自己")
    void referenceOf_collidingCodesAcrossExchanges_shouldStayDistinctAndQueryable() {
        // Given：聯想（HKEX 0992）與某滬市公司（SSE 0992）持有相同代號值——撞號目前無實例，
        // 因此驗收只能靠刻意建構的資料，不能等真實資料出現
        Company lenovo = Company.builder().id(5L).normalizedName("聯想").displayName("聯想").build();
        Company sseListed = Company.builder().id(6L).normalizedName("某滬市公司").displayName("某滬市公司").build();
        CompanyIdentifier hkex = CompanyIdentifier.builder().id(11L).company(lenovo)
                .identifierType(IdentifierType.HKEX).identifierValue("0992").isPrimary(true).build();
        CompanyIdentifier sse = CompanyIdentifier.builder().id(12L).company(sseListed)
                .identifierType(IdentifierType.SSE).identifierValue("0992").isPrimary(true).build();
        when(companyIdentifierRepository.findByCompanyId(5L)).thenReturn(List.of(hkex));
        when(companyIdentifierRepository.findByCompanyId(6L)).thenReturn(List.of(sse));
        when(companyIdentifierRepository.findByIdentifierTypeAndIdentifierValue(IdentifierType.HKEX, "0992"))
                .thenReturn(Optional.of(hkex));
        when(companyIdentifierRepository.findByIdentifierTypeAndIdentifierValue(IdentifierType.SSE, "0992"))
                .thenReturn(Optional.of(sse));

        // When：各自取出對外識別再原封不動拿去查
        String lenovoReference = companyService.referenceOf(lenovo);
        String sseReference = companyService.referenceOf(sseListed);

        // Then：識別值互不相同，且各自查回自己而非對方
        assertAll(
                () -> assertNotEquals(lenovoReference, sseReference),
                () -> assertEquals(lenovo, companyService.getByReference(lenovoReference)),
                () -> assertEquals(sseListed, companyService.getByReference(sseReference)));
    }

    @Test
    @DisplayName("公司資料回應、單筆與批次取得的對外識別應為同一個限定形式值")
    void companyReference_shouldBeIdenticalAcrossEndpoints() {
        // Given：公司資料走 CompanyResponse、供應商與市佔率清單走 referencesOf——
        // 三者若給出不同形狀，同一家公司在不同回應裡就成了兩家
        CompanyIdentifier primary = CompanyIdentifier.builder()
                .id(1L).company(tsmc).identifierType(IdentifierType.TWSE).identifierValue("2330")
                .isPrimary(true).build();
        when(companyIdentifierRepository.findByCompanyId(1L)).thenReturn(List.of(primary));
        when(companyIdentifierRepository.findByCompanyIdInAndIsPrimaryTrue(Set.of(1L)))
                .thenReturn(List.of(primary));

        // When
        String onCompanyResponse = CompanyResponse.from(tsmc, List.of(primary)).getReference();
        String single = companyService.referenceOf(tsmc);
        String batch = companyService.referencesOf(List.of(tsmc)).get(1L);

        // Then
        assertAll(
                () -> assertEquals("TWSE:2330", onCompanyResponse),
                () -> assertEquals(onCompanyResponse, single),
                () -> assertEquals(onCompanyResponse, batch));
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


    @Test
    @DisplayName("列出公司未指定審核範圍時應只查已驗證公司")
    void findCompanies_defaultScope_shouldQueryVerifiedOnly() {
        // Given
        givenListing(List.of(tsmc), 1L);

        // When
        PageResponse<CompanyResponse> page = companyService.findCompanies(CompanyQuery.builder().build());

        // Then：送進查詢的審核範圍只能有 VERIFIED
        ArgumentCaptor<Collection<String>> statuses = ArgumentCaptor.forClass(Collection.class);
        verify(companyRepository).findCompanies(statuses.capture(), any(), any(), any(), any(), any(),
                any(), any(), anyInt(), anyLong());
        assertAll(
                () -> assertEquals(Set.of(ReviewStatus.VERIFIED.name()), Set.copyOf(statuses.getValue())),
                () -> assertEquals(1, page.getContent().size()),
                () -> assertEquals("台積電", page.getContent().get(0).getDisplayName()));
    }

    @Test
    @DisplayName("列出公司指定納入草稿時應一併查詢草稿並標示各筆審核狀態")
    void findCompanies_includeDrafts_shouldQueryDraftsAndExposeReviewStatus() {
        Company draft = Company.builder().id(3L).normalizedName("草稿公司").displayName("草稿公司")
                .reviewStatus(ReviewStatus.DRAFT).build();
        givenListing(List.of(draft), 1L);

        PageResponse<CompanyResponse> page =
                companyService.findCompanies(CompanyQuery.builder().includeDrafts(true).build());

        ArgumentCaptor<Collection<String>> statuses = ArgumentCaptor.forClass(Collection.class);
        verify(companyRepository).findCompanies(statuses.capture(), any(), any(), any(), any(), any(),
                any(), any(), anyInt(), anyLong());
        assertAll(
                () -> assertEquals(
                        Set.of(ReviewStatus.VERIFIED.name(), ReviewStatus.DRAFT.name()),
                        Set.copyOf(statuses.getValue())),
                () -> assertEquals(ReviewStatus.DRAFT, page.getContent().get(0).getReviewStatus()));
    }

    @Test
    @DisplayName("列出公司納入草稿時查詢範圍仍不得包含已駁回")
    void findCompanies_includeDrafts_shouldNeverQueryRejected() {
        givenListing(List.of(), 0L);

        companyService.findCompanies(CompanyQuery.builder().includeDrafts(true).build());

        // 已駁回一律不外露：範圍由 ReviewScopes 決定，這裡確認沒有任何路徑把它加回來
        ArgumentCaptor<Collection<String>> statuses = ArgumentCaptor.forClass(Collection.class);
        verify(companyRepository).findCompanies(statuses.capture(), any(), any(), any(), any(), any(),
                any(), any(), anyInt(), anyLong());
        assertFalse(statuses.getValue().contains(ReviewStatus.REJECTED.name()));
    }

    @Test
    @DisplayName("列出公司指定不存在的品類節點時應拋出 404 ServerException")
    void findCompanies_unknownItemId_shouldThrowNotFound() {
        when(itemRepository.existsById(99L)).thenReturn(false);

        ServerException ex = assertThrows(ServerException.class,
                () -> companyService.findCompanies(CompanyQuery.builder().itemId(99L).build()));

        assertAll(
                () -> assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus()),
                () -> verify(companyRepository, never()).findCompanies(any(), any(), any(), any(), any(),
                        any(), any(), any(), anyInt(), anyLong()));
    }

    @Test
    @DisplayName("列出公司無符合資料時應回空清單與總筆數 0，而非 404")
    void findCompanies_noMatch_shouldReturnEmptyPage() {
        givenListing(List.of(), 0L);

        PageResponse<CompanyResponse> page =
                companyService.findCompanies(CompanyQuery.builder().name("不存在").build());

        assertAll(
                () -> assertTrue(page.getContent().isEmpty()),
                () -> assertEquals(0L, page.getTotalElements()),
                () -> assertEquals(0, page.getTotalPages()));
    }

    @Test
    @DisplayName("列出公司時各筆的對外識別應與單筆查詢一致，且不外露已駁回的識別碼")
    void findCompanies_shouldBuildReferenceWithoutRejectedIdentifiers() {
        // Given：一筆有效的主要識別碼，加一筆已被駁回的識別碼
        CompanyIdentifier primary = CompanyIdentifier.builder().id(1L).company(tsmc)
                .identifierType(IdentifierType.TWSE).identifierValue("2330").isPrimary(true)
                .reviewStatus(ReviewStatus.VERIFIED).build();
        CompanyIdentifier rejected = CompanyIdentifier.builder().id(2L).company(tsmc)
                .identifierType(IdentifierType.SSE).identifierValue("9999")
                .reviewStatus(ReviewStatus.REJECTED).build();
        givenListing(List.of(tsmc), 1L);
        when(companyIdentifierRepository.findByCompanyIdIn(Set.of(1L)))
                .thenReturn(List.of(primary, rejected));

        // When
        CompanyResponse response = companyService.findCompanies(CompanyQuery.builder().build())
                .getContent().get(0);

        // Then
        assertAll(
                () -> assertEquals("TWSE:2330", response.getReference()),
                () -> assertEquals(1, response.getIdentifiers().size()));
    }

    @Test
    @DisplayName("列出公司時國別給空字串應視為不過濾，而非過濾空國別")
    void findCompanies_blankCountry_shouldNotFilter() {
        // Given：前端把所有查詢參數都帶上、值留空是常見行為（?country=），
        // 若照字面過濾就會回 0 筆，使用者看到的是「一家都沒有」而不是「沒有這個條件」
        givenListing(List.of(tsmc), 1L);

        // When
        companyService.findCompanies(CompanyQuery.builder().country("   ").build());

        // Then：送進查詢的國別必須是 null
        ArgumentCaptor<String> country = ArgumentCaptor.forClass(String.class);
        verify(companyRepository).findCompanies(any(), any(), country.capture(), any(), any(), any(),
                any(), any(), anyInt(), anyLong());
        assertNull(country.getValue());
    }

    @Test
    @DisplayName("列出公司時國別前後空白應去除後比對")
    void findCompanies_countryWithPadding_shouldBeTrimmed() {
        givenListing(List.of(tsmc), 1L);

        companyService.findCompanies(CompanyQuery.builder().country(" TW ").build());

        ArgumentCaptor<String> country = ArgumentCaptor.forClass(String.class);
        verify(companyRepository).findCompanies(any(), any(), country.capture(), any(), any(), any(),
                any(), any(), anyInt(), anyLong());
        assertEquals("TW", country.getValue());
    }

    @Test
    @DisplayName("列出公司時每頁筆數與位移應正確換算，且大頁碼不得溢位")
    void findCompanies_largePage_shouldComputeOffsetWithoutOverflow() {
        givenListing(List.of(), 0L);

        companyService.findCompanies(CompanyQuery.builder().page(3_000_000).size(100).build());

        // int 相乘會溢位成負數，送進 SQL 就是負的 OFFSET，資料庫直接報錯
        ArgumentCaptor<Long> offset = ArgumentCaptor.forClass(Long.class);
        verify(companyRepository).findCompanies(any(), any(), any(), any(), any(), any(), any(), any(),
                eq(100), offset.capture());
        assertEquals(300_000_000L, offset.getValue());
    }

    /** 列表查詢的共用 stub：內容與總筆數由呼叫端指定，其餘條件不設限 */
    private void givenListing(List<Company> companies, long totalElements) {
        when(companyRepository.findCompanies(any(), any(), any(), any(), any(), any(), any(), any(),
                anyInt(), anyLong())).thenReturn(companies);
        when(companyRepository.countCompanies(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(totalElements);
        lenient().when(companyIdentifierRepository.findByCompanyIdIn(any())).thenReturn(List.of());
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
