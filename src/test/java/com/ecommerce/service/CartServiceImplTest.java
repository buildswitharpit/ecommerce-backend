package com.ecommerce.service;

import com.ecommerce.dto.request.CartItemAddRequest;
import com.ecommerce.dto.request.CartItemUpdateRequest;
import com.ecommerce.dto.response.CartResponse;
import com.ecommerce.entity.Cart;
import com.ecommerce.entity.CartItem;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.Role;
import com.ecommerce.entity.User;
import com.ecommerce.exception.NotFoundException;
import com.ecommerce.mapper.CartMapper;
import com.ecommerce.mapper.ProductMapper;
import com.ecommerce.repository.CartItemRepository;
import com.ecommerce.repository.CartRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.service.impl.CartServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    @Mock
    private CartRepository cartRepository;
    @Mock
    private CartItemRepository cartItemRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private UserRepository userRepository;

    private final CartMapper cartMapper = new CartMapper(new ProductMapper());

    private CartServiceImpl cartService;
    private User sampleUser;
    private Cart sampleCart;
    private Product sampleProduct;

    @BeforeEach
    void setUp() {
        cartService = new CartServiceImpl(cartRepository, cartItemRepository, productRepository, userRepository, cartMapper);
        sampleUser = new User("jane@example.com", "hash", "Jane Doe", Role.CUSTOMER);
        sampleUser.setId(1L);
        sampleCart = new Cart(sampleUser);
        sampleCart.setId(10L);
        sampleProduct = new Product("SKU-1", "Widget", "desc", new BigDecimal("9.99"), 20, "Widgets");
        sampleProduct.setId(100L);
    }

    @Test
    void getCart_noExistingCart_createsOneLazily() {
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> {
            Cart c = inv.getArgument(0);
            c.setId(10L);
            return c;
        });

        CartResponse response = cartService.getCart(1L);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.items()).isEmpty();
    }

    @Test
    void addItem_newProduct_addsLineItem() {
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(sampleCart));
        when(productRepository.findById(100L)).thenReturn(Optional.of(sampleProduct));
        when(cartItemRepository.findByCartIdAndProductId(10L, 100L)).thenReturn(Optional.empty());

        CartItemAddRequest request = new CartItemAddRequest();
        request.setProductId(100L);
        request.setQuantity(3);

        CartResponse response = cartService.addItem(1L, request);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).quantity()).isEqualTo(3);
        assertThat(response.totalAmount()).isEqualByComparingTo("29.97");
    }

    @Test
    void addItem_existingProduct_increasesQuantityRatherThanDuplicating() {
        CartItem existingItem = new CartItem(sampleCart, sampleProduct, 2);
        sampleCart.getItems().add(existingItem);

        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(sampleCart));
        when(productRepository.findById(100L)).thenReturn(Optional.of(sampleProduct));
        when(cartItemRepository.findByCartIdAndProductId(10L, 100L)).thenReturn(Optional.of(existingItem));

        CartItemAddRequest request = new CartItemAddRequest();
        request.setProductId(100L);
        request.setQuantity(3);

        cartService.addItem(1L, request);

        assertThat(existingItem.getQuantity()).isEqualTo(5);
        verify(cartItemRepository).save(existingItem);
    }

    @Test
    void addItem_inactiveProduct_throwsNotFound() {
        sampleProduct.setActive(false);
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(sampleCart));
        when(productRepository.findById(100L)).thenReturn(Optional.of(sampleProduct));

        CartItemAddRequest request = new CartItemAddRequest();
        request.setProductId(100L);
        request.setQuantity(1);

        assertThatThrownBy(() -> cartService.addItem(1L, request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateItem_changesQuantity() {
        CartItem existingItem = new CartItem(sampleCart, sampleProduct, 2);
        sampleCart.getItems().add(existingItem);

        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(sampleCart));
        when(cartItemRepository.findByCartIdAndProductId(10L, 100L)).thenReturn(Optional.of(existingItem));

        CartItemUpdateRequest request = new CartItemUpdateRequest();
        request.setQuantity(7);

        cartService.updateItem(1L, 100L, request);

        assertThat(existingItem.getQuantity()).isEqualTo(7);
    }

    @Test
    void removeItem_removesLineItem() {
        CartItem existingItem = new CartItem(sampleCart, sampleProduct, 2);
        sampleCart.getItems().add(existingItem);

        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(sampleCart));
        when(cartItemRepository.findByCartIdAndProductId(10L, 100L)).thenReturn(Optional.of(existingItem));

        CartResponse response = cartService.removeItem(1L, 100L);

        assertThat(response.items()).isEmpty();
        verify(cartItemRepository).delete(existingItem);
    }

    @Test
    void clear_removesAllItems() {
        CartItem existingItem = new CartItem(sampleCart, sampleProduct, 2);
        sampleCart.getItems().add(existingItem);

        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(sampleCart));

        CartResponse response = cartService.clear(1L);

        assertThat(response.items()).isEmpty();
        verify(cartItemRepository).deleteAll(any());
    }
}
