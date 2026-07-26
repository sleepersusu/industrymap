package com.profetai.industrymap.service.supply;

import com.profetai.industrymap.enums.PeriodType;
import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.enums.ShareMetric;
import com.profetai.industrymap.enums.SourceType;
import com.profetai.industrymap.exceptions.ServerException;
import com.profetai.industrymap.model.Company;
import com.profetai.industrymap.model.Item;
import com.profetai.industrymap.model.MarketShare;
import com.profetai.industrymap.payloads.ProvenanceRequest;
import com.profetai.industrymap.payloads.supply.CreateMarketShareRequest;
import com.profetai.industrymap.payloads.supply.MarketShareResponse;
import com.profetai.industrymap.repository.CompanyItemRoleRepository;
import com.profetai.industrymap.repository.ItemRepository;
import com.profetai.industrymap.repository.MarketShareRepository;
import com.profetai.industrymap.service.company.CompanyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketShareServiceTest {

    @Mock
    private CompanyService companyService;

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private MarketShareRepository marketShareRepository;

    @Mock
    private CompanyItemRoleRepository companyItemRoleRepository;

    @InjectMocks
    private MarketShareService marketShareService;

    private static final String SHIMANO_CODE = "7309";

    private final Company shimano = Company.builder().id(1L).normalizedName("shimano").displayName("Shimano").build();
    private final Item derailleur = Item.builder().id(2L).normalizedName("變速器").displayName("變速器").build();

    @Test
    @DisplayName("寫入市佔率未提供地區應拋出 400 ServerException")
    void create_missingRegion_shouldThrowBadRequest() {
        CreateMarketShareRequest request = validRequest("報告 A", new BigDecimal("70.0"));
        request.setRegion("  ");

        ServerException ex = assertThrows(ServerException.class, () -> marketShareService.create(request));

        assertAll(
                () -> assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus()),
                () -> verify(marketShareRepository, never()).save(any(MarketShare.class)));
    }

    @Test
    @DisplayName("寫入市佔率未提供期間應拋出 400 ServerException")
    void create_missingPeriod_shouldThrowBadRequest() {
        CreateMarketShareRequest request = validRequest("報告 A", new BigDecimal("70.0"));
        request.setPeriodType(null);

        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ServerException.class, () -> marketShareService.create(request)).getHttpStatus());
    }

    @Test
    @DisplayName("寫入市佔率未提供口徑應拋出 400 ServerException")
    void create_missingMetric_shouldThrowBadRequest() {
        CreateMarketShareRequest request = validRequest("報告 A", new BigDecimal("70.0"));
        request.setMetric(null);

        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ServerException.class, () -> marketShareService.create(request)).getHttpStatus());
    }

    @Test
    @DisplayName("市佔百分比超出 0–100 應拋出 400 ServerException")
    void create_sharePercentOutOfRange_shouldThrowBadRequest() {
        CreateMarketShareRequest request = validRequest("報告 A", new BigDecimal("120.0"));

        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ServerException.class, () -> marketShareService.create(request)).getHttpStatus());
    }

    @Test
    @DisplayName("不同來源對同一組維度給出不同數值時應允許並存")
    void create_sameDimensionsFromDifferentSource_shouldPersist() {
        // Given：來源 A 已寫過 70%，來源 B 現在要寫 50%
        givenCompanyAndItem();
        when(marketShareRepository.existsSameDimensionsFromSameSource(
                1L, 2L, "YEAR", "2024", "全球", "REVENUE", "報告 B")).thenReturn(false);
        when(marketShareRepository.save(any(MarketShare.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        MarketShare created = marketShareService.create(validRequest("報告 B", new BigDecimal("50.0")));

        // Then
        assertAll(
                () -> assertEquals(new BigDecimal("50.0"), created.getSharePercent()),
                () -> assertEquals("報告 B", created.getSourceDetail()));
    }

    @Test
    @DisplayName("同一來源對同一組維度重複寫入應拋出 409 ServerException")
    void create_sameDimensionsFromSameSource_shouldThrowConflict() {
        givenCompanyAndItem();
        when(marketShareRepository.existsSameDimensionsFromSameSource(
                1L, 2L, "YEAR", "2024", "全球", "REVENUE", "報告 A")).thenReturn(true);

        ServerException ex = assertThrows(ServerException.class,
                () -> marketShareService.create(validRequest("報告 A", new BigDecimal("72.0"))));

        assertAll(
                () -> assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus()),
                () -> verify(marketShareRepository, never()).save(any(MarketShare.class)));
    }

    @Test
    @DisplayName("尚未建立任何供應角色關係時仍應可寫入市佔率")
    void create_withoutExistingCompanyItemRole_shouldPersistWithoutCheckingRoles() {
        givenCompanyAndItem();
        when(marketShareRepository.existsSameDimensionsFromSameSource(
                1L, 2L, "YEAR", "2024", "全球", "REVENUE", "報告 A")).thenReturn(false);
        when(marketShareRepository.save(any(MarketShare.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MarketShare created = marketShareService.create(validRequest("報告 A", new BigDecimal("70.0")));

        // 市佔率資料常先於角色關係到達，因此不該去查角色表，更不該因為查無角色而擋下
        assertAll(
                () -> assertEquals(shimano, created.getCompany()),
                () -> assertEquals(derailleur, created.getItem()),
                () -> verifyNoInteractions(companyItemRoleRepository));
    }

    @Test
    @DisplayName("以公司代號寫入市佔率時應解析出對應公司")
    void create_byCompanyCode_shouldResolveCompanyWithoutInternalId() {
        givenCompanyAndItem();
        when(marketShareRepository.existsSameDimensionsFromSameSource(
                1L, 2L, "YEAR", "2024", "全球", "REVENUE", "報告 A")).thenReturn(false);
        when(marketShareRepository.save(any(MarketShare.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MarketShare created = marketShareService.create(validRequest("報告 A", new BigDecimal("70.0")));

        assertAll(
                () -> assertEquals(shimano, created.getCompany()),
                () -> verify(companyService).getByReference(SHIMANO_CODE));
    }

    @Test
    @DisplayName("公司代號不存在時應拋出 404 ServerException 且不寫入")
    void create_unknownCompanyCode_shouldThrowNotFound() {
        when(companyService.getByReference(SHIMANO_CODE))
                .thenThrow(new ServerException("查無此公司：" + SHIMANO_CODE, HttpStatus.NOT_FOUND));

        ServerException ex = assertThrows(ServerException.class,
                () -> marketShareService.create(validRequest("報告 A", new BigDecimal("70.0"))));

        assertAll(
                () -> assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus()),
                () -> verify(marketShareRepository, never()).save(any(MarketShare.class)));
    }

    @Test
    @DisplayName("排名查詢應回傳依百分比降冪排序的結果")
    void findRanking_existingData_shouldReturnSortedByShareDesc() {
        MarketShare top = MarketShare.builder().company(shimano).item(derailleur)
                .sharePercent(new BigDecimal("70.0")).build();
        MarketShare second = MarketShare.builder().company(shimano).item(derailleur)
                .sharePercent(new BigDecimal("20.0")).build();
        when(marketShareRepository.findRanking(2L, "YEAR", "2024", "全球", "REVENUE",
                Set.of(ReviewStatus.VERIFIED.name()))).thenReturn(List.of(top, second));

        List<MarketShareResponse> ranking =
                marketShareService.findRanking(2L, PeriodType.YEAR, "2024", "全球", ShareMetric.REVENUE, false);

        assertAll(
                () -> assertEquals(2, ranking.size()),
                () -> assertEquals(new BigDecimal("70.0"), ranking.get(0).getSharePercent()));
    }

    @Test
    @DisplayName("排名查詢無資料時應回傳空清單而非 404")
    void findRanking_noData_shouldReturnEmptyListInsteadOfNotFound() {
        when(marketShareRepository.findRanking(2L, "YEAR", "2024", "全球", "REVENUE",
                Set.of(ReviewStatus.VERIFIED.name()))).thenReturn(List.of());

        assertTrue(marketShareService.findRanking(2L, PeriodType.YEAR, "2024", "全球", ShareMetric.REVENUE, false).isEmpty());
    }

    @Test
    @DisplayName("市佔率已驗證但其公司已被駁回時，該筆不得出現在排名")
    void findRanking_rejectedCompany_shouldBeExcluded() {
        // Given：市佔率本身通過審核，但公司主檔事後被駁回
        Company rejectedCompany = Company.builder().id(9L).normalizedName("空殼公司").displayName("空殼公司")
                .reviewStatus(ReviewStatus.REJECTED).build();
        MarketShare valid = MarketShare.builder().company(shimano).item(derailleur)
                .sharePercent(new BigDecimal("70.0")).reviewStatus(ReviewStatus.VERIFIED).build();
        MarketShare viaRejectedCompany = MarketShare.builder().company(rejectedCompany).item(derailleur)
                .sharePercent(new BigDecimal("90.0")).reviewStatus(ReviewStatus.VERIFIED).build();
        when(marketShareRepository.findRanking(2L, "YEAR", "2024", "全球", "REVENUE",
                Set.of(ReviewStatus.VERIFIED.name()))).thenReturn(List.of(viaRejectedCompany, valid));

        // When
        List<MarketShareResponse> ranking =
                marketShareService.findRanking(2L, PeriodType.YEAR, "2024", "全球", ShareMetric.REVENUE, false);

        // Then
        assertAll(
                () -> assertEquals(1, ranking.size()),
                () -> assertEquals(new BigDecimal("70.0"), ranking.get(0).getSharePercent()));
    }

    private void givenCompanyAndItem() {
        when(companyService.getByReference(SHIMANO_CODE)).thenReturn(shimano);
        when(itemRepository.findById(2L)).thenReturn(Optional.of(derailleur));
    }

    private CreateMarketShareRequest validRequest(String sourceDetail, BigDecimal sharePercent) {
        return CreateMarketShareRequest.builder()
                .companyCode(SHIMANO_CODE)
                .itemId(2L)
                .periodType(PeriodType.YEAR)
                .periodValue("2024")
                .region("全球")
                .metric(ShareMetric.REVENUE)
                .sharePercent(sharePercent)
                .provenance(ProvenanceRequest.builder()
                        .sourceType(SourceType.EXTERNAL)
                        .sourceDetail(sourceDetail)
                        .build())
                .build();
    }
}
