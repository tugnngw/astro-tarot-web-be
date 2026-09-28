import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;

/**
 * Dò một kết nối Postgres bằng ĐÚNG driver và ĐÚNG JVM mà Render dùng.
 *
 * <h3>Vì sao cần cái này</h3>
 *
 * <p>"Kết nối thử từ máy mình thì được" thường có nghĩa là thử bằng psql hoặc
 * một công cụ đồ hoạ. Cả hai dùng OpenSSL, còn ứng dụng dùng tầng TLS của
 * Java — hai thứ thương lượng khác nhau, tin bộ chứng chỉ khác nhau, và xử lý
 * SNI khác nhau. Một máy chủ chấp nhận psql mà cắt kết nối của Java là chuyện
 * có thật, nên "psql chạy được" KHÔNG chứng minh được gì về lỗi ở Render.
 *
 * <p>Chương trình này tách bạch ba câu hỏi mà một thông báo lỗi duy nhất gộp
 * lại thành một:
 *
 * <ol>
 *   <li><b>Tên miền phân giải ra gì?</b> Chỉ ra IPv6 thì Render bản free
 *       không nối tới được — đó là lý do phải dùng pooler.</li>
 *   <li><b>Mở được cổng TCP không?</b> Không mở được thì chưa tới lượt TLS,
 *       và mọi phỏng đoán về chứng chỉ đều lạc đề.</li>
 *   <li><b>Bắt tay TLS và đăng nhập có qua không?</b> In ra đúng chuỗi
 *       nguyên nhân, vì lớp ngoài cùng ("Broken pipe") thường vô nghĩa.</li>
 * </ol>
 *
 * <h3>Chạy</h3>
 *
 * <pre>
 * export JDBC_URL='jdbc:postgresql://...:5432/postgres?sslmode=require'
 * export DB_USER='postgres.abcxyz'
 * export DB_PASSWORD='...'
 * java -cp ~/.m2/repository/org/postgresql/postgresql/42.7.10/postgresql-42.7.10.jar \
 *      deploy/KiemKetNoi.java
 * </pre>
 *
 * <p>Mật khẩu đọc từ biến môi trường và KHÔNG bao giờ được in ra — kể cả
 * trong thông báo lỗi, vì chuỗi JDBC in kèm mật khẩu là cách rò rỉ phổ biến
 * nhất khi người ta dán log đi nhờ xem.
 */
public class KiemKetNoi {

    public static void main(String[] args) throws Exception {
        String url = env("JDBC_URL");
        String user = env("DB_USER");
        String pass = env("DB_PASSWORD");

        System.out.println("=== 1. Tên miền ===");
        String host = hostCua(url);
        int port = portCua(url);
        System.out.println("host = " + host + "   cổng = " + port);

        boolean coIPv4 = false;
        try {
            for (InetAddress a : InetAddress.getAllByName(host)) {
                boolean v4 = a.getAddress().length == 4;
                coIPv4 |= v4;
                System.out.println("  → " + a.getHostAddress() + (v4 ? "  (IPv4)" : "  (IPv6)"));
            }
        } catch (Exception e) {
            System.out.println("  KHÔNG phân giải được: " + e.getMessage());
            return;
        }
        if (!coIPv4) {
            System.out.println("""

                    ✗ Tên miền này CHỈ có IPv6.
                      Render gói free không có IPv6, nên nó sẽ không bao giờ nối được —
                      bất kể chứng chỉ hay mật khẩu đúng hay sai. Phải dùng host pooler
                      (aws-*.pooler.supabase.com) thay cho db.<ref>.supabase.co.""");
            return;
        }

        System.out.println("\n=== 2. Mở cổng TCP ===");
        long t0 = System.nanoTime();
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), 8000);
            System.out.printf("  mở được sau %d ms%n", (System.nanoTime() - t0) / 1_000_000);
        } catch (Exception e) {
            System.out.println("  ✗ không mở được: " + e);
            System.out.println("""
                      Chưa tới lượt TLS. Kiểm tra cổng (session pooler = 5432,
                      transaction pooler = 6543) và tường lửa.""");
            return;
        }

        System.out.println("\n=== 3. Bắt tay TLS + đăng nhập ===");
        Properties p = new Properties();
        p.setProperty("user", user);
        p.setProperty("password", pass);
        // Thời gian chờ ngắn: treo ba mươi giây rồi mới báo lỗi thì rất khó
        // phân biệt "bị cắt" với "mạng chậm".
        p.setProperty("connectTimeout", "10");
        p.setProperty("socketTimeout", "20");
        p.setProperty("loginTimeout", "15");

        try (Connection c = DriverManager.getConnection(url, p);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "select version(), current_user, inet_server_port()")) {
            rs.next();
            System.out.println("  ✓ NỐI ĐƯỢC");
            System.out.println("  " + rs.getString(1));
            System.out.println("  đăng nhập là: " + rs.getString(2));
            System.out.println("  cổng máy chủ: " + rs.getString(3));
        } catch (Exception e) {
            System.out.println("  ✗ HỎNG");
            // In cả chuỗi nguyên nhân. Lớp ngoài cùng hay là "Broken pipe" —
            // một câu đúng nhưng vô dụng; lớp trong mới nói vì sao ống vỡ.
            for (Throwable t = e; t != null; t = t.getCause()) {
                System.out.println("    " + t.getClass().getName() + ": " + t.getMessage());
            }
            System.out.println("""

                      Chạy lại kèm nhật ký TLS nếu dòng trên nhắc tới handshake:
                        java -Djavax.net.debug=ssl:handshake -cp <driver.jar> deploy/KiemKetNoi.java""");
        }
    }

    private static String env(String ten) {
        String v = System.getenv(ten);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("Thiếu biến môi trường " + ten);
        }
        return v;
    }

    /** Lấy host từ chuỗi JDBC. Bỏ tiền tố "jdbc:" để URI đọc được. */
    private static String hostCua(String jdbc) {
        return URI.create(jdbc.substring("jdbc:".length())).getHost();
    }

    private static int portCua(String jdbc) {
        int p = URI.create(jdbc.substring("jdbc:".length())).getPort();
        return p == -1 ? 5432 : p;
    }
}
