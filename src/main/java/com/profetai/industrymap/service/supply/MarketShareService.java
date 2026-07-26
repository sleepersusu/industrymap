package com.profetai.industrymap.service.supply;

import com.profetai.industrymap.enums.PeriodType;
import com.profetai.industrymap.enums.ShareMetric;
import com.profetai.industrymap.exceptions.ServerException;
import com.profetai.industrymap.helper.ProvenanceValidator;
import com.profetai.industrymap.helper.ReviewScopes;
import com.profetai.industrymap.model.Company;
import com.profetai.industrymap.model.Item;
import com.profetai.industrymap.model.MarketShare;
import com.profetai.industrymap.payloads.supply.CreateMarketShareRequest;
import com.profetai.industrymap.payloads.supply.MarketShareResponse;
import com.profetai.industrymap.repository.CompanyRepository;
import com.profetai.industrymap.repository.ItemRepository;
import com.profetai.industrymap.repository.MarketShareRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 市佔率的寫入與排名查詢（design D7）。
 *
 * <p>兩個刻意的取捨寫在這裡：其一，不檢查也不要求 {@code company_item_role} 先存在——
 * 市佔率資料常先於角色關係到達；其二，唯一性以「維度 + 來源」判斷，
 * 讓來源互相衝突的數值並存，由使用者判斷，而不是由系統擅自選一個。</p>
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class MarketShareService {

    private static final BigDecimal MIN_SHARE_PERCENT = BigDecimal.ZERO;
    private static final BigDecimal MAX_SHARE_PERCENT = BigDecimal.valueOf(100);

    private final CompanyRepository companyRepository;
    private final ItemRepository itemRepository;
    private final MarketShareRepository marketShareRepository;

    /**
     * 寫入市佔率。
     *
     * @throws ServerException 維度缺漏或百分比超出 0–100（400）、公司或零件不存在（404）、
     *                         同一來源對同一組維度重複（409）
     */
    @Transactional
    public MarketShare create(CreateMarketShareRequest request) {
        ProvenanceValidator.validate(request.getProvenance());
        validateDimensions(request);

        Company company = companyRepository.findById(request.getCompanyId())
                .orElseThrow(() -> new ServerException("查無此公司：" + request.getCompanyId(), HttpStatus.NOT_FOUND));
        Item item = itemRepository.findById(request.getItemId())
                .orElseThrow(() -> new ServerException("查無此品類節點：" + request.getItemId(), HttpStatus.NOT_FOUND));

        String sourceDetail = request.getProvenance().getSourceDetail();
        boolean duplicated = marketShareRepository.existsSameDimensionsFromSameSource(
                company.getId(), item.getId(), request.getPeriodType().name(), request.getPeriodValue(),
                request.getRegion(), request.getMetric().name(), sourceDetail);
        if (duplicated) {
            throw new ServerException("同一來源已寫過相同維度的市佔率：" + sourceDetail, HttpStatus.CONFLICT);
        }

        MarketShare marketShare = MarketShare.builder()
                .company(company)
                .item(item)
                .periodType(request.getPeriodType())
                .periodValue(request.getPeriodValue())
                .region(request.getRegion())
                .metric(request.getMetric())
                .sharePercent(request.getSharePercent())
                .sourceType(request.getProvenance().getSourceType())
                .sourceDetail(sourceDetail)
                .confidence(request.getProvenance().getConfidence())
                .build();

        MarketShare saved = marketShareRepository.save(marketShare);
        log.info("寫入市佔率 companyId={} itemId={} period={} region={} metric={} share={}",
                company.getId(), item.getId(), request.getPeriodValue(), request.getRegion(),
                request.getMetric(), request.getSharePercent());
        return saved;
    }

    /**
     * 依零件與期間、地區、口徑取市佔率排名，百分比由高至低。
     * 查無資料回空清單而非 404——「這個零件在這個切面沒有資料」是空狀態，不是查不到資源。
     *
     * <p>回傳 payload 而非 entity：{@code company} 是 LAZY 關聯，open-in-view 為 false，
     * 交易外再取公司名稱會拋 LazyInitializationException，因此組裝留在這個交易內。</p>
     */
    @Transactional(readOnly = true)
    public List<MarketShareResponse> findRanking(Long itemId, PeriodType periodType, String periodValue,
                                                 String region, ShareMetric metric, boolean includeDrafts) {
        return marketShareRepository.findRanking(itemId, periodType.name(), periodValue, region, metric.name(),
                        ReviewScopes.visibleStatusNames(includeDrafts)).stream()
                .map(MarketShareResponse::from)
                .toList();
    }

    /** 缺任一維度的市佔率數字沒有意義，寧可擋下也不要存進一筆無法解讀的資料 */
    private void validateDimensions(CreateMarketShareRequest request) {
        if (request.getPeriodType() == null || request.getPeriodValue() == null
                || request.getPeriodValue().isBlank()) {
            throw new ServerException("市佔率必須指定期間", HttpStatus.BAD_REQUEST);
        }
        if (request.getRegion() == null || request.getRegion().isBlank()) {
            throw new ServerException("市佔率必須指定地區", HttpStatus.BAD_REQUEST);
        }
        if (request.getMetric() == null) {
            throw new ServerException("市佔率必須指定口徑", HttpStatus.BAD_REQUEST);
        }
        BigDecimal sharePercent = request.getSharePercent();
        if (sharePercent == null
                || sharePercent.compareTo(MIN_SHARE_PERCENT) < 0
                || sharePercent.compareTo(MAX_SHARE_PERCENT) > 0) {
            throw new ServerException("市佔百分比必須介於 0 至 100 之間", HttpStatus.BAD_REQUEST);
        }
    }
}
