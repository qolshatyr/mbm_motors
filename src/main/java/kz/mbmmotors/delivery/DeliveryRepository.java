package kz.mbmmotors.delivery;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class DeliveryRepository {
  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;

  public DeliveryRepository(JdbcTemplate jdbc, PlatformTransactionManager manager) {
    this.jdbc = jdbc;
    tx = new TransactionTemplate(manager);
    tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    tx.setTimeout(15);
  }

  boolean transition(UUID id, String before, String after, Instant now) {
    return Boolean.TRUE.equals(
        tx.execute(
            status ->
                jdbc.update(
                        "update delivery_outbox set state=?,updated_at=? where order_id=? and state=?",
                        after,
                        Timestamp.from(now),
                        id,
                        before)
                    == 1));
  }

  DeliverySnapshot snapshot(UUID id) {
    return tx.execute(
        status -> {
          var items =
              jdbc.query(
                  "select * from local_order_items where order_id=? order by position",
                  (rs, row) ->
                      new DeliverySnapshot.Item(
                          rs.getString("product_id"),
                          rs.getString("product_name"),
                          rs.getString("sku"),
                          rs.getString("unit"),
                          rs.getInt("quantity"),
                          rs.getBigDecimal("price"),
                          rs.getBigDecimal("line_total")),
                  id);
          return jdbc.queryForObject(
              "select * from local_orders where id=?",
              (rs, row) ->
                  new DeliverySnapshot(
                      id,
                      rs.getLong("number"),
                      rs.getString("store_id"),
                      rs.getString("customer_name"),
                      rs.getString("phone"),
                      rs.getString("comment"),
                      rs.getBigDecimal("total"),
                      rs.getInt("item_count"),
                      items),
              id);
        });
  }

  String customerId(UUID id) {
    return tx.execute(
        status ->
            jdbc.queryForObject(
                "select customer_id from delivery_outbox where order_id=?", String.class, id));
  }

  boolean customerConfirmed(UUID id, String customerId, Instant now) {
    return Boolean.TRUE.equals(
        tx.execute(
            status ->
                jdbc.update(
                        "update delivery_outbox set state='CUSTOMER_CREATED',customer_id=?,updated_at=? where order_id=? and state='CUSTOMER_IN_FLIGHT'",
                        customerId,
                        Timestamp.from(now),
                        id)
                    == 1));
  }

  void saleConfirmed(UUID id, DeliveryGateway.SaleResult sale, Instant now) {
    tx.executeWithoutResult(
        status ->
            jdbc.update(
                "update delivery_outbox set state='SENT',sale_id=?,sale_number=?,updated_at=? where order_id=? and state='SALE_IN_FLIGHT'",
                sale.id(),
                sale.number(),
                Timestamp.from(now),
                id));
  }

  /**
   * Partner orders skip customer creation: the partner's CloudShop card is already linked. Orders
   * without a partner or without a linked card stay waiting for staff.
   */
  boolean promotePartnerOrder(UUID id, Instant now) {
    return Boolean.TRUE.equals(
        tx.execute(
            status ->
                jdbc.update(
                        """
                        update delivery_outbox d
                        set state='CUSTOMER_CREATED',customer_id=p.cloudshop_customer_id,updated_at=?
                        from local_orders o join partners p on p.id=o.partner_id
                        where d.order_id=? and o.id=d.order_id and d.state='WAITING_CONFIGURATION'
                          and p.enabled and p.cloudshop_customer_id is not null
                        """,
                        Timestamp.from(now),
                        id)
                    == 1));
  }

  /** Oldest deliverable partner orders first; at most {@code limit} per run. */
  java.util.List<UUID> deliverable(int limit) {
    return tx.execute(
        status ->
            jdbc.queryForList(
                """
                select d.order_id from delivery_outbox d
                join local_orders o on o.id=d.order_id join partners p on p.id=o.partner_id
                where (d.state='CUSTOMER_CREATED'
                  or (d.state='WAITING_CONFIGURATION' and p.enabled and p.cloudshop_customer_id is not null))
                order by d.created_at limit ?
                """,
                UUID.class,
                limit));
  }

  int recover(Instant before, Instant now) {
    return tx.execute(
        status ->
            jdbc.update(
                "update delivery_outbox set state='UNKNOWN',updated_at=? where state in ('CUSTOMER_IN_FLIGHT','SALE_IN_FLIGHT') and updated_at < ?",
                Timestamp.from(now),
                Timestamp.from(before)));
  }
}
