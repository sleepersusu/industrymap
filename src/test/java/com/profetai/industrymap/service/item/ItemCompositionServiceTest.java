package com.profetai.industrymap.service.item;

import com.profetai.industrymap.enums.Necessity;
import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.enums.SourceType;
import com.profetai.industrymap.exceptions.ServerException;
import com.profetai.industrymap.model.Item;
import com.profetai.industrymap.model.ItemComposition;
import com.profetai.industrymap.payloads.ProvenanceRequest;
import com.profetai.industrymap.payloads.item.ComponentNode;
import com.profetai.industrymap.payloads.item.CreateCompositionRequest;
import com.profetai.industrymap.repository.ItemCompositionRepository;
import com.profetai.industrymap.repository.ItemRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemCompositionServiceTest {

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private ItemCompositionRepository itemCompositionRepository;

    @InjectMocks
    private ItemCompositionService itemCompositionService;

    private static final Set<ReviewStatus> VERIFIED_ONLY = Set.of(ReviewStatus.VERIFIED);

    private final Item itemA = Item.builder().id(1L).normalizedName("a").displayName("A").build();
    private final Item itemB = Item.builder().id(2L).normalizedName("b").displayName("B").build();

    @Test
    @DisplayName("已存在 A→B 時再建立 B→A 應拋出 409 ServerException")
    void create_directCycle_shouldThrowConflict() {
        // Given：A → B 已存在，現在要建 B → A
        givenItems(itemB, itemA);
        when(itemCompositionRepository.existsByParentItemIdAndChildItemId(2L, 1L)).thenReturn(false);
        when(itemCompositionRepository.findByParentItemId(1L)).thenReturn(List.of(composition(itemA, itemB)));

        // When
        ServerException ex = assertThrows(ServerException.class,
                () -> itemCompositionService.create(2L, request(1L, Necessity.STANDARD)));

        // Then
        assertAll(
                () -> assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus()),
                () -> verify(itemCompositionRepository, never()).save(any(ItemComposition.class)));
    }

    @Test
    @DisplayName("已存在 A→B、B→C 時再建立 C→A 應偵測到多層循環並拋出 409")
    void create_indirectCycleAcrossMultipleLevels_shouldThrowConflict() {
        // Given：A → B → C 已成鏈，現在要建 C → A
        Item itemC = Item.builder().id(3L).normalizedName("c").displayName("C").build();
        givenItems(itemC, itemA);
        when(itemCompositionRepository.existsByParentItemIdAndChildItemId(3L, 1L)).thenReturn(false);
        when(itemCompositionRepository.findByParentItemId(1L)).thenReturn(List.of(composition(itemA, itemB)));
        when(itemCompositionRepository.findByParentItemId(2L)).thenReturn(List.of(composition(itemB, itemC)));

        // When
        ServerException ex = assertThrows(ServerException.class,
                () -> itemCompositionService.create(3L, request(1L, Necessity.STANDARD)));

        // Then
        assertAll(
                () -> assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus()),
                () -> verify(itemCompositionRepository, never()).save(any(ItemComposition.class)));
    }

    @Test
    @DisplayName("已存在 A→C 時再建立 B→C 應成功，多重上層屬 DAG 正常情況")
    void create_multipleParentsForSameChild_shouldPersist() {
        // Given：C 已掛在 A 之下，現在要再掛到 B 之下（PCB 同時屬於主機板與汽車）
        Item itemC = Item.builder().id(3L).normalizedName("c").displayName("C").build();
        givenItems(itemB, itemC);
        when(itemCompositionRepository.existsByParentItemIdAndChildItemId(2L, 3L)).thenReturn(false);
        when(itemCompositionRepository.findByParentItemId(3L)).thenReturn(List.of());
        when(itemCompositionRepository.save(any(ItemComposition.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ItemComposition created = itemCompositionService.create(2L, request(3L, Necessity.STANDARD));

        // Then
        assertAll(
                () -> assertEquals(itemB, created.getParentItem()),
                () -> assertEquals(itemC, created.getChildItem()),
                () -> assertEquals(Necessity.STANDARD, created.getNecessity()));
    }

    @Test
    @DisplayName("建立組成關係未提供必要性時應拋出 400 ServerException")
    void create_missingNecessity_shouldThrowBadRequest() {
        ServerException ex = assertThrows(ServerException.class,
                () -> itemCompositionService.create(1L, request(2L, null)));

        assertAll(
                () -> assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus()),
                () -> verify(itemCompositionRepository, never()).save(any(ItemComposition.class)));
    }

    @Test
    @DisplayName("展開組成樹指定深度為 1 時不應再往下展開第二層")
    void expandTree_depthOne_shouldNotExpandSecondLevel() {
        // Given：A → B → C 的兩層結構
        Item itemC = Item.builder().id(3L).normalizedName("c").displayName("C").build();
        when(itemRepository.findById(1L)).thenReturn(Optional.of(itemA));
        when(itemCompositionRepository.findByParentItemIdAndReviewStatusIn(1L, VERIFIED_ONLY))
                .thenReturn(List.of(composition(itemA, itemB)));

        // When
        ComponentNode root = itemCompositionService.expandTree(1L, 1, null, false);

        // Then：只展開一層，第二層的查詢完全不該發生
        assertAll(
                () -> assertEquals(1L, root.getItemId()),
                () -> assertEquals(1, root.getChildren().size()),
                () -> assertEquals(2L, root.getChildren().get(0).getItemId()),
                () -> assertTrue(root.getChildren().get(0).getChildren().isEmpty()),
                () -> verify(itemCompositionRepository, never())
                        .findByParentItemIdAndReviewStatusIn(itemC.getId(), VERIFIED_ONLY),
                () -> verify(itemCompositionRepository, never())
                        .findByParentItemIdAndReviewStatusIn(2L, VERIFIED_ONLY));
    }

    @Test
    @DisplayName("展開組成樹指定只取標配時應排除選配零件")
    void expandTree_standardOnlyFilter_shouldExcludeOptionalComponents() {
        // Given：腳踏車有標配的 B 與選配的 C
        Item itemC = Item.builder().id(3L).normalizedName("c").displayName("C").build();
        ItemComposition optional = composition(itemA, itemC);
        optional.setNecessity(Necessity.OPTIONAL);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(itemA));
        when(itemCompositionRepository.findByParentItemIdAndReviewStatusIn(1L, VERIFIED_ONLY))
                .thenReturn(List.of(composition(itemA, itemB), optional));

        // When
        ComponentNode root = itemCompositionService.expandTree(1L, 1, Necessity.STANDARD, false);

        // Then
        assertAll(
                () -> assertEquals(1, root.getChildren().size()),
                () -> assertEquals(2L, root.getChildren().get(0).getItemId()),
                () -> assertEquals(Necessity.STANDARD, root.getChildren().get(0).getNecessity()));
    }

    @Test
    @DisplayName("展開不存在的節點應拋出 404 ServerException")
    void expandTree_unknownItem_shouldThrowNotFound() {
        when(itemRepository.findById(99L)).thenReturn(Optional.empty());

        ServerException ex = assertThrows(ServerException.class,
                () -> itemCompositionService.expandTree(99L, 1, null, false));

        assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
    }

    @Test
    @DisplayName("反向查詢應沿組成關係向上回溯並只回傳可達的終端成品")
    void findReachableEndProducts_multiLevelUpwards_shouldReturnOnlyEndProducts() {
        // Given：PCB → 主機板 → PC，主機板不是終端成品、PC 是；PCB 另外直接掛在汽車（終端成品）下
        Item pcb = Item.builder().id(10L).normalizedName("pcb").displayName("PCB").build();
        Item motherboard = Item.builder().id(11L).normalizedName("主機板").displayName("主機板").build();
        Item pc = Item.builder().id(12L).normalizedName("pc").displayName("PC").isEndProduct(true).build();
        Item car = Item.builder().id(13L).normalizedName("汽車").displayName("汽車").isEndProduct(true).build();
        when(itemRepository.findById(10L)).thenReturn(Optional.of(pcb));
        when(itemCompositionRepository.findByChildItemIdAndReviewStatusIn(10L, VERIFIED_ONLY))
                .thenReturn(List.of(composition(motherboard, pcb), composition(car, pcb)));
        when(itemCompositionRepository.findByChildItemIdAndReviewStatusIn(11L, VERIFIED_ONLY)).thenReturn(List.of(composition(pc, motherboard)));
        when(itemCompositionRepository.findByChildItemIdAndReviewStatusIn(12L, VERIFIED_ONLY)).thenReturn(List.of());
        when(itemCompositionRepository.findByChildItemIdAndReviewStatusIn(13L, VERIFIED_ONLY)).thenReturn(List.of());

        // When
        List<Item> endProducts = itemCompositionService.findReachableEndProducts(10L, false);

        // Then：中間節點主機板不應出現在結果中
        assertAll(
                () -> assertEquals(2, endProducts.size()),
                () -> assertTrue(endProducts.contains(pc)),
                () -> assertTrue(endProducts.contains(car)));
    }

    @Test
    @DisplayName("組成樹中被駁回的子節點不得出現，即使指向它的關係本身已驗證")
    void expandTree_rejectedChildItem_shouldNotAppearInTree() {
        // Given：A → B 的關係已驗證，但 B 這個節點本身已被駁回
        Item rejectedB = Item.builder().id(2L).normalizedName("b").displayName("B")
                .reviewStatus(ReviewStatus.REJECTED).build();
        when(itemRepository.findById(1L)).thenReturn(Optional.of(itemA));
        when(itemCompositionRepository.findByParentItemIdAndReviewStatusIn(1L, VERIFIED_ONLY))
                .thenReturn(List.of(composition(itemA, rejectedB)));

        // When
        ComponentNode root = itemCompositionService.expandTree(1L, 2, null, false);

        // Then：關係驗證過不代表節點還算數，被駁回的節點整枝不外露
        assertTrue(root.getChildren().isEmpty());
    }

    @Test
    @DisplayName("反向查詢時被駁回的上層節點不得列入終端成品")
    void findReachableEndProducts_rejectedAncestor_shouldBeExcluded() {
        // Given：PCB 掛在已駁回的「汽車」與正常的「PC」之下，兩者都是終端成品
        Item pcb = Item.builder().id(10L).normalizedName("pcb").displayName("PCB").build();
        Item pc = Item.builder().id(12L).normalizedName("pc").displayName("PC").isEndProduct(true).build();
        Item rejectedCar = Item.builder().id(13L).normalizedName("汽車").displayName("汽車")
                .isEndProduct(true).reviewStatus(ReviewStatus.REJECTED).build();
        when(itemRepository.findById(10L)).thenReturn(Optional.of(pcb));
        when(itemCompositionRepository.findByChildItemIdAndReviewStatusIn(10L, VERIFIED_ONLY))
                .thenReturn(List.of(composition(pc, pcb), composition(rejectedCar, pcb)));
        when(itemCompositionRepository.findByChildItemIdAndReviewStatusIn(12L, VERIFIED_ONLY)).thenReturn(List.of());

        // When
        List<Item> endProducts = itemCompositionService.findReachableEndProducts(10L, false);

        // Then
        assertAll(
                () -> assertEquals(List.of(pc), endProducts),
                () -> verify(itemCompositionRepository, never())
                        .findByChildItemIdAndReviewStatusIn(13L, VERIFIED_ONLY));
    }

    @Test
    @DisplayName("反向查詢不存在的節點應拋出 404 ServerException")
    void findReachableEndProducts_unknownItem_shouldThrowNotFound() {
        when(itemRepository.findById(99L)).thenReturn(Optional.empty());

        ServerException ex = assertThrows(ServerException.class,
                () -> itemCompositionService.findReachableEndProducts(99L, false));

        assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
    }

    private void givenItems(Item parent, Item child) {
        when(itemRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(itemRepository.findById(child.getId())).thenReturn(Optional.of(child));
    }

    private ItemComposition composition(Item parent, Item child) {
        return ItemComposition.builder()
                .parentItem(parent)
                .childItem(child)
                .necessity(Necessity.STANDARD)
                .sourceType(SourceType.MANUAL)
                .build();
    }

    private CreateCompositionRequest request(Long childItemId, Necessity necessity) {
        return CreateCompositionRequest.builder()
                .childItemId(childItemId)
                .necessity(necessity)
                .provenance(ProvenanceRequest.builder().sourceType(SourceType.MANUAL).build())
                .build();
    }
}
