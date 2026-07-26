package com.profetai.industrymap.service.bulk;

import com.profetai.industrymap.enums.ReviewTargetType;
import com.profetai.industrymap.exceptions.ServerException;
import com.profetai.industrymap.model.Company;
import com.profetai.industrymap.model.CompanyIdentifier;
import com.profetai.industrymap.model.CompanyItemRole;
import com.profetai.industrymap.model.Item;
import com.profetai.industrymap.model.ItemComposition;
import com.profetai.industrymap.model.MarketShare;
import com.profetai.industrymap.payloads.bulk.BatchCompositionItem;
import com.profetai.industrymap.payloads.bulk.BatchCreateResultResponse;
import com.profetai.industrymap.payloads.bulk.BatchIdentifierItem;
import com.profetai.industrymap.payloads.company.CreateCompanyRequest;
import com.profetai.industrymap.payloads.item.CreateItemRequest;
import com.profetai.industrymap.payloads.review.ReviewTargetKey;
import com.profetai.industrymap.payloads.supply.CreateCompanyItemRoleRequest;
import com.profetai.industrymap.payloads.supply.CreateMarketShareRequest;
import com.profetai.industrymap.service.company.CompanyService;
import com.profetai.industrymap.service.item.ItemCompositionService;
import com.profetai.industrymap.service.item.ItemService;
import com.profetai.industrymap.service.supply.CompanyItemRoleService;
import com.profetai.industrymap.service.supply.MarketShareService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * 內容資料的批次建立（design D3）。
 *
 * <p>本層刻意不開交易：逐筆呼叫的都是各領域 service 上已標註 {@code @Transactional} 的方法，
 * 且它們是不同的 bean，因此每一筆各自成為一個交易。批次中某筆失敗只回報該筆，
 * 不會把先前已成功的項目一併撤銷——與 {@code BatchReviewService} 同一套理由。</p>
 *
 * <p>每筆成功項目回傳自然鍵，呼叫端可直接把回應轉成批次審核請求。建立邏輯本身完全沿用
 * 各領域 service，批次只是外圈迴圈，因此重複檢查、循環偵測、來源驗證都與單筆端點一致。</p>
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class BulkAuthoringService {

    private final ItemService itemService;
    private final ItemCompositionService itemCompositionService;
    private final CompanyService companyService;
    private final CompanyItemRoleService companyItemRoleService;
    private final MarketShareService marketShareService;

    /** 批次建立品類節點 */
    public List<BatchCreateResultResponse> createItems(List<CreateItemRequest> requests) {
        return createEach(requests, (index, request) -> {
            Item item = itemService.create(request);
            return BatchCreateResultResponse.success(index, ReviewTargetType.ITEM, item.getId(),
                    ReviewTargetKey.builder().name(item.getNormalizedName()).build());
        });
    }

    /** 批次建立 part-of 組成關係 */
    public List<BatchCreateResultResponse> createCompositions(List<BatchCompositionItem> requests) {
        return createEach(requests, (index, request) -> {
            ItemComposition composition =
                    itemCompositionService.create(request.getParentItemId(), request.toRequest());
            return BatchCreateResultResponse.success(index, ReviewTargetType.ITEM_COMPOSITION, composition.getId(),
                    ReviewTargetKey.builder()
                            .parentItemId(request.getParentItemId())
                            .childItemId(request.getChildItemId())
                            .build());
        });
    }

    /** 批次建立公司 */
    public List<BatchCreateResultResponse> createCompanies(List<CreateCompanyRequest> requests) {
        return createEach(requests, (index, request) -> {
            Company company = companyService.create(request);
            // 剛建立的公司還沒有任何識別碼，對外識別必然是正規化名稱
            return BatchCreateResultResponse.success(index, ReviewTargetType.COMPANY, company.getId(),
                    ReviewTargetKey.builder().companyCode(company.getNormalizedName()).build());
        });
    }

    /** 批次登記公司識別碼 */
    public List<BatchCreateResultResponse> createIdentifiers(List<BatchIdentifierItem> requests) {
        return createEach(requests, (index, request) -> {
            Company company = companyService.getByReference(request.getCompanyCode());
            CompanyIdentifier identifier =
                    companyService.addIdentifier(company.getId(), request.toRequest());
            return BatchCreateResultResponse.success(index, ReviewTargetType.COMPANY_IDENTIFIER, identifier.getId(),
                    ReviewTargetKey.builder()
                            .identifierType(identifier.getIdentifierType())
                            .identifierValue(identifier.getIdentifierValue())
                            .build());
        });
    }

    /** 批次建立公司與零件的供應角色 */
    public List<BatchCreateResultResponse> createSupplyRoles(List<CreateCompanyItemRoleRequest> requests) {
        return createEach(requests, (index, request) -> {
            CompanyItemRole role = companyItemRoleService.create(request);
            return BatchCreateResultResponse.success(index, ReviewTargetType.COMPANY_ITEM_ROLE, role.getId(),
                    ReviewTargetKey.builder()
                            // 回傳統一規則下的對外識別，而非呼叫端原本填的值（design D4）
                            .companyCode(companyService.referenceOf(role.getCompany()))
                            .itemId(request.getItemId())
                            .companyRole(request.getCompanyRole())
                            .build());
        });
    }

    /** 批次寫入市佔率 */
    public List<BatchCreateResultResponse> createMarketShares(List<CreateMarketShareRequest> requests) {
        return createEach(requests, (index, request) -> {
            MarketShare marketShare = marketShareService.create(request);
            return BatchCreateResultResponse.success(index, ReviewTargetType.MARKET_SHARE, marketShare.getId(),
                    ReviewTargetKey.builder()
                            .companyCode(companyService.referenceOf(marketShare.getCompany()))
                            .itemId(request.getItemId())
                            .periodType(request.getPeriodType())
                            .periodValue(request.getPeriodValue())
                            .region(request.getRegion())
                            .metric(request.getMetric())
                            .sourceDetail(marketShare.getSourceDetail())
                            .build());
        });
    }

    /**
     * 逐筆建立並回報結果。
     *
     * <p>攔截範圍刻意涵蓋非業務例外：一筆資料違反 DB constraint 或撞上鎖競爭時拋的不是
     * {@link ServerException}，只攔 ServerException 會讓剩餘項目全部不處理、呼叫端也拿不到
     * 逐筆結果，正好違反本端點的逐筆回報語意（與 {@code BatchReviewService} 同一取捨）。</p>
     */
    private <T> List<BatchCreateResultResponse> createEach(
            List<T> requests, BiFunction<Integer, T, BatchCreateResultResponse> creator) {

        List<BatchCreateResultResponse> results = new ArrayList<>(requests.size());
        for (int index = 0; index < requests.size(); index++) {
            try {
                results.add(creator.apply(index, requests.get(index)));
            } catch (ServerException ex) {
                log.warn("批次建立單筆失敗 index={} status={} reason={}",
                        index, ex.getHttpStatus(), ex.getMessage());
                results.add(BatchCreateResultResponse.failure(index, ex.getHttpStatus().value(), ex.getMessage()));
            } catch (Exception ex) {
                log.warn("批次建立單筆發生非預期錯誤 index={} reason={}", index, ex.getMessage(), ex);
                results.add(BatchCreateResultResponse.failure(
                        index, HttpStatus.INTERNAL_SERVER_ERROR.value(), ex.getMessage()));
            }
        }

        long failed = results.stream().filter(result -> !result.isSuccess()).count();
        log.info("批次建立完成 total={} failed={}", results.size(), failed);
        return results;
    }
}
