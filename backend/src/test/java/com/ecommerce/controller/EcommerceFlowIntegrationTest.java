package com.ecommerce.controller;

import com.ecommerce.dto.request.LoginRequest;
import com.ecommerce.dto.request.RegisterRequest;
import com.ecommerce.entity.Role;
import com.ecommerce.entity.User;
import com.ecommerce.repository.CartRepository;
import com.ecommerce.repository.InventoryTransactionRepository;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.PaymentRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.RefreshTokenRepository;
import com.ecommerce.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end flow through the whole controller -> service -> repository stack (real
 * in-memory H2, real JWTs obtained from the actual login endpoint): registration,
 * role-gated product management, cart, and the full checkout flow including both the
 * successful and mock-decline payment paths, plus admin order cancellation with
 * restock.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EcommerceFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private InventoryTransactionRepository inventoryTransactionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDatabase() {
        inventoryTransactionRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        cartRepository.deleteAll();
        productRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    private String registerAndLoginCustomer(String email) throws Exception {
        RegisterRequest register = new RegisterRequest();
        register.setEmail(email);
        register.setPassword("SecurePass123!");
        register.setFullName("Test Customer");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated());

        return login(email, "SecurePass123!");
    }

    private String seedAndLoginAdmin(String email) throws Exception {
        User admin = new User(email, passwordEncoder.encode("AdminPass123!"), "Test Admin", Role.ADMIN);
        userRepository.save(admin);
        return login(email, "AdminPass123!");
    }

    private String login(String email, String password) throws Exception {
        LoginRequest login = new LoginRequest();
        login.setEmail(email);
        login.setPassword(password);

        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        return json.get("accessToken").asText();
    }

    private Long createProductAsAdmin(String adminToken, String sku, int stock) throws Exception {
        String body = "{\"sku\":\"" + sku + "\",\"name\":\"Widget\",\"description\":\"A widget\","
                + "\"price\":10.00,\"stockQuantity\":" + stock + ",\"category\":\"Widgets\"}";

        String response = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("id").asLong();
    }

    @Test
    void fullHappyPath_registerLoginCreateProductAddToCartCheckout_decrementsStock() throws Exception {
        String adminToken = seedAndLoginAdmin("admin1@example.com");
        String customerToken = registerAndLoginCustomer("shopper1@example.com");

        // Unauthenticated cart access -> 401.
        mockMvc.perform(get("/api/cart")).andExpect(status().isUnauthorized());

        // Customer cannot create a product -> 403.
        String createBody = "{\"sku\":\"SKU-FORBIDDEN\",\"name\":\"Nope\",\"price\":5.00,\"stockQuantity\":1}";
        mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isForbidden());

        Long productId = createProductAsAdmin(adminToken, "SKU-HAPPY-1", 10);

        // Add 3 units to cart.
        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"quantity\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(3));

        // Checkout with a normal (non-decline) token -> PAID, stock decremented 10 -> 7.
        mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodToken\":\"tok_visa\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.payment.status").value("SUCCEEDED"));

        assertThat(productRepository.findById(productId).orElseThrow().getStockQuantity()).isEqualTo(7);

        // Cart was cleared after a successful checkout.
        mockMvc.perform(get("/api/cart").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void checkout_withDeclineToken_marksPaymentFailed_stockUntouched_cartSurvives() throws Exception {
        String adminToken = seedAndLoginAdmin("admin2@example.com");
        String customerToken = registerAndLoginCustomer("shopper2@example.com");

        Long productId = createProductAsAdmin(adminToken, "SKU-DECLINE-1", 10);

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"quantity\":2}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodToken\":\"tok_chargeDeclined\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAYMENT_FAILED"))
                .andExpect(jsonPath("$.payment.status").value("FAILED"));

        assertThat(productRepository.findById(productId).orElseThrow().getStockQuantity()).isEqualTo(10);

        mockMvc.perform(get("/api/cart").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(2));
    }

    @Test
    void adminCancelsPaidOrder_restocksQuantity() throws Exception {
        String adminToken = seedAndLoginAdmin("admin3@example.com");
        String customerToken = registerAndLoginCustomer("shopper3@example.com");

        Long productId = createProductAsAdmin(adminToken, "SKU-CANCEL-1", 10);

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"quantity\":4}"))
                .andExpect(status().isOk());

        String checkoutResponse = mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodToken\":\"tok_visa\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andReturn().getResponse().getContentAsString();

        Long orderId = objectMapper.readTree(checkoutResponse).get("id").asLong();
        assertThat(productRepository.findById(productId).orElseThrow().getStockQuantity()).isEqualTo(6);

        // A customer cannot cancel an order (admin-only route).
        mockMvc.perform(patch("/api/orders/" + orderId + "/status")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isForbidden());

        // Admin cancels the paid order -> stock restored to 10.
        mockMvc.perform(patch("/api/orders/" + orderId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(productRepository.findById(productId).orElseThrow().getStockQuantity()).isEqualTo(10);
    }

    @Test
    void getOrderById_belongingToAnotherCustomer_returns404ForNonAdmin() throws Exception {
        String adminToken = seedAndLoginAdmin("admin4@example.com");
        String ownerToken = registerAndLoginCustomer("owner@example.com");
        String otherToken = registerAndLoginCustomer("other@example.com");

        Long productId = createProductAsAdmin(adminToken, "SKU-PRIVACY-1", 5);

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"quantity\":1}"))
                .andExpect(status().isOk());

        String checkoutResponse = mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodToken\":\"tok_visa\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Long orderId = objectMapper.readTree(checkoutResponse).get("id").asLong();

        mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
    }

    @Test
    void checkout_insufficientStock_returns409_doesNotCreatePaidOrder() throws Exception {
        String adminToken = seedAndLoginAdmin("admin5@example.com");
        String customerToken = registerAndLoginCustomer("shopper5@example.com");

        Long productId = createProductAsAdmin(adminToken, "SKU-LOWSTOCK-1", 2);

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"quantity\":5}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodToken\":\"tok_visa\"}"))
                .andExpect(status().isConflict());

        assertThat(productRepository.findById(productId).orElseThrow().getStockQuantity()).isEqualTo(2);
    }

    @Test
    void softDeletedProduct_hiddenFromPublicListing_visibleToAdmin() throws Exception {
        String adminToken = seedAndLoginAdmin("admin6@example.com");
        Long productId = createProductAsAdmin(adminToken, "SKU-SOFTDEL-1", 5);

        mockMvc.perform(delete("/api/products/" + productId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/products/" + productId))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/products/" + productId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }
}
