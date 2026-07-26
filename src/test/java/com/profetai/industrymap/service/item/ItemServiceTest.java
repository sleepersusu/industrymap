package com.profetai.industrymap.service.item;

import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.enums.SourceType;
import com.profetai.industrymap.exceptions.ServerException;
import com.profetai.industrymap.model.Item;
import com.profetai.industrymap.model.ItemAlias;
import com.profetai.industrymap.payloads.ProvenanceRequest;
import com.profetai.industrymap.payloads.item.CreateItemAliasRequest;
import com.profetai.industrymap.payloads.item.CreateItemRequest;
import com.profetai.industrymap.repository.ItemAliasRepository;
import com.profetai.industrymap.repository.ItemRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @InjectMocks
    private ItemService itemService;

    @Test
    @DisplayName("建立 item 時正規化名稱與既有節點重複應拋出 409 ServerException")
    void create_duplicatedNormalizedName_shouldThrowConflict() {
        // Given：既有節點「WiFi模組」，新請求用「wifi 模組」——正規化後相同
        when(itemRepository.existsByNormalizedName("wifi模組")).thenReturn(true);

        // When
        ServerException ex = assertThrows(ServerException.class,
                () -> itemService.create(createItemRequest("wifi 模組")));

        // Then
        assertAll(
                () -> assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus()),
                () -> verify(itemRepository, never()).save(any(Item.class)));
    }

    @Test
    @DisplayName("建立 item 應同時存下正規化名稱與原始顯示名稱")
    void create_newName_shouldPersistNormalizedAndDisplayName() {
        // Given
        when(itemRepository.existsByNormalizedName("wifi模組")).thenReturn(false);
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
