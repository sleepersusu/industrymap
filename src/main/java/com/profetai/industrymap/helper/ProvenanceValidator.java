package com.profetai.industrymap.helper;

import com.profetai.industrymap.enums.SourceType;
import com.profetai.industrymap.exceptions.ServerException;
import com.profetai.industrymap.payloads.ProvenanceRequest;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

/**
 * 來源欄位的共用驗證（design D8）。
 *
 * <p>{@link ProvenanceRequest} 上的 jakarta validation annotation 只在 {@code @Valid} 的路徑生效；
 * 本類別讓 service 層在任何進入點都套用同一組規則——資料以 AI 生成初稿進來，
 * 沒有來源與信心度的數字沒有價值。</p>
 */
public final class ProvenanceValidator {

    private static final BigDecimal MIN_CONFIDENCE = BigDecimal.ZERO;
    private static final BigDecimal MAX_CONFIDENCE = BigDecimal.ONE;

    private ProvenanceValidator() {
    }

    /**
     * @throws ServerException 缺少來源資訊或來源類型、AI 生成未帶信心度、信心度超出 0–1（皆為 400）
     */
    public static void validate(ProvenanceRequest provenance) {
        if (provenance == null) {
            throw new ServerException("必須提供來源資訊", HttpStatus.BAD_REQUEST);
        }
        if (provenance.getSourceType() == null) {
            throw new ServerException("必須提供來源類型", HttpStatus.BAD_REQUEST);
        }

        BigDecimal confidence = provenance.getConfidence();
        if (provenance.getSourceType() == SourceType.AI_GENERATED && confidence == null) {
            throw new ServerException("來源類型為 AI 生成時必須提供信心度", HttpStatus.BAD_REQUEST);
        }
        if (confidence != null
                && (confidence.compareTo(MIN_CONFIDENCE) < 0 || confidence.compareTo(MAX_CONFIDENCE) > 0)) {
            throw new ServerException("信心度必須介於 0 至 1 之間", HttpStatus.BAD_REQUEST);
        }
    }
}
