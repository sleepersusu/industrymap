package com.profetai.industrymap.helper;

import com.profetai.industrymap.model.Company;
import com.profetai.industrymap.model.CompanyIdentifier;

import java.util.Collection;

/**
 * 公司「對外識別」的唯一組裝規則（design D4）。
 *
 * <p>同一家公司在不同回應裡叫不同名字，呼叫端很難不誤會，AI pipeline 更會把同一家公司
 * 當成兩個。因此規則只寫一份：優先主要識別碼的代號，該公司無可用識別碼時才退回正規化名稱。</p>
 *
 * <p>已駁回的識別碼不列入——它不外露於任何回應，若拿它當對外識別，
 * 呼叫端回頭查詢就會查不到公司。</p>
 */
public final class CompanyReferences {

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
                .map(CompanyIdentifier::getIdentifierValue)
                .findFirst()
                .orElse(company.getNormalizedName());
    }
}
