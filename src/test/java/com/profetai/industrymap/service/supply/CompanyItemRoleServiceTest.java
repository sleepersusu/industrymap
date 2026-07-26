package com.profetai.industrymap.service.supply;

import com.profetai.industrymap.enums.CompanyRole;
import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.enums.SourceType;
import com.profetai.industrymap.exceptions.ServerException;
import com.profetai.industrymap.model.Company;
import com.profetai.industrymap.model.CompanyItemRole;
import com.profetai.industrymap.model.Item;
import com.profetai.industrymap.payloads.ProvenanceRequest;
import com.profetai.industrymap.payloads.supply.CreateCompanyItemRoleRequest;
import com.profetai.industrymap.payloads.supply.SupplierResponse;
import com.profetai.industrymap.repository.CompanyItemRoleRepository;
import com.profetai.industrymap.repository.ItemRepository;
import com.profetai.industrymap.service.company.CompanyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyItemRoleServiceTest {

    @Mock
    private CompanyService companyService;

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private CompanyItemRoleRepository companyItemRoleRepository;

    @InjectMocks
    private CompanyItemRoleService companyItemRoleService;

    private static final String TSMC_CODE = "2330";

    private final Company tsmc = Company.builder().id(1L).normalizedName("台積電").displayName("台積電").build();
    private final Item chip = Item.builder().id(2L).normalizedName("晶片").displayName("晶片").build();

    @Test
    @DisplayName("同公司同零件登記第二個不同角色應成功")
    void create_sameCompanyAndItemWithDifferentRole_shouldPersist() {
        // Given：台積電對該晶片已有製造角色，現在補登封測
        givenCompanyAndItem();
        when(companyItemRoleRepository.existsByCompanyIdAndItemIdAndCompanyRole(1L, 2L, CompanyRole.PACKAGING_TESTING))
                .thenReturn(false);
        when(companyItemRoleRepository.save(any(CompanyItemRole.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        CompanyItemRole created = companyItemRoleService.create(request(CompanyRole.PACKAGING_TESTING));

        // Then
        assertAll(
                () -> assertEquals(tsmc, created.getCompany()),
                () -> assertEquals(chip, created.getItem()),
                () -> assertEquals(CompanyRole.PACKAGING_TESTING, created.getCompanyRole()));
    }

    @Test
    @DisplayName("以公司代號建立供應角色時應解析出對應公司")
    void create_byCompanyCode_shouldResolveCompanyWithoutInternalId() {
        // Given：呼叫端只知道公司代號，不知道內部識別碼
        givenCompanyAndItem();
        when(companyItemRoleRepository.existsByCompanyIdAndItemIdAndCompanyRole(1L, 2L, CompanyRole.MANUFACTURE))
                .thenReturn(false);
        when(companyItemRoleRepository.save(any(CompanyItemRole.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        CompanyItemRole created = companyItemRoleService.create(request(CompanyRole.MANUFACTURE));

        // Then
        assertAll(
                () -> assertEquals(tsmc, created.getCompany()),
                () -> verify(companyService).getByReference(TSMC_CODE));
    }

    @Test
    @DisplayName("公司代號不存在時應拋出 404 ServerException 且不寫入")
    void create_unknownCompanyCode_shouldThrowNotFound() {
        when(companyService.getByReference(TSMC_CODE))
                .thenThrow(new ServerException("查無此公司：" + TSMC_CODE, HttpStatus.NOT_FOUND));

        ServerException ex = assertThrows(ServerException.class,
                () -> companyItemRoleService.create(request(CompanyRole.MANUFACTURE)));

        assertAll(
                () -> assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus()),
                () -> verify(companyItemRoleRepository, never()).save(any(CompanyItemRole.class)));
    }

    @Test
    @DisplayName("同公司同零件同角色重複登記應拋出 409 ServerException")
    void create_duplicatedRole_shouldThrowConflict() {
        givenCompanyAndItem();
        when(companyItemRoleRepository.existsByCompanyIdAndItemIdAndCompanyRole(1L, 2L, CompanyRole.MANUFACTURE))
                .thenReturn(true);

        ServerException ex = assertThrows(ServerException.class,
                () -> companyItemRoleService.create(request(CompanyRole.MANUFACTURE)));

        assertAll(
                () -> assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus()),
                () -> verify(companyItemRoleRepository, never()).save(any(CompanyItemRole.class)));
    }

    @Test
    @DisplayName("查詢零件供應公司並指定角色時應只回傳該角色的關係")
    void findSuppliers_withRoleFilter_shouldQueryFilteredByRole() {
        CompanyItemRole assembly = CompanyItemRole.builder()
                .company(tsmc).item(chip).companyRole(CompanyRole.ASSEMBLY).build();
        when(companyItemRoleRepository.findByItemIdAndCompanyRoleAndReviewStatusIn(
                2L, CompanyRole.ASSEMBLY, Set.of(ReviewStatus.VERIFIED))).thenReturn(List.of(assembly));

        List<SupplierResponse> suppliers = companyItemRoleService.findSuppliers(2L, CompanyRole.ASSEMBLY, false);

        assertAll(
                () -> assertEquals(1, suppliers.size()),
                () -> assertEquals(CompanyRole.ASSEMBLY, suppliers.get(0).getCompanyRole()),
                () -> assertEquals("台積電", suppliers.get(0).getCompanyName()),
                () -> verify(companyItemRoleRepository, never())
                        .findByItemIdAndReviewStatusIn(any(), any()));
    }

    @Test
    @DisplayName("查詢零件供應公司未指定角色時應回傳所有角色的關係")
    void findSuppliers_withoutRoleFilter_shouldReturnAllRoles() {
        CompanyItemRole design = CompanyItemRole.builder()
                .company(tsmc).item(chip).companyRole(CompanyRole.DESIGN).build();
        CompanyItemRole manufacture = CompanyItemRole.builder()
                .company(tsmc).item(chip).companyRole(CompanyRole.MANUFACTURE).build();
        when(companyItemRoleRepository.findByItemIdAndReviewStatusIn(2L, Set.of(ReviewStatus.VERIFIED)))
                .thenReturn(List.of(design, manufacture));

        List<SupplierResponse> suppliers = companyItemRoleService.findSuppliers(2L, null, false);

        assertEquals(2, suppliers.size());
    }

    @Test
    @DisplayName("明確指定納入草稿時查詢範圍應含草稿，且任何情況都不含已駁回")
    void findSuppliers_includingDrafts_shouldQueryVerifiedAndDraftButNeverRejected() {
        Set<ReviewStatus> expectedScope = Set.of(ReviewStatus.VERIFIED, ReviewStatus.DRAFT);
        when(companyItemRoleRepository.findByItemIdAndReviewStatusIn(2L, expectedScope)).thenReturn(List.of());

        companyItemRoleService.findSuppliers(2L, null, true);

        assertAll(
                () -> verify(companyItemRoleRepository).findByItemIdAndReviewStatusIn(2L, expectedScope),
                () -> verify(companyItemRoleRepository, never())
                        .findByItemIdAndReviewStatusIn(2L, Set.of(ReviewStatus.REJECTED)));
    }

    @Test
    @DisplayName("供應關係已驗證但其公司已被駁回時，該筆不得出現在供應商清單")
    void findSuppliers_rejectedCompany_shouldBeExcluded() {
        // Given：關係本身通過審核，但公司主檔事後被駁回
        Company rejectedCompany = Company.builder().id(9L).normalizedName("空殼公司").displayName("空殼公司")
                .reviewStatus(ReviewStatus.REJECTED).build();
        CompanyItemRole valid = CompanyItemRole.builder()
                .company(tsmc).item(chip).companyRole(CompanyRole.MANUFACTURE)
                .reviewStatus(ReviewStatus.VERIFIED).build();
        CompanyItemRole viaRejectedCompany = CompanyItemRole.builder()
                .company(rejectedCompany).item(chip).companyRole(CompanyRole.ASSEMBLY)
                .reviewStatus(ReviewStatus.VERIFIED).build();
        when(companyItemRoleRepository.findByItemIdAndReviewStatusIn(2L, Set.of(ReviewStatus.VERIFIED)))
                .thenReturn(List.of(valid, viaRejectedCompany));

        // When
        List<SupplierResponse> suppliers = companyItemRoleService.findSuppliers(2L, null, false);

        // Then
        assertAll(
                () -> assertEquals(1, suppliers.size()),
                () -> assertEquals("台積電", suppliers.get(0).getCompanyName()));
    }

    private void givenCompanyAndItem() {
        when(companyService.getByReference(TSMC_CODE)).thenReturn(tsmc);
        when(itemRepository.findById(2L)).thenReturn(Optional.of(chip));
    }

    private CreateCompanyItemRoleRequest request(CompanyRole companyRole) {
        return CreateCompanyItemRoleRequest.builder()
                .companyCode(TSMC_CODE)
                .itemId(2L)
                .companyRole(companyRole)
                .provenance(ProvenanceRequest.builder().sourceType(SourceType.MANUAL).build())
                .build();
    }
}
