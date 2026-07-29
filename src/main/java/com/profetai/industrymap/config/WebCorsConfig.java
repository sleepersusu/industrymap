package com.profetai.industrymap.config;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * 跨來源請求設定。
 *
 * <p>前端與後端跑在不同 port 時，瀏覽器會擋掉每一個跨來源請求——沒有這個設定，
 * 前端連第一支 API 都打不到。</p>
 *
 * <p>以 {@link WebMvcConfigurer} 實作而非 Spring Security：本專案目前沒有 Security，
 * 只為了 CORS 引入整套過重。日後補上認證授權時，CORS 應一併移交給 Security 的過濾鏈，
 * 避免兩處各有一份設定而互相覆蓋。</p>
 *
 * <p><b>刻意不使用 {@code allowedOrigins("*")}</b>，即使目前沒有認證也一樣：
 * 萬用字元會在日後加上 cookie 或 Authorization header 時默默變成破口，
 * 而那時不會有人回頭想起這個設定。用具名清單，新增來源就是一次明確的決定。</p>
 *
 * <p><b>安全前提</b>：本服務目前所有寫入端點（建立、修正、審核、批次匯入）都沒有認證，
 * 因此允許來源不得放寬到不受控的來源，且在補上認證授權之前不得部署至公開網路。</p>
 */
@Log4j2
@Configuration
public class WebCorsConfig implements WebMvcConfigurer {

    private final List<String> allowedOrigins;

    public WebCorsConfig(@Value("${industrymap.cors.allowed-origins}") List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    /**
     * 只掛 {@code /api/**}：OpenAPI 文件（{@code /api-docs}，注意不符合這個樣式）與 Swagger UI
     * 刻意不開放跨來源。由 CLI 產生型別是讀檔或直接打伺服器，不經瀏覽器、不受 CORS 管；
     * 真的要在瀏覽器裡自架 Swagger UI 時再明確加上，不預先開放不需要的路徑。
     *
     * <p>{@code HEAD} 與 {@code GET} 一起允許：兩者的可見性完全相同，允許其一卻擋另一個
     * 是任意的缺口而非刻意的限制。</p>
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        log.info("CORS 允許來源 origins={}", allowedOrigins);
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "HEAD", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
