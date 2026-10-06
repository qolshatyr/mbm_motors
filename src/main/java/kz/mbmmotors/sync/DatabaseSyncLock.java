package kz.mbmmotors.sync;

import java.sql.*;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

@Component
class DatabaseSyncLock {
  private final DataSource dataSource;

  DatabaseSyncLock(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  // Session lock uses its own connection, preventing overlap across application processes.
  boolean execute(Runnable task) {
    try (Connection connection = dataSource.getConnection()) {
      try (Statement statement = connection.createStatement();
          ResultSet result = statement.executeQuery("select pg_try_advisory_lock(73528101)")) {
        result.next();
        if (!result.getBoolean(1)) return false;
      }
      Throwable primary = null;
      try {
        task.run();
      } catch (RuntimeException | Error failure) {
        primary = failure;
        throw failure;
      } finally {
        try (Statement statement = connection.createStatement()) {
          statement.execute("select pg_advisory_unlock(73528101)");
        } catch (SQLException failure) {
          // close() alone can return a still-locked physical session to the pool.
          try {
            connection.abort(Runnable::run);
          } catch (SQLException ignored) {
          }
          var safe = new IllegalStateException("Cannot release catalog synchronization lock");
          if (primary != null) primary.addSuppressed(safe);
          else throw safe;
        }
      }
      return true;
    } catch (SQLException error) {
      throw new IllegalStateException("Cannot acquire/release catalog synchronization lock");
    }
  }
}
