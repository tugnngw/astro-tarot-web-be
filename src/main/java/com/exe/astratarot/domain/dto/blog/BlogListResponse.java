package com.exe.astratarot.domain.dto.blog;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlogListResponse {
    private List<BlogResponse> content;
    private int totalPages;
    private long totalElements;
    private int number;
    private int size;
}
