package com.profetai.industrymap.repository;

import com.profetai.industrymap.enums.CompanyRole;
import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.model.CompanyItemRole;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CompanyItemRoleRepository extends JpaRepository<CompanyItemRole, Long> {

    /**
     * 供應商清單一律連公司一起載入（{@code @EntityGraph}）。
     *
     * <p>{@code company} 是 LAZY 關聯，而 {@code Company} 的 {@code @Id} 標在欄位上，
     * Hibernate 無法在代理上攔截識別碼 getter——過濾公司的審核狀態與組出回應都要讀公司，
     * 逐筆各補一次 SELECT 就是 N+1。回應本來就一定用得到公司，一次撈齊即可。</p>
     *
     * <p>寫在查詢上而非只靠全域的批次抓取設定：join fetch 永遠一次查詢，不受批次大小影響，
     * 且日後有人改這支查詢時看得到這個相依。</p>
     */
    @EntityGraph(attributePaths = "company")
    List<CompanyItemRole> findByItemIdAndReviewStatusIn(Long itemId, Collection<ReviewStatus> reviewStatuses);

    /** 依角色過濾：查 PCB 的代工組裝商，不要把設計與品牌一起回傳。公司一併載入，理由同上 */
    @EntityGraph(attributePaths = "company")
    List<CompanyItemRole> findByItemIdAndCompanyRoleAndReviewStatusIn(Long itemId, CompanyRole companyRole,
                                                                     Collection<ReviewStatus> reviewStatuses);

    /**
     * 由公司查零件——{@code company_item_role} 的另一個方向，供公司詳情頁往下走。
     *
     * <p>節點一併載入（{@code @EntityGraph}）：回應要讀每個節點的名稱與審核狀態，
     * 而 {@code Item} 的 {@code @Id} 標在欄位上，不 join fetch 就是逐筆 N+1。</p>
     *
     * <p>排序固定 {@code display_name} 再 {@code id}：沒有 ORDER BY 時拿到的是資料庫的回傳順序，
     * 任一筆角色被審核（UPDATE）就可能讓整份清單重排，前端看到的零件順序無故變動。
     * display_name 沒有唯一性保證，因此補 id 作為 tie-break。</p>
     */
    @EntityGraph(attributePaths = "item")
    List<CompanyItemRole> findByCompanyIdAndReviewStatusInOrderByItemDisplayNameAscItemIdAsc(
            Long companyId, Collection<ReviewStatus> reviewStatuses);

    /** 依角色過濾：只列該公司以此角色供應的零件。節點一併載入、排序固定，理由同上 */
    @EntityGraph(attributePaths = "item")
    List<CompanyItemRole> findByCompanyIdAndCompanyRoleAndReviewStatusInOrderByItemDisplayNameAscItemIdAsc(
            Long companyId, CompanyRole companyRole, Collection<ReviewStatus> reviewStatuses);

    boolean existsByCompanyIdAndItemIdAndCompanyRole(Long companyId, Long itemId, CompanyRole companyRole);

    /** 以自然鍵（公司 + 零件 + 角色）定位單筆供應關係，供審核端點使用（design D1） */
    Optional<CompanyItemRole> findByCompanyIdAndItemIdAndCompanyRole(Long companyId, Long itemId,
                                                                    CompanyRole companyRole);
}
