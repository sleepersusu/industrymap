package com.profetai.industrymap.util;

import com.profetai.industrymap.exceptions.ServerException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 名稱正規化是節點共用（D3）與別名去重（D9）的地基，
 * 這裡的每個案例都直接對應「同義異名不得產生第二個節點」的需求。
 */
class NameNormalizerTest {

    @Test
    @DisplayName("大小寫、空白、全形差異的同一名稱應正規化為同一字串")
    void normalize_sameNameWithCaseWhitespaceAndFullWidthVariants_shouldProduceIdenticalKey() {
        // Given
        String halfWidthMixedCase = "WiFi模組";
        String lowerCaseWithSpace = "wifi 模組";
        String fullWidth = "ＷｉＦｉ模組";

        // When
        String normalizedA = NameNormalizer.normalize(halfWidthMixedCase);
        String normalizedB = NameNormalizer.normalize(lowerCaseWithSpace);
        String normalizedC = NameNormalizer.normalize(fullWidth);

        // Then
        assertAll(
                () -> assertEquals(normalizedA, normalizedB),
                () -> assertEquals(normalizedB, normalizedC),
                () -> assertEquals("wifi模組", normalizedA));
    }

    @Test
    @DisplayName("前後空白與連續空白應被去除")
    void normalize_surroundingAndRepeatedWhitespace_shouldBeRemoved() {
        assertEquals("wifi模組", NameNormalizer.normalize("  WiFi   模組  "));
    }

    @Test
    @DisplayName("標點與符號差異不應造成不同的正規化結果")
    void normalize_punctuationVariants_shouldProduceIdenticalKey() {
        // Given：連字號、點號、全形括號都是同一個品類的常見寫法差異
        String withHyphen = "Wi-Fi 模組";
        String withDot = "Wi.Fi．模組";

        // When / Then
        assertEquals(NameNormalizer.normalize(withHyphen), NameNormalizer.normalize(withDot));
    }

    @Test
    @DisplayName("全形數字與英數應轉為半形")
    void normalize_fullWidthAlphanumeric_shouldBecomeHalfWidth() {
        assertEquals("pcb2330", NameNormalizer.normalize("ＰＣＢ２３３０"));
    }

    @Test
    @DisplayName("null 名稱應拋出 400 ServerException")
    void normalize_nullInput_shouldThrowBadRequest() {
        ServerException ex = assertThrows(ServerException.class, () -> NameNormalizer.normalize(null));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }

    @Test
    @DisplayName("正規化後為空字串的名稱應拋出 400 ServerException")
    void normalize_blankAfterNormalization_shouldThrowBadRequest() {
        ServerException ex = assertThrows(ServerException.class, () -> NameNormalizer.normalize(" -- "));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }
}
