FROM eclipse-temurin:21
WORKDIR /app
COPY lib/mysql-connector-j-9.7.0.jar /app/lib/
COPY src/App.java /app/src/
RUN javac -cp /app/lib/mysql-connector-j-9.7.0.jar /app/src/App.java -d /app/bin
EXPOSE 8080

# 強制要求 Java 使用 IPv4 解析 DNS，避免 Render 與 CleverCloud 之間的 IPv6 衝突
CMD ["java", "-Djava.net.preferIPv4Stack=true", "-cp", "/app/bin:/app/lib/mysql-connector-j-9.7.0.jar", "App"]
