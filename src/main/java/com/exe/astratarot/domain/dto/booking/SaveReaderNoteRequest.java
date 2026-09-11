package com.exe.astratarot.domain.dto.booking;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** Ghi chú Reader viết cho khách sau buổi xem. */
@Data
public class SaveReaderNoteRequest {

    /**
     * Nội dung. Để trống là xoá ghi chú — nên KHÔNG @NotBlank ở đây.
     *
     * <p>4000 ký tự: đủ cho một bản tường thuật đầy đủ của buổi xem, và vẫn là
     * một trần rõ ràng để không ai dán cả quyển sách vào đây.
     */
    @Size(max = 4000, message = "Ghi chú tối đa 4000 ký tự")
    private String note;
}
