package com.profetai.industrymap.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.enums.SourceType;
import com.profetai.industrymap.exceptions.ServerException;
import com.profetai.industrymap.model.HotspotPoint;
import com.profetai.industrymap.model.Item;
import com.profetai.industrymap.model.ItemHotspot;
import com.profetai.industrymap.model.ItemImage;
import com.profetai.industrymap.payloads.ProvenanceRequest;
import com.profetai.industrymap.payloads.item.AmendHotspotRequest;
import com.profetai.industrymap.payloads.item.CreateHotspotRequest;
import com.profetai.industrymap.payloads.item.HotspotPointPayload;
import com.profetai.industrymap.service.item.ItemImageService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 熱區寫入端點的狀態碼語意，以及座標驗證的 payload 那一層。
 *
 * <p>座標規則在 payload 與 service 各擋一次（design D5）：這支測試驗 payload 的 annotation
 * 有沒有在 API 邊界擋下不合法的座標，service 那一層由 {@code ItemImageServiceTest} 驗。
 * 兩層缺一不可——annotation 擋不到繞過 HTTP 的呼叫，service 的檢查則不會讓
 * HTTP 呼叫端拿到逐欄位的錯誤訊息。</p>
 */
@Tag("integration")
@WebMvcTest(ItemHotspotController.class)
class ItemHotspotControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ItemImageService itemImageService;

    @Test
    @DisplayName("建立熱區成功時應回傳 201 與該筆熱區")
    void createHotspot_validRequest_shouldReturnCreated() throws Exception {
        when(itemImageService.createHotspot(any(CreateHotspotRequest.class))).thenReturn(hotspot("前煞車"));

        mockMvc.perform(post("/api/item-hotspots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("前煞車", triangle()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.positionLabel").value("前煞車"))
                .andExpect(jsonPath("$.data.childItemId").value(2))
                .andExpect(jsonPath("$.data.polygon.length()").value(3));
    }

    @Test
    @DisplayName("建立熱區的座標少於三點時應回傳 400")
    void createHotspot_polygonWithTwoPoints_shouldReturnBadRequest() throws Exception {
        List<HotspotPointPayload> twoPoints = List.of(point(0.1, 0.1), point(0.2, 0.2));

        mockMvc.perform(post("/api/item-hotspots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("前煞車", twoPoints))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("建立熱區的座標超出 0–1 時應回傳 400")
    void createHotspot_coordinateOutOfRange_shouldReturnBadRequest() throws Exception {
        List<HotspotPointPayload> polygon = List.of(point(0.1, 0.1), point(1.01, 0.2), point(0.3, 0.3));

        mockMvc.perform(post("/api/item-hotspots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("前煞車", polygon))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("建立熱區未提供位置標籤時應回傳 400")
    void createHotspot_blankPositionLabel_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/item-hotspots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("  ", triangle()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("同一張圖的位置標籤重複時應回傳 409")
    void createHotspot_duplicatedPositionLabel_shouldReturnConflict() throws Exception {
        when(itemImageService.createHotspot(any(CreateHotspotRequest.class)))
                .thenThrow(new ServerException("此圖片已有同一位置標籤的熱區：前煞車", HttpStatus.CONFLICT));

        mockMvc.perform(post("/api/item-hotspots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("前煞車", triangle()))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("熱區指向不存在的節點時應回傳 404")
    void createHotspot_unknownChildItem_shouldReturnNotFound() throws Exception {
        when(itemImageService.createHotspot(any(CreateHotspotRequest.class)))
                .thenThrow(new ServerException("查無此品類節點：2", HttpStatus.NOT_FOUND));

        mockMvc.perform(post("/api/item-hotspots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("前煞車", triangle()))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("修正熱區成功時應回傳 200 與退回草稿的狀態")
    void amendHotspot_validRequest_shouldReturnOk() throws Exception {
        ItemHotspot amended = hotspot("前煞車");
        amended.setReviewStatus(ReviewStatus.DRAFT);
        when(itemImageService.amendHotspot(eq(9L), any(AmendHotspotRequest.class))).thenReturn(amended);

        mockMvc.perform(put("/api/item-hotspots/9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AmendHotspotRequest("前煞車", triangle()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("DRAFT"));
    }

    @Test
    @DisplayName("修正熱區未帶座標時應回傳 400")
    void amendHotspot_missingPolygon_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(put("/api/item-hotspots/9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"positionLabel":"前煞車"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("修正不存在的熱區時應回傳 404")
    void amendHotspot_unknownHotspot_shouldReturnNotFound() throws Exception {
        when(itemImageService.amendHotspot(eq(99L), any(AmendHotspotRequest.class)))
                .thenThrow(new ServerException("查無此熱區：99", HttpStatus.NOT_FOUND));

        mockMvc.perform(put("/api/item-hotspots/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AmendHotspotRequest("前煞車", triangle()))))
                .andExpect(status().isNotFound());
    }

    private CreateHotspotRequest createRequest(String positionLabel, List<HotspotPointPayload> polygon) {
        return CreateHotspotRequest.builder()
                .itemImageId(5L)
                .childItemId(2L)
                .positionLabel(positionLabel)
                .polygon(polygon)
                .provenance(ProvenanceRequest.builder().sourceType(SourceType.MANUAL).build())
                .build();
    }

    private List<HotspotPointPayload> triangle() {
        return List.of(point(0.1, 0.1), point(0.2, 0.1), point(0.2, 0.2));
    }

    private HotspotPointPayload point(double x, double y) {
        return HotspotPointPayload.builder().x(x).y(y).build();
    }

    private ItemHotspot hotspot(String positionLabel) {
        Item brake = Item.builder().id(2L).normalizedName("煞車").displayName("煞車").build();
        ItemImage image = ItemImage.builder().id(5L).viewLabel("爆炸圖")
                .storageKey("https://cdn.example.com/bike.png").build();
        return ItemHotspot.builder()
                .id(9L)
                .itemImage(image)
                .childItem(brake)
                .positionLabel(positionLabel)
                .polygon(List.of(new HotspotPoint(0.1, 0.1), new HotspotPoint(0.2, 0.1), new HotspotPoint(0.2, 0.2)))
                .sourceType(SourceType.MANUAL)
                .reviewStatus(ReviewStatus.VERIFIED)
                .build();
    }
}
