package com.exe.astratarot.domain.dto.blog;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateBlogRequest {
    @Size(max = 255)
    private String title;

    @Size(max = 255)
    private String slug;

    @Size(max = 500)
    private String summary;

    private String content;

    @Size(max = 500)
    private String thumbnailUrl;
}
