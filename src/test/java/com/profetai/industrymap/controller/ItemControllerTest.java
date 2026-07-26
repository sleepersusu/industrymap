package com.profetai.industrymap.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.profetai.industrymap.enums.Necessity;
import com.profetai.industrymap.enums.SourceType;
import com.profetai.industrymap.exceptions.ServerException;
import com.profetai.industrymap.model.Item;
import com.profetai.industrymap.payloads.ProvenanceRequest;
import com.profetai.industrymap.payloads.item.CreateCompositionRequest;
import com.profetai.industrymap.payloads.item.CreateItemRequest;
import com.profetai.industrymap.service.item.ItemCompositionService;
import com.profetai.industrymap.service.item.ItemService;
import com.profetai.industrymap.service.supply.CompanyItemRoleService;
import com.profetai.industrymap.service.supply.MarketShareService;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 驗證 API 邊界的狀態碼語意符合 .claude/rules/api-design.md：
 * 404 查無資源、409 衝突、400 驗證失敗。
 */
@Tag("integration")
@WebMvcTest(ItemController.class)
class ItemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ItemService itemService;

    @MockitoBean
    private ItemCompositionService itemCompositionService;

    @MockitoBean
    private CompanyItemRoleService companyItemRoleService;

    @MockitoBean
    private MarketShareService marketShareService;

    @Test
    @DisplayName("建立品類節點成功時應回傳 201")
    void createItem_validRequest_shouldReturnCreated() throws Exception {
        when(itemService.create(any(CreateItemRequest.class))).thenReturn(
                Item.builder().id(1L).normalizedName("wifi模組").displayName("WiFi 模組")
                        .sourceType(SourceType.MANUAL).build());

        mockMvc.perform(post("/api/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createItemRequest("WiFi 模組"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.normalizedName").value("wifi模組"));
    }

    @Test
    @DisplayName("建立品類節點名稱重複時應回傳 409")
    void createItem_duplicatedName_shouldReturnConflict() throws Exception {
        when(itemService.create(any(CreateItemRequest.class)))
                .thenThrow(new ServerException("已存在相同名稱的品類節點", HttpStatus.CONFLICT));

        mockMvc.perform(post("/api/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createItemRequest("WiFi 模組"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("建立品類節點缺少名稱時應回傳 400")
    void createItem_missingDisplayName_shouldReturnBadRequest() throws Exception {
        CreateItemRequest request = createItemRequest("WiFi 模組");
        request.setDisplayName("  ");

        mockMvc.perform(post("/api/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("AI 生成來源未帶信心度時應回傳 400")
    void createItem_aiSourceWithoutConfidence_shouldReturnBadRequest() throws Exception {
        CreateItemRequest request = CreateItemRequest.builder()
                .displayName("WiFi 模組")
                .provenance(ProvenanceRequest.builder().sourceType(SourceType.AI_GENERATED).build())
                .build();

        mockMvc.perform(post("/api/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("查無品類節點時應回傳 404")
    void getItem_unknownId_shouldReturnNotFound() throws Exception {
        when(itemService.getById(99L)).thenThrow(new ServerException("查無此品類節點", HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/items/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("以名稱解析節點但名稱與別名皆查無時應回傳 404")
    void resolveByName_noMatch_shouldReturnNotFound() throws Exception {
        when(itemService.resolveByName("不存在")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/items").param("name", "不存在"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("建立組成關係會造成循環時應回傳 409")
    void addComponent_cycle_shouldReturnConflict() throws Exception {
        when(itemCompositionService.create(eq(1L), any(CreateCompositionRequest.class)))
                .thenThrow(new ServerException("組成關係會造成循環", HttpStatus.CONFLICT));

        mockMvc.perform(post("/api/items/1/components")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(compositionRequest(Necessity.STANDARD))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("建立組成關係未提供必要性時應回傳 400")
    void addComponent_missingNecessity_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/items/1/components")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(compositionRequest(null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("市佔率排名查無資料時應回傳 200 與空清單，而非 404")
    void getMarketShare_noData_shouldReturnEmptyList() throws Exception {
        when(marketShareService.findRanking(eq(1L), any(), eq("2024"), eq("全球"), any(), anyBoolean()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/items/1/market-share")
                        .param("periodType", "YEAR")
                        .param("periodValue", "2024")
                        .param("region", "全球")
                        .param("metric", "REVENUE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("市佔率排名缺少必要查詢參數時應回傳 400")
    void getMarketShare_missingRequiredParam_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/items/1/market-share").param("periodType", "YEAR"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("查詢參數的列舉值不合法時應回傳 400")
    void getSuppliers_invalidRoleEnum_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/items/1/suppliers").param("role", "NOT_A_ROLE"))
                .andExpect(status().isBadRequest());
    }

    private CreateItemRequest createItemRequest(String displayName) {
        return CreateItemRequest.builder()
                .displayName(displayName)
                .provenance(ProvenanceRequest.builder().sourceType(SourceType.MANUAL).build())
                .build();
    }

    private CreateCompositionRequest compositionRequest(Necessity necessity) {
        return CreateCompositionRequest.builder()
                .childItemId(2L)
                .necessity(necessity)
                .provenance(ProvenanceRequest.builder().sourceType(SourceType.MANUAL).build())
                .build();
    }
}
