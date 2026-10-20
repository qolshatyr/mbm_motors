package kz.mbmmotors.partner;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows password guessing: after {@code limit} failures within {@code window} for one client
 * address and login, further attempts are refused until the window passes. Keyed by both, so an
 * attacker cannot lock a partner out from elsewhere. In memory: a restart clears it.
 */
public final class LoginThrottle {
  private static final int MAX_KEYS = 10_000;
  private final int limit;
  private final Duration window;
  private final Clock clock;
  private final Map<String, ArrayDeque<Instant>> failures = new ConcurrentHashMap<>();

  public LoginThrottle(int limit, Duration window, Clock clock) {
    this.limit = limit;
    this.window = window;
    this.clock = clock;
  }

  public boolean blocked(String address, String login) {
    var attempts = failures.get(key(address, login));
    if (attempts == null) return false;
    synchronized (attempts) {
      prune(attempts);
      return attempts.size() >= limit;
    }
  }

  public void failure(String address, String login) {
    if (failures.size() >= MAX_KEYS) failures.clear();
    var attempts = failures.computeIfAbsent(key(address, login), k -> new ArrayDeque<>());
    synchronized (attempts) {
      prune(attempts);
      attempts.addLast(clock.instant());
    }
  }

  public void success(String address, String login) {
    failures.remove(key(address, login));
  }

  private void prune(ArrayDeque<Instant> attempts) {
    Instant from = clock.instant().minus(window);
    while (!attempts.isEmpty() && attempts.peekFirst().isBefore(from)) attempts.removeFirst();
  }

  private static String key(String address, String login) {
    String user = login == null ? "" : login.strip().toLowerCase(Locale.ROOT);
    if (user.length() > 64) user = user.substring(0, 64);
    return (address == null ? "" : address) + "|" + user;
  }
}
