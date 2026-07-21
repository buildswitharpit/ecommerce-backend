package com.ecommerce.service.impl;

import com.ecommerce.dto.request.CheckoutRequest;
import com.ecommerce.dto.request.OrderStatusUpdateRequest;
import com.ecommerce.dto.response.OrderResponse;
import com.ecommerce.entity.Cart;
import com.ecommerce.entity.CartItem;
import com.ecommerce.entity.InventoryReason;
import com.ecommerce.entity.InventoryTransaction;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.OrderItem;
import com.ecommerce.entity.OrderStatus;
import com.ecommerce.entity.Payment;
import com.ecommerce.entity.PaymentStatus;
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
import com.ecommerce.service.CartService;
import com.ecommerce.service.OrderService;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * The checkout flow -- the core business logic of this project -- lives here. See
 * {@link #checkout} for the step-by-step behavior, which follows this exact order:
 * validate cart contents, create the order as PENDING, charge the payment gateway,
 * then <em>only on a successful charge</em> decrement stock and write the inventory
 * audit trail. On a decline, stock is never touched and the cart survives so the
 * caller can retry with a different payment method.
 */
@Service
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final UserRepository userRepository;
    private final CartService cartService;
    private final PaymentGateway paymentGateway;
    private final OrderMapper orderMapper;

    public OrderServiceImpl(OrderRepository orderRepository, ProductRepository productRepository,
                             PaymentRepository paymentRepository,
                             InventoryTransactionRepository inventoryTransactionRepository,
                             UserRepository userRepository, CartService cartService,
                             PaymentGateway paymentGateway, OrderMapper orderMapper) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.paymentRepository = paymentRepository;
        this.inventoryTransactionRepository = inventoryTransactionRepository;
        this.userRepository = userRepository;
        this.cartService = cartService;
        this.paymentGateway = paymentGateway;
        this.orderMapper = orderMapper;
    }

    @Override
    @Transactional
    public OrderResponse checkout(Long userId, CheckoutRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("No user found with id: " + userId));
        Cart cart = cartService.getOrCreateCartEntity(userId);

        if (cart.getItems().isEmpty()) {
            throw new ConflictException("Cannot checkout an empty cart");
        }

        // Step 1/2: validate every line before creating anything or charging anything.
        for (CartItem item : cart.getItems()) {
            Product product = item.getProduct();
            if (!product.isActive()) {
                throw new ConflictException("Product '" + product.getName() + "' is no longer available");
            }
            if (product.getStockQuantity() < item.getQuantity()) {
                throw new InsufficientStockException("Insufficient stock for product '" + product.getName()
                        + "': requested " + item.getQuantity() + ", available " + product.getStockQuantity());
            }
        }

        BigDecimal total = cart.getItems().stream()
                .map(item -> item.getProduct().getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Step 3: create the order (PENDING) with snapshot line items.
        Order order = new Order(user, total);
        for (CartItem item : cart.getItems()) {
            Product product = item.getProduct();
            order.addItem(new OrderItem(product, product.getName(), product.getPrice(), item.getQuantity()));
        }
        order = orderRepository.save(order);

        // Step 4: charge through whichever PaymentGateway is wired in (mock by default).
        PaymentResult result = paymentGateway.charge(total, request.getPaymentMethodToken());

        Payment payment = new Payment(order, result.gateway(), result.gatewayReferenceId(), total,
                result.success() ? PaymentStatus.SUCCEEDED : PaymentStatus.FAILED);

        if (result.success()) {
            // Step 5: only now touch stock -- one InventoryTransaction per product.
            for (OrderItem orderItem : order.getItems()) {
                Product product = orderItem.getProduct();
                product.setStockQuantity(product.getStockQuantity() - orderItem.getQuantity());
                try {
                    productRepository.saveAndFlush(product);
                } catch (OptimisticLockingFailureException e) {
                    // Rolls back the whole transaction, including the order/payment rows
                    // created above -- safe here because the mock gateway moves no real
                    // money. A production integration with a real gateway would need a
                    // compensating refund in this branch; see README "Known limitations".
                    throw new ConflictException("Stock for product '" + product.getName()
                            + "' changed while processing checkout, please retry");
                }
                inventoryTransactionRepository.save(new InventoryTransaction(
                        product, -orderItem.getQuantity(), InventoryReason.ORDER_PLACED, order.getId()));
            }
            order.setStatus(OrderStatus.PAID);
            cartService.clearCartEntity(cart);
        } else {
            // Step 6: decline -- no stock touched, cart left intact for retry.
            order.setStatus(OrderStatus.PAYMENT_FAILED);
        }

        order = orderRepository.save(order);
        payment = paymentRepository.save(payment);

        return orderMapper.toResponse(order, payment);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> findMyOrders(Long userId, Pageable pageable) {
        return orderRepository.findByUserId(userId, pageable)
                .map(order -> orderMapper.toResponse(order, paymentRepository.findByOrderId(order.getId()).orElse(null)));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse findById(Long orderId, UserPrincipal caller) {
        Order order = findEntityById(orderId);
        boolean isOwner = order.getUser().getId().equals(caller.id());
        if (!isOwner && caller.role() != Role.ADMIN) {
            // 404, not 403: don't reveal that an order belonging to someone else exists.
            throw new NotFoundException("No order found with id: " + orderId);
        }
        Payment payment = paymentRepository.findByOrderId(order.getId()).orElse(null);
        return orderMapper.toResponse(order, payment);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> findAllForAdmin(OrderStatus status, Pageable pageable) {
        Page<Order> page = status != null
                ? orderRepository.findByStatus(status, pageable)
                : orderRepository.findAll(pageable);
        return page.map(order -> orderMapper.toResponse(order, paymentRepository.findByOrderId(order.getId()).orElse(null)));
    }

    @Override
    @Transactional
    public OrderResponse updateStatus(Long orderId, OrderStatusUpdateRequest request) {
        Order order = findEntityById(orderId);
        OrderStatus current = order.getStatus();
        OrderStatus target = request.getStatus();

        if (!isTransitionAllowed(current, target)) {
            throw new InvalidStatusTransitionException(
                    "Cannot transition order " + orderId + " from " + current + " to " + target);
        }

        boolean restock = target == OrderStatus.CANCELLED
                && (current == OrderStatus.PAID || current == OrderStatus.SHIPPED);

        if (restock) {
            for (OrderItem item : order.getItems()) {
                Product product = item.getProduct();
                product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
                try {
                    productRepository.saveAndFlush(product);
                } catch (OptimisticLockingFailureException e) {
                    throw new ConflictException("Stock for product '" + product.getName()
                            + "' changed concurrently, please retry the cancellation");
                }
                inventoryTransactionRepository.save(new InventoryTransaction(
                        product, item.getQuantity(), InventoryReason.ORDER_CANCELLED, order.getId()));
            }
        }

        order.setStatus(target);
        order = orderRepository.save(order);

        Payment payment = paymentRepository.findByOrderId(order.getId()).orElse(null);
        return orderMapper.toResponse(order, payment);
    }

    /**
     * PENDING/PAYMENT_FAILED orders never had stock decremented, so cancelling them is
     * a no-op status flip. PAID/SHIPPED orders did decrement stock, so cancelling them
     * restocks. DELIVERED and CANCELLED are terminal -- see {@link OrderStatus}.
     */
    private boolean isTransitionAllowed(OrderStatus from, OrderStatus to) {
        return switch (from) {
            case PENDING, PAYMENT_FAILED -> to == OrderStatus.CANCELLED;
            case PAID -> to == OrderStatus.SHIPPED || to == OrderStatus.CANCELLED;
            case SHIPPED -> to == OrderStatus.DELIVERED || to == OrderStatus.CANCELLED;
            case DELIVERED, CANCELLED -> false;
        };
    }

    private Order findEntityById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("No order found with id: " + orderId));
    }
}
