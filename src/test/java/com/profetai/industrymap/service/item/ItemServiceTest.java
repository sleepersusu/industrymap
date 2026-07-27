package com.profetai.industrymap.service.item;

import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.enums.SourceType;
import com.profetai.industrymap.exceptions.ServerException;
import com.profetai.industrymap.model.Item;
import com.profetai.industrymap.model.ItemAlias;
import com.profetai.industrymap.payloads.PageResponse;
import com.profetai.industrymap.payloads.ProvenanceRequest;
import com.profetai.industrymap.payloads.item.AmendItemRequest;
import com.profetai.industrymap.payloads.item.CreateItemAliasRequest;
import com.profetai.industrymap.payloads.item.CreateItemRequest;
import com.profetai.industrymap.payloads.item.EndProductQuery;
import com.profetai.industrymap.payloads.item.ItemResponse;
import com.profetai.industrymap.repository.ItemAliasRepository;
import com.profetai.industrymap.repository.ItemRepository;
import com.profetai.industrymap.service.review.ReviewService;
import com.profetai.industrymap.util.NameNormalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private ItemAliasRepository itemAliasRepository;

    /** 審核狀態流轉沿用真實邏輯：這裡要驗的是「修正後有沒有退回草稿」，mock 掉就等於什麼都沒驗 */
    @Spy
    private ReviewService reviewService = new ReviewService();

    @InjectMocks
    private ItemService itemService;

    @Test
    @DisplayName("建立 item 時正規化名稱與既有節點重複應拋出 409 ServerException")
    void create_duplicatedNormalizedName_shouldThrowConflict() {
        // Given：既有節點「WiFi模組」，新請求用「wifi 模組」——正規化後相同
        Item existing = Item.builder().id(1L).normalizedName("wifi模組").displayName("WiFi模組").build();
        when(itemRepository.findByNormalizedName("wifi模組")).thenReturn(Optional.of(existing));

        // When
        ServerException ex = assertThrows(ServerException.class,
                () -> itemService.create(createItemRequest("wifi 模組")));

        // Then
        assertAll(
                () -> assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus()),
                () -> verify(itemRepository, never()).save(any(Item.class)));
    }

    @Test
    @DisplayName("建立 item 時名稱已被登記為別名應拋出 409 ServerException")
    void create_nameAlreadyRegisteredAsAlias_shouldThrowConflict() {
        // Given：「無線網卡」已是別的節點的別名——名稱與別名共用同一組衝突規則，
        // 否則新節點的 normalized_name 會與既有別名重複，名稱解析從此指向錯誤節點
        when(itemRepository.findByNormalizedName("無線網卡")).thenReturn(Optional.empty());
        when(itemAliasRepository.existsByNormalizedAlias("無線網卡")).thenReturn(true);

        // When
        ServerException ex = assertThrows(ServerException.class,
                () -> itemService.create(createItemRequest("無線網卡")));

        // Then
        assertAll(
                () -> assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus()),
                () -> verify(itemRepository, never()).save(any(Item.class)));
    }

    @Test
    @DisplayName("建立 item 應同時存下正規化名稱與原始顯示名稱")
    void create_newName_shouldPersistNormalizedAndDisplayName() {
        // Given
        when(itemRepository.findByNormalizedName("wifi模組")).thenReturn(Optional.empty());
        when(itemAliasRepository.existsByNormalizedAlias("wifi模組")).thenReturn(false);
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Item created = itemService.create(createItemRequest("WiFi 模組"));

        // Then
        assertAll(
                () -> assertEquals("wifi模組", created.getNormalizedName()),
                () -> assertEquals("WiFi 模組", created.getDisplayName()),
                () -> assertEquals(SourceType.MANUAL, created.getSourceType()));
    }

    @Test
    @DisplayName("以已登記的別名查詢應回傳對應的既有節點")
    void resolveByName_matchingAlias_shouldReturnAliasedItem() {
        // Given：「無線網卡」是 WiFi 模組的別名，本身不是任何節點的名稱
        Item wifiModule = Item.builder().id(1L).normalizedName("wifi模組").displayName("WiFi 模組").build();
        when(itemRepository.findByNormalizedName("無線網卡")).thenReturn(Optional.empty());
        when(itemAliasRepository.findByNormalizedAlias("無線網卡"))
                .thenReturn(Optional.of(ItemAlias.builder().item(wifiModule).normalizedAlias("無線網卡").build()));

        // When
        Optional<Item> resolved = itemService.resolveByName("無線網卡");

        // Then
        assertEquals(wifiModule, resolved.orElse(null));
    }

    @Test
    @DisplayName("以節點本名查詢應直接命中，不需再查別名表")
    void resolveByName_matchingItemName_shouldReturnItemWithoutAliasLookup() {
        Item wifiModule = Item.builder().id(1L).normalizedName("wifi模組").displayName("WiFi 模組").build();
        when(itemRepository.findByNormalizedName("wifi模組")).thenReturn(Optional.of(wifiModule));

        Optional<Item> resolved = itemService.resolveByName("ＷｉＦｉ模組");

        assertAll(
                () -> assertEquals(wifiModule, resolved.orElse(null)),
                () -> verify(itemAliasRepository, never()).findByNormalizedAlias(any()));
    }

    @Test
    @DisplayName("名稱與別名皆無命中時應回傳空 Optional")
    void resolveByName_noMatch_shouldReturnEmpty() {
        when(itemRepository.findByNormalizedName("不存在的東西")).thenReturn(Optional.empty());
        when(itemAliasRepository.findByNormalizedAlias("不存在的東西")).thenReturn(Optional.empty());

        assertTrue(itemService.resolveByName("不存在的東西").isEmpty());
    }

    @Test
    @DisplayName("別名與另一個節點的正規化名稱衝突時應拋出 409 ServerException")
    void addAlias_aliasCollidesWithAnotherItemName_shouldThrowConflict() {
        // Given：要把「天線」登記成 WiFi 模組的別名，但「天線」本身已是另一個節點
        Item wifiModule = Item.builder().id(1L).normalizedName("wifi模組").displayName("WiFi 模組").build();
        Item antenna = Item.builder().id(2L).normalizedName("天線").displayName("天線").build();
        when(itemRepository.findById(1L)).thenReturn(Optional.of(wifiModule));
        when(itemRepository.findByNormalizedName("天線")).thenReturn(Optional.of(antenna));

        // When
        ServerException ex = assertThrows(ServerException.class,
                () -> itemService.addAlias(1L, createAliasRequest("天線")));

        // Then
        assertAll(
                () -> assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus()),
                () -> verify(itemAliasRepository, never()).save(any(ItemAlias.class)));
    }

    @Test
    @DisplayName("別名已被其他節點登記時應拋出 409 ServerException")
    void addAlias_aliasAlreadyRegistered_shouldThrowConflict() {
        Item wifiModule = Item.builder().id(1L).normalizedName("wifi模組").displayName("WiFi 模組").build();
        when(itemRepository.findById(1L)).thenReturn(Optional.of(wifiModule));
        when(itemRepository.findByNormalizedName("wlanmodule")).thenReturn(Optional.empty());
        when(itemAliasRepository.existsByNormalizedAlias("wlanmodule")).thenReturn(true);

        ServerException ex = assertThrows(ServerException.class,
                () -> itemService.addAlias(1L, createAliasRequest("WLAN Module")));

        assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
    }

    @Test
    @DisplayName("別名等於該節點自身名稱時不視為衝突，可正常登記")
    void addAlias_aliasEqualsOwnItemName_shouldPersist() {
        Item wifiModule = Item.builder().id(1L).normalizedName("wifi模組").displayName("WiFi 模組").build();
        when(itemRepository.findById(1L)).thenReturn(Optional.of(wifiModule));
        when(itemRepository.findByNormalizedName("wifi模組")).thenReturn(Optional.of(wifiModule));
        when(itemAliasRepository.existsByNormalizedAlias("wifi模組")).thenReturn(false);
        when(itemAliasRepository.save(any(ItemAlias.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ItemAlias created = itemService.addAlias(1L, createAliasRequest("ＷｉＦｉ模組"));

        assertAll(
                () -> assertEquals("wifi模組", created.getNormalizedAlias()),
                () -> assertEquals("ＷｉＦｉ模組", created.getDisplayAlias()),
                () -> assertEquals(wifiModule, created.getItem()));
    }

    @Test
    @DisplayName("對外取得節點時已駁回的節點應視為查無而回 404")
    void getVisibleById_rejected_shouldThrowNotFound() {
        Item rejected = Item.builder().id(1L).normalizedName("wifi模組").displayName("WiFi 模組")
                .reviewStatus(ReviewStatus.REJECTED).build();
        when(itemRepository.findById(1L)).thenReturn(Optional.of(rejected));

        ServerException ex = assertThrows(ServerException.class, () -> itemService.getVisibleById(1L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
    }

    @Test
    @DisplayName("對外取得節點時草稿仍應取得，草稿與已駁回的待遇不同")
    void getVisibleById_draft_shouldReturnItem() {
        Item draft = Item.builder().id(1L).normalizedName("wifi模組").displayName("WiFi 模組").build();
        when(itemRepository.findById(1L)).thenReturn(Optional.of(draft));

        assertEquals(draft, itemService.getVisibleById(1L));
    }

    @Test
    @DisplayName("對外以名稱解析節點時已駁回的節點應視為查無")
    void resolveVisibleByName_rejected_shouldReturnEmpty() {
        Item rejected = Item.builder().id(1L).normalizedName("wifi模組").displayName("WiFi 模組")
                .reviewStatus(ReviewStatus.REJECTED).build();
        when(itemRepository.findByNormalizedName("wifi模組")).thenReturn(Optional.of(rejected));

        assertTrue(itemService.resolveVisibleByName("WiFi 模組").isEmpty());
    }

    @Test
    @DisplayName("終端成品列表應回傳分頁中繼資訊，總頁數由總筆數與每頁筆數導出")
    void findEndProducts_multiplePages_shouldReturnPagingMetadata() {
        // Given：共 5 筆符合，每頁 2 筆，取第 1 頁（自 0 起算）
        Set<String> verifiedOnly = Set.of(ReviewStatus.VERIFIED.name());
        when(itemRepository.countEndProducts(verifiedOnly, "%")).thenReturn(5L);
        when(itemRepository.findEndProducts(verifiedOnly, "%", 2, 2))
                .thenReturn(List.of(endProduct(3L, "桌上型電腦"), endProduct(4L, "筆記型電腦")));

        // When
        PageResponse<ItemResponse> page =
                itemService.findEndProducts(EndProductQuery.builder().page(1).size(2).build());

        // Then
        assertAll(
                () -> assertEquals(2, page.getContent().size()),
                () -> assertEquals("桌上型電腦", page.getContent().get(0).getDisplayName()),
                () -> assertEquals(1, page.getPage()),
                () -> assertEquals(2, page.getSize()),
                () -> assertEquals(5L, page.getTotalElements()),
                () -> assertEquals(3, page.getTotalPages()));
    }

    @Test
    @DisplayName("終端成品列表無符合資料時應回空清單與總筆數 0，而非拋出 404")
    void findEndProducts_noMatch_shouldReturnEmptyPage() {
        Set<String> verifiedOnly = Set.of(ReviewStatus.VERIFIED.name());
        when(itemRepository.countEndProducts(verifiedOnly, "%")).thenReturn(0L);
        when(itemRepository.findEndProducts(verifiedOnly, "%", 20, 0)).thenReturn(List.of());

        PageResponse<ItemResponse> page = itemService.findEndProducts(EndProductQuery.builder().build());

        assertAll(
                () -> assertTrue(page.getContent().isEmpty()),
                () -> assertEquals(0L, page.getTotalElements()),
                () -> assertEquals(0, page.getTotalPages()));
    }

    @Test
    @DisplayName("終端成品列表指定納入草稿時應以含草稿的審核範圍查詢")
    void findEndProducts_includeDrafts_shouldQueryWithDraftScope() {
        Set<String> withDrafts = Set.of(ReviewStatus.VERIFIED.name(), ReviewStatus.DRAFT.name());
        when(itemRepository.countEndProducts(withDrafts, "%")).thenReturn(1L);
        when(itemRepository.findEndProducts(withDrafts, "%", 20, 0))
                .thenReturn(List.of(endProduct(1L, "腳踏車")));

        PageResponse<ItemResponse> page =
                itemService.findEndProducts(EndProductQuery.builder().includeDrafts(true).build());

        assertEquals(1, page.getContent().size());
    }

    @Test
    @DisplayName("終端成品列表的名稱關鍵字應正規化後做包含比對")
    void findEndProducts_nameKeyword_shouldQueryWithNormalizedPattern() {
        // Given：關鍵字帶全形與空白，正規化後才是資料庫裡的比對鍵
        Set<String> verifiedOnly = Set.of(ReviewStatus.VERIFIED.name());
        when(itemRepository.countEndProducts(verifiedOnly, "%wifi模組%")).thenReturn(1L);
        when(itemRepository.findEndProducts(verifiedOnly, "%wifi模組%", 20, 0))
                .thenReturn(List.of(endProduct(1L, "WiFi 模組")));

        PageResponse<ItemResponse> page =
                itemService.findEndProducts(EndProductQuery.builder().name("ＷｉＦｉ 模組").build());

        assertEquals("WiFi 模組", page.getContent().get(0).getDisplayName());
    }

    @Test
    @DisplayName("修正顯示名稱後 displayName 與 normalizedName 皆應更新")
    void amend_newDisplayName_shouldUpdateDisplayAndNormalizedName() {
        // Given
        Item item = node(1L, "主機板", true, null, ReviewStatus.VERIFIED);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(itemRepository.findByNormalizedName("母板")).thenReturn(Optional.empty());
        when(itemAliasRepository.existsByNormalizedAlias("母板")).thenReturn(false);
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Item amended = itemService.amend(1L, amendRequest("母板", true, null));

        // Then
        assertAll(
                () -> assertEquals("母板", amended.getDisplayName()),
                () -> assertEquals("母板", amended.getNormalizedName()));
    }

    @Test
    @DisplayName("將終端成品改為非終端成品後應寫回 isEndProduct=false")
    void amend_endProductToFalse_shouldPersistFlag() {
        // 「不再出現於列表端點」由 findEndProducts 的 is_end_product 過濾保證，
        // 見 ItemEndProductNativeQueryTest；這裡守的是旗標確實被寫回
        Item item = node(1L, "主機板", true, null, ReviewStatus.VERIFIED);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Item amended = itemService.amend(1L, amendRequest("主機板", false, null));

        assertFalse(amended.isEndProduct());
    }

    @Test
    @DisplayName("修正時將上層品類指定為 null 應清空 is-a 上層")
    void amend_nullParentCategoryId_shouldClearParentCategory() {
        Item pcb = node(2L, "PCB", false, null, ReviewStatus.VERIFIED);
        Item item = node(1L, "車用 PCB", false, pcb, ReviewStatus.VERIFIED);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Item amended = itemService.amend(1L, amendRequest("車用 PCB", false, null));

        assertNull(amended.getParentCategory());
    }

    @Test
    @DisplayName("修正不存在的節點應拋出 404 ServerException")
    void amend_unknownItem_shouldThrowNotFound() {
        when(itemRepository.findById(99L)).thenReturn(Optional.empty());

        ServerException ex = assertThrows(ServerException.class,
                () -> itemService.amend(99L, amendRequest("主機板", true, null)));

        assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
    }

    @Test
    @DisplayName("改名撞到其他節點的名稱應拋出 409，且該節點不被修改")
    void amend_renameCollidesWithAnotherItem_shouldThrowConflictAndLeaveItemUntouched() {
        // Given：要把「主機板」改名為「顯示卡」，但「顯示卡」已是另一個節點
        Item item = node(1L, "主機板", true, null, ReviewStatus.VERIFIED);
        Item other = node(2L, "顯示卡", false, null, ReviewStatus.VERIFIED);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(itemRepository.findByNormalizedName("顯示卡")).thenReturn(Optional.of(other));

        // When
        ServerException ex = assertThrows(ServerException.class,
                () -> itemService.amend(1L, amendRequest("顯示卡", false, null)));

        // Then：欄位與審核狀態都不得被動到
        assertAll(
                () -> assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus()),
                () -> assertEquals("主機板", item.getDisplayName()),
                () -> assertTrue(item.isEndProduct()),
                () -> assertEquals(ReviewStatus.VERIFIED, item.getReviewStatus()),
                () -> verify(itemRepository, never()).save(any(Item.class)));
    }

    @Test
    @DisplayName("改名撞到已登記的別名應拋出 409")
    void amend_renameCollidesWithRegisteredAlias_shouldThrowConflict() {
        Item item = node(1L, "主機板", true, null, ReviewStatus.VERIFIED);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(itemRepository.findByNormalizedName("母板")).thenReturn(Optional.empty());
        when(itemAliasRepository.existsByNormalizedAlias("母板")).thenReturn(true);

        ServerException ex = assertThrows(ServerException.class,
                () -> itemService.amend(1L, amendRequest("母板", true, null)));

        assertAll(
                () -> assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus()),
                () -> verify(itemRepository, never()).save(any(Item.class)));
    }

    @Test
    @DisplayName("將上層品類指定為自己應拋出 409")
    void amend_parentCategoryPointingToItself_shouldThrowConflict() {
        Item item = node(1L, "主機板", true, null, ReviewStatus.VERIFIED);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));

        ServerException ex = assertThrows(ServerException.class,
                () -> itemService.amend(1L, amendRequest("主機板", true, 1L)));

        assertAll(
                () -> assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus()),
                () -> verify(itemRepository, never()).save(any(Item.class)));
    }

    @Test
    @DisplayName("上層品類造成 is-a 循環時應拋出 409，且該節點不被修改")
    void amend_parentCategoryFormsIsaCycle_shouldThrowConflict() {
        // Given：B 的上層已經是 A，此時把 A 的上層指為 B 就成環
        Item itemA = node(1L, "PCB", false, null, ReviewStatus.VERIFIED);
        Item itemB = node(2L, "車用 PCB", false, itemA, ReviewStatus.VERIFIED);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(itemA));
        when(itemRepository.findById(2L)).thenReturn(Optional.of(itemB));

        // When
        ServerException ex = assertThrows(ServerException.class,
                () -> itemService.amend(1L, amendRequest("PCB", false, 2L)));

        // Then
        assertAll(
                () -> assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus()),
                () -> assertNull(itemA.getParentCategory()),
                () -> verify(itemRepository, never()).save(any(Item.class)));
    }

    @Test
    @DisplayName("上層品類指向不存在的節點應拋出 404")
    void amend_unknownParentCategory_shouldThrowNotFound() {
        Item item = node(1L, "主機板", true, null, ReviewStatus.VERIFIED);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(itemRepository.findById(99L)).thenReturn(Optional.empty());

        ServerException ex = assertThrows(ServerException.class,
                () -> itemService.amend(1L, amendRequest("主機板", true, 99L)));

        assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
    }

    @Test
    @DisplayName("上層品類同時是本節點的組成零件時仍應允許，part-of 與 is-a 各自獨立")
    void amend_parentCategoryAlsoUsedAsComponent_shouldBeAllowed() {
        // 修正流程刻意不查 item_composition：part-of 的循環偵測守的是另一張表與另一條關係
        Item pcb = node(2L, "PCB", false, null, ReviewStatus.VERIFIED);
        Item motherboard = node(1L, "主機板", false, null, ReviewStatus.VERIFIED);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(motherboard));
        when(itemRepository.findById(2L)).thenReturn(Optional.of(pcb));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Item amended = itemService.amend(1L, amendRequest("主機板", false, 2L));

        assertEquals(pcb, amended.getParentCategory());
    }

    @Test
    @DisplayName("修正已驗證節點的顯示名稱後審核狀態應退回草稿")
    void amend_verifiedItemWithChangedName_shouldRevertToDraft() {
        Item item = node(1L, "主機板", true, null, ReviewStatus.VERIFIED);
        item.setReviewedBy("reviewer");
        item.setReviewedAt(Instant.now());
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(itemRepository.findByNormalizedName("母板")).thenReturn(Optional.empty());
        when(itemAliasRepository.existsByNormalizedAlias("母板")).thenReturn(false);
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Item amended = itemService.amend(1L, amendRequest("母板", true, null));

        assertAll(
                () -> assertEquals(ReviewStatus.DRAFT, amended.getReviewStatus()),
                () -> assertNull(amended.getReviewedBy()),
                () -> assertNull(amended.getReviewedAt()));
    }

    @Test
    @DisplayName("送出與現況完全相同的欄位值時審核狀態應維持已驗證")
    void amend_sameValues_shouldKeepVerified() {
        // Given：只有顯示寫法不同（全形），正規化後與現況相同，視為沒有實質變更
        Item item = node(1L, "WiFi 模組", false, null, ReviewStatus.VERIFIED);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Item amended = itemService.amend(1L, amendRequest("ＷｉＦｉ模組", false, null));

        assertEquals(ReviewStatus.VERIFIED, amended.getReviewStatus());
    }

    @Test
    @DisplayName("修正草稿節點後審核狀態應維持草稿")
    void amend_draftItem_shouldStayDraft() {
        Item item = node(1L, "主機板", true, null, ReviewStatus.DRAFT);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Item amended = itemService.amend(1L, amendRequest("主機板", false, null));

        assertEquals(ReviewStatus.DRAFT, amended.getReviewStatus());
    }

    @Test
    @DisplayName("修正已駁回節點後審核狀態應變為草稿，讓它有重新來過的機會")
    void amend_rejectedItem_shouldBecomeDraft() {
        Item item = node(1L, "主機板", true, null, ReviewStatus.REJECTED);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Item amended = itemService.amend(1L, amendRequest("主機板", false, null));

        assertEquals(ReviewStatus.DRAFT, amended.getReviewStatus());
    }

    private AmendItemRequest amendRequest(String displayName, boolean endProduct, Long parentCategoryId) {
        return AmendItemRequest.builder()
                .displayName(displayName)
                .endProduct(endProduct)
                .parentCategoryId(parentCategoryId)
                .parentCategoryIdSpecified(true)
                .build();
    }

    private Item node(Long id, String displayName, boolean endProduct, Item parentCategory, ReviewStatus status) {
        return Item.builder()
                .id(id)
                .normalizedName(NameNormalizer.normalize(displayName))
                .displayName(displayName)
                .isEndProduct(endProduct)
                .parentCategory(parentCategory)
                .reviewStatus(status)
                .sourceType(SourceType.MANUAL)
                .build();
    }

    private Item endProduct(Long id, String displayName) {
        return Item.builder()
                .id(id)
                .normalizedName(NameNormalizer.normalize(displayName))
                .displayName(displayName)
                .isEndProduct(true)
                .reviewStatus(ReviewStatus.VERIFIED)
                .build();
    }

    private CreateItemAliasRequest createAliasRequest(String alias) {
        return CreateItemAliasRequest.builder()
                .alias(alias)
                .provenance(ProvenanceRequest.builder().sourceType(SourceType.MANUAL).build())
                .build();
    }

    private CreateItemRequest createItemRequest(String displayName) {
        return CreateItemRequest.builder()
                .displayName(displayName)
                .provenance(ProvenanceRequest.builder().sourceType(SourceType.MANUAL).build())
                .build();
    }
}
