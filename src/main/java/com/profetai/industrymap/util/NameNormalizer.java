package com.profetai.industrymap.util;

import com.profetai.industrymap.exceptions.ServerException;
import org.springframework.http.HttpStatus;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 名稱正規化工具：把同一個品類／公司的各種寫法收斂成唯一的比對鍵。
 *
 * <p>節點共用（design D3）與別名去重（design D9）的前提是「同一個東西全站只有一個節點」，
 * 而 AI 生成資料最容易以大小寫、全半形、空白、標點的差異破壞這件事。此工具產出的字串
 * 會存入 `item.normalized_name` / `company.normalized_name` 等唯一鍵欄位，
 * 顯示用名稱另存 display_name，不受正規化影響。</p>
 */
public final class NameNormalizer {

    /**
     * 標點（\p{P}）、符號（\p{S}）與所有空白一律移除。
     * 移除而非統一替換，是為了讓「Wi-Fi 模組」「WiFi模組」「Wi.Fi．模組」收斂到同一鍵。
     */
    private static final Pattern IGNORABLE_CHARS = Pattern.compile("[\\p{P}\\p{S}\\s]+");

    private NameNormalizer() {
    }

    /**
     * 將名稱正規化為唯一鍵：NFKC（全形轉半形）→ 轉小寫 → 去除標點、符號與空白。
     *
     * @throws ServerException 名稱為 null，或正規化後為空字串（400）
     */
    public static String normalize(String rawName) {
        if (rawName == null) {
            throw new ServerException("名稱不可為空", HttpStatus.BAD_REQUEST);
        }
        String nfkc = Normalizer.normalize(rawName, Normalizer.Form.NFKC);
        String normalized = IGNORABLE_CHARS.matcher(nfkc.toLowerCase(Locale.ROOT)).replaceAll("");
        if (normalized.isEmpty()) {
            throw new ServerException("名稱不可為空", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }
}
