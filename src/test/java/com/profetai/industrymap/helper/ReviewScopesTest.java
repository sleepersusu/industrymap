package com.profetai.industrymap.helper;

import com.profetai.industrymap.enums.ReviewStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ReviewScopesTest {

    @Test
    @DisplayName("未指定納入草稿時查詢範圍應只有已驗證")
    void visibleStatuses_withoutDrafts_shouldContainVerifiedOnly() {
        assertEquals(Set.of(ReviewStatus.VERIFIED), ReviewScopes.visibleStatuses(false));
    }

    @Test
    @DisplayName("明確指定納入草稿時查詢範圍應含已驗證與草稿")
    void visibleStatuses_includingDrafts_shouldContainVerifiedAndDraft() {
        assertEquals(Set.of(ReviewStatus.VERIFIED, ReviewStatus.DRAFT), ReviewScopes.visibleStatuses(true));
    }

    @Test
    @DisplayName("已駁回的資料在任何查詢範圍下都不得外露")
    void visibleStatuses_anyScope_shouldNeverContainRejected() {
        assertAll(
                () -> assertFalse(ReviewScopes.visibleStatuses(false).contains(ReviewStatus.REJECTED)),
                () -> assertFalse(ReviewScopes.visibleStatuses(true).contains(ReviewStatus.REJECTED)),
                () -> assertFalse(ReviewScopes.visibleStatusNames(true).contains(ReviewStatus.REJECTED.name())));
    }

    @Test
    @DisplayName("native 查詢用的狀態名稱應與列舉名稱一致")
    void visibleStatusNames_shouldMatchEnumNames() {
        assertEquals(Set.of("VERIFIED", "DRAFT"), ReviewScopes.visibleStatusNames(true));
    }
}
