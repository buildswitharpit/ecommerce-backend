package com.ecommerce.service;

import com.ecommerce.dto.request.CheckoutRequest;
import com.ecommerce.dto.request.OrderStatusUpdateRequest;
import com.ecommerce.dto.response.OrderResponse;
import com.ecommerce.entity.Cart;
import com.ecommerce.entity.CartItem;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.OrderItem;
import com.ecommerce.entity.OrderStatus;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.Role;
import com.ecommerce.entity.User;
import com.ecommerce.exception.ConflictException;
import com.ecommerce.exception.InsufficientStockException;
import com.ecommerce.exception.InvalidStatusTransitionException;
import com.ecommerce.exception.NotFoundException;
import com.ecommerce.mapper.OrderMapper;
import com.ecommerce.payment.PaymentGateway;
import com.ecommerce.payment.PaymentResult;
import com.ecommerce.repository.InventoryTransactionRepository;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.PaymentRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.security.UserPrincipal;
import com.ecommerce.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the checkout flow -- the core business logic of this project -- plus
 * order status transitions (in particular cancel-with-restock).
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private InventoryTransactionRepository inventoryTransactionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CartService cartService;
    @Mock
    private PaymentGateway paymentGateway;

    private final OrderMapper orderMapper = new OrderMapper();

    private OrderServiceImpl orderService;

    private User sampleUser;
    private Product sampleProduct;
    private Cart sampleCart;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(orderRepository, productRepository, paymentRepository,
                inventoryTransactionRepository, userRepository, cartService, paymentGateway, orderMapper);

        sampleUser = new User("jane@example.com", "hash", "Jane Doe", Role.CUSTOMER);
        sampleUser.setId(1L);

        sampleProduct = new Product("SKU-1", "Widget", "desc", new BigDecimal("10.00"), 10, "Widgets");
        sampleProduct.setId(100L);

        sampleCart = new Cart(sampleUser);
        sampleCart.setId(50L);
        sampleCart.getItems().add(new CartItem(sampleCart, sampleProduct, 2));

        // lenient: not every test in this class exercises the checkout path, so not
        // every test needs all of these stubs -- avoids Mockito's strict-stubbing
        // UnnecessaryStubbingException on tests that only cover updateStatus/findById.
        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        lenient().when(cartService.getOrCreateCartEntity(1L)).thenReturn(sampleCart);
        lenient().when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order order = inv.getArgument(0);
            if (order.getId() == null) {
                order.setId(500L);
            }
            return order;
        });
        lenient().when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void checkout_successfulCharge_decrementsStock_marksPaid_clearsCart() {
        when(paymentGateway.charge(eq(new BigDecimal("20.00")), eq("tok_visa")))
                .thenReturn(PaymentResult.success("MOCK", "mock_abc123"));
        when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        CheckoutRequest checkoutRequest = new CheckoutRequest();
        checkoutRequest.setPaymentMethodToken("tok_visa");
        OrderResponse response = orderService.checkout(1L, checkoutRequest);

        assertThat(response.status()).isEqualTo(OrderStatus.PAID);
        assertThat(response.totalAmount()).isEqualByComparingTo("20.00");
        assertThat(sampleProduct.getStockQuantity()).isEqualTo(8);

        verify(inventoryTransactionRepository).save(argThat(tx ->
                tx.getQuantityChange() == -2 && tx.getReason().name().equals("ORDER_PLACED")));
        verify(cartService).clearCartEntity(sampleCart);
    }

    @Test
    void checkout_insufficientStock_throws409_doesNotChargeOrCreateOrder() {
        sampleCart.getItems().clear();
        sampleCart.getItems().add(new CartItem(sampleCart, sampleProduct, 50)); // more than the 10 in stock

        CheckoutRequest request = new CheckoutRequest();
        request.setPaymentMethodToken("tok_visa");

        assertThatThrownBy(() -> orderService.checkout(1L, request))
                .isInstanceOf(InsufficientStockException.class);

        verify(orderRepository, never()).save(any());
        verify(paymentGateway, never()).charge(any(), any());
    }

    @Test
    void checkout_emptyCart_throws409() {
        sampleCart.getItems().clear();

        CheckoutRequest request = new CheckoutRequest();
        request.setPaymentMethodToken("tok_visa");

        assertThatThrownBy(() -> orderService.checkout(1L, request))
                .isInstanceOf(ConflictException.class);

        verify(paymentGateway, never()).charge(any(), any());
    }

    @Test
    void checkout_inactiveProductInCart_throws409() {
        sampleProduct.setActive(false);

        CheckoutRequest request = new CheckoutRequest();
        request.setPaymentMethodToken("tok_visa");

        assertThatThrownBy(() -> orderService.checkout(1L, request))
                .isInstanceOf(ConflictException.class);

        verify(paymentGateway, never()).charge(any(), any());
    }

    @Test
    void checkout_decline_marksPaymentFailed_leavesStockAndCartUntouched() {
        when(paymentGateway.charge(any(), eq("tok_chargeDeclined")))
                .thenReturn(PaymentResult.failure("MOCK", "Your card was declined (simulated by mock gateway)"));

        CheckoutRequest request = new CheckoutRequest();
        request.setPaymentMethodToken("tok_chargeDeclined");

        OrderResponse response = orderService.checkout(1L, request);

        assertThat(response.status()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(sampleProduct.getStockQuantity()).isEqualTo(10); // untouched
        assertThat(sampleCart.getItems()).hasSize(1); // cart survives

        verify(productRepository, never()).saveAndFlush(any());
        verify(inventoryTransactionRepository, never()).save(any());
        verify(cartService, never()).clearCartEntity(any());

        ArgumentCaptor<com.ecommerce.entity.Payment> paymentCaptor = ArgumentCaptor.forClass(com.ecommerce.entity.Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getStatus().name()).isEqualTo("FAILED");
    }

    @Test
    void checkout_optimisticLockConflictDuringStockDecrement_throws409() {
        when(paymentGateway.charge(any(), any())).thenReturn(PaymentResult.success("MOCK", "mock_xyz"));
        when(productRepository.saveAndFlush(any(Product.class)))
                .thenThrow(new OptimisticLockingFailureException("stale version"));

        CheckoutRequest request = new CheckoutRequest();
        request.setPaymentMethodToken("tok_visa");

        assertThatThrownBy(() -> orderService.checkout(1L, request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void findById_notOwnerAndNotAdmin_throwsNotFoundNot403() {
        Order order = new Order(sampleUser, new BigDecimal("20.00"));
        order.setId(1L);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        UserPrincipal otherCustomer = new UserPrincipal(999L, "other@example.com", Role.CUSTOMER);

        assertThatThrownBy(() -> orderService.findById(1L, otherCustomer))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void findById_admin_canSeeAnyOrder() {
        Order order = new Order(sampleUser, new BigDecimal("20.00"));
        order.setId(1L);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());

        UserPrincipal admin = new UserPrincipal(999L, "admin@example.com", Role.ADMIN);

        OrderResponse response = orderService.findById(1L, admin);

        assertThat(response.id()).isEqualTo(1L);
    }

    @Test
    void updateStatus_cancelPaidOrder_restocksAndWritesAuditRow() {
        Order order = new Order(sampleUser, new BigDecimal("20.00"));
        order.setId(1L);
        order.setStatus(OrderStatus.PAID);
        order.addItem(new OrderItem(sampleProduct, "Widget", new BigDecimal("10.00"), 2));

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());

        OrderStatusUpdateRequest request = new OrderStatusUpdateRequest();
        request.setStatus(OrderStatus.CANCELLED);

        int stockBefore = sampleProduct.getStockQuantity();
        OrderResponse response = orderService.updateStatus(1L, request);

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(sampleProduct.getStockQuantity()).isEqualTo(stockBefore + 2);
        verify(inventoryTransactionRepository).save(argThat(tx ->
                tx.getQuantityChange() == 2 && tx.getReason().name().equals("ORDER_CANCELLED")));
    }

    @Test
    void updateStatus_cancelDeliveredOrder_rejected409() {
        Order order = new Order(sampleUser, new BigDecimal("20.00"));
        order.setId(1L);
        order.setStatus(OrderStatus.DELIVERED);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        OrderStatusUpdateRequest request = new OrderStatusUpdateRequest();
        request.setStatus(OrderStatus.CANCELLED);

        assertThatThrownBy(() -> orderService.updateStatus(1L, request))
                .isInstanceOf(InvalidStatusTransitionException.class);

        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateStatus_paidToShipped_noRestock() {
        Order order = new Order(sampleUser, new BigDecimal("20.00"));
        order.setId(1L);
        order.setStatus(OrderStatus.PAID);
        order.addItem(new OrderItem(sampleProduct, "Widget", new BigDecimal("10.00"), 2));

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());

        OrderStatusUpdateRequest request = new OrderStatusUpdateRequest();
        request.setStatus(OrderStatus.SHIPPED);

        OrderResponse response = orderService.updateStatus(1L, request);

        assertThat(response.status()).isEqualTo(OrderStatus.SHIPPED);
        verify(productRepository, never()).saveAndFlush(any());
        verify(inventoryTransactionRepository, never()).save(any());
    }
}
