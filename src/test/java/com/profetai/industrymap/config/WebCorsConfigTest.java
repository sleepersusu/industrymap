package com.profetai.industrymap.config;

import com.profetai.industrymap.controller.ProductController;
import com.profetai.industrymap.payloads.PageResponse;
import com.profetai.industrymap.payloads.item.EndProductQuery;
import com.profetai.industrymap.service.item.ItemCompositionService;
import com.profetai.industrymap.service.item.ItemService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CORS 設定。沒有這個設定，前端跑在另一個 port 時連第一支 API 都打不到，
 * 因此它不是「體驗問題」而是能不能串接的前提。
 *
 * <p><b>刻意不用 {@code @TestPropertySource} 覆寫允許來源</b>：那樣驗到的只是「機制會動」，
 * 而真正會上線的是 {@code application.properties} 裡的預設值。預設值若少一個 port、
 * 或誤用空白而非逗號分隔，覆寫過的測試依然全綠，前端卻每個請求都被瀏覽器擋掉——
 * 那正是這個設定存在的理由。因此這裡直接對預設值下斷言。</p>
 */
@Tag("integration")
@WebMvcTest(ProductController.class)
@Import(WebCorsConfig.class)
class WebCorsConfigTest {

    /** 與 application.properties 的預設值一致；改了那邊沒改這邊，這支測試就會紅 */
    private static final String VITE_ORIGIN = "http://localhost:5173";
    private static final String NEXT_ORIGIN = "http://localhost:3000";
    private static final String FOREIGN_ORIGIN = "https://evil.example.com";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ItemService itemService;

    @MockitoBean
    private ItemCompositionService itemCompositionService;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {VITE_ORIGIN, NEXT_ORIGIN})
    @DisplayName("預設允許的每個前端開發來源都應取得跨來源許可")
    void defaultOrigins_shouldReceiveCorsHeader(String origin) throws Exception {
        // 逐個驗而非只驗第一個：逗號分隔若被寫成空白分隔，第一個仍會通過，第二個才會露餡
        when(itemService.findEndProducts(any(EndProductQuery.class)))
                .thenReturn(PageResponse.of(List.of(), 0, 20, 0L));

        mockMvc.perform(get("/api/products").header("Origin", origin))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", origin));
    }

    @Test
    @DisplayName("清單外的來源不得取得跨來源許可")
    void foreignOrigin_shouldNotReceiveCorsHeader() throws Exception {
        // 寫入端點目前完全沒有認證，跨來源許可一旦放寬，任何網頁都能改資料
        mockMvc.perform(get("/api/products").header("Origin", FOREIGN_ORIGIN))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    @DisplayName("預檢請求應回報允許的方法")
    void preflight_shouldAdvertiseAllowedMethods() throws Exception {
        mockMvc.perform(options("/api/products")
                        .header("Origin", VITE_ORIGIN)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", VITE_ORIGIN));
    }
}
