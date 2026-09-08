package com.exe.astratarot.exception;

import com.exe.astratarot.domain.dto.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.naming.AuthenticationException;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleSpringSecurityAuthException(
            org.springframework.security.core.AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Authentication failed"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        // Spring ném exception này với message mặc định "Access Denied" khi
        // @PreAuthorize chặn. Còn khi tầng service tự ném thì message là lời
        // giải thích viết cho người dùng ("Không thể hạ vai trò của quản trị
        // viên cuối cùng") — giữ nguyên để giao diện nói được lý do thay vì chỉ
        // báo "bị từ chối".
        String message = ex.getMessage();
        boolean isDefaultMessage = message == null || message.isBlank()
                || message.equalsIgnoreCase("Access Denied");
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(isDefaultMessage ? "Bạn không có quyền thực hiện thao tác này" : message));
    }

    /**
     * Body gửi lên không đọc được: JSON sai cú pháp, sai kiểu, hoặc sai bảng mã.
     *
     * Không có handler này thì nó rơi xuống @ExceptionHandler(Exception) và trả
     * 500 — báo cho client rằng máy chủ hỏng, trong khi lỗi nằm ở request họ
     * gửi. 500 còn làm mọi cơ chế cảnh báo lỗi hệ thống kêu oan.
     */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(
            org.springframework.http.converter.HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Dữ liệu gửi lên không hợp lệ. Kiểm tra lại định dạng JSON và bảng mã UTF-8."));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleRateLimit(RateLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Tình huống nghiệp vụ bình thường, không phải sự cố.
     *
     * Bảy exception của luồng Reader trước đây KHÔNG có handler nào, nên rơi
     * hết xuống @ExceptionHandler(Exception) và trả 500 kèm "Đã có lỗi xảy ra".
     * Người dùng nộp hồ sơ lần thứ hai, hay khai một khung giờ bị trùng, đều
     * nhận về màn hình lỗi hệ thống thay vì câu giải thích họ cần đọc.
     */
    @ExceptionHandler({
            EmailAlreadyExistsException.class,
            UsernameAlreadyExistsException.class,
            AccountDeactivatedException.class,
            EmailNotVerifiedException.class,
            InvalidCredentialsException.class,
            TokenReusedException.class,
            AlreadyReaderException.class,
            AlreadyAppliedException.class,
            InvalidApplicationStatusException.class,
            AvailabilityConflictException.class,
            InvalidAvailabilityTimeException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleConflictAndBadRequests(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /** Không tìm thấy đối tượng. Giao diện phân biệt 404 với lỗi thật để hiện đúng màn hình. */
    @ExceptionHandler({
            ApplicationNotFoundException.class,
            ReaderNotVerifiedException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleNotFoundDomain(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Nhà cung cấp mô hình ngôn ngữ lỗi hoặc quá tải.
     *
     * 502 chứ không 500: hỏng nằm ở dịch vụ bên ngoài, và giao diện cần phân
     * biệt để mời người dùng thử lại thay vì báo hệ thống hỏng.
     */
    @ExceptionHandler(LLMProviderException.class)
    public ResponseEntity<ApiResponse<Void>> handleLlmProvider(LLMProviderException ex) {
        log.warn("Nhà cung cấp AI lỗi: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error("Dịch vụ AI đang bận. Bạn thử lại sau ít phút nhé."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidToken(InvalidTokenException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception ex) {
        log.error("Unexpected error: {} - {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred. Please try again later."));
    }
}
