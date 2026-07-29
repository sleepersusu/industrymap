package com.profetai.industrymap.service.item;

import com.profetai.industrymap.exceptions.ServerException;
import com.profetai.industrymap.helper.ProvenanceValidator;
import com.profetai.industrymap.model.Item;
import com.profetai.industrymap.model.ItemComposition;
import com.profetai.industrymap.enums.Necessity;
import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.helper.ReviewScopes;
import com.profetai.industrymap.payloads.item.ComponentNode;
import com.profetai.industrymap.payloads.item.CompositionResponse;
import com.profetai.industrymap.payloads.item.CreateCompositionRequest;
import com.profetai.industrymap.repository.ItemCompositionRepository;
import com.profetai.industrymap.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * part-of 組成關係的建立與查詢。
 *
 * <p>循環偵測放在這一層（design D10）：PostgreSQL 沒有原生的 DAG 約束，
 * 自寫 trigger 維護成本高且難測試，因此所有寫入路徑都必須經過本服務。</p>
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class ItemCompositionService {

    private final ItemRepository itemRepository;
    private final ItemCompositionRepository itemCompositionRepository;

    /**
     * 建立組成關係。
     *
     * @throws ServerException 節點不存在（404）、關係重複或會造成循環（409）
     */
    @Transactional
    public ItemComposition create(Long parentItemId, CreateCompositionRequest request) {
        ProvenanceValidator.validate(request.getProvenance());
        // 必要性缺漏時整筆組成關係沒有意義（品類層的組成並非總是成立），先擋在最前面
        if (request.getNecessity() == null) {
            throw new ServerException("組成關係必須指定必要性", HttpStatus.BAD_REQUEST);
        }

        Item parent = getItem(parentItemId);
        Item child = getItem(request.getChildItemId());

        if (itemCompositionRepository.existsByParentItemIdAndChildItemId(parent.getId(), child.getId())) {
            throw new ServerException("組成關係已存在：" + parent.getId() + " → " + child.getId(), HttpStatus.CONFLICT);
        }
        if (wouldFormCycle(parent.getId(), child.getId())) {
            throw new ServerException(
                    "組成關係會造成循環：" + parent.getId() + " → " + child.getId(), HttpStatus.CONFLICT);
        }

        ItemComposition composition = ItemComposition.builder()
                .parentItem(parent)
                .childItem(child)
                .necessity(request.getNecessity())
                .sourceType(request.getProvenance().getSourceType())
                .sourceDetail(request.getProvenance().getSourceDetail())
                .confidence(request.getProvenance().getConfidence())
                .build();

        ItemComposition saved = itemCompositionRepository.save(composition);
        log.info("建立組成關係 parentItemId={} childItemId={} necessity={}",
                parent.getId(), child.getId(), request.getNecessity());
        return saved;
    }

    /**
     * 查某節點的組成關係，上下兩個方向都回傳。
     *
     * <p>組成樹回應只給節點 id，拿不到關係本身的定位資訊，這一類資料因此無法經 API 審核；
     * 本方法把關係逐筆攤平回傳，補上這一角（見 spec「組成關係可經查詢取得」）。</p>
     *
     * <p>刻意不只回下行邊：產業地圖的主要使用方向是「從零件往上看」，只回下行邊時，
     * 站在葉節點的呼叫端就取不到那筆把它接上去的關係，也就無從審核它——除非它另外知道
     * 上層節點的 id，而那正是本 change 要消除的前提。</p>
     *
     * <p>回傳 payload 而非 entity：上下層節點是 LAZY 關聯，open-in-view 為 false，
     * 組裝必須留在這個交易內。</p>
     *
     * @param includeDrafts 是否納入草稿關係；已駁回一律不外露
     * @throws ServerException 節點不存在或已被駁回（404）
     */
    @Transactional(readOnly = true)
    public List<CompositionResponse> findCompositions(Long itemId, boolean includeDrafts) {
        getVisibleItem(itemId);
        Set<ReviewStatus> statuses = ReviewScopes.visibleStatuses(includeDrafts);

        // 兩個方向的結果不會重疊：DB 已擋掉自我循環，同一筆關係不可能同時以本節點為上層與下層
        return Stream.concat(
                        itemCompositionRepository.findByParentItemIdAndReviewStatusIn(itemId, statuses).stream(),
                        itemCompositionRepository.findByChildItemIdAndReviewStatusIn(itemId, statuses).stream())
                // 關係的狀態與它兩端節點的狀態是兩回事：一筆已驗證的關係可能指向事後被駁回的節點，
                // 此時關係過得了 statuses，另一端的節點卻不該外露——回應只有 id，照樣是已駁回資料外露
                .filter(this::pointsAtExposableItems)
                .map(CompositionResponse::from)
                .toList();
    }

    private boolean pointsAtExposableItems(ItemComposition composition) {
        return ReviewScopes.isExposable(composition.getParentItem().getReviewStatus())
                && ReviewScopes.isExposable(composition.getChildItem().getReviewStatus());
    }

    /**
     * 展開組成樹。
     *
     * <p>深度由呼叫端指定而非一次拉出整張圖——節點是全站共用的 DAG，
     * 不限深度時一台腳踏車可能牽出整個電子業。</p>
     *
     * @param depth           展開層數，至少 1
     * @param necessityFilter 只取特定必要性的組成；null 表示不過濾
     * @param includeDrafts   是否納入草稿關係；預設 false 只回已驗證，已駁回一律不外露
     * @throws ServerException 節點不存在或已被駁回（404）
     */
    @Transactional(readOnly = true)
    public ComponentNode expandTree(Long itemId, int depth, Necessity necessityFilter, boolean includeDrafts) {
        Item root = getVisibleItem(itemId);
        return buildNode(root, null, depth, necessityFilter, ReviewScopes.visibleStatuses(includeDrafts));
    }

    private ComponentNode buildNode(Item item, Necessity necessity, int remainingDepth,
                                    Necessity necessityFilter, Set<ReviewStatus> statuses) {
        // 關係的狀態與節點自身的狀態是兩回事：一筆已驗證的關係可能指向事後被駁回的節點，
        // 此時關係過得了 statuses，節點卻不該外露，整枝一併略過
        List<ComponentNode> children = remainingDepth <= 0
                ? List.of()
                : itemCompositionRepository.findByParentItemIdAndReviewStatusIn(item.getId(), statuses).stream()
                        .filter(edge -> necessityFilter == null || edge.getNecessity() == necessityFilter)
                        .filter(edge -> ReviewScopes.isExposable(edge.getChildItem().getReviewStatus()))
                        .map(edge -> buildNode(edge.getChildItem(), edge.getNecessity(),
                                remainingDepth - 1, necessityFilter, statuses))
                        .toList();

        return ComponentNode.builder()
                .itemId(item.getId())
                .displayName(item.getDisplayName())
                .endProduct(item.isEndProduct())
                .necessity(necessity)
                .reviewStatus(item.getReviewStatus())
                .children(children)
                .build();
    }

    /**
     * 由零件回溯所有可達的終端成品——這是產業地圖的核心價值：
     * 「某公司做的這顆零件，最後裝進了哪些產品」。
     *
     * <p>節點全站共用（design D3）才讓這個查詢有意義；若每個上層各自複製節點，
     * 跨產業關聯就查不出來。中間節點（主機板）不算終端成品，不列入結果。</p>
     *
     * @throws ServerException 節點不存在或已被駁回（404）
     */
    @Transactional(readOnly = true)
    public List<Item> findReachableEndProducts(Long itemId, boolean includeDrafts) {
        getVisibleItem(itemId);
        Set<ReviewStatus> statuses = ReviewScopes.visibleStatuses(includeDrafts);

        List<Item> endProducts = new ArrayList<>();
        Deque<Long> pending = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        pending.push(itemId);
        visited.add(itemId);

        while (!pending.isEmpty()) {
            Long current = pending.pop();
            for (ItemComposition edge : itemCompositionRepository.findByChildItemIdAndReviewStatusIn(current, statuses)) {
                Item ancestor = edge.getParentItem();
                if (!visited.add(ancestor.getId())) {
                    continue;
                }
                // 節點被駁回代表這個節點本身不成立，經由它往上的路徑同樣不可信，不列入也不續走
                if (!ReviewScopes.isExposable(ancestor.getReviewStatus())) {
                    continue;
                }
                if (ancestor.isEndProduct()) {
                    endProducts.add(ancestor);
                }
                // 終端成品仍可能是別的產品的零件（主機板→PC→…），因此一律繼續向上走
                pending.push(ancestor.getId());
            }
        }
        return endProducts;
    }

    /**
     * 自下層節點出發沿組成關係向下走訪，若可達上層節點則新關係會形成循環。
     *
     * <p>必須走訪多層——A→B、B→C 之後建 C→A 同樣是循環，只檢查直接下層會漏掉。
     * visited 除了避免重複展開，也讓既有資料若已含循環時本方法仍能終止。</p>
     */
    private boolean wouldFormCycle(Long parentItemId, Long childItemId) {
        Deque<Long> pending = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        pending.push(childItemId);
        visited.add(childItemId);

        while (!pending.isEmpty()) {
            Long current = pending.pop();
            for (ItemComposition edge : itemCompositionRepository.findByParentItemId(current)) {
                Long descendantId = edge.getChildItem().getId();
                if (descendantId.equals(parentItemId)) {
                    return true;
                }
                if (visited.add(descendantId)) {
                    pending.push(descendantId);
                }
            }
        }
        return false;
    }

    private Item getItem(Long itemId) {
        return itemRepository.findById(itemId)
                .orElseThrow(() -> new ServerException("查無此品類節點：" + itemId, HttpStatus.NOT_FOUND));
    }

    /**
     * 對外查詢用的節點解析：已駁回的節點視為不存在。
     *
     * <p>與 {@link #getItem} 分開的理由同 {@code ItemService.getById} / {@code getVisibleById}——
     * 建立組成關係仍需取得節點實體本身，而對外查詢不得把已駁回的節點當成存在的資源。
     * 少了這一層，組成樹會把已駁回節點的名稱與狀態直接回出去，同一個節點在
     * {@code GET /api/items/{id}} 卻是 404。</p>
     *
     * @throws ServerException 節點不存在或已被駁回（404）
     */
    private Item getVisibleItem(Long itemId) {
        Item item = getItem(itemId);
        if (!ReviewScopes.isExposable(item.getReviewStatus())) {
            throw new ServerException("查無此品類節點：" + itemId, HttpStatus.NOT_FOUND);
        }
        return item;
    }
}
