package com.profetai.industrymap.service.item;

import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.exceptions.ServerException;
import com.profetai.industrymap.helper.ProvenanceValidator;
import com.profetai.industrymap.helper.ReviewScopes;
import com.profetai.industrymap.model.HotspotPoint;
import com.profetai.industrymap.model.Item;
import com.profetai.industrymap.model.ItemHotspot;
import com.profetai.industrymap.model.ItemImage;
import com.profetai.industrymap.payloads.item.AmendHotspotRequest;
import com.profetai.industrymap.payloads.item.CreateHotspotRequest;
import com.profetai.industrymap.payloads.item.CreateItemImageRequest;
import com.profetai.industrymap.payloads.item.HotspotPointPayload;
import com.profetai.industrymap.payloads.item.HotspotResponse;
import com.profetai.industrymap.payloads.item.ItemImageResponse;
import com.profetai.industrymap.repository.ItemHotspotRepository;
import com.profetai.industrymap.repository.ItemImageRepository;
import com.profetai.industrymap.repository.ItemRepository;
import com.profetai.industrymap.service.review.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 品類節點的圖片與熱區（design D2～D9）：互動爆炸圖的資料層。
 *
 * <p>驗證刻意在這一層再擋一次，而不是只靠 payload 的 jakarta validation annotation：
 * 那組 annotation 只在 {@code @Valid} 的 HTTP 路徑生效，日後的 AI 拆解流程會繞過 HTTP
 * 直接呼叫本服務（design D5）。座標的點數與範圍 PostgreSQL 也表達不了（JSONB），
 * 因此這裡是非 HTTP 進入點唯一擋得住的地方。</p>
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class ItemImageService {

    /** 多邊形的最少點數：兩點只是一條線，圍不出可點擊的區域 */
    private static final int MIN_POLYGON_POINTS = 3;
    private static final double MIN_COORDINATE = 0.0;
    private static final double MAX_COORDINATE = 1.0;

    /** 對齊 migration 的欄位寬度：這裡不擋，非 HTTP 進入點就會落到 DB 才撞 VARCHAR，錯誤變成 500 */
    private static final int MAX_LABEL_LENGTH = 64;
    private static final int MAX_STORAGE_KEY_LENGTH = 1024;

    private final ItemRepository itemRepository;
    private final ItemImageRepository itemImageRepository;
    private final ItemHotspotRepository itemHotspotRepository;
    private final ReviewService reviewService;

    /**
     * 為品類節點掛一張圖。
     *
     * @throws ServerException 標籤或位置缺漏（400）、節點不存在（404）、同節點同視角標籤重複（409）
     */
    @Transactional
    public ItemImage createImage(Long itemId, CreateItemImageRequest request) {
        ProvenanceValidator.validate(request.getProvenance());
        requireText(request.getViewLabel(), "圖片必須指定視角標籤", MAX_LABEL_LENGTH);
        requireText(request.getStorageKey(), "圖片必須指定物件儲存位置", MAX_STORAGE_KEY_LENGTH);
        requirePositiveOrNull(request.getWidthPx(), "原圖寬");
        requirePositiveOrNull(request.getHeightPx(), "原圖高");

        Item item = getItem(itemId);
        itemImageRepository.findByItemIdAndViewLabel(item.getId(), request.getViewLabel())
                .ifPresent(existing -> {
                    throw new ServerException(
                            "此節點已有同一視角標籤的圖片：" + request.getViewLabel(), HttpStatus.CONFLICT);
                });

        ItemImage image = ItemImage.builder()
                .item(item)
                .viewLabel(request.getViewLabel())
                .storageKey(request.getStorageKey())
                .widthPx(request.getWidthPx())
                .heightPx(request.getHeightPx())
                .sourceType(request.getProvenance().getSourceType())
                .sourceDetail(request.getProvenance().getSourceDetail())
                .confidence(request.getProvenance().getConfidence())
                .build();

        ItemImage saved = itemImageRepository.save(image);
        log.info("建立節點圖片 itemId={} viewLabel={}", item.getId(), request.getViewLabel());
        return saved;
    }

    /**
     * 在一張圖上標記一個熱區。
     *
     * <p>唯一性只看「這張圖 + 這個位置標籤」（design D3）：同一張圖上兩個位置標籤不同的熱區
     * 可以指向同一個節點，那正是位置標籤存在的理由。</p>
     *
     * @throws ServerException 標籤或座標不合法（400）、圖片或節點不存在（404）、同圖位置標籤重複（409）
     */
    @Transactional
    public ItemHotspot createHotspot(CreateHotspotRequest request) {
        ProvenanceValidator.validate(request.getProvenance());
        requireText(request.getPositionLabel(), "熱區必須指定位置標籤", MAX_LABEL_LENGTH);
        List<HotspotPoint> polygon = toPolygon(request.getPolygon());

        ItemImage image = getImage(request.getItemImageId());
        Item childItem = getItem(request.getChildItemId());
        assertPositionLabelAvailable(image.getId(), request.getPositionLabel(), null);

        ItemHotspot hotspot = ItemHotspot.builder()
                .itemImage(image)
                .childItem(childItem)
                .positionLabel(request.getPositionLabel())
                .polygon(polygon)
                .sourceType(request.getProvenance().getSourceType())
                .sourceDetail(request.getProvenance().getSourceDetail())
                .confidence(request.getProvenance().getConfidence())
                .build();

        ItemHotspot saved = itemHotspotRepository.save(hotspot);
        log.info("建立圖片熱區 itemImageId={} positionLabel={} childItemId={}",
                image.getId(), request.getPositionLabel(), childItem.getId());
        return saved;
    }

    /**
     * 修正熱區：全量替換位置標籤與座標（design D7）。
     *
     * <p>內容確實變更後審核狀態退回草稿，與 {@code ItemService.amend} 同一套語意——
     * 已驗證的內容不該被靜默改寫。送進來的值與現況完全相同時不動狀態，
     * 否則批次重跑會讓一筆沒被改過的資料無故從對外查詢消失。</p>
     *
     * <p>刻意不提供實體刪除：拿掉一個畫錯的熱區走審核駁回，那條路已經存在，
     * 且保留了「誰在何時拿掉它」的軌跡。</p>
     *
     * @throws ServerException 標籤或座標不合法（400）、熱區不存在（404）、位置標籤與同圖其他熱區衝突（409）
     */
    @Transactional
    public ItemHotspot amendHotspot(Long hotspotId, AmendHotspotRequest request) {
        // 檢查一律排在寫入之前：被拒絕的修正不得留下半套變更
        requireText(request.getPositionLabel(), "熱區必須指定位置標籤", MAX_LABEL_LENGTH);
        List<HotspotPoint> polygon = toPolygon(request.getPolygon());

        // 一併載入圖片與所指向的節點：回應在交易外組裝，LAZY 關聯到那時已經取不到
        // （見 ItemHotspotRepository#findWithAssociationsById）
        ItemHotspot hotspot = itemHotspotRepository.findWithAssociationsById(hotspotId)
                .orElseThrow(() -> new ServerException("查無此熱區：" + hotspotId, HttpStatus.NOT_FOUND));
        boolean labelChanged = !request.getPositionLabel().equals(hotspot.getPositionLabel());
        if (labelChanged) {
            assertPositionLabelAvailable(
                    hotspot.getItemImage().getId(), request.getPositionLabel(), hotspot.getId());
        }

        boolean contentChanged = labelChanged || !polygon.equals(hotspot.getPolygon());
        hotspot.setPositionLabel(request.getPositionLabel());
        hotspot.setPolygon(polygon);
        if (contentChanged) {
            // 沿用審核服務退回草稿，殘留的審核者與審核時間一併清掉——
            // 這筆內容已經不是當初被審過的那一筆了
            reviewService.applyReview(hotspot, ReviewStatus.DRAFT, null);
        }

        ItemHotspot saved = itemHotspotRepository.save(hotspot);
        log.info("修正圖片熱區 hotspotId={} positionLabel={} contentChanged={} reviewStatus={}",
                saved.getId(), saved.getPositionLabel(), contentChanged, saved.getReviewStatus());
        return saved;
    }

    /**
     * 取節點的圖片，每張巢狀帶其熱區（design D6）。
     *
     * <p>四層審核過濾（design D8），缺一層就會有已駁回資料外露：</p>
     * <ol>
     *   <li>路徑上的節點——<b>實體</b>，只擋 REJECTED；已駁回時與不存在的節點完全一致（404）</li>
     *   <li>圖片——<b>關係</b>（「這個節點有這張圖」），跟呼叫端的 {@code includeDrafts} 走</li>
     *   <li>熱區——<b>關係</b>（「這塊區域是這個零件」），同上</li>
     *   <li>熱區指向的節點——<b>實體</b>，只擋 REJECTED。熱區已驗證不代表它指向的節點還算數，
     *       少了這一層，使用者點下去會下鑽到一個對外不存在的節點</li>
     * </ol>
     *
     * <p>熱區以一次查詢撈齊所有圖片的（design D9），不逐張圖各查一次；
     * 回傳 payload 而非 entity：關聯都是 LAZY 而 open-in-view 為 false，組裝必須留在交易內。</p>
     *
     * @throws ServerException 節點不存在或已被駁回（404）
     */
    @Transactional(readOnly = true)
    public List<ItemImageResponse> findImages(Long itemId, boolean includeDrafts) {
        Item item = getItem(itemId);
        if (!ReviewScopes.isExposable(item.getReviewStatus())) {
            throw new ServerException("查無此品類節點：" + itemId, HttpStatus.NOT_FOUND);
        }

        Set<ReviewStatus> visibleStatuses = ReviewScopes.visibleStatuses(includeDrafts);
        List<ItemImage> images =
                itemImageRepository.findByItemIdAndReviewStatusInOrderByViewLabelAscIdAsc(itemId, visibleStatuses);
        if (images.isEmpty()) {
            return List.of();
        }

        Map<Long, List<HotspotResponse>> hotspotsByImage = itemHotspotRepository
                .findByItemImageIdInAndReviewStatusInOrderByPositionLabelAscIdAsc(
                        images.stream().map(ItemImage::getId).toList(), visibleStatuses)
                .stream()
                .filter(hotspot -> ReviewScopes.isExposable(hotspot.getChildItem().getReviewStatus()))
                .collect(Collectors.groupingBy(hotspot -> hotspot.getItemImage().getId(),
                        Collectors.mapping(HotspotResponse::from, Collectors.toList())));

        return images.stream()
                .map(image -> ItemImageResponse.from(image, hotspotsByImage.getOrDefault(image.getId(), List.of())))
                .toList();
    }

    /**
     * 位置標籤在同一張圖內唯一。
     *
     * @param excludedHotspotId 修正時要排除的自己；建立時傳 null
     * @throws ServerException 標籤已被同一張圖的其他熱區使用（409）
     */
    private void assertPositionLabelAvailable(Long itemImageId, String positionLabel, Long excludedHotspotId) {
        boolean taken = itemHotspotRepository.findByItemImageIdAndPositionLabel(itemImageId, positionLabel)
                .filter(existing -> !existing.getId().equals(excludedHotspotId))
                .isPresent();
        if (taken) {
            throw new ServerException("此圖片已有同一位置標籤的熱區：" + positionLabel, HttpStatus.CONFLICT);
        }
    }

    /**
     * 座標驗證與轉換（design D5）：至少三點，每個座標值落在 0 至 1 之間（含端點）。
     *
     * @throws ServerException 點數不足、缺座標值或超出範圍（皆為 400）
     */
    private List<HotspotPoint> toPolygon(List<HotspotPointPayload> points) {
        if (points == null || points.size() < MIN_POLYGON_POINTS) {
            throw new ServerException("熱區座標至少需要 " + MIN_POLYGON_POINTS + " 個點", HttpStatus.BAD_REQUEST);
        }
        return points.stream().map(this::toPoint).toList();
    }

    private HotspotPoint toPoint(HotspotPointPayload point) {
        if (point == null || point.getX() == null || point.getY() == null) {
            throw new ServerException("熱區座標的 x 與 y 皆為必填", HttpStatus.BAD_REQUEST);
        }
        assertInRange(point.getX());
        assertInRange(point.getY());
        return new HotspotPoint(point.getX(), point.getY());
    }

    /** 相對比例才讓熱區在換圖或縮放後仍對得準；超出 0–1 的值必然是誤把絕對像素填進來 */
    private void assertInRange(double coordinate) {
        if (coordinate < MIN_COORDINATE || coordinate > MAX_COORDINATE) {
            throw new ServerException("熱區座標必須介於 0 至 1 之間：" + coordinate, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 必填文字欄位的檢查：空白與長度一起擋。
     *
     * <p>長度上限對齊 migration 的欄位寬度——payload 的 {@code @Size} 只在 HTTP 路徑生效，
     * 少了這一層，繞過 HTTP 的寫入會一路走到 save 才撞 VARCHAR，
     * 呼叫端拿到的是 500 而不是「你的標籤太長」。</p>
     */
    private void requireText(String value, String message, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new ServerException(message, HttpStatus.BAD_REQUEST);
        }
        if (value.length() > maxLength) {
            throw new ServerException(message + "，且長度不得超過 " + maxLength + " 字", HttpStatus.BAD_REQUEST);
        }
    }

    /** 原圖尺寸可不填，填了就必須為正——DB 有同名的 check constraint，理由同 {@link #requireText} */
    private void requirePositiveOrNull(Integer value, String label) {
        if (value != null && value <= 0) {
            throw new ServerException(label + "必須大於 0", HttpStatus.BAD_REQUEST);
        }
    }

    private Item getItem(Long itemId) {
        return itemRepository.findById(itemId)
                .orElseThrow(() -> new ServerException("查無此品類節點：" + itemId, HttpStatus.NOT_FOUND));
    }

    /**
     * 取圖片，並一併載入所屬節點——熱區的自然鍵含它，而批次建立會在交易外組裝回應
     * （見 {@code ItemImageRepository#findWithItemById}）。
     */
    private ItemImage getImage(Long itemImageId) {
        return itemImageRepository.findWithItemById(itemImageId)
                .orElseThrow(() -> new ServerException("查無此圖片：" + itemImageId, HttpStatus.NOT_FOUND));
    }
}
