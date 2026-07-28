package com.profetai.industrymap.helper;

import com.profetai.industrymap.enums.IdentifierType;
import com.profetai.industrymap.model.Company;
import com.profetai.industrymap.model.CompanyIdentifier;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 公司「對外識別」的唯一組裝規則（design D4）。
 *
 * <p>同一家公司在不同回應裡叫不同名字，呼叫端很難不誤會，AI pipeline 更會把同一家公司
 * 當成兩個。因此規則只寫一份：優先主要識別碼，該公司無可用識別碼時才退回正規化名稱。</p>
 *
 * <p>識別碼以 {@code <類型>:<代號值>} 的<b>限定形式</b>表達（如 {@code TWSE:2330}）。
 * 代號只在發行它的交易所內唯一——四位數字代號在台股、港股、滬深股的號碼空間完全重疊，
 * 裸代號不足以指定唯一一家公司。限定形式是資料庫唯一鍵 {@code (type, value)} 的字串投影，
 * 兩者一一對應。注意這與「識別碼值本身不得內嵌前綴」不衝突：交易所在<b>儲存層</b>
 * 必須是獨立欄位才能下 constraint，在<b>識別層</b>必須與代號綁在一起才唯一（design D2）。</p>
 *
 * <p>已駁回的識別碼不列入——它不外露於任何回應，若拿它當對外識別，
 * 呼叫端回頭查詢就會查不到公司。</p>
 *
 * <p>拆解（{@link #parse}）與組裝（{@link #qualify}）刻意放在同一處：分開寫遲早漂移，
 * 產出端與解析端對不上就是「識別值查不回自己」的缺陷（design D5）。</p>
 */
public final class CompanyReferences {

    /** 限定形式的分隔符。正規化名稱不含標點，因此不會與名稱混淆 */
    private static final String SEPARATOR = ":";

    private static final Map<String, IdentifierType> TYPES_BY_NAME = Arrays.stream(IdentifierType.values())
            .collect(Collectors.toMap(Enum::name, Function.identity()));

    private CompanyReferences() {
    }

    /**
     * 組出公司的對外識別值。
     *
     * @param identifiers 該公司的識別碼；可為 null 或空（未上市公司）
     */
    public static String of(Company company, Collection<CompanyIdentifier> identifiers) {
        if (identifiers == null) {
            return company.getNormalizedName();
        }
        return identifiers.stream()
                .filter(CompanyIdentifier::isPrimary)
                .filter(identifier -> ReviewScopes.isExposable(identifier.getReviewStatus()))
                .map(CompanyReferences::qualify)
                .findFirst()
                .orElse(company.getNormalizedName());
    }

    /** 把單一識別碼組成限定形式 */
    public static String qualify(CompanyIdentifier identifier) {
        return qualify(identifier.getIdentifierType(), identifier.getIdentifierValue());
    }

    /** 把（類型, 代號值）組成限定形式 */
    public static String qualify(IdentifierType identifierType, String identifierValue) {
        return identifierType.name() + SEPARATOR + identifierValue;
    }

    /**
     * 把對外識別拆回（類型, 代號值）；不是限定形式時回傳空。
     *
     * <p>「冒號前必須是合法類型」這個條件不可省：識別碼值本身可能含冒號
     * （{@code OTHER} 底下合法的 {@code ISIN:DE0006231004} 之類），
     * 少了它會被誤判成限定形式而查無資料。值含冒號時只切第一個冒號，其餘留給值。</p>
     */
    public static Optional<Qualified> parse(String reference) {
        if (reference == null) {
            return Optional.empty();
        }
        int separator = reference.indexOf(SEPARATOR);
        if (separator < 0) {
            return Optional.empty();
        }
        IdentifierType identifierType = TYPES_BY_NAME.get(reference.substring(0, separator));
        String identifierValue = reference.substring(separator + SEPARATOR.length());
        if (identifierType == null || identifierValue.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Qualified(identifierType, identifierValue));
    }

    /** 限定形式拆解後的（類型, 代號值），即 {@code company_identifier} 的唯一鍵 */
    @Getter
    @RequiredArgsConstructor
    public static final class Qualified {

        private final IdentifierType identifierType;
        private final String identifierValue;
    }
}
