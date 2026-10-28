package com.exe.astratarot.domain.entity;

import lombok.*;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

/** Khoá của {@link GameNgay}: một người, một ngày. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class GameNgayId implements Serializable {

    private UUID userId;
    private LocalDate ngay;

    @Override
    public String toString() {
        return "GameNgayId{userId=" + userId + ", ngay=" + ngay + "}";
    }
}
