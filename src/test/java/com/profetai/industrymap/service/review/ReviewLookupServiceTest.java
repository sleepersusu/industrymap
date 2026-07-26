package com.profetai.industrymap.service.review;

import com.profetai.industrymap.enums.ReviewTargetType;
import com.profetai.industrymap.exceptions.ServerException;
import com.profetai.industrymap.model.Company;
import com.profetai.industrymap.model.Item;
import com.profetai.industrymap.model.MarketShare;
import com.profetai.industrymap.model.ProvenanceEntity;
import com.profetai.industrymap.repository.CompanyAliasRepository;
import com.profetai.industrymap.repository.CompanyIdentifierRepository;
import com.profetai.industrymap.repository.CompanyItemRoleRepository;
import com.profetai.industrymap.repository.CompanyRepository;
import com.profetai.industrymap.repository.ItemAliasRepository;
import com.profetai.industrymap.repository.ItemCompositionRepository;
import com.profetai.industrymap.repository.ItemRepository;
import com.profetai.industrymap.repository.MarketShareRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewLookupServiceTest {

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private ItemAliasRepository itemAliasRepository;

    @Mock
    private ItemCompositionRepository itemCompositionRepository;

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private CompanyAliasRepository companyAliasRepository;

    @Mock
    private CompanyIdentifierRepository companyIdentifierRepository;

    @Mock
    private CompanyItemRoleRepository companyItemRoleRepository;

    @Mock
    private MarketShareRepository marketShareRepository;

    private ReviewLookupService reviewLookupService() {
        return new ReviewLookupService(itemRepository, itemAliasRepository, itemCompositionRepository,
                companyRepository, companyAliasRepository, companyIdentifierRepository,
                companyItemRoleRepository, marketShareRepository);
    }

    @Test
    @DisplayName("依目標類型與識別碼應取得對應實體")
    void getTarget_existingId_shouldReturnEntity() {
        Item pcb = Item.builder().id(1L).normalizedName("pcb").displayName("PCB").build();
        when(itemRepository.findById(1L)).thenReturn(Optional.of(pcb));

        ProvenanceEntity found = reviewLookupService().getTarget(ReviewTargetType.ITEM, 1L);

        assertSame(pcb, found);
    }

    @Test
    @DisplayName("目標識別碼不存在時應拋出 404 ServerException")
    void getTarget_unknownId_shouldThrowNotFound() {
        when(marketShareRepository.findById(99L)).thenReturn(Optional.empty());

        ServerException ex = assertThrows(ServerException.class,
                () -> reviewLookupService().getTarget(ReviewTargetType.MARKET_SHARE, 99L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
    }

    @Test
    @DisplayName("八種支援的目標類型都應對應到可查詢的 repository")
    void getTarget_everySupportedType_shouldResolveRepository() {
        ReviewLookupService lookupService = reviewLookupService();

        assertAll(java.util.Arrays.stream(ReviewTargetType.values())
                .map(type -> () -> {
                    ServerException ex = assertThrows(ServerException.class,
                            () -> lookupService.getTarget(type, 404L),
                            "類型 " + type + " 未註冊對應 repository");
                    // 查無資料是 404；若類型未註冊則會是 400，藉此區分「沒資料」與「不支援」
                    assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus(), "類型 " + type + " 未註冊對應 repository");
                }));
    }

    @Test
    @DisplayName("儲存時應寫回目標類型對應的 repository")
    void save_shouldPersistThroughMatchingRepository() {
        Company giant = Company.builder().id(3L).normalizedName("巨大機械").displayName("巨大機械").build();
        when(companyRepository.save(any(Company.class))).thenReturn(giant);

        ProvenanceEntity saved = reviewLookupService().save(ReviewTargetType.COMPANY, giant);

        assertSame(giant, saved);
    }

    @Test
    @DisplayName("市佔率實體應可經 save 寫回對應 repository")
    void save_marketShare_shouldPersistThroughMarketShareRepository() {
        MarketShare share = MarketShare.builder().id(7L).build();
        when(marketShareRepository.save(any(MarketShare.class))).thenReturn(share);

        assertSame(share, reviewLookupService().save(ReviewTargetType.MARKET_SHARE, share));
    }
}
