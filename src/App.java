import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.InetSocketAddress;
import java.sql.*;
import java.util.stream.Collectors;

public class App {
private static final String DB_URL = "jdbc:mysql://bqpot4qp10h8enc9j78c-mysql.services.clever-cloud.com:3306/bqpot4qp10h8enc9j78c?serverTimezone=UTC&sslMode=PREFERRED&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "u13fdbq9vhlx4z64";
    private static final String DB_PASSWORD = "F3t4vsqXWxfnkfexdncQ";
    public static void main(String[] args) throws Exception {
        // 監聽 8080 連接埠
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        System.out.println("🚀 後端伺服器已啟動，正在監聽 http://localhost:8080");

        // 2. 路由註冊 (對應題目要求的 API 端點)
        server.createContext("/health-logs", new HealthLogHandler());
        server.createContext("/health-logs/risk", new RiskHandler());

        server.setExecutor(null);
        server.start();
    }

    /**
     * 核心邏輯：決策樹風險評估 (對應評分對照表 5.2)
     */
    public static String calculateRiskLevel(double sleepHours, int steps, int moodScore) {
        if (sleepHours <= 5.5) { 
            if (steps <= 3500) {
                return moodScore <= 4 ? "High" : "Medium";
            }
            return "Medium";
        } else if (sleepHours >= 7.0) {
            if (steps >= 6000) {
                return moodScore >= 6 ? "Low" : "Medium";
            }
            return "Medium";
        }
        return "Medium"; // 中間過渡情況
    }

    /**
     * 處理 /health-logs 的 CRUD 路由 (包含 GET, POST, PUT, DELETE)
     */
    static class HealthLogHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // 處理前端跨域請求 (CORS)
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath(); // 例如: /health-logs 或 /health-logs/15
            String response = "";
            int statusCode = 200;

