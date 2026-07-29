package com.profetai.industrymap.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.profetai.industrymap.enums.ReviewTargetType;
import com.profetai.industrymap.enums.SourceType;
import com.profetai.industrymap.payloads.ProvenanceRequest;
import com.profetai.industrymap.payloads.bulk.BatchCreateRequest;
import com.profetai.industrymap.payloads.bulk.BatchCreateResultResponse;
import com.profetai.industrymap.payloads.bulk.BatchItemImageItem;
import com.profetai.industrymap.payloads.item.CreateHotspotRequest;
import com.profetai.industrymap.payloads.item.CreateItemRequest;
import com.profetai.industrymap.payloads.item.HotspotPointPayload;
import com.profetai.industrymap.payloads.review.ReviewTargetKey;
import com.profetai.industrymap.service.bulk.BulkAuthoringService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@WebMvcTest(BulkAuthoringController.class)
class BulkAuthoringControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private BulkAuthoringService bulkAuthoringService;

    @Test
    @DisplayName("批次建立部分失敗時仍回傳 200，成功項目帶自然鍵、失敗項目只帶原因")
    void createItems_partialFailure_shouldReturnOkWithPerItemResults() throws Exception {
        when(bulkAuthoringService.createItems(anyList())).thenReturn(List.of(
                BatchCreateResultResponse.success(0, ReviewTargetType.ITEM, 1L,
                        ReviewTargetKey.builder().name("腳踏車").build()),
                BatchCreateResultResponse.failure(1, 409, "已存在相同名稱的品類節點：變速器")));

        mockMvc.perform(post("/api/bulk/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batchOf(
                                itemRequest("腳踏車"), itemRequest("變速器")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].success").value(true))
                .andExpect(jsonPath("$.data[0].naturalKey.name").value("腳踏車"))
                .andExpect(jsonPath("$.data[1].success").value(false))
                .andExpect(jsonPath("$.data[1].statusCode").value(409))
                .andExpect(jsonPath("$.data[1].naturalKey").doesNotExist())
                .andExpect(jsonPath("$.data[1].targetId").doesNotExist());
    }

    @Test
    @DisplayName("空批次應回傳 400 且不進 service")
    void createItems_emptyBatch_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/bulk/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                BatchCreateRequest.<CreateItemRequest>builder().items(List.of()).build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(bulkAuthoringService, never()).createItems(anyList());
    }

    @Test
    @DisplayName("批次項目本身欄位驗證失敗時應回傳 400 且不進 service")
    void createItems_invalidItem_shouldReturnBadRequest() throws Exception {
        CreateItemRequest invalid = itemRequest("腳踏車");
        invalid.setDisplayName("  ");

        mockMvc.perform(post("/api/bulk/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batchOf(invalid))))
                .andExpect(status().isBadRequest());

        verify(bulkAuthoringService, never()).createItems(any());
    }

    @Test
    @DisplayName("批次建立熱區成功時應逐筆帶回「節點 + 視角標籤 + 位置標籤」自然鍵")
    void createHotspots_valid_shouldReturnNaturalKeys() throws Exception {
        when(bulkAuthoringService.createHotspots(anyList())).thenReturn(List.of(
                BatchCreateResultResponse.success(0, ReviewTargetType.ITEM_HOTSPOT, 81L,
                        ReviewTargetKey.builder().itemId(1L).viewLabel("爆炸圖").positionLabel("前煞車").build())));

        mockMvc.perform(post("/api/bulk/item-hotspots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                BatchCreateRequest.<CreateHotspotRequest>builder()
                                        .items(List.of(hotspotRequest("前煞車"))).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].naturalKey.viewLabel").value("爆炸圖"))
                .andExpect(jsonPath("$.data[0].naturalKey.positionLabel").value("前煞車"));
    }

    @Test
    @DisplayName("批次建立熱區的座標不合法時應回傳 400 且不進 service")
    void createHotspots_invalidPolygon_shouldReturnBadRequest() throws Exception {
        CreateHotspotRequest invalid = hotspotRequest("前煞車");
        invalid.setPolygon(List.of(HotspotPointPayload.builder().x(0.1).y(0.1).build()));

        mockMvc.perform(post("/api/bulk/item-hotspots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                BatchCreateRequest.<CreateHotspotRequest>builder().items(List.of(invalid)).build())))
                .andExpect(status().isBadRequest());

        verify(bulkAuthoringService, never()).createHotspots(any());
    }

    @Test
    @DisplayName("批次建立圖片的空批次應回傳 400 且不進 service")
    void createItemImages_emptyBatch_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/bulk/item-images")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                BatchCreateRequest.<BatchItemImageItem>builder().items(List.of()).build())))
                .andExpect(status().isBadRequest());

        verify(bulkAuthoringService, never()).createItemImages(any());
    }

    private CreateHotspotRequest hotspotRequest(String positionLabel) {
        return CreateHotspotRequest.builder()
                .itemImageId(71L)
                .childItemId(2L)
                .positionLabel(positionLabel)
                .polygon(List.of(HotspotPointPayload.builder().x(0.1).y(0.1).build(),
                        HotspotPointPayload.builder().x(0.2).y(0.1).build(),
                        HotspotPointPayload.builder().x(0.2).y(0.2).build()))
                .provenance(ProvenanceRequest.builder().sourceType(SourceType.MANUAL).build())
                .build();
    }

    private BatchCreateRequest<CreateItemRequest> batchOf(CreateItemRequest... requests) {
        return BatchCreateRequest.<CreateItemRequest>builder().items(List.of(requests)).build();
    }

    private CreateItemRequest itemRequest(String displayName) {
        return CreateItemRequest.builder()
                .displayName(displayName)
                .provenance(ProvenanceRequest.builder().sourceType(SourceType.MANUAL).build())
                .build();
    }
}
