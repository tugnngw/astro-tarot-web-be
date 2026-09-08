package com.exe.astratarot.domain.mapper;

import com.exe.astratarot.domain.dto.reader.ReaderProfileResponse;
import com.exe.astratarot.domain.dto.reader.UpdateProfileRequest;
import com.exe.astratarot.domain.entity.ReaderProfile;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Mapper for the {@link ReaderProfile} entity and its DTOs.
 */
@Mapper(config = MapStructConfig.class)
public interface ReaderProfileMapper {

    /**
     * Maps a {@link ReaderProfile} entity to a {@link ReaderProfileResponse} DTO.
     */
    /*
     * `id` là id của HỒ SƠ, không phải của tài khoản.
     *
     * Bản trước ánh xạ user.id vào id, nên mọi thứ khoá theo reader_profile_id
     * đều gãy: lấy id từ GET /readers rồi gọi GET /readers/{id} luôn trả 404,
     * vì findById tra bảng reader_profiles. Booking, khung giờ trống và đánh
     * giá cũng đều tham chiếu reader_profile_id.
     *
     * Id của tài khoản vẫn cần cho giao diện nhận ra "hồ sơ này là của tôi",
     * nên nó nằm ở trường userId riêng (gán trong ReaderProfileServiceImpl).
     */
    @Mapping(source = "user.username", target = "username")
    @Mapping(source = "available", target = "isAvailable")
    ReaderProfileResponse toResponse(ReaderProfile readerProfile);

    /**
     * Updates a {@link ReaderProfile} entity from an {@link UpdateProfileRequest} DTO.
     * Only non-null fields are updated (partial update).
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "rating", ignore = true)
    @Mapping(target = "totalReviews", ignore = true)
    @Mapping(target = "verifiedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "available", ignore = true)
    void updateFromRequest(UpdateProfileRequest request, @MappingTarget ReaderProfile entity);
}
