package com.exe.astratarot.domain.enums;

/**
 * Một ngày trên lịch công khai của Reader.
 *
 * <p>Ngày nghỉ và ngày không có giờ làm đều không đặt được, nhưng đó là hai
 * chuyện khác nhau. Gộp chung thành "không còn khung" thì khách tưởng Reader
 * kín lịch, trong khi họ chỉ nghỉ.
 */
public enum CalendarDayKind {
    /** Reader đã khai nghỉ ngày này. */
    OFF,
    /** Thứ này không nằm trong lịch làm việc hàng tuần. */
    CLOSED,
    /** Còn ít nhất một khung trống ở tương lai. */
    OPEN,
    /** Có giờ làm, nhưng mọi khung còn lại đều đã có người đặt. */
    FULL,
    /** Hôm nay, nhưng giờ làm đã qua hết và không còn khung nào để đặt. */
    OVER,
    /** Ngày đã qua. */
    PAST
}
