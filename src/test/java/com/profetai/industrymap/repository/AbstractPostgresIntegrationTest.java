package com.profetai.industrymap.repository;

import com.profetai.industrymap.support.PostgresTestDatabase;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Repository 層整合測試的共用基底：以真實 PostgreSQL 跑 Flyway migration，
 * 才能驗證唯一鍵、partial unique index 與 check constraint 是否真的生效——
 * 這些限制在 H2 或 mock 之下都測不出來。
 *
 * <p>連線來源與 fixture 前綴集中在 {@link PostgresTestDatabase}，與走完整 Spring context 的
 * 端點層整合測試共用同一個資料庫。</p>
 */
@Tag("integration")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class AbstractPostgresIntegrationTest {

    /** 見 {@link PostgresTestDatabase#FIXTURE_PREFIX} */
    protected static final String FIXTURE_PREFIX = PostgresTestDatabase.FIXTURE_PREFIX;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.apply(registry);
    }
}
