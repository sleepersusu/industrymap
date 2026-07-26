package com.profetai.industrymap.service.review;

import com.profetai.industrymap.enums.IdentifierType;
import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.enums.ReviewTargetType;
import com.profetai.industrymap.enums.SourceType;
import com.profetai.industrymap.exceptions.ServerException;
import com.profetai.industrymap.model.CompanyIdentifier;
import com.profetai.industrymap.model.Item;
import com.profetai.industrymap.model.ProvenanceEntity;
import com.profetai.industrymap.payloads.review.ReviewResultResponse;
import com.profetai.industrymap.payloads.review.ReviewTargetKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewApplyServiceTest {

    @Mock
    private ReviewLookupService reviewLookupService;

    @Mock
    private NaturalKeyResolver naturalKeyResolver;

    /** 審核邏輯沿用既有的 ReviewService，不重寫；因此這裡用真實實例而非 mock */
    private final ReviewService reviewService = new ReviewService();

    private ReviewApplyService reviewApplyService() {
        return new ReviewApplyService(reviewLookupService, reviewService, naturalKeyResolver);
    }

    @Test
    @DisplayName("草稿轉已驗證應更新狀態並記錄審核者與審核時間")
    void apply_draftToVerified_shouldRecordReviewerAndTimestamp() {
        // Given
        Item pcb = draftItem();
        when(reviewLookupService.getTarget(ReviewTargetType.ITEM, 1L)).thenReturn(pcb);
        when(reviewLookupService.save(eq(ReviewTargetType.ITEM), any(ProvenanceEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));

        // When
        ReviewResultResponse result =
                reviewApplyService().apply(ReviewTargetType.ITEM, 1L, ReviewStatus.VERIFIED, "reviewer@profetai");

        // Then
        assertAll(
                () -> assertTrue(result.isSuccess()),
                () -> assertEquals(ReviewStatus.VERIFIED, result.getReviewStatus()),
                () -> assertEquals("reviewer@profetai", result.getReviewedBy()),
                () -> assertNotNull(result.getReviewedAt()),
                () -> assertEquals(ReviewTargetType.ITEM, result.getTargetType()),
                () -> assertEquals(1L, result.getTargetId()));
    }

    @Test
    @DisplayName("已驗證資料退回草稿應清空審核者與審核時間")
    void apply_backToDraft_shouldClearReviewRecord() {
        Item pcb = draftItem();
        reviewService.applyReview(pcb, ReviewStatus.VERIFIED, "reviewer@profetai");
        when(reviewLookupService.getTarget(ReviewTargetType.ITEM, 1L)).thenReturn(pcb);
        when(reviewLookupService.save(eq(ReviewTargetType.ITEM), any(ProvenanceEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));

        ReviewResultResponse result =
                reviewApplyService().apply(ReviewTargetType.ITEM, 1L, ReviewStatus.DRAFT, null);

        assertAll(
                () -> assertEquals(ReviewStatus.DRAFT, result.getReviewStatus()),
                () -> assertNull(result.getReviewedBy()),
                () -> assertNull(result.getReviewedAt()));
    }

    @Test
    @DisplayName("標記已駁回應寫回實體狀態並記錄審核者與審核時間")
    void apply_rejected_shouldPersistRejectedStatusOnEntity() {
        // 「已駁回不外露」是各讀取路徑的責任，驗證在對應的查詢測試中；
        // 這裡只驗證本 service 真的把狀態寫進了實體
        Item pcb = draftItem();
        when(reviewLookupService.getTarget(ReviewTargetType.ITEM, 1L)).thenReturn(pcb);
        when(reviewLookupService.save(eq(ReviewTargetType.ITEM), any(ProvenanceEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));

        ReviewResultResponse result =
                reviewApplyService().apply(ReviewTargetType.ITEM, 1L, ReviewStatus.REJECTED, "reviewer@profetai");

        assertAll(
                () -> assertEquals(ReviewStatus.REJECTED, result.getReviewStatus()),
                () -> assertEquals(ReviewStatus.REJECTED, pcb.getReviewStatus()),
                () -> assertEquals("reviewer@profetai", pcb.getReviewedBy()),
                () -> assertNotNull(pcb.getReviewedAt()));
    }

    @Test
    @DisplayName("目標識別碼不存在時應以 404 中止且不寫入")
    void apply_unknownTarget_shouldThrowNotFoundWithoutSaving() {
        when(reviewLookupService.getTarget(ReviewTargetType.ITEM, 404L))
                .thenThrow(new ServerException("查無此審核目標", HttpStatus.NOT_FOUND));

        ServerException ex = assertThrows(ServerException.class, () -> reviewApplyService()
                .apply(ReviewTargetType.ITEM, 404L, ReviewStatus.VERIFIED, "reviewer@profetai"));

        assertAll(
                () -> assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus()),
                () -> verify(reviewLookupService, never()).save(any(), any()));
    }

    @Test
    @DisplayName("轉為已驗證卻未提供審核者時應以 400 中止且不寫入")
    void apply_verifiedWithoutReviewer_shouldThrowBadRequestWithoutSaving() {
        when(reviewLookupService.getTarget(ReviewTargetType.ITEM, 1L)).thenReturn(draftItem());

        ServerException ex = assertThrows(ServerException.class,
                () -> reviewApplyService().apply(ReviewTargetType.ITEM, 1L, ReviewStatus.VERIFIED, "  "));

        assertAll(
                () -> assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus()),
                () -> verify(reviewLookupService, never()).save(any(), any()));
    }

    @Test
    @DisplayName("以自然鍵審核公司識別碼應成功，全程不需要內部 id")
    void apply_byNaturalKey_shouldReviewWithoutKnowingInternalId() {
        // Given：公司識別碼的查詢回應完全不含 id，呼叫端只拿得到類型與代號值（design D1 的 F1）
        ReviewTargetKey key = ReviewTargetKey.builder()
                .identifierType(IdentifierType.TWSE).identifierValue("2330").build();
        CompanyIdentifier identifier = CompanyIdentifier.builder()
                .id(11L).identifierType(IdentifierType.TWSE).identifierValue("2330")
                .sourceType(SourceType.MANUAL).build();
        when(naturalKeyResolver.resolveId(ReviewTargetType.COMPANY_IDENTIFIER, key)).thenReturn(11L);
        when(reviewLookupService.getTarget(ReviewTargetType.COMPANY_IDENTIFIER, 11L)).thenReturn(identifier);
        when(reviewLookupService.save(eq(ReviewTargetType.COMPANY_IDENTIFIER), any(ProvenanceEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));

        // When
        ReviewResultResponse result = reviewApplyService().apply(ReviewTargetType.COMPANY_IDENTIFIER, null, key,
                ReviewStatus.VERIFIED, "reviewer@profetai");

        // Then
        assertAll(
                () -> assertTrue(result.isSuccess()),
                () -> assertEquals(ReviewStatus.VERIFIED, result.getReviewStatus()),
                () -> assertEquals(11L, result.getTargetId()));
    }

    @Test
    @DisplayName("同時提供內部 id 與自然鍵時應以 id 為準且不解析自然鍵")
    void apply_bothIdAndNaturalKey_shouldPreferIdWithoutResolving() {
        // Given
        Item pcb = draftItem();
        ReviewTargetKey key = ReviewTargetKey.builder().name("完全不同的節點").build();
        when(reviewLookupService.getTarget(ReviewTargetType.ITEM, 1L)).thenReturn(pcb);
        when(reviewLookupService.save(eq(ReviewTargetType.ITEM), any(ProvenanceEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));

        // When
        ReviewResultResponse result = reviewApplyService()
                .apply(ReviewTargetType.ITEM, 1L, key, ReviewStatus.VERIFIED, "reviewer@profetai");

        // Then：不回錯誤，也不該再去解析自然鍵
        assertAll(
                () -> assertTrue(result.isSuccess()),
                () -> assertEquals(1L, result.getTargetId()),
                () -> verify(naturalKeyResolver, never()).resolveId(any(), any()));
    }

    @Test
    @DisplayName("內部 id 與自然鍵皆未提供時應以 400 中止且不寫入")
    void apply_neitherIdNorNaturalKey_shouldThrowBadRequestWithoutSaving() {
        ServerException ex = assertThrows(ServerException.class, () -> reviewApplyService()
                .apply(ReviewTargetType.ITEM, null, null, ReviewStatus.VERIFIED, "reviewer@profetai"));

        assertAll(
                () -> assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus()),
                () -> verify(reviewLookupService, never()).save(any(), any()));
    }

    private Item draftItem() {
        return Item.builder()
                .id(1L)
                .normalizedName("pcb")
                .displayName("PCB")
                .sourceType(SourceType.MANUAL)
                .build();
    }
}
