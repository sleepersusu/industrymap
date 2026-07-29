package com.profetai.industrymap.support;

import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 端點層整合測試的共用基底：真實 PostgreSQL + 完整 Spring context + MockMvc。
 *
 * <p>與 {@code AbstractPostgresIntegrationTest} 的差別是驗證的層次——那一支只裝 JPA
 * （{@code @DataJpaTest}），驗的是單支 repository 查詢；這一支要從 HTTP 進入點打到資料庫，
 * 才驗得出「controller → service → repository 一路上有沒有哪一層漏了過濾」。</p>
 *
 * <p>{@code @Transactional} 讓 fixture 在測試結束後回滾：沒有 Docker 時測試跑在共用的開發資料庫上，
 * 留下的 fixture 會撞掉下一次執行的正規化名稱唯一鍵。MockMvc 走 mock 環境、與測試同一條執行緒，
 * service 的交易因此併入本交易，讀得到尚未提交的 fixture。</p>
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public abstract class AbstractPostgresWebIntegrationTest {

    /** 見 {@link PostgresTestDatabase#FIXTURE_PREFIX} */
    protected static final String FIXTURE_PREFIX = PostgresTestDatabase.FIXTURE_PREFIX;

    @Autowired
    protected MockMvc mockMvc;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.apply(registry);
    }
}
