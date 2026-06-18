# 智慧健康日誌與風險評估系統 (Smart Health Log & Risk Assessment System)

本專案為大數據與智慧計算期末專題成果，建構了一個結合 **Java 後端伺服器**、**MySQL 雲端資料庫**、以及 **前端響應式網頁** 的全端健康日記系統。核心功能導入了**機器學習決策樹 (Decision Tree)** 演算法，針對使用者的每日睡眠、步數與心情進行即時健康風險狀態評估。

## 🌐 雲端部署連結
* **前端網頁 (GitHub Pages):** [點此瀏覽系統網頁](https://ducksick1222.github.io/health-log-system/)
* **後端 API 服務 (Render):** `https://health-log-system-0603.onrender.com/health-logs`
* **雲端資料庫 (Clever Cloud):** MySQL 8.0 執行環境

---

## 🛠️ 核心架構與技術棧

### 1. 前端網頁 (Frontend)
* **技術語系：** HTML5, CSS3, Vanilla JavaScript (ES6+)
* **介面設計：** 採用簡約現代風的響應式卡片佈局 (Responsive Card Layout)，完美適配電腦與手機螢幕。
* **資料視覺化：** 串接雲端 API，即時異步 (Async/Await Fetch) 渲染歷史 90 天健康趨勢列表。

### 2. 後端微服務 (Backend)
* **核心語言：** Java 21 / 基礎建構環境：Docker (eclipse-temurin:21)
* **網路通訊：** 使用 Java 原生 `com.sun.net.httpserver.HttpServer` 搭建輕量級 RESTful API 伺服器。
* **演算法實作：** 內嵌硬編碼自建**決策樹分析邏輯 (Decision Tree Classifier)**，根據資訊增益 (Information Gain) 核心原則篩選特徵，對每日數據進行 `High` / `Medium` / `Low` 三級風險即時預測。
* **安全性機制：** 完整實作跨來源資源共享 (CORS) 握手機制，允許前端跨網域安全存取。

### 3. 雲端資料庫 (Database)
* **資料庫系統：** 遠端 MySQL 關係型資料庫 (Hosted on Clever Cloud, Paris Cluster)。
* **驅動程式：** 整合 `mysql-connector-j-9.7.0.jar`，並全面啟用跨洲公網 SSL 加密傳輸安全連線。
* **資料結構：** 內含自動遞增主鍵、嚴格型別約束，並預灌錄 90 天具規律性與雜訊模擬的機器學習標準訓練數據。

---

## 📊 資料庫表結構 (Schema)

```sql
CREATE TABLE health_logs (
    id INT PRIMARY KEY AUTO_INCREMENT,
    log_date DATE NOT NULL,
    sleep_hours REAL NOT NULL,
    steps INT NOT NULL,
    mood_score INT NOT NULL,
    risk_level VARCHAR(20)
);
