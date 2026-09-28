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
# File này chứa mật khẩu nên phải nằm ngoài git. `.gitignore` đã có sẵn mẫu
# `khoa-bi-mat-*.txt`, đặt tên theo mẫu ấy là an toàn.
#
# ---- Vì sao KHÔNG dùng `source` ----
#
# `source` là *chạy* file như mã shell, không phải đọc nó. Mật khẩu thật hay
# có ký tự mà shell coi là lệnh:
#
#     DICH=...:mat<khau@...     →  `<` thành chuyển hướng, báo
#                                  "No such file or directory"
#     DICH=...:a;rm -rf ~;b@... →  shell CHẠY phần giữa
#     DICH=...:mat khau@...     →  cắt ở dấu cách
#
# Bộ đọc dưới đây không diễn giải gì: cắt đúng sau dấu `=` đầu tiên rồi giữ
# nguyên phần còn lại.
doc_bien() {
  local dong
  dong=$(grep -m1 "^[[:space:]]*$2=" "$1") || return 1
  dong=${dong#*=}
  # File soạn bằng Notepad trên Windows có \r ở cuối dòng. Nó sẽ dính vào
  # đuôi chuỗi kết nối và đẻ ra một lỗi không thể nào đoán ra từ thông báo.
  dong=${dong%$'\r'}
  # Bỏ nháy bao ngoài nếu người dùng có gõ.
  case "$dong" in
    \'*\') dong=${dong#\'}; dong=${dong%\'} ;;
    \"*\") dong=${dong#\"}; dong=${dong%\"} ;;
  esac
  printf '%s' "$dong"
}

# --thu: chỉ kiểm hai đầu còn trả lời không, rồi dừng. Không chép gì.
#
# Có riêng một chế độ cho việc này vì câu hỏi "database cũ còn đọc được
# không" thường xuất hiện đúng lúc nó SẮP không đọc được nữa — hết hạn mức,
# sắp bị treo, sắp hết hạn dùng thử. Lúc ấy cần một câu trả lời trong mười
# giây, không phải một lượt chép có thể chết giữa chừng.
CHI_THU=0
for t in "$@"; do
  [ "$t" = "--thu" ] && CHI_THU=1
done
set -- "${@/--thu/}"

if [ -n "${1:-}" ]; then
  [ -f "$1" ] || { loi "Không có file $1"; exit 1; }
  NGUON=$(doc_bien "$1" NGUON)
  DICH=$(doc_bien "$1" DICH)
  # Bắt luôn trường hợp còn nguyên chỗ trống trong file mẫu — nếu không thì
  # nó đi tiếp và báo "không nối được", một câu dẫn người ta đi sai hướng.
  # Kiểm TỪNG ô và nói đích danh ô nào hỏng.
  #
  # Bản trước chỉ báo "còn chỗ trống chưa điền" cho cả file. Đúng nhưng vô
  # dụng: người đọc vẫn phải mở file ra dò xem chỗ nào, mà file thì có hai ô
  # và cả hai đều có thể sai theo ba kiểu khác nhau.
  #
  # Chỉ bắt đúng dạng <TEN_VIET_HOA>, không bắt mọi dấu `<`: mật khẩu thật có
  # thể chứa nó, và từ chối oan thì không ai biết mình bị từ chối vì cái gì.
  # Ở chế độ --thu chỉ cần NGUỒN. Câu hỏi lúc đó là "database cũ còn đọc
  # được không", và ĐÍCH không liên quan gì tới câu trả lời — bắt điền nốt
  # mật khẩu bên kia chỉ dựng thêm một rào cản đúng lúc người ta đang vội.
  hong=0
  can=( NGUON DICH )
  [ "$CHI_THU" -eq 1 ] && can=( NGUON )
  for o in "${can[@]}"; do
    v=${!o}
    if [ -z "$v" ]; then
      loi "$o= còn để trống trong $1"
      hong=1
    elif [[ "$v" =~ (\<[A-Z_]+\>) ]]; then
      loi "$o= còn chỗ trống ${BASH_REMATCH[1]} chưa thay trong $1"
      hong=1
    elif [ "${v#postgresql://}" = "$v" ] && [ "${v#postgres://}" = "$v" ]; then
      # Bắt luôn lỗi dán nhầm chuỗi JDBC — nó khác đúng bốn ký tự ở đầu, và
      # nếu lọt qua thì psql báo một câu chẳng liên quan gì tới nguyên nhân.
      loi "$o= phải bắt đầu bằng postgresql:// (bỏ chữ 'jdbc:' nếu chép từ Render)"
      hong=1
    fi
  done
  [ "$hong" -eq 0 ] || exit 1
  echo "Đọc chuỗi kết nối từ $1"
  echo
else
  # -s: không hiện lại những gì gõ vào. Chuỗi có mật khẩu bên trong.
  read -r -s -p "NGUỒN  (database đang có dữ liệu): " NGUON; echo
  read -r -s -p "ĐÍCH   (database mới, đang rỗng) : " DICH;  echo
  echo
fi

if [ "$CHI_THU" -eq 1 ]; then
  [ -n "${NGUON:-}" ] || { loi "Thiếu chuỗi NGUỒN."; exit 1; }
  DICH=${DICH:-}
else
  [ -n "${NGUON:-}" ] && [ -n "${DICH:-}" ] || { loi "Thiếu một trong hai chuỗi."; exit 1; }
fi
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

echo "--- Đọc phiên bản"
v_nguon=$(hoi NGUON "show server_version_num")
if [ -z "$v_nguon" ]; then
  loi "Không nối được tới NGUỒN."
  echo "  Ba nguyên nhân hay gặp, theo thứ tự:"
  echo "    1. Database đang bị tạm dừng (hết hạn mức gói free, ngủ vì không"
  echo "       dùng lâu). Bảng điều khiển của nhà cung cấp sẽ nói rõ."
  echo "    2. Sai mật khẩu, hoặc mật khẩu có ký tự lạ chưa mã hoá URL"
  echo "       (@ thành %40, dấu cách thành %20)."
  echo "    3. Sai host hoặc sai tên database."
  exit 1
fi
major_nguon=$(( v_nguon / 10000 ))
echo "  nguồn = PostgreSQL $major_nguon"

# ĐÍCH chỉ hỏi khi thật sự sắp ghi vào nó.
major_dich=$major_nguon
if [ "$CHI_THU" -eq 0 ]; then
  v_dich=$(hoi DICH "show server_version_num")
  [ -n "$v_dich" ] || { loi "Không nối được tới ĐÍCH. Kiểm tra chuỗi kết nối."; exit 1; }
  major_dich=$(( v_dich / 10000 ))
  echo "  đích  = PostgreSQL $major_dich"
fi

if [ "$major_nguon" -gt "$major_dich" ]; then
  nhac "Nguồn MỚI hơn đích ($major_nguon > $major_dich)."
  nhac "Dump có thể chứa cú pháp mà đích chưa hiểu. Nếu restore báo lỗi cú"
  nhac "pháp thì phải nâng phiên bản đích, không có cách vòng nào."
  echo
fi

# pg_dump phải mới BẰNG HOẶC HƠN máy chủ nguồn.
anh="postgres:${major_nguon}-alpine"
echo "  dùng ảnh $anh"

if [ "$CHI_THU" -eq 1 ]; then
  echo
  echo "--- Đếm thử vài bảng ở NGUỒN"
  # Đọc được số dòng nghĩa là database còn phục vụ truy vấn thật, không chỉ
  # còn bắt tay được. Một máy chủ hết hạn mức có thể vẫn cho nối rồi mới từ
  # chối câu lệnh.
  co_bang=0
  for b in users bookings payment_transactions; do
    n=$(hoi NGUON "select count(*) from public.$b")
    if [ -n "$n" ]; then
      printf "  %-22s %s dòng\n" "$b" "$n"
      co_bang=1
    else
      printf "  %-22s (không đọc được)\n" "$b"
    fi
  done
  echo
  if [ "$co_bang" -eq 1 ]; then
    xong "NGUỒN còn đọc được. Chạy lại KHÔNG kèm --thu để chép sang ĐÍCH."
  else
    loi "NGUỒN nối được nhưng không truy vấn được bảng nào."
    echo "  Thường là hết hạn mức hoặc sai database trong chuỗi kết nối."
  fi
  exit 0
fi

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
# Dump đi ra STDOUT rồi mới ghi xuống file, thay vì gắn thư mục vào container.
#
# Trên Git Bash ở Windows, `-v "$thu_muc:/x"` KHÔNG chạy: MSYS tưởng `/x` là
# một đường dẫn POSIX rồi đổi nó thành `X:\`, và docker từ chối với
# "destination can't be '/'". Đặt MSYS_NO_PATHCONV rồi gọi cygpath thì chữa
# được, nhưng đó là thêm hai thứ chỉ đúng trên một hệ điều hành.
#
# Ống dẫn thì không có đường dẫn nào để ai đó bóp méo, và chạy như nhau ở mọi
# nơi. `-Fc` vẫn giữ để pg_restore chọn lọc được và báo lỗi theo từng mục.
if ! docker run --rm -e NGUON "$anh" \
      sh -c 'pg_dump "$NGUON" -n public --no-owner --no-privileges -Fc' \
      > "$thu_muc/astro.dump"; then
  loi "pg_dump hỏng. Không có gì bị thay đổi ở đâu cả."
  exit 1
fi
[ -s "$thu_muc/astro.dump" ] || { loi "Dump rỗng. Không chép gì cả."; exit 1; }
co=$(du -h "$thu_muc/astro.dump" 2>/dev/null | cut -f1)
xong "dump xong ($co)"

echo
echo "--- Restore sang ĐÍCH"
# pg_restore trả mã khác 0 cả khi chỉ là cảnh báo, nên không dừng ở đây —
# phần đếm dòng bên dưới mới là chỗ phán xử.
# `-i` để container nhận được STDIN. pg_restore không có tên file thì đọc từ
# STDIN — cùng lý do như lúc dump: không có đường dẫn nào để bóp méo.
docker run --rm -i -e DICH "$anh" \
  sh -c 'pg_restore --no-owner --no-privileges -d "$DICH"' \
  < "$thu_muc/astro.dump" 2>&1 | tail -20
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
