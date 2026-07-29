package com.profetai.industrymap.service.review;

import com.profetai.industrymap.enums.CompanyRole;
import com.profetai.industrymap.enums.IdentifierType;
import com.profetai.industrymap.enums.PeriodType;
import com.profetai.industrymap.enums.ReviewTargetType;
import com.profetai.industrymap.enums.ShareMetric;
import com.profetai.industrymap.exceptions.ServerException;
import com.profetai.industrymap.model.Company;
import com.profetai.industrymap.model.CompanyAlias;
import com.profetai.industrymap.model.CompanyIdentifier;
import com.profetai.industrymap.model.CompanyItemRole;
import com.profetai.industrymap.model.Item;
import com.profetai.industrymap.model.ItemAlias;
import com.profetai.industrymap.model.ItemComposition;
import com.profetai.industrymap.model.ItemHotspot;
import com.profetai.industrymap.model.ItemImage;
import com.profetai.industrymap.model.MarketShare;
import com.profetai.industrymap.payloads.review.ReviewTargetKey;
import com.profetai.industrymap.repository.CompanyAliasRepository;
import com.profetai.industrymap.repository.CompanyIdentifierRepository;
import com.profetai.industrymap.repository.CompanyItemRoleRepository;
import com.profetai.industrymap.repository.ItemAliasRepository;
import com.profetai.industrymap.repository.ItemCompositionRepository;
import com.profetai.industrymap.repository.ItemHotspotRepository;
import com.profetai.industrymap.repository.ItemImageRepository;
import com.profetai.industrymap.repository.ItemRepository;
import com.profetai.industrymap.repository.MarketShareRepository;
import com.profetai.industrymap.service.company.CompanyService;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NaturalKeyResolverTest {

    private static final String TSMC_CODE = "TWSE:2330";

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private ItemAliasRepository itemAliasRepository;

    @Mock
    private ItemCompositionRepository itemCompositionRepository;

    @Mock
    private ItemImageRepository itemImageRepository;

    @Mock
    private ItemHotspotRepository itemHotspotRepository;

    @Mock
    private CompanyAliasRepository companyAliasRepository;

    @Mock
    private CompanyIdentifierRepository companyIdentifierRepository;

    @Mock
    private CompanyItemRoleRepository companyItemRoleRepository;

    @Mock
    private MarketShareRepository marketShareRepository;

    @Mock
    private CompanyService companyService;

    @InjectMocks
    private NaturalKeyResolver naturalKeyResolver;

    private final Company tsmc = Company.builder().id(1L).normalizedName("台積電").displayName("台積電").build();
    private final Item chip = Item.builder().id(2L).normalizedName("晶片").displayName("晶片").build();

    @Test
    @DisplayName("以識別碼類型與值應解析到對應的公司識別碼")
    void resolveId_companyIdentifierByTypeAndValue_shouldReturnIdentifierId() {
        // Given：這一類的查詢回應完全不含 id，只能靠自然鍵定位（design D1 的 F1）
        CompanyIdentifier identifier = CompanyIdentifier.builder()
                .id(11L).company(tsmc).identifierType(IdentifierType.TWSE).identifierValue(TSMC_CODE).build();
        when(companyIdentifierRepository.findByIdentifierTypeAndIdentifierValue(IdentifierType.TWSE, TSMC_CODE))
                .thenReturn(Optional.of(identifier));

        // When
        Long resolved = naturalKeyResolver.resolveId(ReviewTargetType.COMPANY_IDENTIFIER, ReviewTargetKey.builder()
                .identifierType(IdentifierType.TWSE).identifierValue(TSMC_CODE).build());

        // Then
        assertEquals(11L, resolved);
    }

    @Test
    @DisplayName("以上下層節點應解析到對應的組成關係")
    void resolveId_compositionByParentAndChild_shouldReturnCompositionId() {
        ItemComposition composition = ItemComposition.builder().id(21L).build();
        when(itemCompositionRepository.findByParentItemIdAndChildItemId(5L, 6L))
                .thenReturn(Optional.of(composition));

        Long resolved = naturalKeyResolver.resolveId(ReviewTargetType.ITEM_COMPOSITION,
                ReviewTargetKey.builder().parentItemId(5L).childItemId(6L).build());

        assertEquals(21L, resolved);
    }

    @Test
    @DisplayName("以名稱應解析到品類節點，且比對前先正規化")
    void resolveId_itemByName_shouldNormalizeBeforeMatching() {
        when(itemRepository.findByNormalizedName("晶片")).thenReturn(Optional.of(chip));

        Long resolved = naturalKeyResolver.resolveId(ReviewTargetType.ITEM,
                ReviewTargetKey.builder().name(" 晶 片 ").build());

        assertEquals(2L, resolved);
    }

    @Test
    @DisplayName("以正規化名稱應解析到節點別名")
    void resolveId_itemAliasByNormalizedName_shouldReturnAliasId() {
        ItemAlias alias = ItemAlias.builder().id(31L).item(chip).normalizedAlias("ic").build();
        when(itemAliasRepository.findByNormalizedAlias("ic")).thenReturn(Optional.of(alias));

        Long resolved = naturalKeyResolver.resolveId(ReviewTargetType.ITEM_ALIAS,
                ReviewTargetKey.builder().name("IC").build());

        assertEquals(31L, resolved);
    }

    @Test
    @DisplayName("以正規化名稱應解析到公司別名")
    void resolveId_companyAliasByNormalizedName_shouldReturnAliasId() {
        CompanyAlias alias = CompanyAlias.builder().id(41L).company(tsmc).normalizedAlias("tsmc").build();
        when(companyAliasRepository.findByNormalizedAlias("tsmc")).thenReturn(Optional.of(alias));

        Long resolved = naturalKeyResolver.resolveId(ReviewTargetType.COMPANY_ALIAS,
                ReviewTargetKey.builder().name("TSMC").build());

        assertEquals(41L, resolved);
    }

    @Test
    @DisplayName("以公司代號應解析到公司")
    void resolveId_companyByCode_shouldReturnCompanyId() {
        when(companyService.getByReference(TSMC_CODE)).thenReturn(tsmc);

        Long resolved = naturalKeyResolver.resolveId(ReviewTargetType.COMPANY,
                ReviewTargetKey.builder().companyCode(TSMC_CODE).build());

        assertEquals(1L, resolved);
    }

    @Test
    @DisplayName("以公司識別、零件與角色應解析到供應角色")
    void resolveId_companyItemRoleByCompanyItemAndRole_shouldReturnRoleId() {
        CompanyItemRole role = CompanyItemRole.builder().id(51L).build();
        when(companyService.getByReference(TSMC_CODE)).thenReturn(tsmc);
        when(companyItemRoleRepository.findByCompanyIdAndItemIdAndCompanyRole(1L, 2L, CompanyRole.MANUFACTURE))
                .thenReturn(Optional.of(role));

        Long resolved = naturalKeyResolver.resolveId(ReviewTargetType.COMPANY_ITEM_ROLE, ReviewTargetKey.builder()
                .companyCode(TSMC_CODE).itemId(2L).companyRole(CompanyRole.MANUFACTURE).build());

        assertEquals(51L, resolved);
    }

    @Test
    @DisplayName("以完整六維度加來源應解析到市佔率")
    void resolveId_marketShareByAllDimensions_shouldReturnShareId() {
        MarketShare share = MarketShare.builder().id(61L).build();
        when(companyService.getByReference(TSMC_CODE)).thenReturn(tsmc);
        when(marketShareRepository.findByDimensionsAndSource(1L, 2L, "YEAR", "2024", "全球", "REVENUE", "報告 A"))
                .thenReturn(Optional.of(share));

        Long resolved = naturalKeyResolver.resolveId(ReviewTargetType.MARKET_SHARE, marketShareKey());

        assertEquals(61L, resolved);
    }

    @Test
    @DisplayName("市佔率自然鍵缺少期間與地區時應回 400 且訊息指出缺哪些維度")
    void resolveId_marketShareMissingDimensions_shouldThrowBadRequestNamingMissingFields() {
        // Given：市佔率的自然鍵有六個欄位，少一個就定位不到，呼叫端必須知道少了哪些
        ReviewTargetKey incomplete = ReviewTargetKey.builder()
                .companyCode(TSMC_CODE).itemId(2L).metric(ShareMetric.REVENUE).build();

        // When
        ServerException ex = assertThrows(ServerException.class,
                () -> naturalKeyResolver.resolveId(ReviewTargetType.MARKET_SHARE, incomplete));

        // Then
        assertAll(
                () -> assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus()),
                () -> assertTrue(ex.getMessage().contains("期間單位"), ex.getMessage()),
                () -> assertTrue(ex.getMessage().contains("期間值"), ex.getMessage()),
                () -> assertTrue(ex.getMessage().contains("地區"), ex.getMessage()));
    }

    @Test
    @DisplayName("組成關係自然鍵缺少下層節點時應回 400 且訊息指出缺哪個欄位")
    void resolveId_compositionMissingChild_shouldThrowBadRequestNamingMissingField() {
        ServerException ex = assertThrows(ServerException.class,
                () -> naturalKeyResolver.resolveId(ReviewTargetType.ITEM_COMPOSITION,
                        ReviewTargetKey.builder().parentItemId(5L).build()));

        assertAll(
                () -> assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus()),
                () -> assertTrue(ex.getMessage().contains("下層節點"), ex.getMessage()));
    }

    @Test
    @DisplayName("完全未提供自然鍵時應回 400")
    void resolveId_nullKey_shouldThrowBadRequest() {
        ServerException ex = assertThrows(ServerException.class,
                () -> naturalKeyResolver.resolveId(ReviewTargetType.ITEM, null));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }

    @Test
    @DisplayName("自然鍵查無對應資料時應回 404")
    void resolveId_unknownNaturalKey_shouldThrowNotFound() {
        when(companyIdentifierRepository.findByIdentifierTypeAndIdentifierValue(IdentifierType.TWSE, "9999"))
                .thenReturn(Optional.empty());

        ServerException ex = assertThrows(ServerException.class,
                () -> naturalKeyResolver.resolveId(ReviewTargetType.COMPANY_IDENTIFIER, ReviewTargetKey.builder()
                        .identifierType(IdentifierType.TWSE).identifierValue("9999").build()));

        assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
    }

    @Test
    @DisplayName("市佔率來源明細為空時仍應可定位，null 與空字串視為同一筆")
    void resolveId_marketShareWithoutSourceDetail_shouldStillResolve() {
        // 來源明細可為 null，唯一索引以 COALESCE 正規化後比對，自然鍵解析必須一致
        MarketShare share = MarketShare.builder().id(62L).build();
        when(companyService.getByReference(TSMC_CODE)).thenReturn(tsmc);
        when(marketShareRepository.findByDimensionsAndSource(1L, 2L, "YEAR", "2024", "全球", "REVENUE", null))
                .thenReturn(Optional.of(share));

        ReviewTargetKey key = marketShareKey();
        key.setSourceDetail(null);

        assertEquals(62L, naturalKeyResolver.resolveId(ReviewTargetType.MARKET_SHARE, key));
    }

    @Test
    @DisplayName("以節點 id 與視角標籤應解析到對應的圖片")
    void resolveId_itemImageByItemAndViewLabel_shouldReturnImageId() {
        ItemImage image = ItemImage.builder().id(71L).item(chip).viewLabel("爆炸圖").build();
        when(itemImageRepository.findByItemIdAndViewLabel(2L, "爆炸圖")).thenReturn(Optional.of(image));

        Long resolved = naturalKeyResolver.resolveId(ReviewTargetType.ITEM_IMAGE,
                ReviewTargetKey.builder().itemId(2L).viewLabel("爆炸圖").build());

        assertEquals(71L, resolved);
    }

    @Test
    @DisplayName("圖片的節點也可以用名稱指定，比對前先正規化")
    void resolveId_itemImageByItemName_shouldNormalizeBeforeMatching() {
        // 全程不需要內部 id 是這一組自然鍵的重點（design D3）
        ItemImage image = ItemImage.builder().id(71L).item(chip).viewLabel("爆炸圖").build();
        when(itemRepository.findByNormalizedName("晶片")).thenReturn(Optional.of(chip));
        when(itemImageRepository.findByItemIdAndViewLabel(2L, "爆炸圖")).thenReturn(Optional.of(image));

        Long resolved = naturalKeyResolver.resolveId(ReviewTargetType.ITEM_IMAGE,
                ReviewTargetKey.builder().name(" 晶 片 ").viewLabel("爆炸圖").build());

        assertEquals(71L, resolved);
    }

    @Test
    @DisplayName("以節點、視角標籤與位置標籤應解析到對應的熱區")
    void resolveId_hotspotByItemViewAndPositionLabel_shouldReturnHotspotId() {
        ItemImage image = ItemImage.builder().id(71L).item(chip).viewLabel("爆炸圖").build();
        ItemHotspot hotspot = ItemHotspot.builder().id(81L).itemImage(image).positionLabel("前煞車").build();
        when(itemImageRepository.findByItemIdAndViewLabel(2L, "爆炸圖")).thenReturn(Optional.of(image));
        when(itemHotspotRepository.findByItemImageIdAndPositionLabel(71L, "前煞車"))
                .thenReturn(Optional.of(hotspot));

        Long resolved = naturalKeyResolver.resolveId(ReviewTargetType.ITEM_HOTSPOT,
                ReviewTargetKey.builder().itemId(2L).viewLabel("爆炸圖").positionLabel("前煞車").build());

        assertEquals(81L, resolved);
    }

    @Test
    @DisplayName("熱區自然鍵缺少位置標籤時應回 400 且訊息指出缺的是位置標籤")
    void resolveId_hotspotWithoutPositionLabel_shouldThrowBadRequestNamingTheField() {
        // 同一張圖上可以有多個熱區指向同一個節點，少了位置標籤就不只定位到一筆
        ServerException ex = assertThrows(ServerException.class,
                () -> naturalKeyResolver.resolveId(ReviewTargetType.ITEM_HOTSPOT,
                        ReviewTargetKey.builder().itemId(2L).viewLabel("爆炸圖").build()));

        assertAll(
                () -> assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus()),
                () -> assertTrue(ex.getMessage().contains("位置標籤"), ex.getMessage()));
    }

    @Test
    @DisplayName("圖片自然鍵缺少視角標籤時應回 400 且訊息指出缺的是視角標籤")
    void resolveId_itemImageWithoutViewLabel_shouldThrowBadRequestNamingTheField() {
        ServerException ex = assertThrows(ServerException.class,
                () -> naturalKeyResolver.resolveId(ReviewTargetType.ITEM_IMAGE,
                        ReviewTargetKey.builder().itemId(2L).build()));

        assertAll(
                () -> assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus()),
                () -> assertTrue(ex.getMessage().contains("視角標籤"), ex.getMessage()));
    }

    @Test
    @DisplayName("圖片自然鍵未指定節點時應回 400 且訊息指出缺的是節點")
    void resolveId_itemImageWithoutItem_shouldThrowBadRequestNamingTheField() {
        ServerException ex = assertThrows(ServerException.class,
                () -> naturalKeyResolver.resolveId(ReviewTargetType.ITEM_IMAGE,
                        ReviewTargetKey.builder().viewLabel("爆炸圖").build()));

        assertAll(
                () -> assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus()),
                () -> assertTrue(ex.getMessage().contains("節點"), ex.getMessage()));
    }

    @Test
    @DisplayName("欄位齊全但查無圖片時應回 404")
    void resolveId_itemImageNotFound_shouldThrowNotFound() {
        when(itemImageRepository.findByItemIdAndViewLabel(2L, "側視圖")).thenReturn(Optional.empty());

        ServerException ex = assertThrows(ServerException.class,
                () -> naturalKeyResolver.resolveId(ReviewTargetType.ITEM_IMAGE,
                        ReviewTargetKey.builder().itemId(2L).viewLabel("側視圖").build()));

        assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
    }

    private ReviewTargetKey marketShareKey() {
        return ReviewTargetKey.builder()
                .companyCode(TSMC_CODE)
                .itemId(2L)
                .periodType(PeriodType.YEAR)
                .periodValue("2024")
                .region("全球")
                .metric(ShareMetric.REVENUE)
                .sourceDetail("報告 A")
                .build();
    }
}
