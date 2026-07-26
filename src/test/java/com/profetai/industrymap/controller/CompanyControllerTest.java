package com.profetai.industrymap.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.profetai.industrymap.enums.IdentifierType;
import com.profetai.industrymap.enums.SourceType;
import com.profetai.industrymap.exceptions.ServerException;
import com.profetai.industrymap.model.Company;
import com.profetai.industrymap.model.CompanyIdentifier;
import com.profetai.industrymap.payloads.ProvenanceRequest;
import com.profetai.industrymap.payloads.company.CreateCompanyRequest;
import com.profetai.industrymap.payloads.company.CreateIdentifierRequest;
import com.profetai.industrymap.service.company.CompanyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    @DisplayName("依代號查詢公司應回傳公司資料與所有識別碼")
    void getCompany_byCode_shouldReturnCompanyWithIdentifiers() throws Exception {
        when(companyService.getVisibleByReference("2330")).thenReturn(tsmc);
        when(companyService.findVisibleIdentifiers(1L)).thenReturn(List.of(
                CompanyIdentifier.builder().company(tsmc).identifierType(IdentifierType.TWSE)
                        .identifierValue("2330").isPrimary(true).build(),
                CompanyIdentifier.builder().company(tsmc).identifierType(IdentifierType.NYSE)
                        .identifierValue("TSM").isPrimary(false).build()));

        mockMvc.perform(get("/api/companies/2330"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("台積電"))
                .andExpect(jsonPath("$.data.reference").value("2330"))
                .andExpect(jsonPath("$.data.identifiers.length()").value(2));
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
}
