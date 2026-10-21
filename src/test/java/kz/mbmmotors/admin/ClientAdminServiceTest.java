package kz.mbmmotors.admin;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import kz.mbmmotors.cloudshop.CloudShopException;
import kz.mbmmotors.partner.CustomerCards;
import kz.mbmmotors.partner.Partner;
import kz.mbmmotors.partner.PartnerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class ClientAdminServiceTest {
  PartnerRepository partners = mock(PartnerRepository.class);
  CustomerCards cards = mock(CustomerCards.class);
  AdminProperties properties = new AdminProperties();
  ClientAdminService service =
      new ClientAdminService(
          partners,
          new BCryptPasswordEncoder(4),
          cards,
          properties,
          Clock.fixed(Instant.parse("2026-10-21T00:00:00Z"), ZoneOffset.UTC));
  ClientAdminService.NewClient request =
      new ClientAdminService.NewClient(
          " Turbo ", "ТОО «Turbo»", "Ерлан", "8 701 555 12 34", "123456789012", "synthetic-pass-1");

  @BeforeEach
  void stored() {
    when(partners.client("turbo")).thenReturn(Optional.of(client(null, null)));
    when(partners.find("turbo"))
        .thenReturn(
            Optional.of(
                new Partner(
                    UUID.randomUUID(),
                    "turbo",
                    "hash",
                    "ТОО «Turbo»",
                    "Ерлан",
                    "+77015551234",
                    null,
                    true,
                    0)));
  }

  @Test
  void createStoresHashNormalizedDetailsAndCreatesNoCardWhenDisabled() {
    service.create(request);
    verify(partners)
        .create(
            argThat(
                p ->
                    p.login().equals("turbo")
                        && p.phone().equals("+77015551234")
                        && p.bin().equals("123456789012")),
            argThat(hash -> hash.startsWith("$2") && !hash.contains("synthetic")),
            any());
    verifyNoInteractions(cards);
  }

  @Test
  void createLinksTheNewCardOnce() {
    properties.setCreateCustomers(true);
    when(partners.claimCustomerRequest("turbo", false)).thenReturn(true);
    when(cards.create("ТОО «Turbo»", "+77015551234")).thenReturn("6ac110842dd87801040fed88");
    when(partners.linkCustomer("turbo", "6ac110842dd87801040fed88")).thenReturn(true);
    service.create(request);
    verify(cards, times(1)).create(any(), any());
    verify(partners, never()).markCustomerUnknown(any());
  }

  @Test
  void unclearCardAnswerIsRecordedAndNotRetried() {
    properties.setCreateCustomers(true);
    when(partners.claimCustomerRequest("turbo", false)).thenReturn(true, false);
    when(cards.create(any(), any())).thenThrow(new CloudShopException("timeout"));
    service.create(request);
    verify(partners).markCustomerUnknown("turbo");
    when(partners.client("turbo")).thenReturn(Optional.of(client(null, "UNKNOWN")));
    assertThatThrownBy(() -> service.createCustomer("turbo", false))
        .isInstanceOf(ClientAdminService.Problem.class)
        .hasMessageContaining("уже запрашивалась");
    verify(cards, times(1)).create(any(), any());
  }

  @Test
  void validationAndDuplicatesBecomeProblems() {
    doThrow(new DuplicateKeyException("login")).when(partners).create(any(), any(), any());
    assertThatThrownBy(() -> service.create(request))
        .hasMessage("Такой логин уже занят.")
        .extracting(e -> ((ClientAdminService.Problem) e).status())
        .isEqualTo(409);
    var shortPassword =
        new ClientAdminService.NewClient("turbo", "ТОО", "Ерлан", "+77015551234", null, "short");
    assertThatThrownBy(() -> service.create(shortPassword))
        .extracting(e -> ((ClientAdminService.Problem) e).status())
        .isEqualTo(400);
    assertThatThrownBy(() -> service.linkCustomer("turbo", "not-an-id"))
        .extracting(e -> ((ClientAdminService.Problem) e).status())
        .isEqualTo(400);
    assertThat(request.toString()).doesNotContain("synthetic");
  }

  @Test
  void newPasswordAndBlockReachTheRepository() {
    when(partners.changePassword(eq("turbo"), anyString())).thenReturn(true);
    service.changePassword("Turbo", "synthetic-pass-2");
    verify(partners).changePassword(eq("turbo"), argThat(h -> h.startsWith("$2")));
    when(partners.setEnabled("turbo", false)).thenReturn(true);
    service.setEnabled("turbo", false);
    verify(partners).setEnabled("turbo", false);
    when(partners.changePassword(eq("ghost"), anyString())).thenReturn(false);
    assertThatThrownBy(() -> service.changePassword("ghost", "synthetic-pass-2"))
        .extracting(e -> ((ClientAdminService.Problem) e).status())
        .isEqualTo(404);
  }

  private static PartnerRepository.Client client(String customerId, String state) {
    return new PartnerRepository.Client(
        "turbo",
        "ТОО «Turbo»",
        "Ерлан",
        "+77015551234",
        null,
        true,
        Instant.parse("2026-10-21T00:00:00Z"),
        null,
        customerId,
        state);
  }
}
