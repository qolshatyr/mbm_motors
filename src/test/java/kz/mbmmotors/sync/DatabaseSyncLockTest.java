package kz.mbmmotors.sync;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class DatabaseSyncLockTest {
  @Test
  void failedUnlockAbortsPhysicalSessionAndPreservesTaskFailure() throws Exception {
    var source = mock(DataSource.class);
    var connection = mock(Connection.class);
    var acquire = mock(Statement.class);
    var release = mock(Statement.class);
    var result = mock(ResultSet.class);
    when(source.getConnection()).thenReturn(connection);
    when(connection.createStatement()).thenReturn(acquire, release);
    when(acquire.executeQuery(anyString())).thenReturn(result);
    when(result.getBoolean(1)).thenReturn(true);
    when(release.execute(anyString())).thenThrow(new SQLException("secret"));
    var failure = new IllegalStateException("Task failed");
    assertThatThrownBy(
            () ->
                new DatabaseSyncLock(source)
                    .execute(
                        () -> {
                          throw failure;
                        }))
        .isSameAs(failure);
    verify(connection).abort(any());
    assertThat(failure.getSuppressed()).hasSize(1);
    assertThat(failure.getSuppressed()[0]).hasMessageNotContaining("secret");
  }
}
