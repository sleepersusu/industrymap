package com.profetai.industrymap.service.item;

import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.enums.SourceType;
import com.profetai.industrymap.exceptions.ServerException;
import com.profetai.industrymap.model.HotspotPoint;
import com.profetai.industrymap.model.Item;
import com.profetai.industrymap.model.ItemHotspot;
import com.profetai.industrymap.model.ItemImage;
import com.profetai.industrymap.payloads.ProvenanceRequest;
import com.profetai.industrymap.payloads.item.AmendHotspotRequest;
import com.profetai.industrymap.payloads.item.CreateHotspotRequest;
import com.profetai.industrymap.payloads.item.CreateItemImageRequest;
import com.profetai.industrymap.payloads.item.HotspotPointPayload;
import com.profetai.industrymap.payloads.item.ItemImageResponse;
import com.profetai.industrymap.repository.ItemHotspotRepository;
import com.profetai.industrymap.repository.ItemImageRepository;
import com.profetai.industrymap.repository.ItemRepository;
import com.profetai.industrymap.service.review.ReviewService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 圖片與熱區的建立、修正與查詢。
 *
 * <p>座標驗證在 payload 與 service 兩層各擋一次（design D5）：payload 的 annotation 只在
 * {@code @Valid} 的 HTTP 路徑生效，而日後的 AI 拆解流程會繞過 HTTP 直接呼叫 service。
 * 這支測試驗的是後者那一層——它是唯一擋得住非 HTTP 進入點的東西。</p>
 */
@ExtendWith(MockitoExtension.class)
class ItemImageServiceTest {

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private ItemImageRepository itemImageRepository;

    @Mock
    private ItemHotspotRepository itemHotspotRepository;

    @Mock
    private ReviewService reviewService;

    @InjectMocks
    private ItemImageService itemImageService;

    private final Item bike = Item.builder().id(1L).normalizedName("腳踏車").displayName("腳踏車")
            .isEndProduct(true).reviewStatus(ReviewStatus.VERIFIED).build();
    private final Item brake = Item.builder().id(2L).normalizedName("煞車").displayName("煞車")
            .reviewStatus(ReviewStatus.VERIFIED).build();

