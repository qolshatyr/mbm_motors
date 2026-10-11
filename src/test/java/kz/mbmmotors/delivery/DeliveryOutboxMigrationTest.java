package kz.mbmmotors.delivery;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@EnabledIfEnvironmentVariable(named = "MBM_TEST_DATABASE_URL", matches = ".*/mbm_test")
@SpringBootTest(
    properties = {
      "spring.datasource.url=${MBM_TEST_DATABASE_URL}",
      "spring.datasource.username=${MBM_TEST_DATABASE_USER:mbm}",
      "spring.datasource.password=${MBM_TEST_DATABASE_PASSWORD:}"
    })
@Transactional
class DeliveryOutboxMigrationTest {
  @Autowired JdbcTemplate jdbc;

  @Test
  void migrationBackfillsExistingOrdersAndCascadeRemovesOnlyTheirJobs() throws Exception {
    // Transaction-local schema keeps the shared mbm_test tables untouched and rolls back in full.
    String schema = "outbox_test_" + UUID.randomUUID().toString().replace("-", "");
    jdbc.execute("create schema " + schema);
    jdbc.execute("set local search_path to " + schema);
    jdbc.execute(
        new ClassPathResource("db/migration/V2__local_orders.sql")
            .getContentAsString(StandardCharsets.UTF_8));
    var id = UUID.randomUUID();
    jdbc.update(
        """
        insert into local_orders(id,idempotency_key,owner_id,request_hash,revision,status,
        store_id,store_name,customer_name,phone,comment,consent_at,created_at,total,total_text,item_count)
        values (?,?,?,? ,?,'RECEIVED','aaaaaaaaaaaaaaaaaaaaaaaa','Synthetic store',
        'Synthetic buyer','+77000000000','',now(),'2026-10-01T00:00:00Z',3000,'3 000 ₸',1)
        """,
        id,
        UUID.randomUUID(),
        UUID.randomUUID(),
        "a".repeat(64),
        "b".repeat(64));
    jdbc.update(
        """
        insert into local_orders(id,idempotency_key,owner_id,request_hash,revision,status,
        store_id,store_name,customer_name,phone,comment,consent_at,created_at,total,total_text,item_count)
        select ?,?,?,request_hash,revision,status,store_id,store_name,customer_name,phone,comment,
        consent_at,created_at,total,total_text,item_count from local_orders where id=?
        """,
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        id);
    jdbc.execute(
        new ClassPathResource("db/migration/V4__delivery_outbox.sql")
            .getContentAsString(StandardCharsets.UTF_8));
    assertThat(jdbc.queryForObject("select count(*) from delivery_outbox", Integer.class))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "select order_id from delivery_outbox where order_id=?", UUID.class, id))
        .isEqualTo(id);
    assertThat(
            jdbc.queryForObject(
                "select state from delivery_outbox where order_id=?", String.class, id))
        .isEqualTo("WAITING_CONFIGURATION");
    assertThat(
            jdbc.queryForObject(
                "select bool_and(created_at = (select created_at from local_orders where id=order_id)) from delivery_outbox",
                Boolean.class))
        .isTrue();
    jdbc.update("delete from local_orders where id=?", id);
    assertThat(jdbc.queryForObject("select count(*) from delivery_outbox", Integer.class))
        .isEqualTo(1);
  }
}
