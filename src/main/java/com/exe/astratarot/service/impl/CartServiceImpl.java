package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.shop.AddToCartRequest;
import com.exe.astratarot.domain.dto.shop.CartItemResponse;
import com.exe.astratarot.domain.dto.shop.CartResponse;
import com.exe.astratarot.domain.dto.shop.UpdateCartItemRequest;
import com.exe.astratarot.domain.entity.CartItem;
import com.exe.astratarot.domain.entity.Product;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.exception.ResourceNotFoundException;
import com.exe.astratarot.repository.CartItemRepository;
import com.exe.astratarot.repository.ProductRepository;
import com.exe.astratarot.repository.UserRepository;
import com.exe.astratarot.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public CartResponse getCart(UUID userId) {
        return buildCart(cartItemRepository.findAllByUserId(userId));
    }

    @Override
    @Transactional
    public CartResponse addItem(UUID userId, AddToCartRequest request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm"));

        if (Boolean.FALSE.equals(product.getActive())) {
            throw new IllegalArgumentException("Sản phẩm này đã ngừng bán");
        }

        // Thêm lại sản phẩm đã có trong giỏ thì cộng dồn, không tạo dòng mới
        // (bảng có UNIQUE(user_id, product_id)).
        CartItem item = cartItemRepository.findByUserIdAndProductId(userId, product.getId())
                .orElse(null);

        int newQuantity = (item == null ? 0 : item.getQuantity()) + request.getQuantity();
        requireStock(product, newQuantity);

        if (item == null) {
            User user = userRepository.getReferenceById(userId);
            item = CartItem.builder()
                    .user(user)
                    .product(product)
                    .quantity(newQuantity)
                    .build();
        } else {
            item.setQuantity(newQuantity);
        }
        cartItemRepository.save(item);

        return buildCart(cartItemRepository.findAllByUserId(userId));
    }

    @Override
    @Transactional
    public CartResponse updateItem(UUID userId, UUID cartItemId, UpdateCartItemRequest request) {
        CartItem item = cartItemRepository.findByIdAndUserId(cartItemId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dòng giỏ hàng"));

        requireStock(item.getProduct(), request.getQuantity());
        item.setQuantity(request.getQuantity());
        cartItemRepository.save(item);

        return buildCart(cartItemRepository.findAllByUserId(userId));
    }

    @Override
    @Transactional
    public CartResponse removeItem(UUID userId, UUID cartItemId) {
        CartItem item = cartItemRepository.findByIdAndUserId(cartItemId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dòng giỏ hàng"));
        cartItemRepository.delete(item);
        return buildCart(cartItemRepository.findAllByUserId(userId));
    }

    @Override
    @Transactional
    public void clear(UUID userId) {
        cartItemRepository.deleteAllByUserId(userId);
    }

    private void requireStock(Product product, int quantity) {
        if (product.getStock() < quantity) {
            throw new IllegalArgumentException(
                    "Sản phẩm \"" + product.getName() + "\" chỉ còn " + product.getStock() + " sản phẩm");
        }
    }

    private CartResponse buildCart(List<CartItem> items) {
        List<CartItemResponse> lines = items.stream().map(ci -> {
            Product p = ci.getProduct();
            return CartItemResponse.builder()
                    .id(ci.getId())
                    .productId(p.getId())
                    .productName(p.getName())
                    .productSlug(p.getSlug())
                    .imageUrl(p.getImageUrl())
                    .unitPrice(p.getPrice())
                    .quantity(ci.getQuantity())
                    .lineTotal(p.getPrice() * ci.getQuantity())
                    .stock(p.getStock())
                    .build();
        }).toList();

        return CartResponse.builder()
                .items(lines)
                .totalQuantity(lines.stream().mapToInt(CartItemResponse::getQuantity).sum())
                .subtotal(lines.stream().mapToLong(CartItemResponse::getLineTotal).sum())
                .build();
    }
}
