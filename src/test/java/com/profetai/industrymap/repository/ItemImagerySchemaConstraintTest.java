package com.profetai.industrymap.repository;

import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.enums.SourceType;
import com.profetai.industrymap.model.HotspotPoint;
import com.profetai.industrymap.model.Item;
import com.profetai.industrymap.model.ItemHotspot;
import com.profetai.industrymap.model.ItemImage;
import com.profetai.industrymap.util.NameNormalizer;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 驗證圖片與熱區兩張表的 Flyway migration 在真實 PostgreSQL 上確實建得起來，
 * 且唯一鍵與非空約束真的生效。
 *
 * <p>特別驗「同一張圖上兩個不同位置標籤可指向同一個節點」：那是本次 change 的核心語意
 * （design D2、D3），唯一鍵若不小心含了 {@code child_item_id} 就會把它擋掉，
 * 而那種寫法在其他測試裡完全看不出問題。</p>
 *
 * <p>座標的存檔／讀回也在這裡驗（design D5）：JSON 欄位的序列化是本專案已知會踩的地雷，
 * 「人工檢查過了」擋不住它，只有真的存進 PostgreSQL 再讀回來才算數。</p>
 */
class ItemImagerySchemaConstraintTest extends AbstractPostgresIntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private ItemImageRepository itemImageRepository;
    @Autowired
    private ItemHotspotRepository itemHotspotRepository;

    @Test
    @DisplayName("同一節點的同一視角標籤重複時 DB 應拒絕寫入")
    void saveItemImage_duplicatedViewLabel_shouldViolateUniqueConstraint() {
        Item bike = itemRepository.saveAndFlush(item("腳踏車"));
        itemImageRepository.saveAndFlush(image(bike, "爆炸圖"));

        assertThrows(DataIntegrityViolationException.class,
                () -> itemImageRepository.saveAndFlush(image(bike, "爆炸圖")));
    }

    @Test
    @DisplayName("同一節點的不同視角標籤應可各存一張")
    void saveItemImage_differentViewLabels_shouldBothPersist() {
        Item bike = itemRepository.saveAndFlush(item("腳踏車"));

        ItemImage explosion = itemImageRepository.saveAndFlush(image(bike, "爆炸圖"));
        ItemImage side = itemImageRepository.saveAndFlush(image(bike, "側視圖"));

        assertNotNull(explosion.getId());
        assertNotNull(side.getId());
    }

    @Test
    @DisplayName("視角標籤為全空白時 DB 應拒絕寫入")
    void saveItemImage_blankViewLabel_shouldViolateCheckConstraint() {
        Item bike = itemRepository.saveAndFlush(item("腳踏車"));

        assertThrows(DataIntegrityViolationException.class,
                () -> itemImageRepository.saveAndFlush(image(bike, "   ")));
    }

    @Test
    @DisplayName("同一張圖的同一位置標籤重複時 DB 應拒絕寫入")
    void saveItemHotspot_duplicatedPositionLabel_shouldViolateUniqueConstraint() {
        ItemImage image = savedImage();
        Item brake = itemRepository.saveAndFlush(item("煞車"));
        itemHotspotRepository.saveAndFlush(hotspot(image, brake, "前煞車"));

        assertThrows(DataIntegrityViolationException.class,
                () -> itemHotspotRepository.saveAndFlush(hotspot(image, brake, "前煞車")));
    }

    @Test
    @DisplayName("同一張圖的兩個不同位置標籤應可指向同一個節點")
    void saveItemHotspot_sameChildItemDifferentPositionLabels_shouldBothPersist() {
        // 這正是位置標籤存在的理由：唯一鍵含 child_item_id 的話這一筆會被擋掉（design D3）
        ItemImage image = savedImage();
        Item brake = itemRepository.saveAndFlush(item("煞車"));

        ItemHotspot front = itemHotspotRepository.saveAndFlush(hotspot(image, brake, "前煞車"));
        ItemHotspot rear = itemHotspotRepository.saveAndFlush(hotspot(image, brake, "後煞車"));

        assertNotNull(front.getId());
        assertNotNull(rear.getId());
    }

    @Test
    @DisplayName("位置標籤為全空白時 DB 應拒絕寫入")
    void saveItemHotspot_blankPositionLabel_shouldViolateCheckConstraint() {
        ItemImage image = savedImage();
        Item brake = itemRepository.saveAndFlush(item("煞車"));

        assertThrows(DataIntegrityViolationException.class,
                () -> itemHotspotRepository.saveAndFlush(hotspot(image, brake, "   ")));
    }

    @Test
    @DisplayName("熱區座標存檔後應能原樣讀回，且不因序列化而拋例外")
    void saveItemHotspot_polygon_shouldRoundTripThroughJsonb() {
        ItemImage image = savedImage();
        Item brake = itemRepository.saveAndFlush(item("煞車"));
        List<HotspotPoint> polygon = List.of(
                new HotspotPoint(0.1, 0.2), new HotspotPoint(0.5, 0.25), new HotspotPoint(0.3, 0.75));

        ItemHotspot saved = itemHotspotRepository.saveAndFlush(
                hotspot(image, brake, "前煞車", polygon));
        // 清掉第一級快取，確保讀回的是資料庫裡那份 JSON 而不是記憶體中的同一個物件
        entityManager.clear();

        ItemHotspot reloaded = itemHotspotRepository.findById(saved.getId()).orElseThrow();
        assertEquals(polygon, reloaded.getPolygon());
    }

    // ---------------------------------------------------------------------
    // fixture
    // ---------------------------------------------------------------------

    private String uniqueName(String label) {
        return FIXTURE_PREFIX + label + SEQUENCE.incrementAndGet();
    }

    private Item item(String label) {
        String displayName = uniqueName(label);
        return Item.builder()
                .normalizedName(NameNormalizer.normalize(displayName))
                .displayName(displayName)
                .isEndProduct(false)
                .sourceType(SourceType.MANUAL)
                .reviewStatus(ReviewStatus.VERIFIED)
                .build();
    }

    private ItemImage savedImage() {
        Item bike = itemRepository.saveAndFlush(item("腳踏車"));
        return itemImageRepository.saveAndFlush(image(bike, "爆炸圖"));
    }

    private ItemImage image(Item item, String viewLabel) {
        return ItemImage.builder()
                .item(item)
                .viewLabel(viewLabel)
                .storageKey("s3://industrymap/" + uniqueName("image") + ".png")
                .widthPx(1200)
                .heightPx(800)
                .sourceType(SourceType.MANUAL)
                .reviewStatus(ReviewStatus.VERIFIED)
                .build();
    }

    private ItemHotspot hotspot(ItemImage image, Item childItem, String positionLabel) {
        return hotspot(image, childItem, positionLabel,
                List.of(new HotspotPoint(0, 0), new HotspotPoint(0.5, 0), new HotspotPoint(0.5, 0.5)));
    }

    private ItemHotspot hotspot(ItemImage image, Item childItem, String positionLabel, List<HotspotPoint> polygon) {
        return ItemHotspot.builder()
                .itemImage(image)
                .childItem(childItem)
                .positionLabel(positionLabel)
                .polygon(polygon)
                .sourceType(SourceType.MANUAL)
                .reviewStatus(ReviewStatus.VERIFIED)
                .build();
    }
}
