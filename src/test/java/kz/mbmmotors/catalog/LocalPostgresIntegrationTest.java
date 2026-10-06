package kz.mbmmotors.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Optional verification against a dedicated real PostgreSQL when Docker is unavailable. */
@EnabledIfEnvironmentVariable(named = "MBM_TEST_DATABASE_URL", matches = ".*/mbm_test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "spring.datasource.url=${MBM_TEST_DATABASE_URL}",
      "spring.datasource.username=${MBM_TEST_DATABASE_USER:mbm}",
      "spring.datasource.password=${MBM_TEST_DATABASE_PASSWORD:}"
    })
class LocalPostgresIntegrationTest {
  @Autowired CatalogImportService catalog;
  @Autowired JdbcTemplate jdbc;

  @Test
  void realPostgresMigrationsAndAtomicRollback() {
    CatalogIntegrationTest.verify(catalog, jdbc);
  }
}
