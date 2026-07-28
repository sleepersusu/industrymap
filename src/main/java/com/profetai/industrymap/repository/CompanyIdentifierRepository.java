package com.profetai.industrymap.repository;

import com.profetai.industrymap.enums.IdentifierType;
import com.profetai.industrymap.model.CompanyIdentifier;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CompanyIdentifierRepository extends JpaRepository<CompanyIdentifier, Long> {

    /**
     * 代號解析用的兩支查詢一律把公司一起載入（{@code @EntityGraph}）。
     *
     * <p>{@code company} 是 LAZY 關聯，而 {@code Company} 的 {@code @Id} 標在欄位上，
     * Hibernate 無法在代理上攔截識別碼 getter——連讀主鍵都會觸發初始化，逐筆各補一次 SELECT。
     * 解析代號本來就一定要用到公司本體（判定歧義、過濾已駁回、回傳實體），一次撈齊即可。</p>
     */
    @EntityGraph(attributePaths = "company")
    Optional<CompanyIdentifier> findByIdentifierTypeAndIdentifierValue(IdentifierType identifierType, String identifierValue);

    boolean existsByIdentifierTypeAndIdentifierValue(IdentifierType identifierType, String identifierValue);

    /**
     * 只以代號值查詢，供呼叫端沿用裸代號（`/api/companies/2330`）時使用。
     * 同一代號值會跨類型重複（不同交易所的號碼空間完全重疊），故回傳清單；
     * 命中多筆時 service 報衝突而非任選一筆，唯一定位請改用
     * {@link #findByIdentifierTypeAndIdentifierValue}。
     */
    @EntityGraph(attributePaths = "company")
    List<CompanyIdentifier> findByIdentifierValue(String identifierValue);

    List<CompanyIdentifier> findByCompanyId(Long companyId);

    /**
     * 一次取多家公司的所有識別碼。公司列表要逐筆組出對外識別與識別碼清單，
     * 逐家查會變成 N+1，因此提供批次版本。
     */
    List<CompanyIdentifier> findByCompanyIdIn(Collection<Long> companyIds);

    /** 取公司的主要識別碼：日後接股價時用來決定抓哪個市場 */
    Optional<CompanyIdentifier> findByCompanyIdAndIsPrimaryTrue(Long companyId);

    /**
     * 一次取多家公司的主要識別碼。供應商清單、市佔率排名都要逐筆組出公司對外識別（design D4），
     * 逐家查會變成 N+1，因此提供批次版本。
     */
    List<CompanyIdentifier> findByCompanyIdInAndIsPrimaryTrue(Collection<Long> companyIds);

    boolean existsByCompanyIdAndIsPrimaryTrue(Long companyId);
}
