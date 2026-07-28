package com.profetai.industrymap.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.profetai.industrymap.enums.CompanyRole;
import com.profetai.industrymap.enums.IdentifierType;
import com.profetai.industrymap.enums.SourceType;
import com.profetai.industrymap.exceptions.ServerException;
import com.profetai.industrymap.model.Company;
import com.profetai.industrymap.model.CompanyAlias;
import com.profetai.industrymap.model.CompanyIdentifier;
import com.profetai.industrymap.payloads.PageResponse;
import com.profetai.industrymap.payloads.ProvenanceRequest;
import com.profetai.industrymap.payloads.company.CompanyQuery;
import com.profetai.industrymap.payloads.company.CompanyResponse;
import com.profetai.industrymap.payloads.company.CreateCompanyAliasRequest;
import com.profetai.industrymap.payloads.company.CreateCompanyRequest;
import com.profetai.industrymap.payloads.company.CreateIdentifierRequest;
import com.profetai.industrymap.service.company.CompanyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@WebMvcTest(CompanyController.class)
class CompanyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CompanyService companyService;

    private final Company tsmc = Company.builder().id(1L).normalizedName("台積電").displayName("台積電")
            .country("TW").isPublic(true).sourceType(SourceType.MANUAL).build();

    @Test
    @DisplayName("列出公司應回傳本頁內容與分頁中繼資訊")
    void listCompanies_defaultQuery_shouldReturnPage() throws Exception {
        CompanyResponse response = CompanyResponse.builder()
                .reference("TWSE:2330").displayName("台積電").country("TW").publicCompany(true).build();
        when(companyService.findCompanies(any(CompanyQuery.class)))
                .thenReturn(PageResponse.of(List.of(response), 0, 20, 1L));

        mockMvc.perform(get("/api/companies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].displayName").value("台積電"))
                .andExpect(jsonPath("$.data.content[0].reference").value("TWSE:2330"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1));
    }

    @Test
    @DisplayName("列出公司時查詢條件應完整綁定到 CompanyQuery")
    void listCompanies_allFilters_shouldBindToQuery() throws Exception {
        when(companyService.findCompanies(any(CompanyQuery.class)))
                .thenReturn(PageResponse.of(List.of(), 1, 50, 0L));

        mockMvc.perform(get("/api/companies")
                        .param("page", "1").param("size", "50")
                        .param("name", "桂盟").param("country", "TW")
                        .param("publicCompany", "true").param("itemId", "12")
                        .param("companyRole", "MANUFACTURE").param("includeDrafts", "true"))
                .andExpect(status().isOk());

        ArgumentCaptor<CompanyQuery> captor = ArgumentCaptor.forClass(CompanyQuery.class);
        verify(companyService).findCompanies(captor.capture());
        CompanyQuery bound = captor.getValue();
        assertAll(
                () -> assertEquals(1, bound.getPage()),
                () -> assertEquals(50, bound.getSize()),
                () -> assertEquals("桂盟", bound.getName()),
                () -> assertEquals("TW", bound.getCountry()),
                () -> assertEquals(Boolean.TRUE, bound.getPublicCompany()),
                () -> assertEquals(12L, bound.getItemId()),
                () -> assertEquals(CompanyRole.MANUFACTURE, bound.getCompanyRole()),
                () -> assertTrue(bound.isIncludeDrafts()));
    }

    @Test
    @DisplayName("列出公司未帶過濾條件時，可選條件應為 null 代表不過濾")
    void listCompanies_noFilters_shouldLeaveOptionalConditionsNull() throws Exception {
        when(companyService.findCompanies(any(CompanyQuery.class)))
                .thenReturn(PageResponse.of(List.of(), 0, 20, 0L));

        mockMvc.perform(get("/api/companies")).andExpect(status().isOk());

        ArgumentCaptor<CompanyQuery> captor = ArgumentCaptor.forClass(CompanyQuery.class);
        verify(companyService).findCompanies(captor.capture());
        CompanyQuery bound = captor.getValue();
        // 未指定的過濾條件必須是 null；塌成 false / 0 會讓「不過濾」變成「過濾 false」
        assertAll(
                () -> assertNull(bound.getPublicCompany()),
                () -> assertNull(bound.getItemId()),
                () -> assertNull(bound.getCompanyRole()),
                () -> assertNull(bound.getCountry()));
    }

    @Test
    @DisplayName("列出公司無符合資料時應回 200 與空清單，而非 404")
    void listCompanies_noMatch_shouldReturnEmptyPage() throws Exception {
        when(companyService.findCompanies(any(CompanyQuery.class)))
                .thenReturn(PageResponse.of(List.of(), 0, 20, 0L));

        mockMvc.perform(get("/api/companies").param("name", "不存在"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("列出公司時每頁筆數超出上限應回 400")
    void listCompanies_sizeAboveLimit_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/companies").param("size", "500"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("列出公司時頁碼為負數應回 400")
    void listCompanies_negativePage_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/companies").param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("列出公司時指定不存在的品類節點應回 404")
    void listCompanies_unknownItemId_shouldReturnNotFound() throws Exception {
        when(companyService.findCompanies(any(CompanyQuery.class)))
                .thenThrow(new ServerException("查無此品類節點：99", HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/companies").param("itemId", "99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("以裸代號查詢公司應回傳公司資料與所有識別碼，回應的對外識別則為限定形式")
    void getCompany_byBareCode_shouldReturnCompanyWithIdentifiers() throws Exception {
        when(companyService.getVisibleByReference("2330")).thenReturn(tsmc);
        when(companyService.findVisibleIdentifiers(1L)).thenReturn(List.of(
                CompanyIdentifier.builder().company(tsmc).identifierType(IdentifierType.TWSE)
                        .identifierValue("2330").isPrimary(true).build(),
                CompanyIdentifier.builder().company(tsmc).identifierType(IdentifierType.NYSE)
                        .identifierValue("TSM").isPrimary(false).build()));

        mockMvc.perform(get("/api/companies/2330"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("台積電"))
                .andExpect(jsonPath("$.data.reference").value("TWSE:2330"))
                .andExpect(jsonPath("$.data.identifiers.length()").value(2));
    }

    @Test
    @DisplayName("以限定形式代號查詢時路徑變數應原封不動綁定，含冒號在內")
    void getCompany_qualifiedReference_shouldBindPathVariableIncludingColon() throws Exception {
        // Given：冒號在 path segment 中合法（RFC 3986 pchar），但 Spring 是否原封綁定必須實測
        when(companyService.getVisibleByReference("TWSE:2330")).thenReturn(tsmc);
        when(companyService.findVisibleIdentifiers(1L)).thenReturn(List.of(
                CompanyIdentifier.builder().company(tsmc).identifierType(IdentifierType.TWSE)
                        .identifierValue("2330").isPrimary(true).build()));

        // When / Then：走到 service 的字串必須是完整的 TWSE:2330，回應的對外識別亦同
        mockMvc.perform(get("/api/companies/TWSE:2330"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("台積電"))
                .andExpect(jsonPath("$.data.reference").value("TWSE:2330"));
    }

    @Test
    @DisplayName("查無此公司代號時應回傳 404")
    void getCompany_unknownCode_shouldReturnNotFound() throws Exception {
        when(companyService.getVisibleByReference("9999"))
                .thenThrow(new ServerException("查無此公司代號", HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/companies/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("建立未上市公司應回傳 201，且識別碼清單為空")
    void createCompany_privateCompany_shouldReturnCreatedWithoutIdentifiers() throws Exception {
        Company sram = Company.builder().id(2L).normalizedName("sram").displayName("SRAM")
                .country("US").sourceType(SourceType.MANUAL).build();
        when(companyService.create(any(CreateCompanyRequest.class))).thenReturn(sram);

        CreateCompanyRequest request = CreateCompanyRequest.builder()
                .displayName("SRAM")
                .country("US")
                .provenance(ProvenanceRequest.builder().sourceType(SourceType.MANUAL).build())
                .build();

        mockMvc.perform(post("/api/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reference").value("sram"))
                .andExpect(jsonPath("$.data.identifiers").isEmpty());
    }

    @Test
    @DisplayName("建立公司缺少來源類型時應回傳 400")
    void createCompany_missingSourceType_shouldReturnBadRequest() throws Exception {
        CreateCompanyRequest request = CreateCompanyRequest.builder()
                .displayName("SRAM")
                .provenance(ProvenanceRequest.builder().build())
                .build();

        mockMvc.perform(post("/api/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("登記第二筆主要識別碼時應回傳 409")
    void addIdentifier_secondPrimary_shouldReturnConflict() throws Exception {
        when(companyService.getByReference("2330")).thenReturn(tsmc);
        when(companyService.addIdentifier(eq(1L), any(CreateIdentifierRequest.class)))
                .thenThrow(new ServerException("該公司已有主要識別碼", HttpStatus.CONFLICT));

        CreateIdentifierRequest request = CreateIdentifierRequest.builder()
                .identifierType(IdentifierType.NYSE)
                .identifierValue("TSM")
                .primary(true)
                .provenance(ProvenanceRequest.builder().sourceType(SourceType.MANUAL).build())
                .build();

        mockMvc.perform(post("/api/companies/2330/identifiers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("以限定形式登記識別碼時子路徑仍應正確綁定")
    void addIdentifier_qualifiedReference_shouldBindPathVariable() throws Exception {
        when(companyService.getByReference("TWSE:2330")).thenReturn(tsmc);
        when(companyService.addIdentifier(eq(1L), any(CreateIdentifierRequest.class)))
                .thenReturn(CompanyIdentifier.builder().company(tsmc).identifierType(IdentifierType.NYSE)
                        .identifierValue("TSM").isPrimary(false).build());

        CreateIdentifierRequest request = CreateIdentifierRequest.builder()
                .identifierType(IdentifierType.NYSE)
                .identifierValue("TSM")
                .provenance(ProvenanceRequest.builder().sourceType(SourceType.MANUAL).build())
                .build();

        mockMvc.perform(post("/api/companies/TWSE:2330/identifiers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.identifierValue").value("TSM"));
    }

    @Test
    @DisplayName("以限定形式登記別名時子路徑仍應正確綁定")
    void addAlias_qualifiedReference_shouldBindPathVariable() throws Exception {
        when(companyService.getByReference("TWSE:2330")).thenReturn(tsmc);
        when(companyService.addAlias(eq(1L), any(CreateCompanyAliasRequest.class)))
                .thenReturn(CompanyAlias.builder().company(tsmc).normalizedAlias("tsmc").displayAlias("TSMC").build());

        CreateCompanyAliasRequest request = CreateCompanyAliasRequest.builder()
                .alias("TSMC")
                .provenance(ProvenanceRequest.builder().sourceType(SourceType.MANUAL).build())
                .build();

        mockMvc.perform(post("/api/companies/TWSE:2330/aliases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data").value("tsmc"));
    }
}
