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
     * 對外 API 以代號查公司時多半不會指定類型（`/api/companies/2330`），
     * 因此提供只以代號值查詢的版本；同一代號值理論上可跨類型重複（TWSE 2330 與某統編相同），
     * 故回傳清單由 service 判斷。
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
