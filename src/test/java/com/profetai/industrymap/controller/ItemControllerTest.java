package com.profetai.industrymap.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.profetai.industrymap.enums.Necessity;
import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.enums.SourceType;
import com.profetai.industrymap.exceptions.ServerException;
import com.profetai.industrymap.model.Item;
import com.profetai.industrymap.payloads.ProvenanceRequest;
import com.profetai.industrymap.payloads.item.AmendItemRequest;
import com.profetai.industrymap.payloads.item.CompositionResponse;
import com.profetai.industrymap.payloads.item.CreateCompositionRequest;
import com.profetai.industrymap.payloads.item.CreateItemImageRequest;
import com.profetai.industrymap.payloads.item.CreateItemRequest;
import com.profetai.industrymap.payloads.item.HotspotPointPayload;
import com.profetai.industrymap.payloads.item.HotspotResponse;
import com.profetai.industrymap.payloads.item.ItemImageResponse;
import com.profetai.industrymap.service.item.ItemCompositionService;
import com.profetai.industrymap.service.item.ItemImageService;
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

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    private ItemImageService itemImageService;

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
        when(itemService.getVisibleById(99L)).thenThrow(new ServerException("查無此品類節點", HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/items/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("以名稱解析節點但名稱與別名皆查無時應回傳 404")
    void resolveByName_noMatch_shouldReturnNotFound() throws Exception {
        when(itemService.resolveVisibleByName("不存在")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/items").param("name", "不存在"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("修正品類節點成功時應回傳 200 與修正後的節點")
    void amendItem_validRequest_shouldReturnOk() throws Exception {
        when(itemService.amend(eq(1L), any(AmendItemRequest.class))).thenReturn(
                Item.builder().id(1L).normalizedName("主機板").displayName("主機板")
                        .reviewStatus(ReviewStatus.DRAFT).sourceType(SourceType.MANUAL).build());

        mockMvc.perform(put("/api/items/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"主機板","endProduct":false,"parentCategoryId":null}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("主機板"))
                .andExpect(jsonPath("$.data.reviewStatus").value("DRAFT"));
    }

    @Test
    @DisplayName("修正品類節點缺少必填欄位時應回傳 400，訊息需指出缺少哪些欄位")
    void amendItem_missingFields_shouldReturnBadRequest() throws Exception {
        // 全量替換語意下，沒送 endProduct 與 parentCategoryId 不能被當成 false / 清空
        mockMvc.perform(put("/api/items/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"主機板"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("endProduct")))
                .andExpect(jsonPath("$.message", containsString("parentCategoryId")));
    }

    @Test
    @DisplayName("修正不存在的品類節點時應回傳 404")
    void amendItem_unknownItem_shouldReturnNotFound() throws Exception {
        when(itemService.amend(eq(99L), any(AmendItemRequest.class)))
                .thenThrow(new ServerException("查無此品類節點：99", HttpStatus.NOT_FOUND));

        mockMvc.perform(put("/api/items/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"主機板","endProduct":false,"parentCategoryId":null}"""))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("修正品類節點改名撞到既有名稱時應回傳 409")
    void amendItem_nameConflict_shouldReturnConflict() throws Exception {
        when(itemService.amend(eq(1L), any(AmendItemRequest.class)))
                .thenThrow(new ServerException("名稱與另一個品類節點衝突：顯示卡", HttpStatus.CONFLICT));

        mockMvc.perform(put("/api/items/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"顯示卡","endProduct":false,"parentCategoryId":null}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
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

    @Test
    @DisplayName("查節點組成關係應回傳上下層節點、必要性與審核狀態")
    void getCompositions_existingRelations_shouldReturnBothEnds() throws Exception {
        when(itemCompositionService.findCompositions(1L, false)).thenReturn(List.of(
                CompositionResponse.builder().id(7L).parentItemId(1L).childItemId(2L)
                        .necessity(Necessity.STANDARD).reviewStatus(ReviewStatus.VERIFIED).build()));

        mockMvc.perform(get("/api/items/1/compositions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].parentItemId").value(1))
                .andExpect(jsonPath("$.data[0].childItemId").value(2))
                .andExpect(jsonPath("$.data[0].necessity").value("STANDARD"))
                .andExpect(jsonPath("$.data[0].reviewStatus").value("VERIFIED"));
    }

    @Test
    @DisplayName("查節點組成關係指定納入草稿時應以含草稿的範圍查詢")
    void getCompositions_includeDrafts_shouldQueryWithDraftScope() throws Exception {
        when(itemCompositionService.findCompositions(1L, true)).thenReturn(List.of());

        mockMvc.perform(get("/api/items/1/compositions").param("includeDrafts", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("查無此節點的組成關係時應回傳 404")
    void getCompositions_unknownItem_shouldReturnNotFound() throws Exception {
        when(itemCompositionService.findCompositions(99L, false))
                .thenThrow(new ServerException("查無此品類節點：99", HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/items/99/compositions"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("查節點圖片應回傳圖片與其巢狀的熱區")
    void getImages_existingImages_shouldNestHotspots() throws Exception {
        when(itemImageService.findImages(1L, false)).thenReturn(List.of(
                ItemImageResponse.builder().id(5L).itemId(1L).viewLabel("爆炸圖")
                        .storageKey("https://cdn.example.com/bike.png").reviewStatus(ReviewStatus.VERIFIED)
                        .hotspots(List.of(HotspotResponse.builder().id(9L).itemImageId(5L).childItemId(2L)
                                .childDisplayName("煞車").positionLabel("前煞車")
                                .polygon(triangle()).reviewStatus(ReviewStatus.VERIFIED).build()))
                        .build()));

        mockMvc.perform(get("/api/items/1/images"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].viewLabel").value("爆炸圖"))
                .andExpect(jsonPath("$.data[0].hotspots[0].positionLabel").value("前煞車"))
                .andExpect(jsonPath("$.data[0].hotspots[0].polygon.length()").value(3));
    }

    @Test
    @DisplayName("節點沒有任何圖片時應回傳 200 與空清單，而非 404")
    void getImages_noImages_shouldReturnEmptyList() throws Exception {
        when(itemImageService.findImages(1L, false)).thenReturn(List.of());

        mockMvc.perform(get("/api/items/1/images"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("查無此節點的圖片時應回傳 404")
    void getImages_unknownItem_shouldReturnNotFound() throws Exception {
        when(itemImageService.findImages(99L, false))
                .thenThrow(new ServerException("查無此品類節點：99", HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/items/99/images"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("建立圖片時同一節點的視角標籤重複應回傳 409")
    void addImage_duplicatedViewLabel_shouldReturnConflict() throws Exception {
        when(itemImageService.createImage(eq(1L), any(CreateItemImageRequest.class)))
                .thenThrow(new ServerException("此節點已有同一視角標籤的圖片：爆炸圖", HttpStatus.CONFLICT));

        mockMvc.perform(post("/api/items/1/images")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(imageRequest("爆炸圖", "https://cdn/bike.png"))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("建立圖片未提供物件儲存位置時應回傳 400")
    void addImage_missingStorageKey_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/items/1/images")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(imageRequest("爆炸圖", "  "))))
                .andExpect(status().isBadRequest());
    }

    private CreateItemImageRequest imageRequest(String viewLabel, String storageKey) {
        return CreateItemImageRequest.builder()
                .viewLabel(viewLabel)
                .storageKey(storageKey)
                .provenance(ProvenanceRequest.builder().sourceType(SourceType.MANUAL).build())
                .build();
    }

    private List<HotspotPointPayload> triangle() {
        return List.of(HotspotPointPayload.builder().x(0.1).y(0.1).build(),
                HotspotPointPayload.builder().x(0.2).y(0.1).build(),
                HotspotPointPayload.builder().x(0.2).y(0.2).build());
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
