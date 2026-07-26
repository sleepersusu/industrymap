package com.profetai.industrymap.service.item;

import com.profetai.industrymap.exceptions.ServerException;
import com.profetai.industrymap.helper.ProvenanceValidator;
import com.profetai.industrymap.model.Item;
import com.profetai.industrymap.model.ItemAlias;
import com.profetai.industrymap.payloads.item.CreateItemAliasRequest;
import com.profetai.industrymap.payloads.item.CreateItemRequest;
import com.profetai.industrymap.repository.ItemAliasRepository;
import com.profetai.industrymap.repository.ItemRepository;
import com.profetai.industrymap.util.NameNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 品類節點的建立與查詢。
 *
 * <p>節點共用（design D3）的前提是「同一個東西全站只有一個節點」，
 * 因此建立前一律以正規化名稱比對，重複即拒絕。</p>
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class ItemService {

    private final ItemRepository itemRepository;
    private final ItemAliasRepository itemAliasRepository;

    /**
     * 建立品類節點。
     *
     * @throws ServerException 正規化名稱與既有節點重複（409）
     */
    @Transactional
    public Item create(CreateItemRequest request) {
        ProvenanceValidator.validate(request.getProvenance());
        String normalizedName = NameNormalizer.normalize(request.getDisplayName());
        if (itemRepository.existsByNormalizedName(normalizedName)) {
            throw new ServerException("已存在相同名稱的品類節點：" + normalizedName, HttpStatus.CONFLICT);
        }

        Item item = Item.builder()
                .normalizedName(normalizedName)
                .displayName(request.getDisplayName())
                .isEndProduct(request.isEndProduct())
                .parentCategory(resolveParentCategory(request.getParentCategoryId()))
                .sourceType(request.getProvenance().getSourceType())
                .sourceDetail(request.getProvenance().getSourceDetail())
                .confidence(request.getProvenance().getConfidence())
                .build();

        Item saved = itemRepository.save(item);
        log.info("建立品類節點 itemId={} normalizedName={}", saved.getId(), normalizedName);
        return saved;
    }

    /**
     * 以名稱或別名解析既有節點——AI 生成流程寫入前先呼叫這裡，
     * 命中就沿用既有節點，避免同義異名把一個品類拆成好幾個節點（design D9）。
     */
    @Transactional(readOnly = true)
    public Optional<Item> resolveByName(String rawName) {
        String normalized = NameNormalizer.normalize(rawName);
        return itemRepository.findByNormalizedName(normalized)
                .or(() -> itemAliasRepository.findByNormalizedAlias(normalized).map(ItemAlias::getItem));
    }

    /**
     * 登記節點別名。別名一旦與別人的名稱撞在一起，查詢就會指向錯誤節點，
     * 因此除了別名表本身的唯一鍵之外，還要擋掉「別名等於另一個節點的正規化名稱」這種跨表衝突。
     *
     * @throws ServerException 節點不存在（404）、別名與其他節點名稱或既有別名衝突（409）
     */
    @Transactional
    public ItemAlias addAlias(Long itemId, CreateItemAliasRequest request) {
        ProvenanceValidator.validate(request.getProvenance());
        Item item = getById(itemId);
        String normalizedAlias = NameNormalizer.normalize(request.getAlias());

        boolean collidesWithAnotherItem = itemRepository.findByNormalizedName(normalizedAlias)
                .filter(existing -> !existing.getId().equals(item.getId()))
                .isPresent();
        if (collidesWithAnotherItem) {
            throw new ServerException("別名與另一個品類節點的名稱衝突：" + normalizedAlias, HttpStatus.CONFLICT);
        }
        if (itemAliasRepository.existsByNormalizedAlias(normalizedAlias)) {
            throw new ServerException("別名已被登記：" + normalizedAlias, HttpStatus.CONFLICT);
        }

        ItemAlias alias = ItemAlias.builder()
                .item(item)
                .normalizedAlias(normalizedAlias)
                .displayAlias(request.getAlias())
                .sourceType(request.getProvenance().getSourceType())
                .sourceDetail(request.getProvenance().getSourceDetail())
                .confidence(request.getProvenance().getConfidence())
                .build();

        ItemAlias saved = itemAliasRepository.save(alias);
        log.info("登記品類別名 itemId={} alias={}", itemId, normalizedAlias);
        return saved;
    }

    /** 取得節點，查無則 404 */
    @Transactional(readOnly = true)
    public Item getById(Long itemId) {
        return itemRepository.findById(itemId)
                .orElseThrow(() -> new ServerException("查無此品類節點：" + itemId, HttpStatus.NOT_FOUND));
    }

    private Item resolveParentCategory(Long parentCategoryId) {
        if (parentCategoryId == null) {
            return null;
        }
        return itemRepository.findById(parentCategoryId)
                .orElseThrow(() -> new ServerException("查無上層品類：" + parentCategoryId, HttpStatus.NOT_FOUND));
    }
}
