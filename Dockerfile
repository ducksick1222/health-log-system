FROM eclipse-temurin:21
WORKDIR /app
COPY lib/mysql-connector-j-9.7.0.jar /app/lib/
COPY src/App.java /app/src/
RUN javac -cp /app/lib/mysql-connector-j-9.7.0.jar /app/src/App.java -d /app/bin
EXPOSE 8080

#  修正後：移除 -Djava.net.preferIPv4Stack=true
CMD ["java", "-cp", "/app/bin:/app/lib/mysql-connector-j-9.7.0.jar", "App"]
