package com.profetai.industrymap.service.item;

import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.exceptions.ServerException;
import com.profetai.industrymap.helper.ProvenanceValidator;
import com.profetai.industrymap.helper.ReviewScopes;
import com.profetai.industrymap.model.Item;
import com.profetai.industrymap.model.ItemAlias;
import com.profetai.industrymap.payloads.PageResponse;
import com.profetai.industrymap.payloads.item.AmendItemRequest;
import com.profetai.industrymap.payloads.item.CreateItemAliasRequest;
import com.profetai.industrymap.payloads.item.CreateItemRequest;
import com.profetai.industrymap.payloads.item.EndProductQuery;
import com.profetai.industrymap.payloads.item.ItemResponse;
import com.profetai.industrymap.repository.ItemAliasRepository;
import com.profetai.industrymap.repository.ItemRepository;
import com.profetai.industrymap.service.review.ReviewService;
import com.profetai.industrymap.util.NameNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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
    private final ReviewService reviewService;

    /**
     * 建立品類節點。
     *
     * @throws ServerException 正規化名稱與既有節點重複（409）
     */
    @Transactional
    public Item create(CreateItemRequest request) {
        ProvenanceValidator.validate(request.getProvenance());
        String normalizedName = NameNormalizer.normalize(request.getDisplayName());
        assertNameAvailable(normalizedName, null);

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
     * 修正既有品類節點：全量替換 {@code displayName}、{@code endProduct} 與 is-a 上層品類（design D2）。
     *
     * <p>任一欄位實際變更後審核狀態一律退回 {@code DRAFT}（design D3）——對外資料都經過審核這條線，
     * 已驗證的內容不該被靜默改寫。但送進來的值與現況完全相同時不動狀態，
     * 否則批次重跑或前端重複送出，會讓一筆沒被改過的已驗證資料無故從對外查詢消失。</p>
     *
     * <p>檢查一律排在寫入之前：被拒絕的修正不得留下半套變更。</p>
     *
     * @throws ServerException 節點或上層品類不存在（404）、改名衝突或 is-a 循環（409）
     */
    @Transactional
    public Item amend(Long itemId, AmendItemRequest request) {
        Item item = getById(itemId);
        String normalizedName = NameNormalizer.normalize(request.getDisplayName());

        boolean nameChanged = !normalizedName.equals(item.getNormalizedName());
        if (nameChanged) {
            assertNameAvailable(normalizedName, item.getId());
        }
        Item parentCategory = resolveParentCategory(request.getParentCategoryId());
        assertNoCategoryCycle(item.getId(), request.getParentCategoryId());

        Long currentParentCategoryId = item.getParentCategory() == null ? null : item.getParentCategory().getId();
        boolean contentChanged = nameChanged
                || item.isEndProduct() != request.getEndProduct()
                || !Objects.equals(currentParentCategoryId, request.getParentCategoryId());

        item.setDisplayName(request.getDisplayName());
        item.setNormalizedName(normalizedName);
        item.setEndProduct(request.getEndProduct());
        item.setParentCategory(parentCategory);
        if (contentChanged) {
            // 沿用審核服務退回草稿，殘留的審核者與審核時間一併清掉——
            // 這筆內容已經不是當初被審過的那一筆了
            reviewService.applyReview(item, ReviewStatus.DRAFT, null);
        }

        Item saved = itemRepository.save(item);
        log.info("修正品類節點 itemId={} normalizedName={} contentChanged={} reviewStatus={}",
                saved.getId(), normalizedName, contentChanged, saved.getReviewStatus());
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
        assertNameAvailable(normalizedAlias, item.getId());

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

    /**
     * 列出終端成品——產業地圖的進入點：呼叫端不需要事先知道任何 id
     * 就能取得可以往下展開的產品清單。
     *
     * <p>回傳 payload 而非 entity：上層品類是 LAZY 關聯，open-in-view 為 false，
     * 組裝必須留在這個交易內。</p>
     */
    @Transactional(readOnly = true)
    public PageResponse<ItemResponse> findEndProducts(EndProductQuery query) {
        Collection<String> reviewStatuses = ReviewScopes.visibleStatusNames(query.isIncludeDrafts());
        String namePattern = toNamePattern(query.getName());

        long totalElements = itemRepository.countEndProducts(reviewStatuses, namePattern);
        List<ItemResponse> content = itemRepository
                .findEndProducts(reviewStatuses, namePattern, query.getSize(), query.getPage() * query.getSize())
                .stream()
                .map(ItemResponse::from)
                .toList();

        return PageResponse.of(content, query.getPage(), query.getSize(), totalElements);
    }

    /**
     * 把名稱關鍵字轉成比對 {@code normalized_name} 的 LIKE 樣式；未指定關鍵字時不過濾。
     *
     * <p>比對正規化名稱而非顯示名稱，才能讓「ＷｉＦｉ 模組」找得到「WiFi模組」。
     * 正規化會移除標點與符號，{@code %} 與 {@code _} 因此不可能殘留在樣式的關鍵字部分。</p>
     */
    private String toNamePattern(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return "%";
        }
        return "%" + NameNormalizer.normalize(rawName) + "%";
    }

    /** 取得節點，查無則 404。供寫入流程使用，不過濾審核狀態 */
    @Transactional(readOnly = true)
    public Item getById(Long itemId) {
        return itemRepository.findById(itemId)
                .orElseThrow(() -> new ServerException("查無此品類節點：" + itemId, HttpStatus.NOT_FOUND));
    }

    /**
     * 對外取得節點：已駁回的節點視為不存在。
     *
     * <p>與 {@link #getById} 分開是因為寫入流程（登記別名、掛組成關係）仍需取得實體本身，
     * 而對外查詢不得回傳已駁回的資料。</p>
     *
     * @throws ServerException 節點不存在或已被駁回（404）
     */
    @Transactional(readOnly = true)
    public Item getVisibleById(Long itemId) {
        Item item = getById(itemId);
        if (!ReviewScopes.isExposable(item.getReviewStatus())) {
            throw new ServerException("查無此品類節點：" + itemId, HttpStatus.NOT_FOUND);
        }
        return item;
    }

    /** 對外以名稱或別名解析節點：已駁回的節點視為查無 */
    @Transactional(readOnly = true)
    public Optional<Item> resolveVisibleByName(String rawName) {
        return resolveByName(rawName).filter(item -> ReviewScopes.isExposable(item.getReviewStatus()));
    }

    /**
     * 名稱可用性檢查：建立節點、登記別名、修正節點改名共用同一份（design D4）。
     *
     * <p>三處要擋的是同一組條件的聯集——名稱不得撞到其他節點的正規化名稱，
     * 也不得撞到任何已登記的別名（包含該節點自己的別名，否則
     * {@code item.normalized_name} 會與自己的 alias 重複，資料重複且無意義）。
     * 各寫一份遲早漂移，因此規則只留這一處。</p>
     *
     * @param excludedItemId 比對節點名稱時要排除的節點；建立節點時傳 null（沒有自己可排除）
     * @throws ServerException 名稱與其他節點或既有別名衝突（409）
     */
    private void assertNameAvailable(String normalizedName, Long excludedItemId) {
        boolean collidesWithAnotherItem = itemRepository.findByNormalizedName(normalizedName)
                .filter(existing -> !existing.getId().equals(excludedItemId))
                .isPresent();
        if (collidesWithAnotherItem) {
            throw new ServerException("名稱與另一個品類節點衝突：" + normalizedName, HttpStatus.CONFLICT);
        }
        if (itemAliasRepository.existsByNormalizedAlias(normalizedName)) {
            throw new ServerException("名稱已被登記為別名：" + normalizedName, HttpStatus.CONFLICT);
        }
    }

    private Item resolveParentCategory(Long parentCategoryId) {
        if (parentCategoryId == null) {
            return null;
        }
        return itemRepository.findById(parentCategoryId)
                .orElseThrow(() -> new ServerException("查無上層品類：" + parentCategoryId, HttpStatus.NOT_FOUND));
    }

    /**
     * is-a 上層品類的循環檢查（design D5）：沿 is-a 鏈往上走，走得回自己就是循環。
     *
     * <p>不共用 {@code ItemCompositionService} 的循環偵測——那一份守的是 part-of
     * （{@code item_composition}，多對多 DAG），這裡是 {@code item} 表上的自我 FK，
     * 兩條關係刻意分離，硬共用只會讓兩邊都難懂。</p>
     *
     * <p>visited 除了避免重複展開，也讓既有資料若已含循環時本方法仍能終止。</p>
     *
     * @throws ServerException 指向自己或沿 is-a 鏈回到自己（409）
     */
    private void assertNoCategoryCycle(Long itemId, Long parentCategoryId) {
        Set<Long> visited = new HashSet<>();
        Long cursor = parentCategoryId;

        while (cursor != null) {
            if (cursor.equals(itemId)) {
                throw new ServerException(
                        "上層品類會造成 is-a 循環：" + itemId + " → " + parentCategoryId, HttpStatus.CONFLICT);
            }
            if (!visited.add(cursor)) {
                return;
            }
            Item ancestor = getById(cursor);
            cursor = ancestor.getParentCategory() == null ? null : ancestor.getParentCategory().getId();
        }
    }
}
