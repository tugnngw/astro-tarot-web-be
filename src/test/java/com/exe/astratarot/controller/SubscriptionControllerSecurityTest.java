package com.exe.astratarot.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mọi endpoint của {@link SubscriptionController} phải khai quyền.
 *
 * <p><b>Vì sao có test này.</b> Controller từng không có một {@code @PreAuthorize}
 * nào, trong khi nó nằm dưới {@code /api/admin/subscriptions} và
 * {@code SecurityConfig} lại không có luật nào cho {@code /api/admin/**} — chỉ
 * một dòng {@code permitAll} cho {@code plans/active}. Thứ duy nhất chặn là
 * {@code .anyRequest().authenticated()}.
 *
 * <p>Nghĩa là bất kỳ ai đăng nhập, kể cả tài khoản khách vừa tạo, đều:
 * <ul>
 *   <li>tạo và sửa được gói AI — đổi giá, đổi hạn mức ngày;</li>
 *   <li>sửa được lượt mua của người khác;</li>
 *   <li>đọc được lượt mua và lịch sử dùng AI của bất kỳ ai, chỉ bằng đổi
 *       {@code userId} trên đường dẫn.</li>
 * </ul>
 *
 * <p>Hai endpoint {@code users/{userId}/…} thậm chí đã NHẬN {@code userDetails}
 * và {@code auth} làm tham số rồi không dùng tới — dấu hiệu việc kiểm quyền bị
 * bỏ dở chứ không phải cố ý mở.
 *
 * <p><b>Vì sao kiểm bằng phản chiếu chứ không gọi thật.</b> Các test controller
 * khác trong dự án là unit test Mockito, gọi thẳng phương thức nên không đi qua
 * proxy bảo mật — chúng sẽ xanh kể cả khi mọi {@code @PreAuthorize} bị xoá.
 * Dựng cả ngữ cảnh Spring Security chỉ để kiểm điều này thì chậm và giòn. Test
 * này khoá đúng điều đã bị vi phạm: không endpoint nào được để trống quyền.
 */
class SubscriptionControllerSecurityTest {

    private static List<Method> endpoints() {
        return Arrays.stream(SubscriptionController.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(RequestMapping.class)
                        || Arrays.stream(m.getAnnotations())
                                 .anyMatch(a -> a.annotationType()
                                         .isAnnotationPresent(RequestMapping.class)))
                .collect(Collectors.toList());
    }

    @Test
    @DisplayName("không endpoint nào thiếu @PreAuthorize")
    void moiEndpointDeuKhaiQuyen() {
        List<String> thieu = endpoints().stream()
                .filter(m -> !m.isAnnotationPresent(PreAuthorize.class))
                .map(Method::getName)
                .sorted()
                .collect(Collectors.toList());

        assertTrue(thieu.isEmpty(),
                "Những endpoint sau không khai quyền, nên chỉ cần đăng nhập là gọi được: "
                        + thieu);
    }

    @Test
    @DisplayName("tìm thấy đủ tám endpoint — test không âm thầm rỗng")
    void khongRong() {
        // Nếu đổi tên lớp hay đổi cách khai annotation mà bộ lọc trên không
        // bắt được nữa, test đầu sẽ xanh vì danh sách rỗng chứ không phải vì
        // mã đúng. Chốt số lượng để chuyện đó lộ ra.
        assertEquals(8, endpoints().size(),
                "Số endpoint đổi — xem lại cả hai test trong lớp này.");
    }

    @Test
    @DisplayName("bốn endpoint quản trị đòi PAYMENTS_MANAGE")
    void endpointQuanTriDoiQuyenTien() {
        // Giá và hạn mức của gói là quyết định tiền bạc. CustomUserDetails đã
        // chốt nguyên tắc ấy: ORDERS_MANAGE và PAYMENTS_MANAGE chỉ ADMIN có,
        // còn CATALOG_MANAGE thì Quản lý cũng có. Dùng nhầm CATALOG_MANAGE là
        // cho Quản lý đổi giá gói.
        List<String> quanTri = List.of(
                "getAllPlans", "createPlan", "updatePlan", "updateUserPurchase");

        for (String ten : quanTri) {
            Method m = endpoints().stream()
                    .filter(x -> x.getName().equals(ten))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Không còn endpoint " + ten));
            PreAuthorize p = m.getAnnotation(PreAuthorize.class);
            assertTrue(p != null && p.value().contains("PAYMENTS_MANAGE"),
                    ten + " phải đòi PAYMENTS_MANAGE, đang là: "
                            + (p == null ? "không có" : p.value()));
        }
    }

    @Test
    @DisplayName("hai endpoint theo userId chỉ cho xem của chính mình")
    void khongDocDuocDuLieuNguoiKhac() {
        List<String> theoNguoi = List.of("getUserActivePurchases", "getUserAIUsage");

        for (String ten : theoNguoi) {
            Method m = endpoints().stream()
                    .filter(x -> x.getName().equals(ten))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Không còn endpoint " + ten));
            String bieuThuc = m.getAnnotation(PreAuthorize.class).value();
            assertTrue(bieuThuc.contains("#userId"),
                    ten + " phải so userId trên đường dẫn với người đang gọi, "
                            + "đang là: " + bieuThuc);
        }
    }
}
