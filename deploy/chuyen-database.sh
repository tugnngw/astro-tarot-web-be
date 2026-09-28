#!/usr/bin/env bash
#
# Chép toàn bộ dữ liệu từ một Postgres sang một Postgres khác, rồi đối chiếu.
#
# ---------------------------------------------------------------------------
# Vì sao cần script chứ không phải hai dòng pg_dump / pg_restore
# ---------------------------------------------------------------------------
#
# Hai dòng ấy chạy xong vẫn im lặng khi hỏng một nửa. Ba thứ dưới đây mới là
# phần đáng giá, và cả ba đều KHÔNG tự lộ ra:
#
#   1. Bản pg_dump phải MỚI BẰNG HOẶC HƠN máy chủ nguồn. Cũ hơn thì nó bỏ qua
#      những thứ nó không hiểu — dump chạy xong, báo thành công, và thiếu dữ
#      liệu. Script tự đọc phiên bản hai bên rồi chọn đúng ảnh Docker.
#
#   2. Đích phải RỖNG. Đổ đè lên một database đã có bảng thì pg_restore báo
#      hàng trăm lỗi "already exists" lẫn giữa những lỗi thật, và không ai đọc
#      hết. Script dừng lại nếu đích đã có bảng.
#
#   3. Chép xong phải ĐẾM LẠI. Đây là chỗ duy nhất chứng minh dữ liệu thật sự
#      sang tới nơi; restore trả mã 0 không có nghĩa là đủ dòng.
#
# ---------------------------------------------------------------------------
# Mật khẩu
# ---------------------------------------------------------------------------
#
# Chuỗi kết nối gõ vào lúc chạy, không truyền qua tham số dòng lệnh — tham số
# thì hiện ra trong `ps` và nằm lại trong lịch sử shell. Nó đi vào container
# bằng biến môi trường và không bao giờ được in ra.
#
# ---------------------------------------------------------------------------
# Chạy
# ---------------------------------------------------------------------------
#
#   bash deploy/chuyen-database.sh                    # gõ chuỗi kết nối vào
#   bash deploy/chuyen-database.sh khoa-bi-mat-db.txt # đọc từ file, không gõ gì
#
# File có dạng hai dòng:
#
#     NGUON=postgresql://user:matkhau@ep-....neon.tech/neondb?sslmode=require
#     DICH=postgresql://postgres.<ref>:matkhau@aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres?sslmode=require
#
# Đặt tên theo mẫu `khoa-bi-mat-*.txt` để .gitignore tự bỏ qua nó.
#
# Cần Docker. Không cần cài pg_dump trên máy.

set -uo pipefail

mau_do=$'\033[31m'; mau_xanh=$'\033[32m'; mau_vang=$'\033[33m'; het=$'\033[0m'
loi()  { echo "${mau_do}✗ $*${het}" >&2; }
xong() { echo "${mau_xanh}✓ $*${het}"; }
nhac() { echo "${mau_vang}! $*${het}"; }

command -v docker >/dev/null 2>&1 || { loi "Không có Docker. Script này dùng Docker để khỏi phải cài pg_dump."; exit 1; }
docker info >/dev/null 2>&1 || { loi "Docker chưa chạy. Mở Docker Desktop rồi thử lại."; exit 1; }

echo "=============================================================="
echo " Chép dữ liệu giữa hai Postgres"
echo "=============================================================="
echo
echo "Dán chuỗi kết nối dạng libpq (KHÔNG phải jdbc:...):"
echo "  postgresql://user:matkhau@host:5432/tendb?sslmode=require"
echo
echo "Supabase: dùng host pooler (aws-0-<region>.pooler.supabase.com:5432)"
echo "và tên đăng nhập postgres.<project-ref>. Host db.<ref>.supabase.co chỉ"
echo "có IPv6."
echo

