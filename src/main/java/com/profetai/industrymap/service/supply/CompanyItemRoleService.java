package com.profetai.industrymap.service.supply;

import com.profetai.industrymap.enums.CompanyRole;
import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.exceptions.ServerException;
import com.profetai.industrymap.helper.ProvenanceValidator;
import com.profetai.industrymap.helper.ReviewScopes;
import com.profetai.industrymap.model.Company;
import com.profetai.industrymap.model.CompanyItemRole;
import com.profetai.industrymap.model.Item;
import com.profetai.industrymap.payloads.supply.CreateCompanyItemRoleRequest;
import com.profetai.industrymap.payloads.supply.SupplierResponse;
import com.profetai.industrymap.repository.CompanyItemRoleRepository;
import com.profetai.industrymap.repository.ItemRepository;
import com.profetai.industrymap.service.company.CompanyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 公司與零件的供應關係（design D6）。
 *
 * <p>唯一鍵含角色，因此「台積電—製造」與「台積電—封測」是兩筆合法資料，
 * 只有同一組（公司, 零件, 角色）重複才算衝突。</p>
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class CompanyItemRoleService {

    private final CompanyService companyService;
    private final ItemRepository itemRepository;
    private final CompanyItemRoleRepository companyItemRoleRepository;

    /**
     * 建立供應關係。
     *
     * <p>公司以代號指定（design D5）：建完公司後拿到的就是代號，
     * 呼叫端不必、也無從取得內部自增主鍵。</p>
     *
     * @throws ServerException 公司或零件不存在（404）、同組公司零件角色重複（409）
     */
    @Transactional
    public CompanyItemRole create(CreateCompanyItemRoleRequest request) {
        ProvenanceValidator.validate(request.getProvenance());
        Company company = companyService.getByReference(request.getCompanyCode());
        Item item = itemRepository.findById(request.getItemId())
                .orElseThrow(() -> new ServerException("查無此品類節點：" + request.getItemId(), HttpStatus.NOT_FOUND));

        if (companyItemRoleRepository.existsByCompanyIdAndItemIdAndCompanyRole(
                company.getId(), item.getId(), request.getCompanyRole())) {
            throw new ServerException("該公司對此零件已登記相同角色：" + request.getCompanyRole(), HttpStatus.CONFLICT);
        }

        CompanyItemRole role = CompanyItemRole.builder()
                .company(company)
                .item(item)
                .companyRole(request.getCompanyRole())
                .sourceType(request.getProvenance().getSourceType())
                .sourceDetail(request.getProvenance().getSourceDetail())
                .confidence(request.getProvenance().getConfidence())
                .build();

        CompanyItemRole saved = companyItemRoleRepository.save(role);
        log.info("建立供應關係 companyCode={} companyId={} itemId={} role={}",
                request.getCompanyCode(), company.getId(), item.getId(), request.getCompanyRole());
        return saved;
    }

    /**
     * 查零件的供應公司。
     *
     * @param companyRole   指定角色時只回該角色（查 PCB 的代工組裝商）；null 表示不過濾
     * @param includeDrafts 是否納入草稿；預設 false 只回已驗證，已駁回一律不外露
     *
     * <p>回傳 payload 而非 entity：{@code company} 是 LAZY 關聯，而 open-in-view 為 false，
     * 交易一結束就無法再載入。組裝必須在這個交易內完成，否則 controller 取公司名稱時會炸。</p>
     */
    @Transactional(readOnly = true)
    public List<SupplierResponse> findSuppliers(Long itemId, CompanyRole companyRole, boolean includeDrafts) {
        Set<ReviewStatus> statuses = ReviewScopes.visibleStatuses(includeDrafts);
        List<CompanyItemRole> roles = companyRole == null
                ? companyItemRoleRepository.findByItemIdAndReviewStatusIn(itemId, statuses)
                : companyItemRoleRepository.findByItemIdAndCompanyRoleAndReviewStatusIn(
                        itemId, companyRole, statuses);
        // 關係本身通過審核，不代表它指向的公司還算數；公司被駁回時這筆供應關係一併不外露
        List<CompanyItemRole> visible = roles.stream()
                .filter(role -> ReviewScopes.isExposable(role.getCompany().getReviewStatus()))
                .toList();

        // 對外識別一次批次解析，避免逐筆查主要識別碼造成 N+1（design D4）
        Map<Long, String> references =
                companyService.referencesOf(visible.stream().map(CompanyItemRole::getCompany).toList());
        return visible.stream()
                .map(role -> SupplierResponse.from(role, references.get(role.getCompany().getId())))
                .toList();
    }
}
