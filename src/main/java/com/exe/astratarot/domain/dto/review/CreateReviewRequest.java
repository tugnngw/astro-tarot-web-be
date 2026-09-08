package com.exe.astratarot.domain.dto.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateReviewRequest {

    @NotNull(message = "Phải chấm điểm")
    @Min(value = 1, message = "Điểm thấp nhất là 1 sao")
    @Max(value = 5, message = "Điểm cao nhất là 5 sao")
    private Integer rating;

    @Size(max = 2000, message = "Nhận xét tối đa 2000 ký tự")
    private String comment;
}
