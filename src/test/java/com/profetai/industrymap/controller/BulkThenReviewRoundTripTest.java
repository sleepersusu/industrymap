package com.profetai.industrymap.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.profetai.industrymap.enums.CompanyRole;
import com.profetai.industrymap.enums.IdentifierType;
import com.profetai.industrymap.enums.Necessity;
import com.profetai.industrymap.enums.PeriodType;
import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.enums.ReviewTargetType;
import com.profetai.industrymap.enums.ShareMetric;
import com.profetai.industrymap.enums.SourceType;
import com.profetai.industrymap.payloads.ProvenanceRequest;
import com.profetai.industrymap.payloads.bulk.BatchCompositionItem;
import com.profetai.industrymap.payloads.bulk.BatchCreateRequest;
import com.profetai.industrymap.payloads.bulk.BatchIdentifierItem;
import com.profetai.industrymap.payloads.bulk.BatchItemImageItem;
import com.profetai.industrymap.payloads.company.CreateCompanyRequest;
import com.profetai.industrymap.payloads.item.CreateHotspotRequest;
import com.profetai.industrymap.payloads.item.CreateItemRequest;
import com.profetai.industrymap.payloads.item.HotspotPointPayload;
import com.profetai.industrymap.payloads.review.BatchReviewRequest;
import com.profetai.industrymap.payloads.review.ReviewTarget;
import com.profetai.industrymap.payloads.review.ReviewTargetKey;
import com.profetai.industrymap.payloads.supply.CreateCompanyItemRoleRequest;
import com.profetai.industrymap.payloads.supply.CreateMarketShareRequest;
import com.profetai.industrymap.repository.CompanyIdentifierRepository;
import com.profetai.industrymap.repository.CompanyItemRoleRepository;
import com.profetai.industrymap.repository.CompanyRepository;
import com.profetai.industrymap.repository.ItemCompositionRepository;
import com.profetai.industrymap.repository.ItemHotspotRepository;
import com.profetai.industrymap.repository.ItemImageRepository;
import com.profetai.industrymap.repository.ItemRepository;
import com.profetai.industrymap.repository.MarketShareRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 本 change 的關鍵驗收（spec「批次建立回應必須帶可直接審核的定位資訊」）：
 * 批次建立一整組資料後，把回應中的自然鍵原封不動組成批次審核請求，全部審核成功，
 * 過程中不查任何其他端點、也不碰資料庫。
 *
 * <p>同時驗證 F1：公司識別碼這一類的查詢回應完全不含 id，上一輪走查得直接查資料庫才審得掉；
 * 這裡刻意只送自然鍵、完全不送 targetId，證明它現在走得完 API 閉環。</p>
 *
 * <p>本測試不加 {@code @Transactional}：批次的「單筆失敗不影響其他項目」建立在每筆各自成為
 * 一個交易之上，把整個測試包進同一個交易會讓那個語意消失。因此改為在 {@code @AfterEach}
 * 逐筆清掉自己建立的資料，且 fixture 名稱帶隨機前綴，不與既有資料衝突。</p>
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class BulkThenReviewRoundTripTest {

    private static final String REVIEWER = "reviewer@profetai";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private ItemCompositionRepository itemCompositionRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CompanyIdentifierRepository companyIdentifierRepository;

    @Autowired
    private CompanyItemRoleRepository companyItemRoleRepository;

    @Autowired
    private MarketShareRepository marketShareRepository;

    @Autowired
    private ItemImageRepository itemImageRepository;

    @Autowired
    private ItemHotspotRepository itemHotspotRepository;

    /** 本次測試建立的資料，於 @AfterEach 依相依順序清除 */
    private final List<Created> created = new ArrayList<>();

    private final String fixtureId = UUID.randomUUID().toString().substring(0, 8);

    @AfterEach
    void cleanUp() {
        deleteAll(ReviewTargetType.ITEM_HOTSPOT, itemHotspotRepository::deleteById);
        deleteAll(ReviewTargetType.ITEM_IMAGE, itemImageRepository::deleteById);
        deleteAll(ReviewTargetType.MARKET_SHARE, marketShareRepository::deleteById);
        deleteAll(ReviewTargetType.COMPANY_ITEM_ROLE, companyItemRoleRepository::deleteById);
        deleteAll(ReviewTargetType.ITEM_COMPOSITION, itemCompositionRepository::deleteById);
        deleteAll(ReviewTargetType.COMPANY_IDENTIFIER, companyIdentifierRepository::deleteById);
        deleteAll(ReviewTargetType.ITEM, itemRepository::deleteById);
        deleteAll(ReviewTargetType.COMPANY, companyRepository::deleteById);
    }

    @Test
    @DisplayName("批次建立後直接以回應的自然鍵批次審核，應全部成功且不需任何額外查詢")
    void bulkCreateThenBatchReview_shouldSucceedUsingOnlyReturnedNaturalKeys() throws Exception {
        // Given：一次建好一台腳踏車所需的六類資料，全程只靠各批次端點的回應往下接
        List<JsonNode> itemResults = bulkCreate("/api/bulk/items", BatchCreateRequest.<CreateItemRequest>builder()
                .items(List.of(itemRequest("腳踏車", true), itemRequest("變速器", false)))
                .build());
        Long bicycleId = itemResults.get(0).get("targetId").asLong();
        Long derailleurId = itemResults.get(1).get("targetId").asLong();

        List<JsonNode> compositionResults = bulkCreate("/api/bulk/compositions",
                BatchCreateRequest.<BatchCompositionItem>builder()
                        .items(List.of(BatchCompositionItem.builder()
                                .parentItemId(bicycleId).childItemId(derailleurId)
                                .necessity(Necessity.STANDARD).provenance(manualProvenance()).build()))
                        .build());

        List<JsonNode> companyResults = bulkCreate("/api/bulk/companies",
                BatchCreateRequest.<CreateCompanyRequest>builder()
                        .items(List.of(CreateCompanyRequest.builder()
                                .displayName(fixtureName("禧瑪諾")).country("JP").publicCompany(true)
                                .provenance(manualProvenance()).build()))
                        .build());
        String companyName = companyResults.get(0).get("naturalKey").get("companyCode").asText();

        String identifierValue = "IT" + fixtureId;
        List<JsonNode> identifierResults = bulkCreate("/api/bulk/identifiers",
                BatchCreateRequest.<BatchIdentifierItem>builder()
                        .items(List.of(BatchIdentifierItem.builder()
                                .companyCode(companyName)
                                .identifierType(IdentifierType.OTHER).identifierValue(identifierValue)
                                .primary(true).provenance(manualProvenance()).build()))
                        .build());

        List<JsonNode> roleResults = bulkCreate("/api/bulk/supply-roles",
                BatchCreateRequest.<CreateCompanyItemRoleRequest>builder()
                        .items(List.of(CreateCompanyItemRoleRequest.builder()
                                .companyCode(companyName).itemId(derailleurId)
                                .companyRole(CompanyRole.MANUFACTURE).provenance(manualProvenance()).build()))
                        .build());

        List<JsonNode> shareResults = bulkCreate("/api/bulk/market-shares",
                BatchCreateRequest.<CreateMarketShareRequest>builder()
                        .items(List.of(CreateMarketShareRequest.builder()
                                .companyCode(companyName).itemId(derailleurId)
                                .periodType(PeriodType.YEAR).periodValue("2024").region("全球")
                                .metric(ShareMetric.REVENUE).sharePercent(new BigDecimal("70.5"))
                                .provenance(externalProvenance("市場報告 " + fixtureId)).build()))
                        .build());

        List<JsonNode> imageResults = bulkCreate("/api/bulk/item-images",
                BatchCreateRequest.<BatchItemImageItem>builder()
                        .items(List.of(BatchItemImageItem.builder()
                                .itemId(bicycleId).viewLabel("爆炸圖")
                                .storageKey("https://cdn.example.com/" + fixtureId + ".png")
                                .provenance(manualProvenance()).build()))
                        .build());
        Long imageId = imageResults.get(0).get("targetId").asLong();

        // 同一張圖上兩個位置標籤不同、卻指向同一個節點的熱區——組成關係仍是那唯一一筆（design D2）
        List<JsonNode> hotspotResults = bulkCreate("/api/bulk/item-hotspots",
                BatchCreateRequest.<CreateHotspotRequest>builder()
                        .items(List.of(hotspotItem(imageId, derailleurId, "前變速器"),
                                hotspotItem(imageId, derailleurId, "後變速器")))
                        .build());

        List<JsonNode> allResults = new ArrayList<>();
        allResults.addAll(itemResults);
        allResults.addAll(compositionResults);
        allResults.addAll(companyResults);
        allResults.addAll(identifierResults);
        allResults.addAll(roleResults);
        allResults.addAll(shareResults);
        allResults.addAll(imageResults);
        allResults.addAll(hotspotResults);

        // When：把回應轉成批次審核請求，刻意只帶自然鍵、不帶 targetId
        BatchReviewRequest reviewRequest = BatchReviewRequest.builder()
                .targets(allResults.stream().map(this::toNaturalKeyTarget).toList())
                .targetStatus(ReviewStatus.VERIFIED)
                .reviewer(REVIEWER)
                .build();

        String reviewBody = mockMvc.perform(post("/api/reviews/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode reviewResults = objectMapper.readTree(reviewBody).get("data");

        // Then：所有資料全部審核完成，其中包含上一輪走查審不掉的公司識別碼，
        // 以及只能靠「節點 + 視角標籤 + 位置標籤」定位的兩筆熱區
        assertAll(
                () -> assertEquals(allResults.size(), reviewResults.size()),
                () -> assertTrue(allResults(reviewResults, result -> result.get("success").asBoolean()),
                        reviewBody),
                () -> assertTrue(allResults(reviewResults,
                        result -> "VERIFIED".equals(result.get("reviewStatus").asText())), reviewBody),
                () -> assertEquals(ReviewStatus.VERIFIED, companyIdentifierRepository
                        .findByIdentifierTypeAndIdentifierValue(IdentifierType.OTHER, identifierValue)
                        .orElseThrow().getReviewStatus()));
    }

    /** 只保留類型與自然鍵——證明審核不需要呼叫端知道任何內部 id */
    private ReviewTarget toNaturalKeyTarget(JsonNode result) {
        try {
            return ReviewTarget.builder()
                    .targetType(ReviewTargetType.valueOf(result.get("targetType").asText()))
                    .naturalKey(objectMapper.treeToValue(result.get("naturalKey"), ReviewTargetKey.class))
                    .build();
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("批次建立回應的自然鍵格式無法解析", ex);
        }
    }

    private List<JsonNode> bulkCreate(String path, BatchCreateRequest<?> request) throws Exception {
        String body = mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<JsonNode> results = new ArrayList<>();
        for (JsonNode result : objectMapper.readTree(body).get("data")) {
            assertTrue(result.get("success").asBoolean(), body);
            results.add(result);
            created.add(new Created(ReviewTargetType.valueOf(result.get("targetType").asText()),
                    result.get("targetId").asLong()));
        }
        return results;
    }

    private boolean allResults(JsonNode results, java.util.function.Predicate<JsonNode> predicate) {
        for (JsonNode result : results) {
            if (!predicate.test(result)) {
                return false;
            }
        }
        return true;
    }

    private void deleteAll(ReviewTargetType targetType, java.util.function.Consumer<Long> deleteById) {
        created.stream()
                .filter(entry -> entry.targetType() == targetType)
                .forEach(entry -> deleteById.accept(entry.targetId()));
    }

    private CreateHotspotRequest hotspotItem(Long imageId, Long childItemId, String positionLabel) {
        return CreateHotspotRequest.builder()
                .itemImageId(imageId)
                .childItemId(childItemId)
                .positionLabel(positionLabel)
                .polygon(List.of(HotspotPointPayload.builder().x(0.1).y(0.1).build(),
                        HotspotPointPayload.builder().x(0.2).y(0.1).build(),
                        HotspotPointPayload.builder().x(0.2).y(0.2).build()))
                .provenance(manualProvenance())
                .build();
    }

    private CreateItemRequest itemRequest(String displayName, boolean endProduct) {
        return CreateItemRequest.builder()
                .displayName(fixtureName(displayName))
                .endProduct(endProduct)
                .provenance(manualProvenance())
                .build();
    }

    /** 正規化名稱是全域唯一鍵，fixture 名稱必須不可能與開發資料庫的既有資料重疊 */
    private String fixtureName(String base) {
        return "it-" + fixtureId + "-" + base;
    }

    private ProvenanceRequest manualProvenance() {
        return ProvenanceRequest.builder().sourceType(SourceType.MANUAL).build();
    }

    private ProvenanceRequest externalProvenance(String sourceDetail) {
        return ProvenanceRequest.builder().sourceType(SourceType.EXTERNAL).sourceDetail(sourceDetail).build();
    }

    private record Created(ReviewTargetType targetType, Long targetId) {
    }
}
