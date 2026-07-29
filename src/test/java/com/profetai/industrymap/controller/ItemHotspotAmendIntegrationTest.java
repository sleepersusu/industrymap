package com.profetai.industrymap.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.enums.SourceType;
import com.profetai.industrymap.model.HotspotPoint;
import com.profetai.industrymap.model.Item;
import com.profetai.industrymap.model.ItemHotspot;
import com.profetai.industrymap.model.ItemImage;
import com.profetai.industrymap.payloads.item.AmendHotspotRequest;
import com.profetai.industrymap.payloads.item.HotspotPointPayload;
import com.profetai.industrymap.repository.ItemHotspotRepository;
import com.profetai.industrymap.repository.ItemImageRepository;
import com.profetai.industrymap.repository.ItemRepository;
import com.profetai.industrymap.support.PostgresTestDatabase;
import com.profetai.industrymap.util.NameNormalizer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 修正熱區（{@code PUT /api/item-hotspots/{id}}）走<b>真實的交易邊界</b>。
 *
 * <p>本測試刻意<b>不</b>繼承 {@code AbstractPostgresWebIntegrationTest}，也不加
 * {@code @Transactional}：那個基底為了回滾 fixture 而把整個測試包進一個交易，於是 Hibernate
 * session 在回應組裝時仍然開著，交易外才會發生的 {@code LazyInitializationException} 全部照不出來。
 * 這正是這支端點的 500 能通過原本全綠的 build 的原因——回應要讀熱區指向節點的名稱，
 * 而 {@code childItem} 是 LAZY 關聯、{@code @Id} 又標在欄位上（連讀主鍵都會觸發初始化），
 * open-in-view 為 false 時必然炸在 controller 組裝回應那一行。</p>
 *
 * <p>代價是 fixture 得自己清，因此於 {@code @AfterEach} 依相依順序刪除，名稱另帶隨機前綴。</p>
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class ItemHotspotAmendIntegrationTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.apply(registry);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private ItemImageRepository itemImageRepository;
    @Autowired
    private ItemHotspotRepository itemHotspotRepository;

    private final String fixtureId = UUID.randomUUID().toString().substring(0, 8);
    private final List<Long> createdHotspotIds = new ArrayList<>();
    private final List<Long> createdImageIds = new ArrayList<>();
    private final List<Long> createdItemIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        createdHotspotIds.forEach(itemHotspotRepository::deleteById);
        createdImageIds.forEach(itemImageRepository::deleteById);
        createdItemIds.forEach(itemRepository::deleteById);
    }

    @Test
    @DisplayName("修正熱區座標應回 200，回應帶新座標並退回草稿")
    void amendHotspot_movedPolygon_shouldReturnOkWithDraftStatus() throws Exception {
        ItemHotspot hotspot = verifiedHotspot("前煞車");

        JsonNode data = amend(hotspot.getId(), new AmendHotspotRequest("前煞車",
                List.of(point(0.4, 0.4), point(0.5, 0.4), point(0.5, 0.5))));

        assertAll(
                () -> assertEquals("DRAFT", data.get("reviewStatus").asText()),
                () -> assertEquals(0.4, data.get("polygon").get(0).get("x").asDouble()),
                () -> assertEquals(ReviewStatus.DRAFT,
                        itemHotspotRepository.findById(hotspot.getId()).orElseThrow().getReviewStatus()));
    }

    @Test
    @DisplayName("修正熱區的回應必須帶得出所指向節點的名稱")
    void amendHotspot_response_shouldCarryChildItemDisplayName() throws Exception {
        // 回應要讀 LAZY 的 childItem，而組裝發生在交易外——這一格就是那個 500 的重現點
        ItemHotspot hotspot = verifiedHotspot("後煞車");

        JsonNode data = amend(hotspot.getId(), new AmendHotspotRequest("後煞車",
                List.of(point(0.4, 0.4), point(0.5, 0.4), point(0.5, 0.5))));

        assertAll(
                () -> assertEquals(hotspot.getChildItem().getId(), data.get("childItemId").asLong()),
                () -> assertEquals(hotspot.getChildItem().getDisplayName(), data.get("childDisplayName").asText()),
                () -> assertEquals(hotspot.getItemImage().getId(), data.get("itemImageId").asLong()));
    }

    @Test
    @DisplayName("修正熱區只改位置標籤時，回應同樣要帶得出所指向節點的名稱")
    void amendHotspot_labelOnlyChange_shouldCarryChildItemDisplayName() throws Exception {
        // 位置標籤有變更時走的是另一條路徑（會先查同圖是否撞名），兩條路徑都得驗
        ItemHotspot hotspot = verifiedHotspot("左煞車");

        JsonNode data = amend(hotspot.getId(), new AmendHotspotRequest("右煞車",
                List.of(point(0.1, 0.1), point(0.2, 0.1), point(0.2, 0.2))));

        assertAll(
                () -> assertEquals("右煞車", data.get("positionLabel").asText()),
                () -> assertEquals(hotspot.getChildItem().getDisplayName(), data.get("childDisplayName").asText()));
    }

    private JsonNode amend(Long hotspotId, AmendHotspotRequest request) throws Exception {
        String body = mockMvc.perform(put("/api/item-hotspots/" + hotspotId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data");
    }

    // ---------------------------------------------------------------------
    // fixture
    // ---------------------------------------------------------------------

    private ItemHotspot verifiedHotspot(String positionLabel) {
        Item bike = item("bike", true);
        Item brake = item("brake", false);
        ItemImage image = itemImageRepository.saveAndFlush(ItemImage.builder()
                .item(bike)
                .viewLabel("爆炸圖")
                .storageKey("https://cdn.example.com/" + fixtureId + "-" + positionLabel + ".png")
                .sourceType(SourceType.MANUAL)
                .reviewStatus(ReviewStatus.VERIFIED)
                .build());
        createdImageIds.add(image.getId());

        ItemHotspot hotspot = itemHotspotRepository.saveAndFlush(ItemHotspot.builder()
                .itemImage(image)
                .childItem(brake)
                .positionLabel(positionLabel)
                .polygon(List.of(new HotspotPoint(0.1, 0.1), new HotspotPoint(0.2, 0.1), new HotspotPoint(0.2, 0.2)))
                .sourceType(SourceType.MANUAL)
                .reviewStatus(ReviewStatus.VERIFIED)
                .reviewedBy("reviewer@profetai")
                .build());
        createdHotspotIds.add(hotspot.getId());
        return hotspot;
    }

    private Item item(String label, boolean endProduct) {
        String displayName = PostgresTestDatabase.FIXTURE_PREFIX + fixtureId + "-" + label
                + "-" + createdItemIds.size();
        Item saved = itemRepository.saveAndFlush(Item.builder()
                .normalizedName(NameNormalizer.normalize(displayName))
                .displayName(displayName)
                .isEndProduct(endProduct)
                .sourceType(SourceType.MANUAL)
                .reviewStatus(ReviewStatus.VERIFIED)
                .build());
        createdItemIds.add(saved.getId());
        return saved;
    }

    private HotspotPointPayload point(double x, double y) {
        return HotspotPointPayload.builder().x(x).y(y).build();
    }
}
