package com.profetai.industrymap.repository;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Repository 層整合測試的共用基底：以真實 PostgreSQL 跑 Flyway migration，
 * 才能驗證唯一鍵、partial unique index 與 check constraint 是否真的生效——
 * 這些限制在 H2 或 mock 之下都測不出來。
 *
 * <p>有 Docker 時用 Testcontainer（CI 的預設路徑，環境乾淨可重現）；
 * 沒有 Docker 時退回本機 PostgreSQL，讓開發機仍跑得動整合測試。
 * 連線資訊一律取自環境變數，帳密不寫進原始碼。</p>
 */
@Tag("integration")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class AbstractPostgresIntegrationTest {

    /**
     * 測試資料的名稱前綴。
     *
     * <p>沒有 Docker 時整合測試跑在共用的本機開發資料庫上，而 item / company 的正規化名稱是全域唯一鍵——
     * fixture 若直接用「腳踏車」「shimano」這種真實名稱，只要開發資料庫裡剛好有同名資料，
     * 測試就會撞唯一鍵而失敗（這正是種子資料走查後踩到的）。前綴讓 fixture 不可能與真實資料重疊。</p>
     */
    protected static final String FIXTURE_PREFIX = "it-";

    private static final PostgreSQLContainer<?> POSTGRES = startContainerIfDockerAvailable();

    @SuppressWarnings("resource")
    private static PostgreSQLContainer<?> startContainerIfDockerAvailable() {
        if (!DockerClientFactory.instance().isDockerAvailable()) {
            return null;
        }
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16-alpine");
        container.start();
        return container;
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        // 沒有 Docker 時完全不覆寫，交給 application.properties + .env 指向本機資料庫
        if (POSTGRES != null) {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
        }
        // schema 由 Flyway 建立，Hibernate 只做驗證，確保 entity 與 migration 不漂移
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
}
