package com.profetai.industrymap.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 整合測試共用的 PostgreSQL 連線來源。
 *
 * <p>有 Docker 時用 Testcontainer（CI 的預設路徑，環境乾淨可重現）；
 * 沒有 Docker 時退回本機 PostgreSQL，讓開發機仍跑得動整合測試。
 * 連線資訊一律取自環境變數，帳密不寫進原始碼。</p>
 *
 * <p>容器持有在這裡而不是各基底類別上：{@code @DataJpaTest} 與 {@code @SpringBootTest}
 * 兩種基底都要連同一個資料庫，各自持有一個 static 容器會在同一個測試 JVM 內開兩份 PostgreSQL。</p>
 */
public final class PostgresTestDatabase {

    /**
     * 測試資料的名稱前綴。
     *
     * <p>沒有 Docker 時整合測試跑在共用的本機開發資料庫上，而 item / company 的正規化名稱是全域唯一鍵——
     * fixture 若直接用「腳踏車」「shimano」這種真實名稱，只要開發資料庫裡剛好有同名資料，
     * 測試就會撞唯一鍵而失敗（這正是種子資料走查後踩到的）。前綴讓 fixture 不可能與真實資料重疊。</p>
     */
    public static final String FIXTURE_PREFIX = "it-";

    private static final PostgreSQLContainer<?> POSTGRES = startContainerIfDockerAvailable();

    private PostgresTestDatabase() {
    }

    @SuppressWarnings("resource")
    private static PostgreSQLContainer<?> startContainerIfDockerAvailable() {
        if (!DockerClientFactory.instance().isDockerAvailable()) {
            return null;
        }
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16-alpine");
        container.start();
        return container;
    }

    /** 把連線與 schema 相關屬性套進測試 context */
    public static void apply(DynamicPropertyRegistry registry) {
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
