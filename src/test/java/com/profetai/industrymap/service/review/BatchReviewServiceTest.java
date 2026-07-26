package com.profetai.industrymap.service.review;

import com.profetai.industrymap.enums.IdentifierType;
import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.enums.ReviewTargetType;
import com.profetai.industrymap.exceptions.ServerException;
import com.profetai.industrymap.payloads.review.BatchReviewRequest;
import com.profetai.industrymap.payloads.review.ReviewResultResponse;
import com.profetai.industrymap.payloads.review.ReviewTarget;
import com.profetai.industrymap.payloads.review.ReviewTargetKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchReviewServiceTest {

    private static final String REVIEWER = "reviewer@profetai";

    @Mock
    private ReviewApplyService reviewApplyService;

    @InjectMocks
    private BatchReviewService batchReviewService;

    @Test
    @DisplayName("批次全部有效時應逐筆回報成功")
    void applyBatch_allValid_shouldReportEverySuccess() {
        // Given
        givenApplySucceeds(ReviewTargetType.ITEM, 1L);
        givenApplySucceeds(ReviewTargetType.ITEM, 2L);

        // When
        List<ReviewResultResponse> results = batchReviewService.applyBatch(batchRequest(
                ReviewTarget.builder().targetType(ReviewTargetType.ITEM).targetId(1L).build(),
                ReviewTarget.builder().targetType(ReviewTargetType.ITEM).targetId(2L).build()));

        // Then
        assertAll(
                () -> assertEquals(2, results.size()),
                () -> assertTrue(results.stream().allMatch(ReviewResultResponse::isSuccess)),
                () -> assertTrue(results.stream()
                        .allMatch(result -> result.getReviewStatus() == ReviewStatus.VERIFIED)));
    }

    @Test
    @DisplayName("批次中一筆查無目標時其餘仍完成審核，該筆標示失敗與原因")
    void applyBatch_oneTargetMissing_shouldReviewOthersAndReportFailure() {
        // Given：第二筆查無目標，第一與第三筆有效
        givenApplySucceeds(ReviewTargetType.ITEM, 1L);
        when(reviewApplyService.apply(ReviewTargetType.ITEM, 404L, null, ReviewStatus.VERIFIED, REVIEWER))
                .thenThrow(new ServerException("查無此審核目標：ITEM 404", HttpStatus.NOT_FOUND));
        givenApplySucceeds(ReviewTargetType.MARKET_SHARE, 3L);

        // When
        List<ReviewResultResponse> results = batchReviewService.applyBatch(batchRequest(
                ReviewTarget.builder().targetType(ReviewTargetType.ITEM).targetId(1L).build(),
                ReviewTarget.builder().targetType(ReviewTargetType.ITEM).targetId(404L).build(),
                ReviewTarget.builder().targetType(ReviewTargetType.MARKET_SHARE).targetId(3L).build()));

        // Then：失敗的一筆不影響其餘兩筆（design D2，未整批 rollback）
        assertAll(
                () -> assertEquals(3, results.size()),
                () -> assertTrue(results.get(0).isSuccess()),
                () -> assertFalse(results.get(1).isSuccess()),
                () -> assertNotNull(results.get(1).getMessage()),
                () -> assertEquals(404L, results.get(1).getTargetId()),
                () -> assertTrue(results.get(2).isSuccess()),
                () -> verify(reviewApplyService)
                        .apply(ReviewTargetType.MARKET_SHARE, 3L, null, ReviewStatus.VERIFIED, REVIEWER));
    }

    @Test
    @DisplayName("批次可跨不同目標類型，各筆分別套用審核")
    void applyBatch_mixedTargetTypes_shouldReviewEachType() {
        givenApplySucceeds(ReviewTargetType.ITEM_COMPOSITION, 5L);
        givenApplySucceeds(ReviewTargetType.COMPANY_ITEM_ROLE, 6L);
        givenApplySucceeds(ReviewTargetType.MARKET_SHARE, 7L);

        List<ReviewResultResponse> results = batchReviewService.applyBatch(batchRequest(
                ReviewTarget.builder().targetType(ReviewTargetType.ITEM_COMPOSITION).targetId(5L).build(),
                ReviewTarget.builder().targetType(ReviewTargetType.COMPANY_ITEM_ROLE).targetId(6L).build(),
                ReviewTarget.builder().targetType(ReviewTargetType.MARKET_SHARE).targetId(7L).build()));

        assertAll(
                () -> assertEquals(3, results.size()),
                () -> assertTrue(results.stream().allMatch(ReviewResultResponse::isSuccess)),
                () -> verify(reviewApplyService)
                        .apply(ReviewTargetType.ITEM_COMPOSITION, 5L, null, ReviewStatus.VERIFIED, REVIEWER),
                () -> verify(reviewApplyService)
                        .apply(ReviewTargetType.COMPANY_ITEM_ROLE, 6L, null, ReviewStatus.VERIFIED, REVIEWER),
                () -> verify(reviewApplyService)
                        .apply(ReviewTargetType.MARKET_SHARE, 7L, null, ReviewStatus.VERIFIED, REVIEWER));
    }

    @Test
    @DisplayName("單筆拋出非業務例外時不得中斷整批，其餘仍完成且該筆標示失敗")
    void applyBatch_unexpectedException_shouldNotAbortRemainingTargets() {
        // Given：commit 期的鎖競爭等失敗拋的不是 ServerException，
        // 若只攔 ServerException 會讓其餘項目全部不處理，呼叫端也拿不到逐筆結果
        givenApplySucceeds(ReviewTargetType.ITEM, 1L);
        when(reviewApplyService.apply(ReviewTargetType.ITEM, 2L, null, ReviewStatus.VERIFIED, REVIEWER))
                .thenThrow(new CannotAcquireLockException("could not obtain lock on row"));
        givenApplySucceeds(ReviewTargetType.MARKET_SHARE, 3L);

        // When
        List<ReviewResultResponse> results = batchReviewService.applyBatch(batchRequest(
                ReviewTarget.builder().targetType(ReviewTargetType.ITEM).targetId(1L).build(),
                ReviewTarget.builder().targetType(ReviewTargetType.ITEM).targetId(2L).build(),
                ReviewTarget.builder().targetType(ReviewTargetType.MARKET_SHARE).targetId(3L).build()));

        // Then
        assertAll(
                () -> assertEquals(3, results.size()),
                () -> assertTrue(results.get(0).isSuccess()),
                () -> assertFalse(results.get(1).isSuccess()),
                () -> assertNotNull(results.get(1).getMessage()),
                () -> assertTrue(results.get(2).isSuccess()),
                () -> verify(reviewApplyService)
                        .apply(ReviewTargetType.MARKET_SHARE, 3L, null, ReviewStatus.VERIFIED, REVIEWER));
    }

    @Test
    @DisplayName("批次中混用內部 id 與自然鍵時應逐筆各自解析")
    void applyBatch_mixedLocators_shouldResolveEachTargetIndependently() {
        // Given：節點用 id、公司識別碼只拿得到自然鍵（該類型的查詢回應不含 id）
        ReviewTargetKey identifierKey = ReviewTargetKey.builder()
                .identifierType(IdentifierType.TWSE).identifierValue("2330").build();
        givenApplySucceeds(ReviewTargetType.ITEM, 1L, null);
        givenApplySucceeds(ReviewTargetType.COMPANY_IDENTIFIER, null, identifierKey);

        // When
        List<ReviewResultResponse> results = batchReviewService.applyBatch(batchRequest(
                ReviewTarget.builder().targetType(ReviewTargetType.ITEM).targetId(1L).build(),
                ReviewTarget.builder().targetType(ReviewTargetType.COMPANY_IDENTIFIER)
                        .naturalKey(identifierKey).build()));

        // Then
        assertAll(
                () -> assertEquals(2, results.size()),
                () -> assertTrue(results.stream().allMatch(ReviewResultResponse::isSuccess)),
                () -> verify(reviewApplyService).apply(ReviewTargetType.ITEM, 1L, null,
                        ReviewStatus.VERIFIED, REVIEWER),
                () -> verify(reviewApplyService).apply(ReviewTargetType.COMPANY_IDENTIFIER, null, identifierKey,
                        ReviewStatus.VERIFIED, REVIEWER));
    }

    @Test
    @DisplayName("以自然鍵定位的項目失敗時，回應應帶回該筆的自然鍵供呼叫端辨識")
    void applyBatch_naturalKeyTargetFails_shouldEchoNaturalKeyInFailure() {
        // Given：同一批同型別的多筆自然鍵目標，失敗項目若只回 targetType 與 null 的 targetId，
        // 兩筆失敗長得一模一樣，呼叫端只能靠陣列位置回推是哪個識別碼打錯
        ReviewTargetKey valid = ReviewTargetKey.builder()
                .identifierType(IdentifierType.TWSE).identifierValue("2330").build();
        ReviewTargetKey unknown = ReviewTargetKey.builder()
                .identifierType(IdentifierType.TWSE).identifierValue("9999").build();
        givenApplySucceeds(ReviewTargetType.COMPANY_IDENTIFIER, null, valid);
        when(reviewApplyService.apply(ReviewTargetType.COMPANY_IDENTIFIER, null, unknown,
                ReviewStatus.VERIFIED, REVIEWER))
                .thenThrow(new ServerException("查無此審核目標", HttpStatus.NOT_FOUND));

        // When
        List<ReviewResultResponse> results = batchReviewService.applyBatch(batchRequest(
                ReviewTarget.builder().targetType(ReviewTargetType.COMPANY_IDENTIFIER).naturalKey(valid).build(),
                ReviewTarget.builder().targetType(ReviewTargetType.COMPANY_IDENTIFIER).naturalKey(unknown).build()));

        // Then
        assertAll(
                () -> assertTrue(results.get(0).isSuccess()),
                () -> assertFalse(results.get(1).isSuccess()),
                () -> assertEquals(unknown, results.get(1).getNaturalKey()),
                () -> assertEquals("9999", results.get(1).getNaturalKey().getIdentifierValue()));
    }

    private void givenApplySucceeds(ReviewTargetType targetType, Long targetId) {
        givenApplySucceeds(targetType, targetId, null);
    }

    private void givenApplySucceeds(ReviewTargetType targetType, Long targetId, ReviewTargetKey naturalKey) {
        when(reviewApplyService.apply(targetType, targetId, naturalKey, ReviewStatus.VERIFIED, REVIEWER))
                .thenReturn(ReviewResultResponse.builder()
                        .targetType(targetType)
                        .targetId(targetId)
                        .success(true)
                        .reviewStatus(ReviewStatus.VERIFIED)
                        .reviewedBy(REVIEWER)
                        .build());
    }

    private BatchReviewRequest batchRequest(ReviewTarget... targets) {
        return BatchReviewRequest.builder()
                .targets(List.of(targets))
                .targetStatus(ReviewStatus.VERIFIED)
                .reviewer(REVIEWER)
                .build();
    }
}
