package com.exe.astratarot.service;

import com.exe.astratarot.domain.entity.User;

/**
 * Nối một tài khoản Google vào người dùng của hệ thống.
 *
 * <p>Dùng chung cho CẢ HAI lối vào — web (chuyển hướng OAuth2) và app (ID
 * token). Hai bản cài đặt của cùng quy tắc này không báo lỗi khi sai lệch,
 * nó chỉ lặng lẽ sinh ra tài khoản trùng.
 */
public interface TaiKhoanGoogleService {

    /**
     * @param providerId     trường {@code sub} của Google — định danh bền
     *                       vững, khác email ở chỗ không đổi khi người dùng
     *                       đổi địa chỉ thư
     * @param emailDaXacMinh {@code email_verified} từ Google; sai thì từ chối
     */
    User timHoacTao(String providerId, String email, boolean emailDaXacMinh,
                    String hoTen, String anhDaiDien);
}
