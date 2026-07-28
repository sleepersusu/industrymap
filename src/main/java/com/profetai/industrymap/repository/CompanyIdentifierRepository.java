package com.profetai.industrymap.repository;

import com.profetai.industrymap.enums.IdentifierType;
import com.profetai.industrymap.model.CompanyIdentifier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CompanyIdentifierRepository extends JpaRepository<CompanyIdentifier, Long> {

    Optional<CompanyIdentifier> findByIdentifierTypeAndIdentifierValue(IdentifierType identifierType, String identifierValue);

    boolean existsByIdentifierTypeAndIdentifierValue(IdentifierType identifierType, String identifierValue);

    /**
     * 只以代號值查詢，供呼叫端沿用裸代號（`/api/companies/2330`）時使用。
     * 同一代號值會跨類型重複（不同交易所的號碼空間完全重疊），故回傳清單；
     * 命中多筆時 service 報衝突而非任選一筆，唯一定位請改用
     * {@link #findByIdentifierTypeAndIdentifierValue}。
     */
    List<CompanyIdentifier> findByIdentifierValue(String identifierValue);

    List<CompanyIdentifier> findByCompanyId(Long companyId);

    /** 取公司的主要識別碼：日後接股價時用來決定抓哪個市場 */
    Optional<CompanyIdentifier> findByCompanyIdAndIsPrimaryTrue(Long companyId);

    /**
     * 一次取多家公司的主要識別碼。供應商清單、市佔率排名都要逐筆組出公司對外識別（design D4），
     * 逐家查會變成 N+1，因此提供批次版本。
     */
    List<CompanyIdentifier> findByCompanyIdInAndIsPrimaryTrue(Collection<Long> companyIds);

    boolean existsByCompanyIdAndIsPrimaryTrue(Long companyId);
}
