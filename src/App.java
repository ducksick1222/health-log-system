import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.InetSocketAddress;
import java.sql.*;
import java.util.stream.Collectors;

public class App {
//  新寫法：加上 .trim() 自動刪除可能存在的隱形空格
private static final String DB_URL = System.getenv("DB_URL") != null ? System.getenv("DB_URL").trim() : null;
private static final String DB_USER = System.getenv("DB_USER") != null ? System.getenv("DB_USER").trim() : null;
private static final String DB_PASSWORD = System.getenv("DB_PASSWORD") != null ? System.getenv("DB_PASSWORD").trim() : null;
    public static void main(String[] args) throws Exception {
    // 1. 動態讀取 Render 分配的 PORT，如果沒有（在地端環境）則預設為 8080
    String portStr = System.getenv("PORT");
    int port = (portStr != null) ? Integer.parseInt(portStr) : 8080;

    // 2. 使用動態的 port 建立伺服器
    HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
    System.out.println("🚀 後端伺服器已啟動，正在監聽連接埠: " + port);

    // // 2. 路由註冊（對應題目要求的 API 端點）
    server.createContext("/health-logs", new HealthLogHandler());

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

           // 💡 判斷路徑結尾是不是數字，如果是，才切出 ID
            Integer logId = null;
            if (path.matches("/health-logs/\\d+")) {
                logId = Integer.parseInt(path.substring(13)); // 13 是 "/health-logs/" 的長度
            }
try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                
                // 優先攔截 🔹 端點 5：獲取最新風險評估結果
                if ("/health-logs/risk".equals(path) && "GET".equalsIgnoreCase(method)) {
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
                } 
                // 🔹 端點 1：取得所有日誌 
                else if ("/health-logs".equals(path) && "GET".equalsIgnoreCase(method)) {
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
                } 
                // 🔹 端點 2：新增健康日誌
                else if ("/health-logs".equals(path) && "POST".equalsIgnoreCase(method)) {
                    // (把你的 POST 字串解析跟 INSERT 邏輯原封不動貼在這裡，也就是原本 POST 裡面的東西)
                    InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                    BufferedReader br = new BufferedReader(isr);
                    String body = br.lines().collect(Collectors.joining());

                    String logDate = body.split("\"log_date\":\"")[1].split("\"")[0];
                    double sleep = Double.parseDouble(body.split("\"sleep_hours\":")[1].split(",")[0].trim());
                    int steps = Integer.parseInt(body.split("\"steps\":")[1].split(",")[0].trim());
                    int mood = Integer.parseInt(body.split("\"mood_score\":")[1].split("}")[0].trim());

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
                } 
                // 🔹 端點 3：修改指定日誌
                else if (logId != null && "PUT".equalsIgnoreCase(method)) {
                    // (把你的 PUT 邏輯原封不動貼在這裡)
                    InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                    BufferedReader br = new BufferedReader(isr);
                    String body = br.lines().collect(Collectors.joining());

                    String logDate = body.split("\"log_date\":\"")[1].split("\"")[0];
                    double sleep = Double.parseDouble(body.split("\"sleep_hours\":")[1].split(",")[0].trim());
                    int steps = Integer.parseInt(body.split("\"steps\":")[1].split(",")[0].trim());
                    int mood = Integer.parseInt(body.split("\"mood_score\":")[1].split("}")[0].trim());

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
                // 🔹 端點 4：刪除指定日誌
                else if (logId != null && "DELETE".equalsIgnoreCase(method)) {
                    // (把你的 DELETE 邏輯原封不動貼在這裡)
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
                // 🔹 防呆：不是以上端點，直接回傳 404
                else {
                    statusCode = 404;
                    response = "{\"error\":\"API Route Not Found\"}";
                }

            } catch (Exception e) {
            // 下方維持原樣
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
   
}
