package com.portfolio.oms;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.portfolio.oms.authentication.AuthController;
import com.portfolio.oms.cart.*;
import com.portfolio.oms.common.BusinessException;
import com.portfolio.oms.customer.*;
import com.portfolio.oms.discount.*;
import com.portfolio.oms.inventory.*;
import com.portfolio.oms.order.*;
import com.portfolio.oms.payment.*;
import com.portfolio.oms.product.*;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc(
    print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
@ActiveProfiles("test")
class BusinessScenariosIT {
  static PostgreSQLContainer<?> container;
  static final String schema = "test_" + UUID.randomUUID().toString().replace("-", "");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) throws Exception {
    String url = System.getenv("TEST_DB_URL"),
        user = System.getenv("TEST_DB_USERNAME"),
        password = System.getenv("TEST_DB_PASSWORD");
    if (url == null) {
      container = new PostgreSQLContainer<>("postgres:17-alpine");
      container.start();
      url = container.getJdbcUrl();
      user = container.getUsername();
      password = container.getPassword();
    }
    try (var c = DriverManager.getConnection(url, user, password);
        var statement = c.createStatement()) {
      statement.execute("CREATE SCHEMA " + schema);
    }
    String jdbcUrl = url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
    String username = user, secret = password;
    registry.add("spring.datasource.url", () -> jdbcUrl);
    registry.add("spring.datasource.username", () -> username);
    registry.add("spring.datasource.password", () -> secret);
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  @Autowired CustomerRepository customers;
  @Autowired ProductService products;
  @Autowired ProductRepository productRepository;
  @Autowired InventoryService inventory;
  @Autowired StockRepository stocks;
  @Autowired CartService carts;
  @Autowired CheckoutService checkout;
  @Autowired OrderRepository orders;
  @Autowired OrderService orderService;
  @Autowired PaymentRepository payments;
  @Autowired PaymentService paymentService;
  @Autowired PaymentWorker worker;
  @Autowired PaymentProvider provider;
  @Autowired DiscountService discounts;
  @Autowired CouponRepository coupons;

  record Buyer(UUID id, String token, UUID address) {}

  Buyer buyer, admin;
  UUID product;

  @BeforeEach
  void setup() throws Exception {
    jdbc.execute("TRUNCATE customers,categories,mock_provider_ledger CASCADE");
    buyer = register("buyer@example.com");
    admin = register("admin@example.com");
    Customer a = customers.findById(admin.id()).orElseThrow();
    a.role = Customer.Role.ADMIN;
    customers.saveAndFlush(a);
    var category = products.category("Books");
    product =
        products.save(
                null,
                new ProductController.Input(
                    "Reliable Systems",
                    "A book",
                    category.id,
                    new BigDecimal("1000.00"),
                    "BOOK-01",
                    Product.Status.ACTIVE))
            .id;
    inventory.adjust(product, 10, "Initial stock", admin.id());
  }

  Buyer register(String email) throws Exception {
    JsonNode response =
        body(
            mvc.perform(
                    post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            json.writeValueAsString(
                                new AuthController.Register(email, "test-password-123", "Buyer"))))
                .andExpect(status().isCreated())
                .andReturn());
    String token = response.get("accessToken").asText();
    UUID id = customers.findByEmail(email).orElseThrow().id;
    var address =
        body(
            mvc.perform(
                    auth(post("/api/customers/me/addresses"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            json.writeValueAsString(
                                new CustomerController.AddressInput(
                                    "Buyer", "1 Main Street", null, "Chennai", "600001", "IN"))))
                .andExpect(status().isCreated())
                .andReturn());
    return new Buyer(id, token, UUID.fromString(address.get("id").asText()));
  }

  MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder r, String token) {
    return r.header("Authorization", "Bearer " + token);
  }

  JsonNode body(MvcResult r) throws Exception {
    return json.readTree(r.getResponse().getContentAsString());
  }

  OrderController.Checkout request(Buyer b, PaymentProvider.Scenario scenario, String coupon) {
    return new OrderController.Checkout(b.address(), coupon, scenario);
  }

  OrderService.View purchase(Buyer b, PaymentProvider.Scenario scenario) {
    carts.set(b.id(), product, 1, false);
    return checkout
        .checkout(b.id(), UUID.randomUUID().toString(), request(b, scenario, null))
        .view();
  }

  Payment payment(UUID order) {
    return payments.findByOrderId(order).orElseThrow();
  }

  void process(UUID order) {
    worker.process(payment(order).id);
  }

  @Test
  void fullHttpPurchaseSnapshotsPricesAndReturnsNotifications() throws Exception {
    mvc.perform(
            auth(post("/api/cart/items"), buyer.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new CartController.ItemInput(product, 2))))
        .andExpect(status().isOk());
    JsonNode created =
        body(
            mvc.perform(
                    auth(post("/api/orders"), buyer.token())
                        .header("Idempotency-Key", "http-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            json.writeValueAsString(
                                request(buyer, PaymentProvider.Scenario.SUCCESS, null))))
                .andExpect(status().isCreated())
                .andReturn());
    UUID id = UUID.fromString(created.at("/order/id").asText());
    assertThat(created.at("/order/total").decimalValue()).isEqualByComparingTo("2360");
    assertThat(stocks.findById(product).orElseThrow().reserved).isEqualTo(2);
    Product p = productRepository.findById(product).orElseThrow();
    p.price = new BigDecimal("2000");
    productRepository.saveAndFlush(p);
    process(id);
    mvc.perform(auth(get("/api/orders/" + id), buyer.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.order.status").value("CONFIRMED"))
        .andExpect(jsonPath("$.items[0].priceAtPurchase").value(1000));
    assertThat(stocks.findById(product).orElseThrow().reserved).isZero();
    mvc.perform(auth(get("/api/notifications"), buyer.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(3));
  }

  @Test
  void failedPaymentReleasesStock() {
    var o = purchase(buyer, PaymentProvider.Scenario.FAILED);
    process(o.order().id);
    assertThat(orders.findById(o.order().id).orElseThrow().status).isEqualTo(OrderStatus.CANCELLED);
    assertThat(payment(o.order().id).status).isEqualTo(Payment.Status.FAILED);
    assertThat(stocks.findById(product).orElseThrow().available).isEqualTo(10);
  }

  @Test
  void timeoutRetriesSamePaymentThenExpiresWithoutCharge() {
    var o = purchase(buyer, PaymentProvider.Scenario.TIMEOUT);
    process(o.order().id);
    process(o.order().id);
    assertThat(payment(o.order().id).status).isEqualTo(Payment.Status.INITIATED);
    assertThat(payment(o.order().id).attempts).isEqualTo(2);
    PurchaseOrder saved = orders.findById(o.order().id).orElseThrow();
    saved.reservationExpiresAt = Instant.now().minusSeconds(1);
    orders.saveAndFlush(saved);
    orderService.expire(saved.id);
    process(saved.id);
    assertThat(payment(saved.id).status).isEqualTo(Payment.Status.FAILED);
    assertThat(stocks.findById(product).orElseThrow().available).isEqualTo(10);
    assertThat(
            jdbc.queryForObject(
                "select charge_count from mock_provider_ledger where payment_id=?",
                Integer.class,
                payment(saved.id).id))
        .isZero();
  }

  @Test
  void sequentialDuplicateReturnsOriginalResponseEvenAfterCartChanges() {
    carts.set(buyer.id(), product, 1, false);
    var r = request(buyer, PaymentProvider.Scenario.SUCCESS, null);
    var first = checkout.checkout(buyer.id(), "same-key", r);
    process(first.view().order().id);
    carts.set(buyer.id(), product, 2, false);
    var second = checkout.checkout(buyer.id(), "same-key", r);
    assertThat(second.replay()).isTrue();
    assertThat(second.view().order().id).isEqualTo(first.view().order().id);
    assertThat(second.view().order().status).isEqualTo(OrderStatus.PENDING);
    assertThat(orders.count()).isEqualTo(1);
    assertThat(carts.view(buyer.id()).items().getFirst().quantity()).isEqualTo(2);
  }

  @Test
  void keyCannotBeReusedWithDifferentRequest() {
    carts.set(buyer.id(), product, 1, false);
    checkout.checkout(buyer.id(), "key", request(buyer, PaymentProvider.Scenario.SUCCESS, null));
    assertThatThrownBy(
            () ->
                checkout.checkout(
                    buyer.id(), "key", request(buyer, PaymentProvider.Scenario.FAILED, null)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("different");
  }

  @Test
  void concurrentDuplicateCreatesOneOrder() throws Exception {
    carts.set(buyer.id(), product, 1, false);
    var outcomes =
        race(
            () ->
                checkout.checkout(
                    buyer.id(),
                    "parallel-key",
                    request(buyer, PaymentProvider.Scenario.SUCCESS, null)),
            () ->
                checkout.checkout(
                    buyer.id(),
                    "parallel-key",
                    request(buyer, PaymentProvider.Scenario.SUCCESS, null)));
    assertThat(outcomes).allMatch(v -> v instanceof CheckoutService.Result);
    assertThat(orders.count()).isEqualTo(1);
    assertThat(payments.count()).isEqualTo(1);
    assertThat(stocks.findById(product).orElseThrow().reserved).isEqualTo(1);
  }

  @Test
  void lastUnitCannotBeSoldTwice() throws Exception {
    Buyer second = register("second@example.com");
    inventory.adjust(product, -9, "Last unit", admin.id());
    carts.set(buyer.id(), product, 1, false);
    carts.set(second.id(), product, 1, false);
    var outcomes =
        race(
            () ->
                checkout.checkout(
                    buyer.id(), "last-a", request(buyer, PaymentProvider.Scenario.SUCCESS, null)),
            () ->
                checkout.checkout(
                    second.id(),
                    "last-b",
                    request(second, PaymentProvider.Scenario.SUCCESS, null)));
    assertThat(outcomes.stream().filter(CheckoutService.Result.class::isInstance).count())
        .isEqualTo(1);
    assertThat(outcomes.stream().filter(BusinessException.class::isInstance).count()).isEqualTo(1);
    assertThat(orders.count()).isEqualTo(1);
    assertThat(stocks.findById(product).orElseThrow().available).isZero();
    assertThat(stocks.findById(product).orElseThrow().reserved).isEqualTo(1);
  }

  @Test
  void concurrentProviderCallsChargeOnce() throws Exception {
    var o = purchase(buyer, PaymentProvider.Scenario.SUCCESS);
    Payment p = payment(o.order().id);
    var results =
        race(
            () -> provider.charge(p.id, p.amount, p.currency, p.scenario),
            () -> provider.charge(p.id, p.amount, p.currency, p.scenario));
    assertThat(results).containsOnly(PaymentProvider.Result.SUCCESS);
    assertThat(
            jdbc.queryForObject(
                "select charge_count from mock_provider_ledger where payment_id=?",
                Integer.class,
                p.id))
        .isEqualTo(1);
  }

  @Test
  void cancellationFencesLateCharge() throws Exception {
    var o = purchase(buyer, PaymentProvider.Scenario.SUCCESS);
    Payment p = payment(o.order().id);
    mvc.perform(auth(post("/api/orders/" + o.order().id + "/cancel"), buyer.token()))
        .andExpect(status().isOk());
    process(o.order().id);
    assertThat(provider.charge(p.id, p.amount, p.currency, p.scenario))
        .isEqualTo(PaymentProvider.Result.FAILED);
    assertThat(stocks.findById(product).orElseThrow().available).isEqualTo(10);
  }

  @Test
  void chargedBeforeCancellationIsRefunded() throws Exception {
    var o = purchase(buyer, PaymentProvider.Scenario.SUCCESS);
    Payment p = payment(o.order().id);
    provider.charge(
        p.id,
        p.amount,
        p.currency,
        p.scenario); // Simulate crash after remote capture but before local settlement.
    mvc.perform(auth(post("/api/orders/" + o.order().id + "/cancel"), buyer.token()))
        .andExpect(status().isOk());
    process(o.order().id);
    assertThat(payment(o.order().id).status).isEqualTo(Payment.Status.REFUNDED);
    assertThat(
            jdbc.queryForObject(
                "select state from mock_provider_ledger where payment_id=?", String.class, p.id))
        .isEqualTo("REFUNDED");
  }

  @Test
  void confirmedCancellationRestocksExactlyOnce() throws Exception {
    var o = purchase(buyer, PaymentProvider.Scenario.SUCCESS);
    process(o.order().id);
    mvc.perform(auth(post("/api/orders/" + o.order().id + "/cancel"), buyer.token()))
        .andExpect(status().isOk());
    mvc.perform(auth(post("/api/orders/" + o.order().id + "/cancel"), buyer.token()))
        .andExpect(status().isOk());
    process(o.order().id);
    assertThat(stocks.findById(product).orElseThrow().available).isEqualTo(10);
    assertThat(payment(o.order().id).status).isEqualTo(Payment.Status.REFUNDED);
  }

  @Test
  void invalidCouponRollsBackEntireCheckout() {
    carts.set(buyer.id(), product, 1, false);
    assertThatThrownBy(
            () ->
                checkout.checkout(
                    buyer.id(),
                    "bad-coupon",
                    request(buyer, PaymentProvider.Scenario.SUCCESS, "MISSING")))
        .isInstanceOf(BusinessException.class);
    assertThat(orders.count()).isZero();
    assertThat(stocks.findById(product).orElseThrow().available).isEqualTo(10);
    assertThat(carts.view(buyer.id()).items()).hasSize(1);
  }

  Coupon coupon() {
    return discounts.create(
        new DiscountController.Input(
            "ORDER10",
            Coupon.Type.PERCENTAGE,
            new BigDecimal("10"),
            new BigDecimal("1000"),
            new BigDecimal("500"),
            Instant.now().plusSeconds(3600),
            1,
            1,
            null));
  }

  @Test
  void expiredCouponRejected() {
    Coupon c = coupon();
    c.expiresAt = Instant.now().minusSeconds(1);
    coupons.saveAndFlush(c);
    carts.set(buyer.id(), product, 1, false);
    assertThatThrownBy(
            () ->
                checkout.checkout(
                    buyer.id(),
                    "expired",
                    request(buyer, PaymentProvider.Scenario.SUCCESS, "ORDER10")))
        .hasMessageContaining("expired");
  }

  @Test
  void couponUsageLimitIsAtomic() throws Exception {
    Buyer second = register("second@example.com");
    coupon();
    carts.set(buyer.id(), product, 1, false);
    carts.set(second.id(), product, 1, false);
    var results =
        race(
            () ->
                checkout.checkout(
                    buyer.id(),
                    "coupon-a",
                    request(buyer, PaymentProvider.Scenario.SUCCESS, "ORDER10")),
            () ->
                checkout.checkout(
                    second.id(),
                    "coupon-b",
                    request(second, PaymentProvider.Scenario.SUCCESS, "ORDER10")));
    assertThat(results.stream().filter(CheckoutService.Result.class::isInstance).count())
        .isEqualTo(1);
    assertThat(coupons.findAll().getFirst().usedCount).isEqualTo(1);
    assertThat(orders.findAll().getFirst().total).isEqualByComparingTo("1062");
  }

  @Test
  void invalidTransitionIsConflict() throws Exception {
    var o = purchase(buyer, PaymentProvider.Scenario.SUCCESS);
    mvc.perform(
            auth(post("/api/orders/" + o.order().id + "/status"), admin.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DELIVERED\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("INVALID_ORDER_STATE"));
  }

  @Test
  void ownershipAndRolesAreEnforced() throws Exception {
    Buyer other = register("other@example.com");
    var o = purchase(buyer, PaymentProvider.Scenario.SUCCESS);
    mvc.perform(auth(get("/api/orders/" + o.order().id), other.token()))
        .andExpect(status().isForbidden());
    mvc.perform(
            auth(
                get("/api/reports/orders?from=2026-01-01T00:00:00Z&to=2026-12-31T00:00:00Z"),
                buyer.token()))
        .andExpect(status().isForbidden());
    mvc.perform(
            auth(put("/api/customers/me/addresses/" + buyer.address()), other.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"recipient\":\"X\",\"line1\":\"X\",\"city\":\"X\",\"postalCode\":\"1\",\"country\":\"IN\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
  }

  @Test
  void inactiveCustomersImmediatelyLoseAccess() throws Exception {
    Customer c = customers.findById(buyer.id()).orElseThrow();
    c.active = false;
    customers.saveAndFlush(c);
    mvc.perform(auth(get("/api/customers/me"), buyer.token())).andExpect(status().isUnauthorized());
  }

  @Test
  void passwordStoredHashedAndDuplicateEmailRejected() throws Exception {
    assertThat(customers.findById(buyer.id()).orElseThrow().passwordHash).startsWith("$2");
    mvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        new AuthController.Register(
                            "BUYER@example.com", "test-password-123", "X"))))
        .andExpect(status().isConflict());
    mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"buyer@example.com\",\"password\":\"wrong\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void inactiveProductCannotCheckout() {
    carts.set(buyer.id(), product, 1, false);
    products.deactivate(product);
    assertThatThrownBy(
            () ->
                checkout.checkout(
                    buyer.id(), "inactive", request(buyer, PaymentProvider.Scenario.SUCCESS, null)))
        .hasMessageContaining("inactive");
    assertThat(orders.count()).isZero();
  }

  @Test
  void reportsReflectPaymentsAndRefunds() throws Exception {
    var o = purchase(buyer, PaymentProvider.Scenario.SUCCESS);
    process(o.order().id);
    String range =
        "?from=" + Instant.now().minusSeconds(3600) + "&to=" + Instant.now().plusSeconds(3600);
    mvc.perform(auth(get("/api/reports/revenue" + range), admin.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totals.revenue").value(1180));
    mvc.perform(auth(get("/api/reports/top-products" + range), admin.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].units").value(1));
    mvc.perform(auth(post("/api/orders/" + o.order().id + "/cancel"), buyer.token()))
        .andExpect(status().isOk());
    process(o.order().id);
    mvc.perform(auth(get("/api/reports/revenue" + range), admin.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totals.revenue").value(0));
  }

  @Test
  void migrationsAndHealthAndOpenApiAreAvailable() throws Exception {
    mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/orders']").exists());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success=true", Integer.class))
        .isGreaterThanOrEqualTo(4);
  }

  @Test
  void secondItemFailureRollsBackReservationsCouponAndOrder() {
    Product original = productRepository.findById(product).orElseThrow();
    UUID second =
        products.save(
                null,
                new ProductController.Input(
                    "Second",
                    null,
                    original.categoryId,
                    new BigDecimal("1000"),
                    "SECOND",
                    Product.Status.ACTIVE))
            .id;
    inventory.adjust(second, 1, "Initial", admin.id());
    coupon();
    carts.set(buyer.id(), product, 1, false);
    carts.set(buyer.id(), second, 1, false);
    UUID last = product.toString().compareTo(second.toString()) > 0 ? product : second;
    inventory.adjust(
        last, -stocks.findById(last).orElseThrow().available, "Sold elsewhere", admin.id());
    long before = jdbc.queryForObject("select count(*) from inventory_transactions", Long.class);
    assertThatThrownBy(
            () ->
                checkout.checkout(
                    buyer.id(),
                    "rollback",
                    request(buyer, PaymentProvider.Scenario.SUCCESS, "ORDER10")))
        .hasMessageContaining("exceeds");
    assertThat(orders.count()).isZero();
    assertThat(payments.count()).isZero();
    assertThat(coupons.findAll().getFirst().usedCount).isZero();
    assertThat(stocks.findAll()).allMatch(s -> s.reserved == 0);
    assertThat(carts.view(buyer.id()).items()).hasSize(2);
    assertThat(jdbc.queryForObject("select count(*) from inventory_transactions", Long.class))
        .isEqualTo(before);
  }

  @Test
  void expiredButCapturedPaymentIsRefunded() {
    var o = purchase(buyer, PaymentProvider.Scenario.SUCCESS);
    PurchaseOrder saved = orders.findById(o.order().id).orElseThrow();
    saved.reservationExpiresAt = Instant.now().minusSeconds(1);
    orders.saveAndFlush(saved);
    process(saved.id);
    assertThat(payment(saved.id).status).isEqualTo(Payment.Status.REFUND_PENDING);
    process(saved.id);
    assertThat(payment(saved.id).status).isEqualTo(Payment.Status.REFUNDED);
    assertThat(orders.findById(saved.id).orElseThrow().status).isEqualTo(OrderStatus.CANCELLED);
  }

  @Test
  void completeDeliveryLifecycleProducesHistory() throws Exception {
    var o = purchase(buyer, PaymentProvider.Scenario.SUCCESS);
    process(o.order().id);
    for (String next : List.of("PROCESSING", "SHIPPED", "DELIVERED"))
      mvc.perform(
              auth(post("/api/orders/" + o.order().id + "/status"), admin.token())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"status\":\"" + next + "\"}"))
          .andExpect(status().isOk());
    mvc.perform(auth(post("/api/orders/" + o.order().id + "/cancel"), buyer.token()))
        .andExpect(status().isConflict());
    mvc.perform(auth(get("/api/orders/" + o.order().id), buyer.token()))
        .andExpect(jsonPath("$.history.length()").value(5));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from notifications where order_id=? and event_type in"
                    + " ('ORDER_SHIPPED','ORDER_DELIVERED')",
                Integer.class,
                o.order().id))
        .isEqualTo(2);
  }

  @Test
  void inventoryManagerRoleCannotManageCatalog() throws Exception {
    Customer c = customers.findById(buyer.id()).orElseThrow();
    c.role = Customer.Role.INVENTORY_MANAGER;
    customers.saveAndFlush(c);
    mvc.perform(
            auth(post("/api/inventory/" + product + "/adjustments"), buyer.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"delta\":2,\"reason\":\"Delivery\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available").value(12));
    mvc.perform(auth(delete("/api/products/" + product), buyer.token()))
        .andExpect(status().isForbidden());
    mvc.perform(auth(get("/api/inventory/" + product + "/history"), buyer.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2));
  }

  @Test
  void missingHeadersAndInvalidRequestsAreBadRequests() throws Exception {
    mvc.perform(
            auth(post("/api/orders"), buyer.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        request(buyer, PaymentProvider.Scenario.SUCCESS, null))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.path").value("/api/orders"));
    mvc.perform(
            auth(post("/api/cart/items"), buyer.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"" + product + "\",\"quantity\":0}"))
        .andExpect(status().isBadRequest());
    mvc.perform(auth(get("/api/reports/revenue"), admin.token()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void signingFailuresAreUnauthorized() throws Exception {
    mvc.perform(auth(get("/api/orders"), buyer.token() + "tampered"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.path").value("/api/orders"));
  }

  @Test
  void reportsOtherEndpointsHaveValidSql() throws Exception {
    var o = purchase(buyer, PaymentProvider.Scenario.FAILED);
    process(o.order().id);
    String range =
        "?from=" + Instant.now().minusSeconds(3600) + "&to=" + Instant.now().plusSeconds(3600);
    for (String endpoint : List.of("orders", "failed-payments", "cancelled-orders"))
      mvc.perform(auth(get("/api/reports/" + endpoint + range), admin.token()))
          .andExpect(status().isOk());
    mvc.perform(auth(get("/api/reports/low-stock?threshold=10"), admin.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].available").value(10));
  }

  @Test
  void concurrentSettlementAndExpiryLeaveStockAndMoneyConsistent() throws Exception {
    var o = purchase(buyer, PaymentProvider.Scenario.SUCCESS);
    Payment p = payment(o.order().id);
    provider.charge(p.id, p.amount, p.currency, p.scenario);
    PurchaseOrder saved = orders.findById(o.order().id).orElseThrow();
    saved.reservationExpiresAt = Instant.now().minusSeconds(1);
    orders.saveAndFlush(saved);
    var results =
        race(
            () -> {
              paymentService.settle(p.id, PaymentProvider.Result.SUCCESS);
              return "settled";
            },
            () -> {
              orderService.expire(saved.id);
              return "expired";
            });
    assertThat(results).allMatch(String.class::isInstance);
    process(saved.id);
    assertThat(payment(saved.id).status).isEqualTo(Payment.Status.REFUNDED);
    assertThat(stocks.findById(product).orElseThrow().available).isEqualTo(10);
    assertThat(stocks.findById(product).orElseThrow().reserved).isZero();
  }

  @Test
  void openApiDoesNotConfuseModuleRequestSchemas() throws Exception {
    JsonNode api = body(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn());
    String productRef =
        api.at("/paths/~1api~1products/post/requestBody/content/application~1json/schema/$ref")
            .asText();
    String couponRef =
        api.at("/paths/~1api~1coupons/post/requestBody/content/application~1json/schema/$ref")
            .asText();
    assertThat(productRef).isNotBlank().isNotEqualTo(couponRef);
    assertThat(api.at(productRef.substring(1)).path("properties").has("sku")).isTrue();
    assertThat(api.at(couponRef.substring(1)).path("properties").has("usageLimit")).isTrue();
    String errorRef =
        api.at("/paths/~1api~1orders/post/responses/409/content/application~1json/schema/$ref")
            .asText();
    assertThat(api.at(errorRef.substring(1)).path("properties").has("error")).isTrue();
  }

  @Test
  void loginSucceedsWithoutReturningPasswordHash() throws Exception {
    mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"buyer@example.com\",\"password\":\"test-password-123\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isString());
    mvc.perform(auth(get("/api/customers"), admin.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
  }

  @Test
  void checkoutQuoteDoesNotRedeemCoupon() throws Exception {
    coupon();
    carts.set(buyer.id(), product, 1, false);
    mvc.perform(auth(get("/api/checkout/quote?couponCode=ORDER10"), buyer.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1062));
    assertThat(coupons.findAll().getFirst().usedCount).isZero();
    assertThat(orders.count()).isZero();
  }

  @Test
  void defaultAddressIsOwnedAndUnique() throws Exception {
    var created =
        body(
            mvc.perform(
                    auth(post("/api/customers/me/addresses"), buyer.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"recipient\":\"Buyer\",\"line1\":\"2 Main"
                                + " Street\",\"city\":\"Chennai\",\"postalCode\":\"600001\",\"country\":\"IN\"}"))
                .andExpect(status().isCreated())
                .andReturn());
    String id = created.get("id").asText();
    mvc.perform(auth(patch("/api/customers/me/addresses/" + id + "/default"), buyer.token()))
        .andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from addresses where customer_id=? and default_address=true",
                Integer.class,
                buyer.id()))
        .isEqualTo(1);
    mvc.perform(
            auth(
                patch("/api/customers/me/addresses/" + buyer.address() + "/default"),
                admin.token()))
        .andExpect(status().isNotFound());
  }

  @Test
  void storefrontCapabilitiesAndManagementAreServerOwned() throws Exception {
    mvc.perform(get("/api/products/" + product + "/availability"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.purchasable").value(true));
    products.deactivate(product);
    mvc.perform(get("/api/products/" + product + "/availability"))
        .andExpect(jsonPath("$.purchasable").value(false));
    mvc.perform(auth(get("/api/manage/products?status=INACTIVE"), admin.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(auth(get("/api/manage/products"), buyer.token())).andExpect(status().isForbidden());
    Coupon coupon = coupon();
    mvc.perform(auth(get("/api/coupons"), admin.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(
            auth(patch("/api/coupons/" + coupon.id + "/active"), admin.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));
  }

  @Test
  void orderActionsRespectRoleAndState() throws Exception {
    var order = purchase(buyer, PaymentProvider.Scenario.SUCCESS);
    process(order.order().id);
    String path = "/api/orders/" + order.order().id + "/actions";
    mvc.perform(auth(get(path), buyer.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.canCancel").value(true))
        .andExpect(jsonPath("$.transitions").isEmpty());
    mvc.perform(auth(get(path), admin.token()))
        .andExpect(jsonPath("$.transitions[0]").value("PROCESSING"));
  }

  @Test
  void adminOrderSearchFiltersBeforePaginationAndEnforcesRole() throws Exception {
    var order = purchase(buyer, PaymentProvider.Scenario.SUCCESS);
    process(order.order().id);
    String prefix = order.order().id.toString().substring(0, 8);
    mvc.perform(
            auth(
                get(
                    "/api/orders/all?status=CONFIRMED&search="
                        + prefix
                        + "&size=1&sort=createdAt,desc"),
                admin.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(order.order().id.toString()));
    mvc.perform(auth(get("/api/orders/all?status=DELIVERED&search=" + prefix), admin.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(auth(get("/api/orders/all"), buyer.token())).andExpect(status().isForbidden());
  }

  List<Object> race(Callable<?> a, Callable<?> b) throws Exception {
    CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      List<Future<Object>> futures = new ArrayList<>();
      for (Callable<?> task : List.of(a, b))
        futures.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  start.await();
                  try {
                    return task.call();
                  } catch (Exception e) {
                    return e;
                  }
                }));
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      return List.of(
          futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS));
    }
  }
}
