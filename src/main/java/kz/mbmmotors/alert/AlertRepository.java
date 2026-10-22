package kz.mbmmotors.alert;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AlertRepository {
  private final JdbcTemplate jdbc;

  public AlertRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** An order staff should look at; only the number and a reason without customer data. */
  public record Pending(UUID orderId, long number, String state, String reason) {}

  /**
   * Unreconciled UNKNOWN orders and, when {@code waitingBefore} is given, orders still waiting to
   * be delivered since before that moment. Oldest first, each order until it is marked alerted.
   */
  public List<Pending> pending(Instant waitingBefore, int limit) {
    return jdbc.query(
        """
        select d.order_id, o.number, d.state,
          case
            when d.state = 'UNKNOWN' then coalesce(d.failure_reason, 'причина не записана')
            when o.partner_id is null then 'заявка без партнёра'
            when not p.enabled then 'партнёр заблокирован'
            when p.cloudshop_customer_id is null then 'у партнёра нет карточки CloudShop'
            else 'передача не началась'
          end as reason
        from delivery_outbox d
        join local_orders o on o.id = d.order_id
        left join partners p on p.id = o.partner_id
        where d.alerted_at is null
          and ((d.state = 'UNKNOWN' and d.reconciled_result is null)
            or (d.state = 'WAITING_CONFIGURATION' and d.created_at < ?))
        order by d.created_at
        limit ?
        """,
        (rs, row) ->
            new Pending(
                rs.getObject("order_id", UUID.class),
                rs.getLong("number"),
                rs.getString("state"),
                rs.getString("reason")),
        Timestamp.from(waitingBefore == null ? Instant.EPOCH : waitingBefore),
        limit);
  }

  /** A new order for staff: the partner company, not the contact person or phone. */
  public record NewOrder(
      UUID orderId, long number, String company, int itemCount, BigDecimal total) {}

  /** Orders not yet announced to staff, oldest first. */
  public List<NewOrder> newOrders(int limit) {
    return jdbc.query(
        """
        select o.id, o.number, p.company, o.item_count, o.total
        from local_orders o
        left join partners p on p.id = o.partner_id
        where o.announced_at is null
        order by o.created_at
        limit ?
        """,
        (rs, row) ->
            new NewOrder(
                rs.getObject("id", UUID.class),
                rs.getLong("number"),
                rs.getString("company"),
                rs.getInt("item_count"),
                rs.getBigDecimal("total")),
        limit);
  }

  /** Marks orders announced; an order already marked keeps its first time. */
  public int markAnnounced(List<UUID> orderIds, Instant now) {
    if (orderIds.isEmpty()) return 0;
    return jdbc.update(
        "update local_orders set announced_at = ? where id = any(?) and announced_at is null",
        ps -> {
          ps.setTimestamp(1, Timestamp.from(now));
          ps.setArray(2, ps.getConnection().createArrayOf("uuid", orderIds.toArray()));
        });
  }

  /** Marks orders alerted; an order already marked keeps its first time. */
  public int markAlerted(List<UUID> orderIds, Instant now) {
    if (orderIds.isEmpty()) return 0;
    return jdbc.update(
        "update delivery_outbox set alerted_at = ? where order_id = any(?) and alerted_at is null",
        ps -> {
          ps.setTimestamp(1, Timestamp.from(now));
          ps.setArray(2, ps.getConnection().createArrayOf("uuid", orderIds.toArray()));
        });
  }
}
