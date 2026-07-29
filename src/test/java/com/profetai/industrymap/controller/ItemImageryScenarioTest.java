package com.profetai.industrymap.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.enums.ReviewTargetType;
import com.profetai.industrymap.enums.SourceType;
import com.profetai.industrymap.model.Item;
import com.profetai.industrymap.payloads.ProvenanceRequest;
import com.profetai.industrymap.payloads.item.CreateHotspotRequest;
import com.profetai.industrymap.payloads.item.CreateItemImageRequest;
import com.profetai.industrymap.payloads.item.HotspotPointPayload;
import com.profetai.industrymap.payloads.review.ReviewRequest;
import com.profetai.industrymap.payloads.review.ReviewTargetKey;
import com.profetai.industrymap.repository.ItemRepository;
import com.profetai.industrymap.support.AbstractPostgresWebIntegrationTest;
import com.profetai.industrymap.util.NameNormalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 本 change 的驗收情境，一路走 HTTP：掛一張爆炸圖 → 標兩個指向<b>同一個節點</b>但位置標籤不同的熱區
 * → 審核 → 查詢 → 駁回其中一個 → 再查詢。
 *
 * <p>驗的是 design D2 要解的那個問題：組成關係的唯一鍵是 {@code (parent, child)}，前煞車與後煞車
 * 在那張表上只有一條邊；若熱區只存「指向哪個節點」，兩筆資料會完全相同，使用者點前煞車與點後煞車
 * 會拿到一模一樣的結果。位置標籤是它們唯一的區分方式。</p>
 *
 * <p>駁回一筆之後另一筆仍在，是「移除以審核駁回表達」（design D7）真的可用的前提——
 * 若駁回會連坐同一節點的其他熱區，這條路等於不能走。</p>
 */
class ItemImageryScenarioTest extends AbstractPostgresWebIntegrationTest {

    private static final String REVIEWER = "reviewer@profetai";

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ItemRepository itemRepository;

    private final String fixtureId = UUID.randomUUID().toString().substring(0, 8);

    @Test
    @DisplayName("同一節點的兩個熱區可由位置標籤區分，駁回其一後另一筆仍在")
    void hotspotsOnSameItem_shouldBeDistinguishableAndIndependentlyReviewable() throws Exception {
        // Given：一台腳踏車、一個煞車節點，腳踏車掛一張爆炸圖
        Item bike = item("腳踏車", true);
        Item brake = item("煞車", false);
        JsonNode image = postJson("/api/items/" + bike.getId() + "/images", CreateItemImageRequest.builder()
                .viewLabel("爆炸圖")
                .storageKey("https://cdn.example.com/" + fixtureId + ".png")
                .widthPx(1200).heightPx(800)
                .provenance(manualProvenance())
                .build());
        long imageId = image.get("id").asLong();

        // 兩個熱區指向同一個節點，只有位置標籤與座標不同
        JsonNode front = postJson("/api/item-hotspots", hotspotRequest(imageId, brake.getId(), "前煞車", 0.1));
        JsonNode rear = postJson("/api/item-hotspots", hotspotRequest(imageId, brake.getId(), "後煞車", 0.6));

        // 全部審核為已驗證；熱區刻意只用自然鍵定位，證明不需要知道任何內部 id
        review(ReviewRequest.builder().targetType(ReviewTargetType.ITEM_IMAGE)
                .naturalKey(ReviewTargetKey.builder().itemId(bike.getId()).viewLabel("爆炸圖").build())
                .targetStatus(ReviewStatus.VERIFIED).reviewer(REVIEWER).build());
        review(hotspotReview(bike.getId(), "前煞車", ReviewStatus.VERIFIED));
        review(hotspotReview(bike.getId(), "後煞車", ReviewStatus.VERIFIED));

        // When：查該節點的圖片
        JsonNode images = getData("/api/items/" + bike.getId() + "/images");

        // Then：一張圖、兩個熱區，指向同一節點但可由位置標籤與座標區分
        JsonNode hotspots = images.get(0).get("hotspots");
        assertAll(
                () -> assertEquals(1, images.size()),
                () -> assertEquals(2, hotspots.size()),
                () -> assertEquals(brake.getId(), hotspots.get(0).get("childItemId").asLong()),
                () -> assertEquals(brake.getId(), hotspots.get(1).get("childItemId").asLong()),
                () -> assertEquals("前煞車", hotspots.get(0).get("positionLabel").asText()),
                () -> assertEquals("後煞車", hotspots.get(1).get("positionLabel").asText()),
                () -> assertEquals(0.1, hotspots.get(0).get("polygon").get(0).get("x").asDouble()),
                () -> assertEquals(0.6, hotspots.get(1).get("polygon").get(0).get("x").asDouble()),
                () -> assertTrue(front.get("id").asLong() != rear.get("id").asLong()));

        // When：駁回其中一個熱區（畫錯的熱區以駁回移除，design D7）
        review(hotspotReview(bike.getId(), "前煞車", ReviewStatus.REJECTED));
        JsonNode afterRejection = getData("/api/items/" + bike.getId() + "/images");

        // Then：被駁回的那筆消失，另一筆完全不受影響
        JsonNode remaining = afterRejection.get(0).get("hotspots");
        assertAll(
                () -> assertEquals(1, remaining.size()),
                () -> assertEquals("後煞車", remaining.get(0).get("positionLabel").asText()),
                () -> assertEquals(rear.get("id").asLong(), remaining.get(0).get("id").asLong()));
    }

    // ---------------------------------------------------------------------
    // 請求與回應
    // ---------------------------------------------------------------------

    private JsonNode postJson(String uri, Object request) throws Exception {
        String body = mockMvc.perform(post(uri)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data");
    }

    private void review(ReviewRequest request) throws Exception {
        mockMvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private JsonNode getData(String uri) throws Exception {
        String body = mockMvc.perform(get(uri))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data");
    }

    // ---------------------------------------------------------------------
    // fixture
    // ---------------------------------------------------------------------

    private ReviewRequest hotspotReview(Long itemId, String positionLabel, ReviewStatus targetStatus) {
        return ReviewRequest.builder()
                .targetType(ReviewTargetType.ITEM_HOTSPOT)
                .naturalKey(ReviewTargetKey.builder()
                        .itemId(itemId).viewLabel("爆炸圖").positionLabel(positionLabel).build())
                .targetStatus(targetStatus)
                .reviewer(REVIEWER)
                .build();
    }

    private CreateHotspotRequest hotspotRequest(long imageId, long childItemId, String positionLabel, double originX) {
        return CreateHotspotRequest.builder()
                .itemImageId(imageId)
                .childItemId(childItemId)
                .positionLabel(positionLabel)
                .polygon(List.of(point(originX, 0.1), point(originX + 0.1, 0.1), point(originX + 0.1, 0.2)))
                .provenance(manualProvenance())
                .build();
    }

    private HotspotPointPayload point(double x, double y) {
        return HotspotPointPayload.builder().x(x).y(y).build();
    }

    private Item item(String label, boolean endProduct) {
        String displayName = FIXTURE_PREFIX + fixtureId + "-" + label;
        return itemRepository.saveAndFlush(Item.builder()
                .normalizedName(NameNormalizer.normalize(displayName))
                .displayName(displayName)
                .isEndProduct(endProduct)
                .sourceType(SourceType.MANUAL)
                .reviewStatus(ReviewStatus.VERIFIED)
                .build());
    }

    private ProvenanceRequest manualProvenance() {
        return ProvenanceRequest.builder().sourceType(SourceType.MANUAL).build();
    }
}
