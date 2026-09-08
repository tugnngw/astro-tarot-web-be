package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.shop.CheckoutRequest;
import com.exe.astratarot.domain.dto.shop.OrderItemResponse;
import com.exe.astratarot.domain.dto.shop.OrderResponse;
import com.exe.astratarot.domain.entity.CartItem;
import com.exe.astratarot.domain.entity.Order;
import com.exe.astratarot.domain.entity.OrderItem;
import com.exe.astratarot.domain.entity.Product;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.OrderStatus;
import com.exe.astratarot.domain.enums.PaymentStatus;
import com.exe.astratarot.exception.ResourceNotFoundException;
import com.exe.astratarot.repository.CartItemRepository;
import com.exe.astratarot.repository.OrderRepository;
import com.exe.astratarot.repository.ProductRepository;
import com.exe.astratarot.repository.UserRepository;
import com.exe.astratarot.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    /** Phí giao hàng phẳng. Tách hằng số để sau này thay bằng bảng cấu hình. */
    private static final long FLAT_SHIPPING_FEE = 30_000L;

    /** Bỏ I, O, 0, 1 để mã đơn đọc qua điện thoại không bị nhầm. */
    private static final String CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int CODE_LENGTH = 8;
    private static final int CODE_MAX_ATTEMPTS = 10;

    private final OrderRepository orderRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final SecureRandom random = new SecureRandom();

    @Override
    @Transactional
    public OrderResponse checkout(UUID userId, CheckoutRequest request) {
        List<CartItem> cartItems = cartItemRepository.findAllByUserId(userId);
        if (cartItems.isEmpty()) {
            throw new IllegalArgumentException("Giỏ hàng đang trống");
        }

        User user = userRepository.getReferenceById(userId);

        Order order = Order.builder()
                .user(user)
                .orderCode(generateOrderCode())
                .status(OrderStatus.PENDING)
                .paymentStatus(PaymentStatus.UNPAID)
                .receiverName(request.getReceiverName().trim())
                .receiverPhone(request.getReceiverPhone().trim())
                .shippingAddress(request.getShippingAddress().trim())
                .note(request.getNote())
                .subtotal(0L)
                .shippingFee(FLAT_SHIPPING_FEE)
                .totalAmount(0L)
                .build();

        long subtotal = 0L;
        List<OrderItem> lines = new ArrayList<>();

        for (CartItem ci : cartItems) {
            Product p = ci.getProduct();

            // Kiểm lại tồn kho ngay lúc đặt: giỏ có thể đã nằm đó nhiều ngày
            // và hàng đã hết trong khoảng thời gian đó.
            if (Boolean.FALSE.equals(p.getActive())) {
                throw new IllegalArgumentException("Sản phẩm " + p.getName() + " đã ngừng bán");
            }
            if (p.getStock() < ci.getQuantity()) {
                throw new IllegalArgumentException(
                        "Sản phẩm " + p.getName() + " chỉ còn " + p.getStock() + " sản phẩm");
            }

            long lineTotal = p.getPrice() * ci.getQuantity();
            subtotal += lineTotal;

            lines.add(OrderItem.builder()
                    .order(order)
                    .product(p)
                    .productName(p.getName())
                    .productImageUrl(p.getImageUrl())
                    .unitPrice(p.getPrice())
                    .quantity(ci.getQuantity())
                    .lineTotal(lineTotal)
                    .build());

            p.setStock(p.getStock() - ci.getQuantity());
            productRepository.save(p);
        }

        order.setSubtotal(subtotal);
        order.setTotalAmount(subtotal + FLAT_SHIPPING_FEE);
        order.getItems().addAll(lines);

        Order saved = orderRepository.save(order);
        cartItemRepository.deleteAllByUserId(userId);

        log.info("Tạo đơn {} cho user {} - {} dòng, tổng {}",
                saved.getOrderCode(), userId, lines.size(), saved.getTotalAmount());

        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getMyOrders(UUID userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getMyOrder(UUID userId, UUID orderId) {
        Order order = orderRepository.findByIdAndUserIdWithItems(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng"));
        return toResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse cancel(UUID userId, UUID orderId, String reason) {
        Order order = orderRepository.findByIdAndUserIdWithItems(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng"));

        // Chỉ cho huỷ khi shop chưa gửi hàng.
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.CONFIRMED) {
            throw new IllegalArgumentException(
                    "Đơn ở trạng thái " + order.getStatus() + " không thể huỷ");
        }

        // Trả tồn kho lại cho những sản phẩm còn tồn tại trong catalog.
        for (OrderItem item : order.getItems()) {
            Product p = item.getProduct();
            if (p != null) {
                p.setStock(p.getStock() + item.getQuantity());
                productRepository.save(p);
            }
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason(reason);
        return toResponse(orderRepository.save(order));
    }

    /**
     * Sinh mã đơn ngắn, dễ đọc. Cột order_code có ràng buộc UNIQUE nên vẫn
     * thử lại vài lần phòng khi trùng.
     */
    private String generateOrderCode() {
        for (int attempt = 0; attempt < CODE_MAX_ATTEMPTS; attempt++) {
            StringBuilder sb = new StringBuilder("AT");
            for (int i = 0; i < CODE_LENGTH - 2; i++) {
                sb.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
            }
            String code = sb.toString();
            if (!orderRepository.existsByOrderCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Không sinh được mã đơn duy nhất sau "
                + CODE_MAX_ATTEMPTS + " lần thử");
    }

    private OrderResponse toResponse(Order o) {
        List<OrderItemResponse> items = o.getItems().stream()
                .map(i -> OrderItemResponse.builder()
                        .id(i.getId())
                        .productId(i.getProduct() == null ? null : i.getProduct().getId())
                        .productName(i.getProductName())
                        .productImageUrl(i.getProductImageUrl())
                        .unitPrice(i.getUnitPrice())
                        .quantity(i.getQuantity())
                        .lineTotal(i.getLineTotal())
                        .build())
                .toList();

        return OrderResponse.builder()
                .id(o.getId())
                .orderCode(o.getOrderCode())
                .status(o.getStatus().name())
                .paymentStatus(o.getPaymentStatus().name())
                .subtotal(o.getSubtotal())
                .shippingFee(o.getShippingFee())
                .totalAmount(o.getTotalAmount())
                .receiverName(o.getReceiverName())
                .receiverPhone(o.getReceiverPhone())
                .shippingAddress(o.getShippingAddress())
                .note(o.getNote())
                .cancelReason(o.getCancelReason())
                .items(items)
                .createdAt(o.getCreatedAt())
                .build();
    }
}