            // 💡 URL 解析：嘗試從路徑中分離出 ID (用於 PUT 與 DELETE)
            String[] pathParts = path.split("/");
            Integer logId = null;
            if (pathParts.length > 2) {
                try {
                    logId = Integer.parseInt(pathParts[2]);
                } catch (NumberFormatException e) {
                    // 若無法解析為整數，代表可能是其他子路徑 (例如 /risk)
                }
            }

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                
                if ("GET".equalsIgnoreCase(method)) {
                    // 🔹 端點 1：取得所有日誌 
                    String sql = "SELECT * FROM health_logs ORDER BY log_date DESC";
                    Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(sql);
                    
                    StringBuilder json = new StringBuilder("[");
                    while (rs.next()) {
                        json.append(String.format(
                            "{\"id\":%d,\"log_date\":\"%s\",\"sleep_hours\":%.1f,\"steps\":%d,\"mood_score\":%d,\"risk_level\":\"%s\"},",
                            rs.getInt("id"), rs.getString("log_date"), rs.getDouble("sleep_hours"),
                            rs.getInt("steps"), rs.getInt("mood_score"), rs.getString("risk_level")
                        ));
                    }
                    if (json.length() > 1) json.setLength(json.length() - 1);
                    json.append("]");
                    response = json.toString();

                } else if ("POST".equalsIgnoreCase(method)) {
                    // 🔹 端點 2：新增健康日誌 
                    InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                    BufferedReader br = new BufferedReader(isr);
                    String body = br.lines().collect(Collectors.joining());

                    String logDate = body.split("\"log_date\":\"")[1].split("\"")[0];
                    double sleep = Double.parseDouble(body.split("\"sleep_hours\":")[1].split(",")[0].trim());
                    int steps = Integer.parseInt(body.split("\"steps\":")[1].split(",")[0].trim());
                    int mood = Integer.parseInt(body.split("\"mood_score\":")[1].split("}")[0].trim());

                    // 整合決策樹商務邏輯
                    String risk = calculateRiskLevel(sleep, steps, mood);

                    String sql = "INSERT INTO health_logs (log_date, sleep_hours, steps, mood_score, risk_level) VALUES (?, ?, ?, ?, ?)";
                    PreparedStatement pstmt = conn.prepareStatement(sql);
                    pstmt.setString(1, logDate);
                    pstmt.setDouble(2, sleep);
                    pstmt.setInt(3, steps);
                    pstmt.setInt(4, mood);
                    pstmt.setString(5, risk);
                    pstmt.executeUpdate();

                    response = "{\"status\":\"success\",\"message\":\"" + risk + "\"}";
                    statusCode = 201;

                } else if ("PUT".equalsIgnoreCase(method)) {
                    // 🔹 端點 3：修改指定日誌 
                    if (logId == null) {
                        statusCode = 400;
                        response = "{\"error\":\"Missing log ID\"}";
                    } else {
                        InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                        BufferedReader br = new BufferedReader(isr);
                        String body = br.lines().collect(Collectors.joining());

                        String logDate = body.split("\"log_date\":\"")[1].split("\"")[0];
                        double sleep = Double.parseDouble(body.split("\"sleep_hours\":")[1].split(",")[0].trim());
                        int steps = Integer.parseInt(body.split("\"steps\":")[1].split(",")[0].trim());
                        int mood = Integer.parseInt(body.split("\"mood_score\":")[1].split("}")[0].trim());

                        // 修改時同樣重新透過決策樹更新風險等級
                        String risk = calculateRiskLevel(sleep, steps, mood);

                        String sql = "UPDATE health_logs SET log_date=?, sleep_hours=?, steps=?, mood_score=?, risk_level=? WHERE id=?";
                        PreparedStatement pstmt = conn.prepareStatement(sql);
                        pstmt.setString(1, logDate);
                        pstmt.setDouble(2, sleep);
                        pstmt.setInt(3, steps);
                        pstmt.setInt(4, mood);
                        pstmt.setString(5, risk);
                        pstmt.setInt(6, logId);
                        
                        int updatedRows = pstmt.executeUpdate();
                        if (updatedRows > 0) {
                            response = "{\"status\":\"success\",\"message\":\"Updated successfully with risk: " + risk + "\"}";
                        } else {
                            statusCode = 404;
                            response = "{\"error\":\"Log not found\"}";
                        }
                    }

                } else if ("DELETE".equalsIgnoreCase(method)) {
                    // 🔹 端點 4：刪除指定日誌 
                    if (logId == null) {
                        statusCode = 400;
                        response = "{\"error\":\"Missing log ID\"}";
                    } else {
                        String sql = "DELETE FROM health_logs WHERE id=?";
                        PreparedStatement pstmt = conn.prepareStatement(sql);
                        pstmt.setInt(1, logId);
                        
                        int deletedRows = pstmt.executeUpdate();
                        if (deletedRows > 0) {
                            response = "{\"status\":\"success\",\"message\":\"Log deleted successfully\"}";
                        } else {
                            statusCode = 404;
                            response = "{\"error\":\"Log not found\"}";
                        }
                    }
                }

            } catch (Exception e) {
                statusCode = 500;
                response = "{\"error\":\"" + e.getMessage() + "\"}";
                e.printStackTrace();
            }

            // 發送回應
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            byte[] bytes = response.getBytes("utf-8");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }

    /**
     * 處理 /health-logs/risk 的路由 (端點 5：獲取最新風險評估結果) 
     */
    static class RiskHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
            
            String response = "";
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                String sql = "SELECT sleep_hours, steps, mood_score FROM health_logs ORDER BY id DESC LIMIT 1";
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql);
                
                if (rs.next()) {
                    double sleep = rs.getDouble("sleep_hours");
                    int steps = rs.getInt("steps");
                    int mood = rs.getInt("mood_score");
                    String currentRisk = calculateRiskLevel(sleep, steps, mood);
                    response = "{\"current_risk\":\"" + currentRisk + "\"}";
                } else {
                    response = "{\"current_risk\":\"None\"}";
                }
            } catch (Exception e) {
                response = "{\"error\":\"" + e.getMessage() + "\"}";
            }

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            byte[] bytes = response.getBytes("utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }
}
