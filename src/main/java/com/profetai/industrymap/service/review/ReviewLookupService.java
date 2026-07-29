package com.profetai.industrymap.service.review;

import com.profetai.industrymap.enums.ReviewTargetType;
import com.profetai.industrymap.exceptions.ServerException;
import com.profetai.industrymap.model.ProvenanceEntity;
import com.profetai.industrymap.repository.CompanyAliasRepository;
import com.profetai.industrymap.repository.CompanyIdentifierRepository;
import com.profetai.industrymap.repository.CompanyItemRoleRepository;
import com.profetai.industrymap.repository.CompanyRepository;
import com.profetai.industrymap.repository.ItemAliasRepository;
import com.profetai.industrymap.repository.ItemCompositionRepository;
import com.profetai.industrymap.repository.ItemHotspotRepository;
import com.profetai.industrymap.repository.ItemImageRepository;
import com.profetai.industrymap.repository.ItemRepository;
import com.profetai.industrymap.repository.MarketShareRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;

/**
 * 依審核目標類型取得對應實體（design D1）。
 *
 * <p>十張內容表的查找只差在用哪個 repository，因此以 {@link EnumMap} 註冊查找函式，
 * 而不是寫成十個 switch 分支——日後新增內容表只要多註冊一行。
 * 查詢一律走 Spring Data 內建的 {@code findById}，本層不寫任何 SQL 或 JPQL。</p>
 */
@Service
public class ReviewLookupService {

    private final Map<ReviewTargetType, JpaRepository<? extends ProvenanceEntity, Long>> repositories;

    public ReviewLookupService(ItemRepository itemRepository,
                               ItemAliasRepository itemAliasRepository,
                               ItemCompositionRepository itemCompositionRepository,
                               CompanyRepository companyRepository,
                               CompanyAliasRepository companyAliasRepository,
                               CompanyIdentifierRepository companyIdentifierRepository,
                               CompanyItemRoleRepository companyItemRoleRepository,
                               MarketShareRepository marketShareRepository,
                               ItemImageRepository itemImageRepository,
                               ItemHotspotRepository itemHotspotRepository) {

        Map<ReviewTargetType, JpaRepository<? extends ProvenanceEntity, Long>> registry =
                new EnumMap<>(ReviewTargetType.class);
        registry.put(ReviewTargetType.ITEM, itemRepository);
        registry.put(ReviewTargetType.ITEM_ALIAS, itemAliasRepository);
        registry.put(ReviewTargetType.ITEM_COMPOSITION, itemCompositionRepository);
        registry.put(ReviewTargetType.COMPANY, companyRepository);
        registry.put(ReviewTargetType.COMPANY_ALIAS, companyAliasRepository);
        registry.put(ReviewTargetType.COMPANY_IDENTIFIER, companyIdentifierRepository);
        registry.put(ReviewTargetType.COMPANY_ITEM_ROLE, companyItemRoleRepository);
        registry.put(ReviewTargetType.MARKET_SHARE, marketShareRepository);
        registry.put(ReviewTargetType.ITEM_IMAGE, itemImageRepository);
        registry.put(ReviewTargetType.ITEM_HOTSPOT, itemHotspotRepository);
        this.repositories = Map.copyOf(registry);
    }

    /**
     * 取得審核目標。
     *
     * @throws ServerException 類型未註冊對應 repository（400）、該類型底下查無此識別碼（404）
     */
    public ProvenanceEntity getTarget(ReviewTargetType targetType, Long targetId) {
        return repositoryFor(targetType).findById(targetId)
                .orElseThrow(() -> new ServerException(
                        "查無此審核目標：" + targetType + " " + targetId, HttpStatus.NOT_FOUND));
    }

    /** 寫回目標類型對應的 repository */
    @SuppressWarnings("unchecked")
    public ProvenanceEntity save(ReviewTargetType targetType, ProvenanceEntity entity) {
        JpaRepository<ProvenanceEntity, Long> repository =
                (JpaRepository<ProvenanceEntity, Long>) repositoryFor(targetType);
        return repository.save(entity);
    }

    /**
     * enum 已是白名單，正常情況必然命中；這裡的 400 是防「新增了 enum 值卻忘了註冊 repository」，
     * 讓它以請求錯誤而非 NullPointerException 呈現。
     */
    private JpaRepository<? extends ProvenanceEntity, Long> repositoryFor(ReviewTargetType targetType) {
        JpaRepository<? extends ProvenanceEntity, Long> repository = repositories.get(targetType);
        if (repository == null) {
            throw new ServerException("不支援的審核目標類型：" + targetType, HttpStatus.BAD_REQUEST);
        }
        return repository;
    }
}
