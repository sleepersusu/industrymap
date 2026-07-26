package com.profetai.industrymap.service.review;

import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.enums.SourceType;
import com.profetai.industrymap.exceptions.ServerException;
import com.profetai.industrymap.model.Item;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewServiceTest {

    private final ReviewService reviewService = new ReviewService();

    @Test
    @DisplayName("新建立的資料應預設為草稿且審核者與審核時間為空")
    void newEntity_withoutExplicitReviewStatus_shouldDefaultToDraft() {
        Item item = Item.builder()
                .normalizedName("pcb")
                .displayName("PCB")
                .sourceType(SourceType.MANUAL)
                .build();

        assertAll(
                () -> assertEquals(ReviewStatus.DRAFT, item.getReviewStatus()),
                () -> assertNull(item.getReviewedBy()),
                () -> assertNull(item.getReviewedAt()));
    }

    @Test
    @DisplayName("標記為已驗證時應記錄審核者與審核時間")
    void applyReview_verifiedWithReviewer_shouldRecordReviewerAndTimestamp() {
        // Given
        Item item = draftItem();
        Instant beforeReview = Instant.now();

        // When
        Item reviewed = reviewService.applyReview(item, ReviewStatus.VERIFIED, "reviewer@profetai");

        // Then
        assertAll(
                () -> assertEquals(ReviewStatus.VERIFIED, reviewed.getReviewStatus()),
                () -> assertEquals("reviewer@profetai", reviewed.getReviewedBy()),
                () -> assertNotNull(reviewed.getReviewedAt()),
                () -> assertTrue(!reviewed.getReviewedAt().isBefore(beforeReview)));
    }

    @Test
    @DisplayName("標記為已驗證但未提供審核者時應拋出 400 ServerException")
    void applyReview_verifiedWithoutReviewer_shouldThrowBadRequest() {
        Item item = draftItem();

        ServerException ex = assertThrows(ServerException.class,
                () -> reviewService.applyReview(item, ReviewStatus.VERIFIED, "  "));

        assertAll(
                () -> assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus()),
                () -> assertEquals(ReviewStatus.DRAFT, item.getReviewStatus()));
    }

    @Test
    @DisplayName("標記為已駁回時應保留資料並更新狀態")
    void applyReview_rejected_shouldKeepEntityAndUpdateStatus() {
        Item item = draftItem();

        Item reviewed = reviewService.applyReview(item, ReviewStatus.REJECTED, "reviewer@profetai");

        assertAll(
                () -> assertEquals(ReviewStatus.REJECTED, reviewed.getReviewStatus()),
                () -> assertEquals("PCB", reviewed.getDisplayName()),
                () -> assertNotNull(reviewed.getReviewedAt()));
    }

    @Test
    @DisplayName("退回草稿狀態時不需審核者，且應清空審核紀錄")
    void applyReview_backToDraft_shouldClearReviewRecord() {
        Item item = draftItem();
        reviewService.applyReview(item, ReviewStatus.VERIFIED, "reviewer@profetai");

        Item reverted = reviewService.applyReview(item, ReviewStatus.DRAFT, null);

        assertAll(
                () -> assertEquals(ReviewStatus.DRAFT, reverted.getReviewStatus()),
                () -> assertNull(reverted.getReviewedBy()),
                () -> assertNull(reverted.getReviewedAt()));
    }

    @Test
    @DisplayName("未提供目標審核狀態時應拋出 400 ServerException")
    void applyReview_nullTargetStatus_shouldThrowBadRequest() {
        Item item = draftItem();

        ServerException ex = assertThrows(ServerException.class,
                () -> reviewService.applyReview(item, null, "reviewer@profetai"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
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
