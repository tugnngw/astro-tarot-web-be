# Build stage
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache ca-certificates && update-ca-certificates
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider "http://localhost:${PORT:-8080}/actuator/health" || exit 1

# Chia lại bộ nhớ cho hộp 512 MB của gói free.
#
# Trước đây JAVA_TOOL_OPTIONS trên Render đặt heap 65% (~333 MB) và giới hạn
# metaspace 128 MB. Chia sai chiều: heap rộng hơn nhiều so với nhu cầu thật,
# metaspace lại chật so với số lớp mà Spring Boot + JPA + các SDK nạp — và
# metaspace mới là thứ vỡ. Ứng dụng ném OutOfMemoryError: Metaspace giữa lúc
# phục vụ request, trả 500 cho màn thống kê của quản trị viên.
#
# Nay heap 35% (~179 MB) và metaspace 192 MB. Cộng cả luồng, code cache và bộ
# nhớ ngoài heap thì khoảng 440 MB, còn chỗ thở.
#
# Đặt ở dòng lệnh chứ không sửa biến môi trường: cờ dòng lệnh GHI ĐÈ
# JAVA_TOOL_OPTIONS, và để trong Dockerfile thì nó nằm trong quản lý phiên bản
# thay vì trong một ô trên bảng điều khiển không ai nhớ ai sửa lần cuối.
#
# ExitOnOutOfMemoryError vì trạng thái hiện tại là tệ nhất trong các khả năng:
# tiến trình còn sống nhưng hỏng, trả 500 cho mọi request, và không ai biết
# cho tới khi có người mở đúng màn hình đó. Chết hẳn để Render dựng lại thì
# người dùng mất vài chục giây, còn hơn mất cả tính năng mà không hay biết.
#
# Dạng shell chứ không phải exec: dạng exec không khai triển biến, nên
# $JAVA_OPTS mà render.yaml khai báo từ trước tới nay chưa từng có tác dụng.
# `exec` để java thành tiến trình chính và nhận được tín hiệu dừng của Render.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -XX:MaxRAMPercentage=35 -XX:MaxMetaspaceSize=192m -XX:+UseSerialGC -Xss512k -XX:+ExitOnOutOfMemoryError -jar app.jar"]