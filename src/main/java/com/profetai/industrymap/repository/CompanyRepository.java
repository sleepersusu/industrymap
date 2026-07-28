package com.profetai.industrymap.repository;

import com.profetai.industrymap.model.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByNormalizedName(String normalizedName);

    boolean existsByNormalizedName(String normalizedName);

    /**
     * 公司列表（design D1–D5）。
     *
     * <p>別名與供應角色一律以 {@code EXISTS} 半連接比對，不用 JOIN：JOIN 會讓同一家公司因為
     * 多個別名或多個角色各命中一次而重複出現，內容端得再 DISTINCT、count 端得再包一層子查詢，
     * 兩邊只要有一邊漏了，前端的「共 N 家」與頁數就全錯（design D2 明列此為易錯處）。
     * {@code EXISTS} 從根本上讓每家公司至多產出一列，內容與 count 因此天生一致。</p>
     *
     * <p>排序固定 {@code display_name} 再 {@code id}：display_name 沒有唯一性保證，
     * 少了 id 這個 tie-break，翻頁就會在頁與頁之間漏或重複資料。</p>
     *
     * <p>可選條件一律以 {@code CAST(:param AS 型別) IS NULL OR ...} 表達「未指定即不過濾」。
     * native SQL 的 null 參數沒有欄位提供型別上下文，不加 CAST 時 PostgreSQL 會直接以
     * 「無法判斷參數型別」拒絕整句查詢，而不是回空結果。</p>
     *
     * <p>供應角色的可見範圍用 {@code roleReviewStatuses} 另外傳入，與公司本身的
     * {@code reviewStatuses} 分開：兩者是不同資料列各自的審核狀態，公司已驗證不代表
     * 它對這個零件的關係也審過（design D3）。</p>
     *
     * @param reviewStatuses     公司本身的可見審核狀態
     * @param namePattern        比對正規化名稱與別名的 LIKE 樣式；不過濾時傳 {@code %}
     * @param country            國別，不分大小寫的精確比對；null 表示不過濾
     * @param publicCompany      是否公開發行；null 表示不過濾
     * @param itemId             品類節點 id；null 表示不依供應零件過濾
     * @param companyRole        供應角色名稱；null 表示不限角色
     * @param roleReviewStatuses 供應角色的可見審核狀態
     */
    @Query(value = """
            SELECT c.* FROM company c
            WHERE c.review_status IN (:reviewStatuses)
              AND (c.normalized_name LIKE :namePattern
                   OR EXISTS (SELECT 1 FROM company_alias a
                              WHERE a.company_id = c.id
                                AND a.normalized_alias LIKE :namePattern))
              AND (CAST(:country AS text) IS NULL OR upper(c.country) = upper(CAST(:country AS text)))
              AND (CAST(:publicCompany AS boolean) IS NULL
                   OR c.is_public = CAST(:publicCompany AS boolean))
              AND (CAST(:itemId AS bigint) IS NULL
                   OR EXISTS (SELECT 1 FROM company_item_role r
                              WHERE r.company_id = c.id
                                AND r.item_id = CAST(:itemId AS bigint)
                                AND r.review_status IN (:roleReviewStatuses)
                                AND (CAST(:companyRole AS text) IS NULL
                                     OR r.company_role = CAST(:companyRole AS text))))
            ORDER BY c.display_name, c.id
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<Company> findCompanies(@Param("reviewStatuses") Collection<String> reviewStatuses,
                                @Param("namePattern") String namePattern,
                                @Param("country") String country,
                                @Param("publicCompany") Boolean publicCompany,
                                @Param("itemId") Long itemId,
                                @Param("companyRole") String companyRole,
                                @Param("roleReviewStatuses") Collection<String> roleReviewStatuses,
                                @Param("limit") int limit,
                                @Param("offset") long offset);

    /** 公司列表的總筆數，過濾條件與 {@link #findCompanies} 逐條一致 */
    @Query(value = """
            SELECT COUNT(*) FROM company c
            WHERE c.review_status IN (:reviewStatuses)
              AND (c.normalized_name LIKE :namePattern
                   OR EXISTS (SELECT 1 FROM company_alias a
                              WHERE a.company_id = c.id
                                AND a.normalized_alias LIKE :namePattern))
              AND (CAST(:country AS text) IS NULL OR upper(c.country) = upper(CAST(:country AS text)))
              AND (CAST(:publicCompany AS boolean) IS NULL
                   OR c.is_public = CAST(:publicCompany AS boolean))
              AND (CAST(:itemId AS bigint) IS NULL
                   OR EXISTS (SELECT 1 FROM company_item_role r
                              WHERE r.company_id = c.id
                                AND r.item_id = CAST(:itemId AS bigint)
                                AND r.review_status IN (:roleReviewStatuses)
                                AND (CAST(:companyRole AS text) IS NULL
                                     OR r.company_role = CAST(:companyRole AS text))))
            """, nativeQuery = true)
    long countCompanies(@Param("reviewStatuses") Collection<String> reviewStatuses,
                        @Param("namePattern") String namePattern,
                        @Param("country") String country,
                        @Param("publicCompany") Boolean publicCompany,
                        @Param("itemId") Long itemId,
                        @Param("companyRole") String companyRole,
                        @Param("roleReviewStatuses") Collection<String> roleReviewStatuses);
}
