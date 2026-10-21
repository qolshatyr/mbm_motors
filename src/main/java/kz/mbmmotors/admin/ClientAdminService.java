package kz.mbmmotors.admin;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import kz.mbmmotors.partner.CustomerCards;
import kz.mbmmotors.partner.NewPartner;
import kz.mbmmotors.partner.PartnerRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Owner's management of client (partner) accounts: create with a password handed over once, new
 * password, block and the CloudShop customer card. A card request is made at most once per claim;
 * an unclear answer is recorded and never retried automatically.
 */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ClientAdminService {
  private final PartnerRepository partners;
  private final PasswordEncoder encoder;
  private final CustomerCards cards;
  private final AdminProperties properties;
  private final Clock clock;

  public ClientAdminService(
      PartnerRepository partners,
      PasswordEncoder encoder,
      CustomerCards cards,
      AdminProperties properties,
      Clock clock) {
    this.partners = partners;
    this.encoder = encoder;
    this.cards = cards;
    this.properties = properties;
    this.clock = clock;
  }

  public Clients list(String query) {
    String q = query == null ? "" : query.strip();
    if (q.length() > 120) throw new Problem(400, "Запрос поиска длиннее 120 символов.");
    List<Client> clients = partners.clients(q).stream().map(ClientAdminService::view).toList();
    return new Clients(clients, properties.isCreateCustomers());
  }

  public Client create(NewClient request) {
    NewPartner partner;
    try {
      partner =
          NewPartner.validate(
              request.login(),
              request.company(),
              request.contactName(),
              request.phone(),
              request.bin());
      NewPartner.validatePassword(request.password());
    } catch (IllegalArgumentException invalid) {
      throw new Problem(400, invalid.getMessage());
    }
    try {
      partners.create(partner, encoder.encode(request.password()), clock.instant());
    } catch (DuplicateKeyException taken) {
      throw new Problem(409, "Такой логин уже занят.");
    }
    if (properties.isCreateCustomers()) requestCard(partner.login(), false);
    return find(partner.login());
  }

  public void changePassword(String login, String password) {
    try {
      NewPartner.validatePassword(password);
    } catch (IllegalArgumentException invalid) {
      throw new Problem(400, invalid.getMessage());
    }
    if (!partners.changePassword(key(login), encoder.encode(password))) throw notFound();
  }

  public Client setEnabled(String login, boolean enabled) {
    if (!partners.setEnabled(key(login), enabled)) throw notFound();
    return find(login);
  }

  /** Links a card staff found in CloudShop, for example after an unclear creation. */
  public Client linkCustomer(String login, String customerId) {
    String id = customerId == null ? "" : customerId.strip().toLowerCase(Locale.ROOT);
    if (!id.matches("[a-f0-9]{24}"))
      throw new Problem(400, "ID карточки CloudShop: 24 символа 0-9, a-f.");
    Client client = find(login);
    if (client.customer().id() != null)
      throw new Problem(409, "У клиента уже есть карточка CloudShop.");
    if (!partners.linkCustomer(client.login(), id))
      throw new Problem(409, "Карточка не привязана: обновите список и повторите.");
    return find(login);
  }

  /** {@code afterCheck}: staff confirmed in CloudShop that an unclear request created no card. */
  public Client createCustomer(String login, boolean afterCheck) {
    if (!properties.isCreateCustomers())
      throw new Problem(409, "Создание карточек CloudShop выключено в настройках сайта.");
    Client client = find(login);
    if (client.customer().id() != null)
      throw new Problem(409, "У клиента уже есть карточка CloudShop.");
    requestCard(client.login(), afterCheck);
    return find(login);
  }

  private void requestCard(String login, boolean afterCheck) {
    if (!partners.claimCustomerRequest(login, afterCheck))
      throw new Problem(
          409,
          "Карточка уже запрашивалась. Проверьте покупателей в CloudShop и укажите ID карточки или подтвердите, что её нет.");
    var partner = partners.find(login).orElseThrow(this::notFound);
    String id;
    try {
      id = cards.create(partner.company(), partner.phone());
    } catch (RuntimeException unclear) {
      partners.markCustomerUnknown(login);
      return;
    }
    if (!partners.linkCustomer(login, id)) partners.markCustomerUnknown(login);
  }

  private Client find(String login) {
    return partners.client(key(login)).map(ClientAdminService::view).orElseThrow(this::notFound);
  }

  private static String key(String login) {
    return login == null ? "" : login.strip().toLowerCase(Locale.ROOT);
  }

  private Problem notFound() {
    return new Problem(404, "Клиент не найден.");
  }

  private static Client view(PartnerRepository.Client c) {
    String state =
        c.cloudshopCustomerId() != null
            ? "LINKED"
            : c.cloudshopCustomerState() == null ? "NONE" : c.cloudshopCustomerState();
    return new Client(
        c.login(),
        c.company(),
        c.contactName(),
        c.phone(),
        c.bin(),
        c.enabled(),
        c.createdAt(),
        c.lastLoginAt(),
        new Customer(state, c.cloudshopCustomerId()));
  }

  public record NewClient(
      String login, String company, String contactName, String phone, String bin, String password) {
    @Override
    public String toString() {
      return "NewClient[login=" + login + "]";
    }
  }

  public record Clients(List<Client> clients, boolean customerCreation) {}

  public record Client(
      String login,
      String company,
      String contactName,
      String phone,
      String bin,
      boolean enabled,
      Instant createdAt,
      Instant lastLoginAt,
      Customer customer) {}

  /** {@code state}: NONE, PENDING (request in flight or interrupted), UNKNOWN or LINKED. */
  public record Customer(String state, String id) {}

  public static class Problem extends RuntimeException {
    private final int status;

    public Problem(int status, String message) {
      super(message);
      this.status = status;
    }

    public int status() {
      return status;
    }
  }
}
