package kz.mbmmotors.admin;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@EnabledIfEnvironmentVariable(named = "MBM_TEST_DATABASE_URL", matches = ".*/mbm_test")
@SpringBootTest(
    properties = {
      "spring.datasource.url=${MBM_TEST_DATABASE_URL}",
      "spring.datasource.username=${MBM_TEST_DATABASE_USER:mbm}",
      "spring.datasource.password=${MBM_TEST_DATABASE_PASSWORD:}",
      "admin.enabled=false"
    })
@Transactional
class StaffRequestPersistenceTest {
  @Autowired JdbcTemplate jdbc;
  @Autowired StaffRequestService service;

  @Test
  void stablePagingAndSnapshotDetailUseRealPostgresWithoutCatalogJoinOrMutation() {
    long before = jdbc.queryForObject("select count(*) from local_orders", Long.class);
    long first = 0;
    long last = 0;
    for (int i = 0; i < 21; i++) {
      var id = UUID.randomUUID();
      long number =
          jdbc.queryForObject(
              """
          insert into local_orders(id,idempotency_key,owner_id,request_hash,revision,status,
          store_id,store_name,customer_name,phone,comment,consent_at,created_at,total,total_text,item_count)
          values (?,?,?,? ,?,'RECEIVED','aaaaaaaaaaaaaaaaaaaaaaaa','Synthetic snapshot store',
          'Synthetic buyer','+77000000000','Synthetic comment',now(),'2100-01-01T00:00:00Z',3000.25,'3 000,25 ₸',1)
          returning number
          """,
              Long.class,
              id,
              UUID.randomUUID(),
              UUID.randomUUID(),
              "a".repeat(64),
              "b".repeat(64));
      jdbc.update(
          """
          insert into local_order_items(order_id,position,product_id,product_name,sku,unit,quantity,price,line_total)
          values (?,1,'bbbbbbbbbbbbbbbbbbbbbbbb','Immutable part','TEST-STAFF','шт',1,3000.25,3000.25)
          """,
          id);
      if (i == 0) first = number;
      last = number;
    }
    var page = service.list(0);
    assertThat(page.pageSize()).isEqualTo(20);
    assertThat(page.totalItems()).isEqualTo(before + 21);
    assertThat(page.items()).hasSize(20);
    assertThat(page.items().getFirst().number()).isEqualTo(Long.toString(last));
    assertThat(service.list(1).items().getFirst().number()).isEqualTo(Long.toString(first));
    assertThat(service.list(100000).items()).isEmpty();
    var detail = service.detail(last).orElseThrow();
    assertThat(detail.customerName()).isEqualTo("Synthetic buyer");
    assertThat(detail.items()).hasSize(1);
    assertThat(detail.items().getFirst().productName()).isEqualTo("Immutable part");
    assertThat(detail.items().getFirst().priceText()).isEqualTo("3\u00a0000,25 ₸");
    assertThat(service.detail(Long.MAX_VALUE)).isEmpty();
    assertThat(jdbc.queryForObject("select count(*) from local_orders", Long.class))
        .isEqualTo(before + 21);
  }
}
