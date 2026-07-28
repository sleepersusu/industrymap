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
     * <p>角色子查詢另外 join {@code item}：關係的狀態與它指向的節點的狀態是兩回事，
     * 一筆已驗證的角色可能掛在事後被駁回的節點上（審核逐列套用，可達狀態）。少了這個判定，
     * 公司會出現在「所有代工組裝廠」清單裡，使用者卻找不到任何對應零件可以解釋——
     * 那個節點對外不存在。同一條規則 {@code ItemCompositionService} 已用於組成樹。</p>
     *
     * <p>{@code itemId} 與 {@code companyRole} 各自可選，兩者皆未指定時才跳過整個供應關係條件
     * ——最外層那組括號是必要的：{@code AND} 的優先序高於 {@code OR}，少了它雖然碰巧仍會解析成
     * 同一件事，但意圖不再顯性，日後改動極易寫錯。兩者併用時刻意走<b>單一</b> {@code EXISTS}：
     * 拆成兩個子查詢會讓「A 零件的製造角色」與「B 零件的組裝角色」分別命中，
     * 於是一家對 A 只做製造的公司會出現在「A 零件＋組裝」的結果裡（design D1）。</p>
     *
     * @param reviewStatuses     公司本身的可見審核狀態
     * @param namePattern        比對正規化名稱與別名的 LIKE 樣式；不過濾時傳 {@code %}
     * @param country            國別，不分大小寫的精確比對；null 表示不過濾
     * @param publicCompany      是否公開發行；null 表示不過濾
     * @param itemId             品類節點 id；null 表示不限零件
     * @param companyRole        供應角色名稱；null 表示不限角色。未指定 {@code itemId} 時語意為
     *                           「對任何零件具有該角色」
     * @param roleReviewStatuses  供應角色的可見審核狀態
     * @param exposableStatuses  別名與品類節點這類<b>實體</b>的可外露審核狀態：只擋 REJECTED，
     *                            不跟著 {@code includeDrafts} 走。被駁回的別名是「明確判定為錯」的寫法，
     *                            不該還能把公司找出來；被駁回的節點對外根本不存在，掛在它上面的角色
     *                            也就不該讓公司浮出來。草稿只是還沒審，擋掉只會讓剛匯入的資料變難找
     */
    @Query(value = """
            SELECT c.* FROM company c
            WHERE c.review_status IN (:reviewStatuses)
              AND (c.normalized_name LIKE :namePattern
                   OR EXISTS (SELECT 1 FROM company_alias a
                              WHERE a.company_id = c.id
                                AND a.normalized_alias LIKE :namePattern
                                AND a.review_status IN (:exposableStatuses)))
              AND (CAST(:country AS text) IS NULL OR upper(c.country) = upper(CAST(:country AS text)))
              AND (CAST(:publicCompany AS boolean) IS NULL
                   OR c.is_public = CAST(:publicCompany AS boolean))
              AND ((CAST(:itemId AS bigint) IS NULL AND CAST(:companyRole AS text) IS NULL)
                   OR EXISTS (SELECT 1 FROM company_item_role r
                              JOIN item i ON i.id = r.item_id
                              WHERE r.company_id = c.id
                                AND (CAST(:itemId AS bigint) IS NULL
                                     OR r.item_id = CAST(:itemId AS bigint))
                                AND r.review_status IN (:roleReviewStatuses)
                                AND i.review_status IN (:exposableStatuses)
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
                                @Param("exposableStatuses") Collection<String> exposableStatuses,
                                @Param("limit") int limit,
                                @Param("offset") long offset);

    /** 公司列表的總筆數，過濾條件與 {@link #findCompanies} 逐條一致 */
    @Query(value = """
            SELECT COUNT(*) FROM company c
            WHERE c.review_status IN (:reviewStatuses)
              AND (c.normalized_name LIKE :namePattern
                   OR EXISTS (SELECT 1 FROM company_alias a
                              WHERE a.company_id = c.id
                                AND a.normalized_alias LIKE :namePattern
                                AND a.review_status IN (:exposableStatuses)))
              AND (CAST(:country AS text) IS NULL OR upper(c.country) = upper(CAST(:country AS text)))
              AND (CAST(:publicCompany AS boolean) IS NULL
                   OR c.is_public = CAST(:publicCompany AS boolean))
              AND ((CAST(:itemId AS bigint) IS NULL AND CAST(:companyRole AS text) IS NULL)
                   OR EXISTS (SELECT 1 FROM company_item_role r
                              JOIN item i ON i.id = r.item_id
                              WHERE r.company_id = c.id
                                AND (CAST(:itemId AS bigint) IS NULL
                                     OR r.item_id = CAST(:itemId AS bigint))
                                AND r.review_status IN (:roleReviewStatuses)
                                AND i.review_status IN (:exposableStatuses)
                                AND (CAST(:companyRole AS text) IS NULL
                                     OR r.company_role = CAST(:companyRole AS text))))
            """, nativeQuery = true)
    long countCompanies(@Param("reviewStatuses") Collection<String> reviewStatuses,
                        @Param("namePattern") String namePattern,
                        @Param("country") String country,
                        @Param("publicCompany") Boolean publicCompany,
                        @Param("itemId") Long itemId,
                        @Param("companyRole") String companyRole,
                        @Param("roleReviewStatuses") Collection<String> roleReviewStatuses,
                        @Param("exposableStatuses") Collection<String> exposableStatuses);
}
