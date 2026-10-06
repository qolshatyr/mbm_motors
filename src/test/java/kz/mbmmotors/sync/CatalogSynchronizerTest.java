package kz.mbmmotors.sync;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import kz.mbmmotors.catalog.CatalogImportService;
import kz.mbmmotors.cloudshop.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CatalogSynchronizerTest {
  private final CloudShopGateway gateway = mock(CloudShopGateway.class);
  private final CatalogImportService catalog = mock(CatalogImportService.class);
  private final DatabaseSyncLock lock = mock(DatabaseSyncLock.class);
  private final Instant now = Instant.parse("2026-10-05T12:00:00Z");
  private CatalogSynchronizer sync;

  @BeforeEach
  void setup() {
    var settings =
        new SyncProperties(
            false,
            Duration.ofMinutes(5),
            Duration.ofMinutes(5),
            Duration.ofDays(1),
            1000,
            10,
            Duration.ZERO,
            SyncProperties.Mode.NONE);
    sync =
        new CatalogSynchronizer(gateway, catalog, settings, lock, Clock.fixed(now, ZoneOffset.UTC));
    when(lock.execute(any()))
        .thenAnswer(
            call -> {
              ((Runnable) call.getArgument(0)).run();
              return true;
            });
    when(catalog.state()).thenReturn(new CatalogImportService.State(null, null));
  }

  private CloudShopProduct product(String id) {
    return new CloudShopProduct(
        id,
        "Товар",
        "same-sku",
        "1",
        "шт",
        new BigDecimal("3000"),
        Map.of(),
        Map.of(),
        now,
        null,
        List.of());
  }

  @Test
  void shortPagesAdvanceByActualCountAndWaitForEmptyPage() {
    when(gateway.products(1000, 0)).thenReturn(List.of(product("a")));
    when(gateway.products(1000, 1)).thenReturn(List.of(product("b")));
    when(gateway.products(1000, 2)).thenReturn(List.of());
    assertThat(sync.synchronize(true)).isTrue();
    verify(catalog).apply(any(), argThat(rows -> rows.size() == 2), eq(now), eq(now), eq(true));
  }

  @Test
  void failedLaterPageDoesNotApplyOrAdvanceCheckpoint() {
    when(gateway.products(1000, 0)).thenReturn(List.of(product("a")));
    when(gateway.products(1000, 1)).thenThrow(new IllegalStateException("upstream-secret"));
    assertThatThrownBy(() -> sync.synchronize(true)).hasMessageNotContaining("upstream-secret");
    verify(catalog, never()).apply(any(), any(), any(), any(), anyBoolean());
    verify(catalog).fail(any(), eq(now), eq("SYNC_FAILED"));
  }

  @Test
  void repeatedPageIsFailureWithoutCommit() {
    when(gateway.products(anyInt(), anyInt())).thenReturn(List.of(product("a")));
    assertThatThrownBy(() -> sync.synchronize(true)).isInstanceOf(IllegalStateException.class);
    verify(catalog, never()).apply(any(), any(), any(), any(), anyBoolean());
  }

  @Test
  void incrementalUsesOverlapAndCommitsStartedTimeEvenForEmptyList() {
    Instant checkpoint = now.minusSeconds(600);
    when(catalog.state())
        .thenReturn(new CatalogImportService.State(checkpoint, now.minusSeconds(3600)));
    when(gateway.updated(checkpoint.minusSeconds(300))).thenReturn(List.of());
    sync.synchronize(false);
    verify(gateway).updated(checkpoint.minusSeconds(300));
    verify(catalog).apply(any(), eq(List.of()), eq(now), eq(now), eq(false));
    verify(gateway, never()).products(anyInt(), anyInt());
  }

  @Test
  void concurrentRunDoesNotFetchOrCreateRun() {
    doReturn(false).when(lock).execute(any());
    assertThat(sync.synchronize(true)).isFalse();
    verifyNoInteractions(gateway, catalog);
  }
}
