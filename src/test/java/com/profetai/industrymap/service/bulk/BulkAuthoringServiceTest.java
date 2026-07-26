package com.profetai.industrymap.service.bulk;

import com.profetai.industrymap.enums.CompanyRole;
import com.profetai.industrymap.enums.IdentifierType;
import com.profetai.industrymap.enums.Necessity;
import com.profetai.industrymap.enums.ReviewTargetType;
import com.profetai.industrymap.enums.SourceType;
import com.profetai.industrymap.exceptions.ServerException;
import com.profetai.industrymap.model.Company;
import com.profetai.industrymap.model.CompanyIdentifier;
import com.profetai.industrymap.model.CompanyItemRole;
import com.profetai.industrymap.model.Item;
import com.profetai.industrymap.model.ItemComposition;
import com.profetai.industrymap.payloads.ProvenanceRequest;
import com.profetai.industrymap.payloads.bulk.BatchCompositionItem;
import com.profetai.industrymap.payloads.bulk.BatchCreateResultResponse;
import com.profetai.industrymap.payloads.bulk.BatchIdentifierItem;
import com.profetai.industrymap.payloads.item.CreateItemRequest;
import com.profetai.industrymap.payloads.supply.CreateCompanyItemRoleRequest;
import com.profetai.industrymap.service.company.CompanyService;
import com.profetai.industrymap.service.item.ItemCompositionService;
import com.profetai.industrymap.service.item.ItemService;
import com.profetai.industrymap.service.supply.CompanyItemRoleService;
import com.profetai.industrymap.service.supply.MarketShareService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BulkAuthoringServiceTest {

    private static final String TSMC_CODE = "2330";

    @Mock
    private ItemService itemService;

    @Mock
    private ItemCompositionService itemCompositionService;

    @Mock
    private CompanyService companyService;

    @Mock
    private CompanyItemRoleService companyItemRoleService;

    @Mock
    private MarketShareService marketShareService;

    @InjectMocks
    private BulkAuthoringService bulkAuthoringService;

    private final Company tsmc = Company.builder().id(1L).normalizedName("台積電").displayName("台積電").build();

    @Test
    @DisplayName("批次建立品類節點應逐筆回報成功並帶可直接審核的自然鍵")
    void createItems_allValid_shouldReportEachSuccessWithNaturalKey() {
        // Given
        when(itemService.create(any(CreateItemRequest.class)))
                .thenReturn(item(1L, "腳踏車"))
                .thenReturn(item(2L, "變速器"));

        // When
        List<BatchCreateResultResponse> results =
                bulkAuthoringService.createItems(List.of(itemRequest("腳踏車"), itemRequest("變速器")));

        // Then
        assertAll(
                () -> assertEquals(2, results.size()),
                () -> assertTrue(results.stream().allMatch(BatchCreateResultResponse::isSuccess)),
                () -> assertEquals(0, results.get(0).getIndex()),
                () -> assertEquals(1, results.get(1).getIndex()),
                () -> assertEquals(ReviewTargetType.ITEM, results.get(0).getTargetType()),
                () -> assertEquals("腳踏車", results.get(0).getNaturalKey().getName()),
                () -> assertEquals(1L, results.get(0).getTargetId()));
    }

    @Test
    @DisplayName("批次中一筆名稱重複時其餘應照常建立，該筆回報 409")
    void createItems_oneDuplicated_shouldCreateOthersAndReportConflict() {
        // Given：第二筆與既有節點重複
        when(itemService.create(any(CreateItemRequest.class)))
                .thenReturn(item(1L, "腳踏車"))
                .thenThrow(new ServerException("已存在相同名稱的品類節點：變速器", HttpStatus.CONFLICT))
                .thenReturn(item(3L, "鏈條"));

        // When
        List<BatchCreateResultResponse> results = bulkAuthoringService.createItems(
                List.of(itemRequest("腳踏車"), itemRequest("變速器"), itemRequest("鏈條")));

        // Then：失敗的一筆不影響其餘兩筆
        assertAll(
                () -> assertEquals(3, results.size()),
                () -> assertTrue(results.get(0).isSuccess()),
                () -> assertFalse(results.get(1).isSuccess()),
                () -> assertEquals(409, results.get(1).getStatusCode()),
                () -> assertNotNull(results.get(1).getMessage()),
                () -> assertTrue(results.get(2).isSuccess()));
    }

    @Test
    @DisplayName("同批次內兩筆名稱相同時第一筆應成功、第二筆回報 409")
    void createItems_duplicatedWithinSameBatch_shouldRejectSecondOnly() {
        // Given：逐筆各自成為一個交易，第一筆先 commit，第二筆才會撞到既有名稱
        when(itemService.create(any(CreateItemRequest.class)))
                .thenReturn(item(1L, "變速器"))
                .thenThrow(new ServerException("已存在相同名稱的品類節點：變速器", HttpStatus.CONFLICT));

        // When
        List<BatchCreateResultResponse> results =
                bulkAuthoringService.createItems(List.of(itemRequest("變速器"), itemRequest("變速器")));

        // Then
        assertAll(
                () -> assertTrue(results.get(0).isSuccess()),
                () -> assertFalse(results.get(1).isSuccess()),
                () -> assertEquals(409, results.get(1).getStatusCode()));
    }

    @Test
    @DisplayName("失敗項目的回應不得帶任何定位資訊")
    void createItems_failedItem_shouldNotCarryLocationInformation() {
        // 帶了定位資訊會讓呼叫端誤以為建立成功，接著把它送進審核
        when(itemService.create(any(CreateItemRequest.class)))
                .thenThrow(new ServerException("已存在相同名稱的品類節點：變速器", HttpStatus.CONFLICT));

        List<BatchCreateResultResponse> results = bulkAuthoringService.createItems(List.of(itemRequest("變速器")));

        assertAll(
                () -> assertFalse(results.get(0).isSuccess()),
                () -> assertNull(results.get(0).getTargetType()),
                () -> assertNull(results.get(0).getTargetId()),
                () -> assertNull(results.get(0).getNaturalKey()));
    }

    @Test
    @DisplayName("單筆拋出非業務例外時不得中斷整批，該筆以 500 回報")
    void createItems_unexpectedException_shouldNotAbortRemainingItems() {
        when(itemService.create(any(CreateItemRequest.class)))
                .thenThrow(new CannotAcquireLockException("could not obtain lock on row"))
                .thenReturn(item(2L, "鏈條"));

        List<BatchCreateResultResponse> results =
                bulkAuthoringService.createItems(List.of(itemRequest("變速器"), itemRequest("鏈條")));

        assertAll(
                () -> assertEquals(2, results.size()),
                () -> assertFalse(results.get(0).isSuccess()),
                () -> assertEquals(500, results.get(0).getStatusCode()),
                () -> assertTrue(results.get(1).isSuccess()));
    }

    @Test
    @DisplayName("批次建立組成關係應回傳上下層節點組成的自然鍵")
    void createCompositions_valid_shouldReturnParentAndChildAsNaturalKey() {
        when(itemCompositionService.create(eq(1L), any())).thenReturn(ItemComposition.builder().id(21L).build());

        List<BatchCreateResultResponse> results = bulkAuthoringService.createCompositions(List.of(
                BatchCompositionItem.builder()
                        .parentItemId(1L).childItemId(2L).necessity(Necessity.STANDARD)
                        .provenance(manualProvenance()).build()));

        assertAll(
                () -> assertTrue(results.get(0).isSuccess()),
                () -> assertEquals(ReviewTargetType.ITEM_COMPOSITION, results.get(0).getTargetType()),
                () -> assertEquals(1L, results.get(0).getNaturalKey().getParentItemId()),
                () -> assertEquals(2L, results.get(0).getNaturalKey().getChildItemId()));
    }

    @Test
    @DisplayName("批次登記公司識別碼應回傳類型與代號值組成的自然鍵")
    void createIdentifiers_valid_shouldReturnTypeAndValueAsNaturalKey() {
        // 這一類的查詢回應完全不含 id，自然鍵是它唯一的定位方式
        CompanyIdentifier identifier = CompanyIdentifier.builder()
                .id(11L).company(tsmc).identifierType(IdentifierType.TWSE).identifierValue(TSMC_CODE).build();
        when(companyService.getByReference("台積電")).thenReturn(tsmc);
        when(companyService.addIdentifier(eq(1L), any())).thenReturn(identifier);

        List<BatchCreateResultResponse> results = bulkAuthoringService.createIdentifiers(List.of(
                BatchIdentifierItem.builder()
                        .companyCode("台積電").identifierType(IdentifierType.TWSE).identifierValue(TSMC_CODE)
                        .primary(true).provenance(manualProvenance()).build()));

        assertAll(
                () -> assertTrue(results.get(0).isSuccess()),
                () -> assertEquals(ReviewTargetType.COMPANY_IDENTIFIER, results.get(0).getTargetType()),
                () -> assertEquals(IdentifierType.TWSE, results.get(0).getNaturalKey().getIdentifierType()),
                () -> assertEquals(TSMC_CODE, results.get(0).getNaturalKey().getIdentifierValue()));
    }

    @Test
    @DisplayName("批次建立供應角色的自然鍵應帶統一規則下的公司對外識別")
    void createSupplyRoles_valid_shouldReturnCanonicalCompanyReference() {
        // Given：呼叫端用名稱建立，回應的自然鍵應給出主要代號（design D4）
        CompanyItemRole role = CompanyItemRole.builder()
                .id(51L).company(tsmc).companyRole(CompanyRole.MANUFACTURE).build();
        when(companyItemRoleService.create(any(CreateCompanyItemRoleRequest.class))).thenReturn(role);
        when(companyService.referenceOf(tsmc)).thenReturn(TSMC_CODE);

        // When
        List<BatchCreateResultResponse> results = bulkAuthoringService.createSupplyRoles(List.of(
                CreateCompanyItemRoleRequest.builder()
                        .companyCode("台積電").itemId(2L).companyRole(CompanyRole.MANUFACTURE)
                        .provenance(manualProvenance()).build()));

        // Then
        assertAll(
                () -> assertTrue(results.get(0).isSuccess()),
                () -> assertEquals(TSMC_CODE, results.get(0).getNaturalKey().getCompanyCode()),
                () -> assertEquals(2L, results.get(0).getNaturalKey().getItemId()),
                () -> assertEquals(CompanyRole.MANUFACTURE, results.get(0).getNaturalKey().getCompanyRole()),
                () -> verify(companyItemRoleService).create(any(CreateCompanyItemRoleRequest.class)));
    }

    @Test
    @DisplayName("建立成功後解析公司對外識別失敗時，該筆仍應回報成功並以呼叫端送出的代號當自然鍵")
    void createSupplyRoles_referenceLookupFails_shouldStillReportSuccessWithRequestedCode() {
        // Given：供應角色那一筆交易已 commit，之後才做的對外識別查詢掛掉。
        // 若讓它把整筆炸成失敗，資料已寫進去卻不帶定位資訊，呼叫端既不會拿去審核、重送又會撞 409
        CompanyItemRole role = CompanyItemRole.builder()
                .id(51L).company(tsmc).companyRole(CompanyRole.MANUFACTURE).build();
        when(companyItemRoleService.create(any(CreateCompanyItemRoleRequest.class))).thenReturn(role);
        when(companyService.referenceOf(tsmc)).thenThrow(new CannotAcquireLockException("connection lost"));

        // When
        List<BatchCreateResultResponse> results = bulkAuthoringService.createSupplyRoles(List.of(
                CreateCompanyItemRoleRequest.builder()
                        .companyCode("台積電").itemId(2L).companyRole(CompanyRole.MANUFACTURE)
                        .provenance(manualProvenance()).build()));

        // Then：退回呼叫端原本送出的代號，它剛剛才成功解析過，一定定位得到同一家公司
        assertAll(
                () -> assertTrue(results.get(0).isSuccess()),
                () -> assertEquals(51L, results.get(0).getTargetId()),
                () -> assertEquals("台積電", results.get(0).getNaturalKey().getCompanyCode()));
    }

    private Item item(Long id, String displayName) {
        return Item.builder().id(id).displayName(displayName).normalizedName(displayName)
                .sourceType(SourceType.MANUAL).build();
    }

    private CreateItemRequest itemRequest(String displayName) {
        return CreateItemRequest.builder().displayName(displayName).provenance(manualProvenance()).build();
    }

    private ProvenanceRequest manualProvenance() {
        return ProvenanceRequest.builder().sourceType(SourceType.MANUAL).build();
    }
}
