package com.profetai.industrymap.helper;

import com.profetai.industrymap.enums.SourceType;
import com.profetai.industrymap.exceptions.ServerException;
import com.profetai.industrymap.payloads.ProvenanceRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 來源欄位的共用驗證。API 邊界已有 {@code @Valid} 擋一次，
 * 這裡再擋一次是為了讓非 HTTP 進入點（未來的批次匯入）套用同一組規則。
 */
class ProvenanceValidatorTest {

    @Test
    @DisplayName("未提供來源資訊時應拋出 400 ServerException")
    void validate_nullProvenance_shouldThrowBadRequest() {
        ServerException ex = assertThrows(ServerException.class, () -> ProvenanceValidator.validate(null));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }

    @Test
    @DisplayName("未提供來源類型時應拋出 400 ServerException")
    void validate_missingSourceType_shouldThrowBadRequest() {
        ProvenanceRequest request = ProvenanceRequest.builder().sourceDetail("某份報告").build();

        ServerException ex = assertThrows(ServerException.class, () -> ProvenanceValidator.validate(request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }

    @Test
    @DisplayName("AI 生成的資料未帶信心度時應拋出 400 ServerException")
    void validate_aiGeneratedWithoutConfidence_shouldThrowBadRequest() {
        ProvenanceRequest request = ProvenanceRequest.builder().sourceType(SourceType.AI_GENERATED).build();

        ServerException ex = assertThrows(ServerException.class, () -> ProvenanceValidator.validate(request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }

    @Test
    @DisplayName("人工建立的資料未帶信心度時應通過驗證")
    void validate_manualWithoutConfidence_shouldPass() {
        ProvenanceRequest request = ProvenanceRequest.builder().sourceType(SourceType.MANUAL).build();

        assertDoesNotThrow(() -> ProvenanceValidator.validate(request));
    }

    @Test
    @DisplayName("信心度超出 0–1 時應拋出 400 ServerException")
    void validate_confidenceOutOfRange_shouldThrowBadRequest() {
        ProvenanceRequest request = ProvenanceRequest.builder()
                .sourceType(SourceType.AI_GENERATED)
                .confidence(new BigDecimal("1.5"))
                .build();

        ServerException ex = assertThrows(ServerException.class, () -> ProvenanceValidator.validate(request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }
}
