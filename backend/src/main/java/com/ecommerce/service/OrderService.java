package com.ecommerce.service;

import com.ecommerce.dto.request.CheckoutRequest;
import com.ecommerce.dto.request.OrderStatusUpdateRequest;
import com.ecommerce.dto.response.OrderResponse;
import com.ecommerce.entity.OrderStatus;
import com.ecommerce.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderService {

    OrderResponse checkout(Long userId, CheckoutRequest request);

    Page<OrderResponse> findMyOrders(Long userId, Pageable pageable);

    OrderResponse findById(Long orderId, UserPrincipal caller);

    Page<OrderResponse> findAllForAdmin(OrderStatus status, Pageable pageable);

    OrderResponse updateStatus(Long orderId, OrderStatusUpdateRequest request);
}