# Đọc từ file nếu có, để khỏi phải dán chuỗi dài bằng tay.
#
# File hai dòng, mỗi dòng một chuỗi kết nối:
#
#     NGUON=postgresql://...
#     DICH=postgresql://...
#
# Đọc bằng `source` chứ không phải đọc rồi eval từng dòng: chuỗi kết nối có
# dấu & và ? bên trong, và một vòng lặp tự cắt chuỗi sẽ nuốt mất chúng.
#
# File này chứa mật khẩu nên phải nằm ngoài git. `.gitignore` đã có sẵn mẫu
# `khoa-bi-mat-*.txt`, đặt tên theo mẫu ấy là an toàn.
if [ $# -ge 1 ]; then
  [ -f "$1" ] || { loi "Không có file $1"; exit 1; }
  # shellcheck disable=SC1090
  . "$1"
  echo "Đọc chuỗi kết nối từ $1"
  echo
else
  # -s: không hiện lại những gì gõ vào. Chuỗi có mật khẩu bên trong.
  read -r -s -p "NGUỒN  (database đang có dữ liệu): " NGUON; echo
  read -r -s -p "ĐÍCH   (database mới, đang rỗng) : " DICH;  echo
  echo
fi

[ -n "${NGUON:-}" ] && [ -n "${DICH:-}" ] || { loi "Thiếu một trong hai chuỗi."; exit 1; }
export NGUON DICH

# Một ảnh tạm để hỏi phiên bản. Bản nào cũng hỏi được, nên dùng 18 cho chắc.
# Chuỗi kết nối được mở ra BÊN TRONG container, không phải ở máy chủ.
#
# Viết `psql "${!1}"` thì bash mở biến ra trước rồi đưa nguyên mật khẩu vào
# dòng lệnh của docker — mà dòng lệnh ấy ai chạy `ps` cũng đọc được. Đẩy biến
# vào bằng -e rồi để shell bên trong tự mở thì mật khẩu không nằm trong argv
# của tiến trình nào trên máy này.
hoi() {
  SQL="$2" BEN="$1" docker run --rm -e NGUON -e DICH -e SQL -e BEN postgres:18-alpine \
    sh -c 'if [ "$BEN" = NGUON ]; then U="$NGUON"; else U="$DICH"; fi
           psql "$U" -tAX -c "$SQL"' 2>/dev/null | tr -d '\r'
}

echo "--- Đọc phiên bản hai bên"
v_nguon=$(hoi NGUON "show server_version_num")
v_dich=$(hoi DICH  "show server_version_num")

[ -n "$v_nguon" ] || { loi "Không nối được tới NGUỒN. Kiểm tra chuỗi kết nối."; exit 1; }
[ -n "$v_dich"  ] || { loi "Không nối được tới ĐÍCH. Kiểm tra chuỗi kết nối."; exit 1; }

major_nguon=$(( v_nguon / 10000 ))
major_dich=$(( v_dich / 10000 ))
echo "  nguồn = PostgreSQL $major_nguon"
echo "  đích  = PostgreSQL $major_dich"

if [ "$major_nguon" -gt "$major_dich" ]; then
  nhac "Nguồn MỚI hơn đích ($major_nguon > $major_dich)."
  nhac "Dump có thể chứa cú pháp mà đích chưa hiểu. Nếu restore báo lỗi cú"
  nhac "pháp thì phải nâng phiên bản đích, không có cách vòng nào."
  echo
fi

# pg_dump phải mới BẰNG HOẶC HƠN máy chủ nguồn.
anh="postgres:${major_nguon}-alpine"
echo "  dùng ảnh $anh"

echo
echo "--- Kiểm tra ĐÍCH có rỗng không"
so_bang=$(hoi DICH "select count(*) from information_schema.tables where table_schema='public'")
so_bang=${so_bang:-0}
if [ "$so_bang" -gt 0 ]; then
  loi "ĐÍCH đã có $so_bang bảng trong schema public."
  echo "  Đổ đè lên sẽ sinh hàng trăm lỗi \"already exists\" lẫn giữa lỗi thật."
  echo "  Muốn làm lại từ đầu thì xoá sạch rồi chạy lại:"
  echo "      drop schema public cascade; create schema public;"
  exit 1
fi
xong "ĐÍCH rỗng"

thu_muc=$(mktemp -d)
trap 'rm -rf "$thu_muc"' EXIT

echo
echo "--- Dump từ NGUỒN (chỉ schema public)"
# -n public: bỏ qua auth, storage, extensions... của Supabase. Bảng của ứng
# dụng đều nằm ở public, và kéo theo schema hệ thống là chuốc lấy xung đột.
# --no-owner --no-privileges: chủ sở hữu ở hai nơi khác nhau, giữ lại chỉ làm
# restore đỏ vì những vai trò không tồn tại bên đích.
if ! docker run --rm -e NGUON -v "$thu_muc:/x" "$anh" \
      sh -c 'pg_dump "$NGUON" -n public --no-owner --no-privileges -Fc -f /x/astro.dump'; then
  loi "pg_dump hỏng. Không có gì bị thay đổi ở đâu cả."
  exit 1
fi
co=$(du -h "$thu_muc/astro.dump" 2>/dev/null | cut -f1)
xong "dump xong ($co)"

echo
echo "--- Restore sang ĐÍCH"
# pg_restore trả mã khác 0 cả khi chỉ là cảnh báo, nên không dừng ở đây —
# phần đếm dòng bên dưới mới là chỗ phán xử.
docker run --rm -e DICH -v "$thu_muc:/x" "$anh" \
  sh -c 'pg_restore "$DICH" --no-owner --no-privileges /x/astro.dump' \
  2>&1 | tail -20
echo

echo "--- Đối chiếu số dòng"
dem="select table_name from information_schema.tables where table_schema='public' and table_type='BASE TABLE' order by 1"
bang=$(hoi NGUON "$dem")
[ -n "$bang" ] || { loi "NGUỒN không có bảng nào trong public. Sai database?"; exit 1; }

lech=0
printf "%-34s %10s %10s\n" "BẢNG" "NGUỒN" "ĐÍCH"
while IFS= read -r t; do
  [ -n "$t" ] || continue
  a=$(hoi NGUON "select count(*) from public.\"$t\"")
  b=$(hoi DICH  "select count(*) from public.\"$t\"")
  a=${a:-?}; b=${b:-?}
  if [ "$a" = "$b" ]; then
    printf "%-34s %10s %10s\n" "$t" "$a" "$b"
  else
    printf "%-34s %10s %10s  ${mau_do}LỆCH${het}\n" "$t" "$a" "$b"
    lech=$((lech + 1))
  fi
done <<< "$bang"

echo
if [ "$lech" -gt 0 ]; then
  loi "$lech bảng lệch số dòng. ĐỪNG đổi biến môi trường trên Render."
  echo "  Đọc lại phần restore ở trên để biết bảng nào hỏng và vì sao."
  exit 1
fi

xong "Mọi bảng khớp số dòng."
echo
echo "=============================================================="
echo " Việc còn lại trên Render (dashboard → astra-tarot-api → Environment)"
echo "=============================================================="
cat <<'HET'

1. XOÁ ba biến này. Bỏ khỏi render.yaml là chưa đủ — biến đã đặt trong dịch
   vụ vẫn còn đó và vẫn thắng:

       SPRING_FLYWAY_URL
       SPRING_FLYWAY_USER
       SPRING_FLYWAY_PASSWORD

2. Đặt lại ba biến này trỏ sang database mới:

       SPRING_DATASOURCE_URL       jdbc:postgresql://<host-pooler>:5432/postgres?sslmode=require
       SPRING_DATASOURCE_USERNAME  postgres.<project-ref>
       SPRING_DATASOURCE_PASSWORD  (và DB_PASSWORD cùng giá trị)

3. Manual Deploy → Deploy latest commit.

4. Kiểm: https://astra-tarot-api.onrender.com/actuator/health trả 200,
   rồi đăng nhập thử bằng một tài khoản có sẵn — nếu database chép thiếu thì
   trang vẫn lên bình thường, chỉ là không đăng nhập được.

GIỮ DATABASE CŨ thêm ít nhất một tuần. Nó là bản lùi duy nhất, và lỗi lúc
chép dữ liệu thường chỉ lộ ra sau vài ngày.
HET
