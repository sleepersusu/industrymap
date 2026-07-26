package com.profetai.industrymap.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.profetai.industrymap.enums.IdentifierType;
import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.enums.ReviewTargetType;
import com.profetai.industrymap.exceptions.ServerException;
import com.profetai.industrymap.payloads.review.BatchReviewRequest;
import com.profetai.industrymap.payloads.review.ReviewRequest;
import com.profetai.industrymap.payloads.review.ReviewResultResponse;
import com.profetai.industrymap.payloads.review.ReviewTarget;
import com.profetai.industrymap.payloads.review.ReviewTargetKey;
import com.profetai.industrymap.service.review.BatchReviewService;
import com.profetai.industrymap.service.review.ReviewApplyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@WebMvcTest(ReviewController.class)
class ReviewControllerTest {

    private static final String REVIEWER = "reviewer@profetai";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ReviewApplyService reviewApplyService;

    @MockitoBean
    private BatchReviewService batchReviewService;

    @Test
    @DisplayName("草稿轉已驗證應回傳更新後的審核狀態與審核者")
    void review_draftToVerified_shouldReturnUpdatedStatus() throws Exception {
        when(reviewApplyService.apply(ReviewTargetType.ITEM, 1L, null, ReviewStatus.VERIFIED, REVIEWER))
                .thenReturn(ReviewResultResponse.builder()
                        .targetType(ReviewTargetType.ITEM).targetId(1L).success(true)
                        .reviewStatus(ReviewStatus.VERIFIED).reviewedBy(REVIEWER).reviewedAt(Instant.now())
                        .build());

        mockMvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewRequest(ReviewStatus.VERIFIED, REVIEWER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.data.reviewedBy").value(REVIEWER))
                .andExpect(jsonPath("$.data.success").value(true));
    }

    @Test
    @DisplayName("轉為已驗證卻未提供審核者時應回傳 400 且不呼叫審核")
    void review_verifiedWithoutReviewer_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewRequest(ReviewStatus.VERIFIED, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(reviewApplyService, never()).apply(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("退回草稿不需審核者，應正常受理")
    void review_backToDraftWithoutReviewer_shouldSucceed() throws Exception {
        when(reviewApplyService.apply(ReviewTargetType.ITEM, 1L, null, ReviewStatus.DRAFT, null))
                .thenReturn(ReviewResultResponse.builder()
                        .targetType(ReviewTargetType.ITEM).targetId(1L).success(true)
                        .reviewStatus(ReviewStatus.DRAFT)
                        .build());

        mockMvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewRequest(ReviewStatus.DRAFT, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("DRAFT"))
                .andExpect(jsonPath("$.data.reviewedBy").doesNotExist());
    }

    @Test
    @DisplayName("指定不支援的資料類型時應回傳 400 而非 500")
    void review_unsupportedTargetType_shouldReturnBadRequest() throws Exception {
        String bodyWithUnsupportedType = """
                {"targetType":"PATENT","targetId":1,"targetStatus":"VERIFIED","reviewer":"%s"}
                """.formatted(REVIEWER);

        mockMvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithUnsupportedType))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(reviewApplyService, never()).apply(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("目標識別碼不存在時應回傳 404")
    void review_unknownTargetId_shouldReturnNotFound() throws Exception {
        when(reviewApplyService.apply(ReviewTargetType.ITEM, 1L, null, ReviewStatus.VERIFIED, REVIEWER))
                .thenThrow(new ServerException("查無此審核目標：ITEM 1", HttpStatus.NOT_FOUND));

        mockMvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewRequest(ReviewStatus.VERIFIED, REVIEWER))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("批次審核應逐筆回報結果")
    void reviewBatch_allValid_shouldReportEachResult() throws Exception {
        when(batchReviewService.applyBatch(any(BatchReviewRequest.class))).thenReturn(List.of(
                ReviewResultResponse.builder().targetType(ReviewTargetType.ITEM).targetId(1L)
                        .success(true).reviewStatus(ReviewStatus.VERIFIED).reviewedBy(REVIEWER).build(),
                ReviewResultResponse.builder().targetType(ReviewTargetType.MARKET_SHARE).targetId(2L)
                        .success(false).message("查無此審核目標：MARKET_SHARE 2").build()));

        BatchReviewRequest request = BatchReviewRequest.builder()
                .targets(List.of(
                        ReviewTarget.builder().targetType(ReviewTargetType.ITEM).targetId(1L).build(),
                        ReviewTarget.builder().targetType(ReviewTargetType.MARKET_SHARE).targetId(2L).build()))
                .targetStatus(ReviewStatus.VERIFIED)
                .reviewer(REVIEWER)
                .build();

        mockMvc.perform(post("/api/reviews/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].success").value(true))
                .andExpect(jsonPath("$.data[1].success").value(false))
                .andExpect(jsonPath("$.data[1].message").value("查無此審核目標：MARKET_SHARE 2"));
    }

    @Test
    @DisplayName("空批次應回傳 400 且不呼叫審核")
    void reviewBatch_emptyTargets_shouldReturnBadRequest() throws Exception {
        BatchReviewRequest request = BatchReviewRequest.builder()
                .targets(List.of())
                .targetStatus(ReviewStatus.VERIFIED)
                .reviewer(REVIEWER)
                .build();

        mockMvc.perform(post("/api/reviews/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(batchReviewService, never()).applyBatch(any(BatchReviewRequest.class));
    }

    @Test
    @DisplayName("以自然鍵指定目標時應受理並轉交解析")
    void review_byNaturalKey_shouldAcceptWithoutTargetId() throws Exception {
        ReviewTargetKey key = ReviewTargetKey.builder()
                .identifierType(IdentifierType.TWSE).identifierValue("2330").build();
        when(reviewApplyService.apply(ReviewTargetType.COMPANY_IDENTIFIER, null, key,
                ReviewStatus.VERIFIED, REVIEWER))
                .thenReturn(ReviewResultResponse.builder()
                        .targetType(ReviewTargetType.COMPANY_IDENTIFIER).targetId(11L).success(true)
                        .reviewStatus(ReviewStatus.VERIFIED).reviewedBy(REVIEWER).reviewedAt(Instant.now())
                        .build());

        ReviewRequest request = ReviewRequest.builder()
                .targetType(ReviewTargetType.COMPANY_IDENTIFIER)
                .naturalKey(key)
                .targetStatus(ReviewStatus.VERIFIED)
                .reviewer(REVIEWER)
                .build();

        mockMvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.data.targetId").value(11));
    }

    @Test
    @DisplayName("內部 id 與自然鍵皆未提供時應回傳 400 且不呼叫審核")
    void review_withoutIdAndNaturalKey_shouldReturnBadRequest() throws Exception {
        ReviewRequest request = ReviewRequest.builder()
                .targetType(ReviewTargetType.ITEM)
                .targetStatus(ReviewStatus.VERIFIED)
                .reviewer(REVIEWER)
                .build();

        mockMvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(reviewApplyService, never()).apply(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("批次中某筆兩種定位方式皆未提供時應回傳 400 且不呼叫審核")
    void reviewBatch_targetWithoutLocator_shouldReturnBadRequest() throws Exception {
        BatchReviewRequest request = BatchReviewRequest.builder()
                .targets(List.of(ReviewTarget.builder().targetType(ReviewTargetType.ITEM).build()))
                .targetStatus(ReviewStatus.VERIFIED)
                .reviewer(REVIEWER)
                .build();

        mockMvc.perform(post("/api/reviews/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(batchReviewService, never()).applyBatch(any(BatchReviewRequest.class));
    }

    private ReviewRequest reviewRequest(ReviewStatus targetStatus, String reviewer) {
        return ReviewRequest.builder()
                .targetType(ReviewTargetType.ITEM)
                .targetId(1L)
                .targetStatus(targetStatus)
                .reviewer(reviewer)
                .build();
    }
}