    // ---------------------------------------------------------------------
    // 建立圖片
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("建立圖片時同一節點的視角標籤重複應拋出 409 ServerException")
    void createImage_duplicatedViewLabel_shouldThrowConflict() {
        when(itemRepository.findById(1L)).thenReturn(Optional.of(bike));
        when(itemImageRepository.findByItemIdAndViewLabel(1L, "爆炸圖"))
                .thenReturn(Optional.of(image(10L, bike, "爆炸圖")));

        ServerException ex = assertThrows(ServerException.class,
                () -> itemImageService.createImage(1L, imageRequest("爆炸圖")));

        assertAll(
                () -> assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus()),
                () -> verify(itemImageRepository, never()).save(any(ItemImage.class)));
    }

    @Test
    @DisplayName("為不存在的節點建立圖片應拋出 404 ServerException")
    void createImage_itemNotFound_shouldThrowNotFound() {
        when(itemRepository.findById(99L)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ServerException.class,
                        () -> itemImageService.createImage(99L, imageRequest("爆炸圖"))).getHttpStatus());
    }

    @Test
    @DisplayName("建立圖片未提供來源類型應拋出 400 ServerException")
    void createImage_missingSourceType_shouldThrowBadRequest() {
        CreateItemImageRequest request = imageRequest("爆炸圖");
        request.setProvenance(ProvenanceRequest.builder().build());

        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ServerException.class,
                        () -> itemImageService.createImage(1L, request)).getHttpStatus());
    }

    @Test
    @DisplayName("建立圖片時視角標籤為全空白應拋出 400 ServerException")
    void createImage_blankViewLabel_shouldThrowBadRequest() {
        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ServerException.class,
                        () -> itemImageService.createImage(1L, imageRequest("   "))).getHttpStatus());
    }

    @Test
    @DisplayName("建立圖片未提供物件儲存位置應拋出 400 ServerException")
    void createImage_blankStorageKey_shouldThrowBadRequest() {
        CreateItemImageRequest request = imageRequest("爆炸圖");
        request.setStorageKey("  ");

        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ServerException.class,
                        () -> itemImageService.createImage(1L, request)).getHttpStatus());
    }

    @Test
    @DisplayName("建立圖片時視角標籤超過長度上限應拋出 400 ServerException")
    void createImage_viewLabelTooLong_shouldThrowBadRequest() {
        // 非 HTTP 進入點沒有 payload annotation 擋著，落到 DB 才撞 VARCHAR(64) 的話是 500 而非 400
        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ServerException.class,
                        () -> itemImageService.createImage(1L, imageRequest("圖".repeat(65)))).getHttpStatus());
    }

    @Test
    @DisplayName("建立圖片時物件儲存位置超過長度上限應拋出 400 ServerException")
    void createImage_storageKeyTooLong_shouldThrowBadRequest() {
        CreateItemImageRequest request = imageRequest("爆炸圖");
        request.setStorageKey("https://cdn.example.com/" + "x".repeat(1024));

        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ServerException.class,
                        () -> itemImageService.createImage(1L, request)).getHttpStatus());
    }

    @Test
    @DisplayName("建立圖片時原圖尺寸為 0 或負值應拋出 400 ServerException")
    void createImage_nonPositiveDimension_shouldThrowBadRequest() {
        CreateItemImageRequest request = imageRequest("爆炸圖");
        request.setWidthPx(0);

        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ServerException.class,
                        () -> itemImageService.createImage(1L, request)).getHttpStatus());
    }

    @Test
    @DisplayName("建立熱區時位置標籤超過長度上限應拋出 400 ServerException")
    void createHotspot_positionLabelTooLong_shouldThrowBadRequest() {
        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ServerException.class,
                        () -> itemImageService.createHotspot(
                                hotspotRequest("煞".repeat(65), triangle()))).getHttpStatus());
    }

    @Test
    @DisplayName("建立圖片應帶入來源欄位並預設為草稿")
    void createImage_validRequest_shouldPersistWithProvenance() {
        when(itemRepository.findById(1L)).thenReturn(Optional.of(bike));
        when(itemImageRepository.findByItemIdAndViewLabel(1L, "爆炸圖")).thenReturn(Optional.empty());
        when(itemImageRepository.save(any(ItemImage.class))).thenAnswer(call -> call.getArgument(0));

        ItemImage created = itemImageService.createImage(1L, imageRequest("爆炸圖"));

        assertAll(
                () -> assertEquals(bike, created.getItem()),
                () -> assertEquals("爆炸圖", created.getViewLabel()),
                () -> assertEquals(SourceType.MANUAL, created.getSourceType()),
                () -> assertEquals(ReviewStatus.DRAFT, created.getReviewStatus()));
    }

    // ---------------------------------------------------------------------
    // 建立熱區
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("同一張圖的位置標籤重複時應拋出 409 ServerException")
    void createHotspot_duplicatedPositionLabel_shouldThrowConflict() {
        ItemImage image = image(10L, bike, "爆炸圖");
        when(itemImageRepository.findWithItemById(10L)).thenReturn(Optional.of(image));
        when(itemRepository.findById(2L)).thenReturn(Optional.of(brake));
        when(itemHotspotRepository.findByItemImageIdAndPositionLabel(10L, "前煞車"))
                .thenReturn(Optional.of(hotspot(20L, image, brake, "前煞車")));

        ServerException ex = assertThrows(ServerException.class,
                () -> itemImageService.createHotspot(hotspotRequest("前煞車", triangle())));

        assertAll(
                () -> assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus()),
                () -> verify(itemHotspotRepository, never()).save(any(ItemHotspot.class)));
    }

    @Test
    @DisplayName("同一張圖上位置標籤不同但指向同一節點的熱區應可建立")
    void createHotspot_sameChildItemDifferentPositionLabel_shouldPersist() {
        // design D2 的核心：唯一鍵不含 child_item_id，前後煞車因此可以並存
        ItemImage image = image(10L, bike, "爆炸圖");
        when(itemImageRepository.findWithItemById(10L)).thenReturn(Optional.of(image));
        when(itemRepository.findById(2L)).thenReturn(Optional.of(brake));
        when(itemHotspotRepository.findByItemImageIdAndPositionLabel(10L, "後煞車")).thenReturn(Optional.empty());
        when(itemHotspotRepository.save(any(ItemHotspot.class))).thenAnswer(call -> call.getArgument(0));

        ItemHotspot created = itemImageService.createHotspot(hotspotRequest("後煞車", triangle()));

        assertAll(
                () -> assertEquals(brake, created.getChildItem()),
                () -> assertEquals("後煞車", created.getPositionLabel()),
                () -> assertEquals(ReviewStatus.DRAFT, created.getReviewStatus()));
    }

    @Test
    @DisplayName("為不存在的圖片建立熱區應拋出 404 ServerException")
    void createHotspot_imageNotFound_shouldThrowNotFound() {
        when(itemImageRepository.findWithItemById(10L)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ServerException.class,
                        () -> itemImageService.createHotspot(hotspotRequest("前煞車", triangle()))).getHttpStatus());
    }

    @Test
    @DisplayName("熱區指向不存在的節點應拋出 404 ServerException")
    void createHotspot_childItemNotFound_shouldThrowNotFound() {
        when(itemImageRepository.findWithItemById(10L)).thenReturn(Optional.of(image(10L, bike, "爆炸圖")));
        when(itemRepository.findById(2L)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ServerException.class,
                        () -> itemImageService.createHotspot(hotspotRequest("前煞車", triangle()))).getHttpStatus());
    }

    @Test
    @DisplayName("熱區位置標籤為全空白應拋出 400 ServerException")
    void createHotspot_blankPositionLabel_shouldThrowBadRequest() {
        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ServerException.class,
                        () -> itemImageService.createHotspot(hotspotRequest("   ", triangle()))).getHttpStatus());
    }

    @Test
    @DisplayName("熱區座標少於三點應拋出 400 ServerException")
    void createHotspot_polygonWithTwoPoints_shouldThrowBadRequest() {
        List<HotspotPointPayload> twoPoints = List.of(point(0.1, 0.1), point(0.2, 0.2));

        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ServerException.class,
                        () -> itemImageService.createHotspot(hotspotRequest("前煞車", twoPoints))).getHttpStatus());
    }

    @Test
    @DisplayName("熱區座標小於 0 應拋出 400 ServerException")
    void createHotspot_coordinateBelowZero_shouldThrowBadRequest() {
        List<HotspotPointPayload> polygon = List.of(point(-0.01, 0.1), point(0.2, 0.2), point(0.3, 0.3));

        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ServerException.class,
                        () -> itemImageService.createHotspot(hotspotRequest("前煞車", polygon))).getHttpStatus());
    }

    @Test
    @DisplayName("熱區座標大於 1 應拋出 400 ServerException")
    void createHotspot_coordinateAboveOne_shouldThrowBadRequest() {
        List<HotspotPointPayload> polygon = List.of(point(0.1, 0.1), point(1.01, 0.2), point(0.3, 0.3));

        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ServerException.class,
                        () -> itemImageService.createHotspot(hotspotRequest("前煞車", polygon))).getHttpStatus());
    }

    @Test
    @DisplayName("熱區座標為 0 與 1 屬合法範圍，應可建立")
    void createHotspot_coordinatesAtBounds_shouldPersist() {
        ItemImage image = image(10L, bike, "爆炸圖");
        when(itemImageRepository.findWithItemById(10L)).thenReturn(Optional.of(image));
        when(itemRepository.findById(2L)).thenReturn(Optional.of(brake));
        when(itemHotspotRepository.findByItemImageIdAndPositionLabel(10L, "前煞車")).thenReturn(Optional.empty());
        when(itemHotspotRepository.save(any(ItemHotspot.class))).thenAnswer(call -> call.getArgument(0));
        List<HotspotPointPayload> polygon = List.of(point(0, 0), point(1, 0), point(1, 1));

        ItemHotspot created = itemImageService.createHotspot(hotspotRequest("前煞車", polygon));

        assertEquals(List.of(new HotspotPoint(0, 0), new HotspotPoint(1, 0), new HotspotPoint(1, 1)),
                created.getPolygon());
    }

    @Test
    @DisplayName("熱區座標缺少 x 或 y 應拋出 400 ServerException")
    void createHotspot_pointWithNullCoordinate_shouldThrowBadRequest() {
        List<HotspotPointPayload> polygon =
                List.of(point(0.1, 0.1), HotspotPointPayload.builder().x(0.2).build(), point(0.3, 0.3));

        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ServerException.class,
                        () -> itemImageService.createHotspot(hotspotRequest("前煞車", polygon))).getHttpStatus());
    }

    // ---------------------------------------------------------------------
    // 修正熱區
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("替換已驗證熱區的座標後審核狀態應退回草稿")
    void amendHotspot_changedPolygon_shouldResetToDraft() {
        ItemHotspot verified = verifiedHotspot();
        when(itemHotspotRepository.findWithAssociationsById(20L)).thenReturn(Optional.of(verified));
        when(itemHotspotRepository.save(any(ItemHotspot.class))).thenAnswer(call -> call.getArgument(0));
        List<HotspotPointPayload> moved = List.of(point(0.4, 0.4), point(0.5, 0.4), point(0.5, 0.5));

        ItemHotspot amended = itemImageService.amendHotspot(20L,
                new AmendHotspotRequest("前煞車", moved));

        assertAll(
                () -> assertEquals(List.of(new HotspotPoint(0.4, 0.4), new HotspotPoint(0.5, 0.4),
                        new HotspotPoint(0.5, 0.5)), amended.getPolygon()),
                () -> verify(reviewService).applyReview(verified, ReviewStatus.DRAFT, null));
    }

    @Test
    @DisplayName("送出與現況完全相同的內容不應改變審核狀態")
    void amendHotspot_unchangedContent_shouldKeepReviewStatus() {
        // 批次重跑或前端重複送出，不該讓一筆沒被改過的已驗證資料無故從對外查詢消失
        ItemHotspot verified = verifiedHotspot();
        when(itemHotspotRepository.findWithAssociationsById(20L)).thenReturn(Optional.of(verified));
        when(itemHotspotRepository.save(any(ItemHotspot.class))).thenAnswer(call -> call.getArgument(0));

        ItemHotspot amended = itemImageService.amendHotspot(20L,
                new AmendHotspotRequest("前煞車", List.of(point(0.1, 0.1), point(0.2, 0.1), point(0.2, 0.2))));

        assertAll(
                () -> assertEquals(ReviewStatus.VERIFIED, amended.getReviewStatus()),
                () -> verify(reviewService, never()).applyReview(any(), any(), any()));
    }

    @Test
    @DisplayName("修正熱區時位置標籤撞到同一張圖的其他熱區應拋出 409 ServerException")
    void amendHotspot_positionLabelTakenOnSameImage_shouldThrowConflict() {
        ItemHotspot verified = verifiedHotspot();
        when(itemHotspotRepository.findWithAssociationsById(20L)).thenReturn(Optional.of(verified));
        when(itemHotspotRepository.findByItemImageIdAndPositionLabel(10L, "後煞車"))
                .thenReturn(Optional.of(hotspot(21L, verified.getItemImage(), brake, "後煞車")));

        ServerException ex = assertThrows(ServerException.class,
                () -> itemImageService.amendHotspot(20L, new AmendHotspotRequest("後煞車", triangle())));

        assertAll(
                () -> assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus()),
                () -> verify(itemHotspotRepository, never()).save(any(ItemHotspot.class)));
    }

    @Test
    @DisplayName("修正不存在的熱區應拋出 404 ServerException")
    void amendHotspot_notFound_shouldThrowNotFound() {
        when(itemHotspotRepository.findWithAssociationsById(99L)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ServerException.class, () -> itemImageService.amendHotspot(99L,
                        new AmendHotspotRequest("前煞車", triangle()))).getHttpStatus());
    }

    @Test
    @DisplayName("修正熱區時座標不合法應拋出 400 ServerException，且不得留下半套變更")
    void amendHotspot_invalidPolygon_shouldThrowBadRequestAndNotPersist() {
        List<HotspotPointPayload> twoPoints = List.of(point(0.1, 0.1), point(0.2, 0.2));

        ServerException ex = assertThrows(ServerException.class,
                () -> itemImageService.amendHotspot(20L, new AmendHotspotRequest("前煞車", twoPoints)));

        assertAll(
                () -> assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus()),
                () -> verify(itemHotspotRepository, never()).save(any(ItemHotspot.class)));
    }

    // ---------------------------------------------------------------------
    // 查詢
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("節點沒有任何圖片時應回空清單而非 404")
    void findImages_noImages_shouldReturnEmptyList() {
        when(itemRepository.findById(1L)).thenReturn(Optional.of(bike));
        when(itemImageRepository.findByItemIdAndReviewStatusInOrderByViewLabelAscIdAsc(anyLong(), anyCollection()))
                .thenReturn(List.of());

        assertTrue(itemImageService.findImages(1L, false).isEmpty());
    }

    @Test
    @DisplayName("查詢不存在節點的圖片應拋出 404 ServerException")
    void findImages_itemNotFound_shouldThrowNotFound() {
        when(itemRepository.findById(99L)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ServerException.class, () -> itemImageService.findImages(99L, false)).getHttpStatus());
    }

    @Test
    @DisplayName("查詢已駁回節點的圖片應拋出 404 ServerException")
    void findImages_rejectedItem_shouldThrowNotFound() {
        Item rejected = Item.builder().id(3L).normalizedName("誤建").displayName("誤建")
                .reviewStatus(ReviewStatus.REJECTED).build();
        when(itemRepository.findById(3L)).thenReturn(Optional.of(rejected));

        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ServerException.class, () -> itemImageService.findImages(3L, false)).getHttpStatus());
    }

    @Test
    @DisplayName("熱區指向已駁回的節點時不得出現在回應中")
    void findImages_hotspotPointingToRejectedItem_shouldNotAppear() {
        // 熱區自身已驗證，不代表它指向的節點還算數——否則使用者點下去會下鑽到一個不該存在的節點
        Item rejectedPart = Item.builder().id(4L).normalizedName("誤建零件").displayName("誤建零件")
                .reviewStatus(ReviewStatus.REJECTED).build();
        ItemImage image = image(10L, bike, "爆炸圖");
        when(itemRepository.findById(1L)).thenReturn(Optional.of(bike));
        when(itemImageRepository.findByItemIdAndReviewStatusInOrderByViewLabelAscIdAsc(anyLong(), anyCollection()))
                .thenReturn(List.of(image));
        when(itemHotspotRepository.findByItemImageIdInAndReviewStatusInOrderByPositionLabelAscIdAsc(
                anyCollection(), anyCollection()))
                .thenReturn(List.of(hotspot(20L, image, brake, "前煞車"),
                        hotspot(21L, image, rejectedPart, "後煞車")));

        List<ItemImageResponse> images = itemImageService.findImages(1L, false);

        assertAll(
                () -> assertEquals(1, images.size()),
                () -> assertEquals(List.of(2L),
                        images.getFirst().getHotspots().stream().map(hotspot -> hotspot.getChildItemId()).toList()));
    }

    @Test
    @DisplayName("圖片的熱區應巢狀在該圖之下一併回傳")
    void findImages_shouldNestHotspotsUnderTheirImage() {
        ItemImage explosion = image(10L, bike, "爆炸圖");
        ItemImage side = image(11L, bike, "側視圖");
        when(itemRepository.findById(1L)).thenReturn(Optional.of(bike));
        when(itemImageRepository.findByItemIdAndReviewStatusInOrderByViewLabelAscIdAsc(anyLong(), anyCollection()))
                .thenReturn(List.of(explosion, side));
        when(itemHotspotRepository.findByItemImageIdInAndReviewStatusInOrderByPositionLabelAscIdAsc(
                anyCollection(), anyCollection()))
                .thenReturn(List.of(hotspot(20L, explosion, brake, "前煞車"),
                        hotspot(21L, side, brake, "煞車")));

        List<ItemImageResponse> images = itemImageService.findImages(1L, false);

        assertAll(
                () -> assertEquals(List.of("前煞車"),
                        images.get(0).getHotspots().stream().map(hotspot -> hotspot.getPositionLabel()).toList()),
                () -> assertEquals(List.of("煞車"),
                        images.get(1).getHotspots().stream().map(hotspot -> hotspot.getPositionLabel()).toList()));
    }

    @Test
    @DisplayName("沒有熱區的圖片應回空陣列而非 null")
    void findImages_imageWithoutHotspots_shouldReturnEmptyArray() {
        ItemImage image = image(10L, bike, "爆炸圖");
        when(itemRepository.findById(1L)).thenReturn(Optional.of(bike));
        when(itemImageRepository.findByItemIdAndReviewStatusInOrderByViewLabelAscIdAsc(anyLong(), anyCollection()))
                .thenReturn(List.of(image));
        when(itemHotspotRepository.findByItemImageIdInAndReviewStatusInOrderByPositionLabelAscIdAsc(
                anyCollection(), anyCollection()))
                .thenReturn(List.of());

        List<ItemImageResponse> images = itemImageService.findImages(1L, false);

        assertTrue(images.getFirst().getHotspots().isEmpty());
    }

    // ---------------------------------------------------------------------
    // fixture
    // ---------------------------------------------------------------------

    private CreateItemImageRequest imageRequest(String viewLabel) {
        return CreateItemImageRequest.builder()
                .viewLabel(viewLabel)
                .storageKey("https://cdn.example.com/bike.png")
                .widthPx(1200)
                .heightPx(800)
                .provenance(ProvenanceRequest.builder().sourceType(SourceType.MANUAL).build())
                .build();
    }

    private CreateHotspotRequest hotspotRequest(String positionLabel, List<HotspotPointPayload> polygon) {
        return CreateHotspotRequest.builder()
                .itemImageId(10L)
                .childItemId(2L)
                .positionLabel(positionLabel)
                .polygon(polygon)
                .provenance(ProvenanceRequest.builder().sourceType(SourceType.MANUAL).build())
                .build();
    }

    private List<HotspotPointPayload> triangle() {
        return List.of(point(0.1, 0.1), point(0.2, 0.1), point(0.2, 0.2));
    }

    private HotspotPointPayload point(double x, double y) {
        return HotspotPointPayload.builder().x(x).y(y).build();
    }

    private ItemImage image(Long id, Item item, String viewLabel) {
        return ItemImage.builder()
                .id(id)
                .item(item)
                .viewLabel(viewLabel)
                .storageKey("https://cdn.example.com/" + id + ".png")
                .sourceType(SourceType.MANUAL)
                .reviewStatus(ReviewStatus.VERIFIED)
                .build();
    }

    private ItemHotspot hotspot(Long id, ItemImage image, Item childItem, String positionLabel) {
        return ItemHotspot.builder()
                .id(id)
                .itemImage(image)
                .childItem(childItem)
                .positionLabel(positionLabel)
                .polygon(List.of(new HotspotPoint(0.1, 0.1), new HotspotPoint(0.2, 0.1), new HotspotPoint(0.2, 0.2)))
                .sourceType(SourceType.MANUAL)
                .reviewStatus(ReviewStatus.VERIFIED)
                .build();
    }

    private ItemHotspot verifiedHotspot() {
        ItemHotspot hotspot = hotspot(20L, image(10L, bike, "爆炸圖"), brake, "前煞車");
        hotspot.setReviewedBy("reviewer");
        hotspot.setReviewedAt(Instant.now());
        return hotspot;
    }
}
