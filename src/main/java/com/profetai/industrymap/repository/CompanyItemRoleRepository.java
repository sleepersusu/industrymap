package com.profetai.industrymap.repository;

import com.profetai.industrymap.enums.CompanyRole;
import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.model.CompanyItemRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CompanyItemRoleRepository extends JpaRepository<CompanyItemRole, Long> {

    List<CompanyItemRole> findByItemIdAndReviewStatusIn(Long itemId, Collection<ReviewStatus> reviewStatuses);

    /** 依角色過濾：查 PCB 的代工組裝商，不要把設計與品牌一起回傳 */
    List<CompanyItemRole> findByItemIdAndCompanyRoleAndReviewStatusIn(Long itemId, CompanyRole companyRole,
                                                                     Collection<ReviewStatus> reviewStatuses);

    boolean existsByCompanyIdAndItemIdAndCompanyRole(Long companyId, Long itemId, CompanyRole companyRole);

    /** 以自然鍵（公司 + 零件 + 角色）定位單筆供應關係，供審核端點使用（design D1） */
    Optional<CompanyItemRole> findByCompanyIdAndItemIdAndCompanyRole(Long companyId, Long itemId,
                                                                    CompanyRole companyRole);
}
