package com.profetai.industrymap.service.review;

import com.profetai.industrymap.enums.ReviewTargetType;
import com.profetai.industrymap.exceptions.ServerException;
import com.profetai.industrymap.model.Company;
import com.profetai.industrymap.model.CompanyAlias;
import com.profetai.industrymap.model.CompanyIdentifier;
import com.profetai.industrymap.model.CompanyItemRole;
import com.profetai.industrymap.model.Item;
import com.profetai.industrymap.model.ItemAlias;
import com.profetai.industrymap.model.ItemComposition;
import com.profetai.industrymap.model.MarketShare;
import com.profetai.industrymap.payloads.review.ReviewTargetKey;
import com.profetai.industrymap.repository.CompanyAliasRepository;
import com.profetai.industrymap.repository.CompanyIdentifierRepository;
import com.profetai.industrymap.repository.CompanyItemRoleRepository;
import com.profetai.industrymap.repository.ItemAliasRepository;
import com.profetai.industrymap.repository.ItemCompositionRepository;
import com.profetai.industrymap.repository.ItemRepository;
import com.profetai.industrymap.repository.MarketShareRepository;
import com.profetai.industrymap.service.company.CompanyService;
import com.profetai.industrymap.util.NameNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 把審核目標的自然鍵解析成內部 id（design D2）。
 *
 * <p>八類資料各有不同的自然鍵組合，解析邏輯集中在這一處而非散在各 service——
 * 分散的話日後新增內容表容易漏掉一種。各類型的自然鍵即其資料庫唯一鍵。</p>
 *
 * <p>兩種錯誤語意刻意分開：自然鍵欄位不足回 400 並指出缺哪些維度（市佔率有六個欄位，
 * 少一個就定位不到，只回「查無資料」會讓呼叫端無從判斷是打錯還是真的沒有）；
 * 欄位齊全但查無資料才回 404。</p>
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class NaturalKeyResolver {

    private final ItemRepository itemRepository;
    private final ItemAliasRepository itemAliasRepository;
    private final ItemCompositionRepository itemCompositionRepository;
    private final CompanyAliasRepository companyAliasRepository;
    private final CompanyIdentifierRepository companyIdentifierRepository;
    private final CompanyItemRoleRepository companyItemRoleRepository;
    private final MarketShareRepository marketShareRepository;
    private final CompanyService companyService;

    /**
     * 各類型的解析方式以 {@link EnumMap} 註冊而非寫成八個 switch 分支，與
     * {@link ReviewLookupService} 的 repository 註冊同一套作法：日後新增內容表只要多註冊一行。
     *
     * <p>值是方法參照，建立時不會碰到注入欄位，因此可安全地在欄位初始化階段組好。</p>
     */
    private final Map<ReviewTargetType, Function<ReviewTargetKey, Long>> resolvers = createResolvers();

    /**
     * 解析自然鍵為該筆資料的內部 id。
     *
     * @throws ServerException 未提供自然鍵或欄位不足（400）、欄位齊全但查無資料（404）
     */
    @Transactional(readOnly = true)
    public Long resolveId(ReviewTargetType targetType, ReviewTargetKey naturalKey) {
        if (naturalKey == null) {
            throw new ServerException("必須提供目標識別碼或自然鍵：" + targetType, HttpStatus.BAD_REQUEST);
        }

        Function<ReviewTargetKey, Long> resolver = resolvers.get(targetType);
        if (resolver == null) {
            throw new ServerException("不支援的審核目標類型：" + targetType, HttpStatus.BAD_REQUEST);
        }
        Long resolved = resolver.apply(naturalKey);
        log.debug("自然鍵解析完成 targetType={} targetId={}", targetType, resolved);
        return resolved;
    }

    private Map<ReviewTargetType, Function<ReviewTargetKey, Long>> createResolvers() {
        Map<ReviewTargetType, Function<ReviewTargetKey, Long>> registry = new EnumMap<>(ReviewTargetType.class);
        registry.put(ReviewTargetType.ITEM, this::resolveItem);
        registry.put(ReviewTargetType.ITEM_ALIAS, this::resolveItemAlias);
        registry.put(ReviewTargetType.ITEM_COMPOSITION, this::resolveComposition);
        registry.put(ReviewTargetType.COMPANY, this::resolveCompany);
        registry.put(ReviewTargetType.COMPANY_ALIAS, this::resolveCompanyAlias);
        registry.put(ReviewTargetType.COMPANY_IDENTIFIER, this::resolveCompanyIdentifier);
        registry.put(ReviewTargetType.COMPANY_ITEM_ROLE, this::resolveCompanyItemRole);
        registry.put(ReviewTargetType.MARKET_SHARE, this::resolveMarketShare);
        return registry;
    }

    private Long resolveItem(ReviewTargetKey key) {
        requireFields(ReviewTargetType.ITEM, fields("名稱", key.getName()));
        String normalized = NameNormalizer.normalize(key.getName());
        return itemRepository.findByNormalizedName(normalized)
                .map(Item::getId)
                .orElseThrow(() -> notFound(ReviewTargetType.ITEM, normalized));
    }

    private Long resolveItemAlias(ReviewTargetKey key) {
        requireFields(ReviewTargetType.ITEM_ALIAS, fields("別名", key.getName()));
        String normalized = NameNormalizer.normalize(key.getName());
        return itemAliasRepository.findByNormalizedAlias(normalized)
                .map(ItemAlias::getId)
                .orElseThrow(() -> notFound(ReviewTargetType.ITEM_ALIAS, normalized));
    }

    private Long resolveComposition(ReviewTargetKey key) {
        requireFields(ReviewTargetType.ITEM_COMPOSITION,
                fields("上層節點", key.getParentItemId(), "下層節點", key.getChildItemId()));
        return itemCompositionRepository.findByParentItemIdAndChildItemId(key.getParentItemId(), key.getChildItemId())
                .map(ItemComposition::getId)
                .orElseThrow(() -> notFound(ReviewTargetType.ITEM_COMPOSITION,
                        key.getParentItemId() + " → " + key.getChildItemId()));
    }

    private Long resolveCompany(ReviewTargetKey key) {
        return resolveCompanyEntity(ReviewTargetType.COMPANY, key).getId();
    }

    private Long resolveCompanyAlias(ReviewTargetKey key) {
        requireFields(ReviewTargetType.COMPANY_ALIAS, fields("別名", key.getName()));
        String normalized = NameNormalizer.normalize(key.getName());
        return companyAliasRepository.findByNormalizedAlias(normalized)
                .map(CompanyAlias::getId)
                .orElseThrow(() -> notFound(ReviewTargetType.COMPANY_ALIAS, normalized));
    }

    private Long resolveCompanyIdentifier(ReviewTargetKey key) {
        requireFields(ReviewTargetType.COMPANY_IDENTIFIER,
                fields("識別碼類型", key.getIdentifierType(), "識別碼值", key.getIdentifierValue()));
        return companyIdentifierRepository
                .findByIdentifierTypeAndIdentifierValue(key.getIdentifierType(), key.getIdentifierValue())
                .map(CompanyIdentifier::getId)
                .orElseThrow(() -> notFound(ReviewTargetType.COMPANY_IDENTIFIER,
                        key.getIdentifierType() + " " + key.getIdentifierValue()));
    }

    private Long resolveCompanyItemRole(ReviewTargetKey key) {
        requireFields(ReviewTargetType.COMPANY_ITEM_ROLE, fields(
                "公司代號", key.getCompanyCode(),
                "零件", key.getItemId(),
                "角色", key.getCompanyRole()));
        Company company = companyService.getByReference(key.getCompanyCode());
        return companyItemRoleRepository
                .findByCompanyIdAndItemIdAndCompanyRole(company.getId(), key.getItemId(), key.getCompanyRole())
                .map(CompanyItemRole::getId)
                .orElseThrow(() -> notFound(ReviewTargetType.COMPANY_ITEM_ROLE,
                        key.getCompanyCode() + " / " + key.getItemId() + " / " + key.getCompanyRole()));
    }

    private Long resolveMarketShare(ReviewTargetKey key) {
        // 來源明細刻意不列入必填：它可為 null，且唯一索引以 COALESCE 正規化後比對
        requireFields(ReviewTargetType.MARKET_SHARE, fields(
                "公司代號", key.getCompanyCode(),
                "零件", key.getItemId(),
                "期間單位", key.getPeriodType(),
                "期間值", key.getPeriodValue(),
                "地區", key.getRegion(),
                "口徑", key.getMetric()));
        Company company = companyService.getByReference(key.getCompanyCode());
        return marketShareRepository.findByDimensionsAndSource(company.getId(), key.getItemId(),
                        key.getPeriodType().name(), key.getPeriodValue(), key.getRegion(),
                        key.getMetric().name(), key.getSourceDetail())
                .map(MarketShare::getId)
                .orElseThrow(() -> notFound(ReviewTargetType.MARKET_SHARE,
                        key.getCompanyCode() + " / " + key.getItemId() + " / " + key.getPeriodValue()
                                + " / " + key.getRegion() + " / " + key.getMetric()
                                + " / " + key.getSourceDetail()));
    }

    private Company resolveCompanyEntity(ReviewTargetType targetType, ReviewTargetKey key) {
        requireFields(targetType, fields("公司代號", key.getCompanyCode()));
        return companyService.getByReference(key.getCompanyCode());
    }

    /** 依序組出「欄位標籤 → 值」，順序即錯誤訊息中列出缺漏欄位的順序 */
    private Map<String, Object> fields(Object... labelValuePairs) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (int i = 0; i < labelValuePairs.length; i += 2) {
            fields.put((String) labelValuePairs[i], labelValuePairs[i + 1]);
        }
        return fields;
    }

    /**
     * 一次列出所有缺漏欄位而非遇到第一個就中止——市佔率的自然鍵有六個欄位，
     * 逐個回報會讓呼叫端來回試好幾次。
     */
    private void requireFields(ReviewTargetType targetType, Map<String, Object> fields) {
        List<String> missing = fields.entrySet().stream()
                .filter(entry -> isMissing(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
        if (!missing.isEmpty()) {
            throw new ServerException(targetType + " 的自然鍵缺少：" + String.join("、", missing),
                    HttpStatus.BAD_REQUEST);
        }
    }

    private boolean isMissing(Object value) {
        return value == null || (value instanceof String text && text.isBlank());
    }

    private ServerException notFound(ReviewTargetType targetType, String keyDescription) {
        return new ServerException("查無此審核目標：" + targetType + " " + keyDescription, HttpStatus.NOT_FOUND);
    }
}
