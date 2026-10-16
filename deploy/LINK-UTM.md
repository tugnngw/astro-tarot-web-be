# Link gắn UTM cho từng kênh

Dán đúng link của kênh nào vào kênh ấy. Vào bằng link trần thì GA4 vẫn đếm được
lượt, nhưng **không biết khách đến từ đâu** — đúng thứ Outcome 3 cần lại mất.

## Vì sao không dùng link trần

GA4 đoán nguồn bằng *referrer* — mẩu thông tin trình duyệt gửi kèm, nói "tôi vừa
từ trang nào sang". Nhưng **trình duyệt trong ứng dụng TikTok và Facebook thường
cắt mẩu ấy đi**. Hậu quả: khách bấm từ TikTok vào vẫn được đếm, nhưng GA4 xếp
chung vào `(direct)` — cùng rổ với người tự gõ địa chỉ.

Tham số `utm_*` nằm ngay trong đường dẫn nên không bị cắt. Có nó thì bảng
**Nguồn / Kênh** trong trang quản trị tách ra được `tiktok` bao nhiêu, `facebook`
bao nhiêu.

## Bộ link

| Đăng ở đâu | Link |
| --- | --- |
| TikTok — phần mô tả hồ sơ | `https://astrotarot.date/?utm_source=tiktok&utm_medium=social&utm_campaign=bio` |
| TikTok — bình luận / mô tả video | `https://astrotarot.date/?utm_source=tiktok&utm_medium=social&utm_campaign=video` |
| Facebook — trang / fanpage | `https://astrotarot.date/?utm_source=facebook&utm_medium=social&utm_campaign=page` |
| Facebook — bài đăng trong nhóm | `https://astrotarot.date/?utm_source=facebook&utm_medium=social&utm_campaign=group` |
| Zalo — nhắn riêng, nhóm chat | `https://astrotarot.date/?utm_source=zalo&utm_medium=social&utm_campaign=chat` |
| Instagram — hồ sơ | `https://astrotarot.date/?utm_source=instagram&utm_medium=social&utm_campaign=bio` |
| Nhóm lớp, bạn bè | `https://astrotarot.date/?utm_source=banbe&utm_medium=truyenmieng&utm_campaign=exe201` |
| Tờ rơi, mã QR in ra | `https://astrotarot.date/?utm_source=qr&utm_medium=offline&utm_campaign=torroi` |
| Chữ ký email, nộp bài | `https://astrotarot.date/?utm_source=email&utm_medium=email&utm_campaign=exe201` |

Muốn thêm kênh thì giữ nguyên công thức:

```
https://astrotarot.date/?utm_source=<ten-kenh>&utm_medium=<cach-tiep-can>&utm_campaign=<dot-dang>
```

- `utm_source` — **chỗ cụ thể** khách đến từ đó: `tiktok`, `facebook`, `zalo`.
- `utm_medium` — **kiểu kênh**: `social`, `email`, `offline`, `truyenmieng`.
- `utm_campaign` — **đợt đăng**, để so sánh về sau: `bio`, `video`, `ramat-t10`.

Viết **không dấu, chữ thường, không khoảng trắng**. GA4 phân biệt chữ hoa chữ
thường: `TikTok` và `tiktok` bị đếm thành hai nguồn khác nhau.

## Ba cái bẫy

**1. Rút gọn link làm mất UTM.** Một số công cụ rút gọn bỏ hết tham số. Rút gọn
xong phải mở thử và nhìn thanh địa chỉ: `utm_source=` còn nguyên thì mới dùng.

**2. Dẫn thẳng vào trang con cũng được**, miễn giữ tham số. Dấu `?` chỉ dùng một
lần, tham số thứ hai trở đi nối bằng `&`:

```
https://astrotarot.date/readers?utm_source=tiktok&utm_medium=social
https://astrotarot.date/tarot?utm_source=facebook&utm_medium=social&utm_campaign=video
```

**3. Đừng tự bấm link UTM của mình nhiều lần.** Mỗi lượt bấm đều vào thống kê;
tự bấm thử hai chục lần là số nguồn hỏng. Thử một lần cho biết chạy, rồi thôi.

## Kiểm xem đã ăn chưa

1. Mở link UTM ở điện thoại (tốt nhất là mạng 4G, không phải wifi nhà).
2. GA4 → **Thời gian thực** → trong vòng 30 phút phải thấy lượt vừa rồi.
3. Nguồn hiện ra ở báo cáo thường sau **24–48 giờ**, không phải ngay.

Trang quản trị → tab **Lưu lượng & GA4** → bảng *Nguồn & Phương thức truy cập*
là chỗ chụp màn hình để nộp Outcome 3.
