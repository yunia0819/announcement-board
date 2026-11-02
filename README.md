# 公告管理系統

依技術測驗需求實作的 Spring MVC＋Hibernate 公告管理程式，使用 Bootstrap 與 Thymeleaf 顯示畫面，可在本機執行，也可打包 WAR 部署至 CentOS 的外部 Tomcat。

線上展示：[公告管理網站](http://35.221.234.22:8080/announcements)。目前部署環境為 Google Cloud、CentOS Stream 10、Java 21、Tomcat 11 與 MySQL 8.4。

原始碼上傳與測驗回覆步驟，請依照 [GitHub 原始碼與網站交件教學](docs/github-submission.md)。使用 Windows 開發時，可繼續透過 IntelliJ 編輯與建置專案。

使用 Google Cloud 免費試用時，請依 [Google Cloud CentOS 部署教學](docs/google-cloud-deployment.md) 建立主機，透過瀏覽器 SSH 上傳及部署網站。

## 功能

- 公告列表：標題、發布日期、截止日期，後端分頁，每頁 10 筆；支援發布日期由新到舊／由舊到新排序與指定發布月份篩選。
- 新增、修改、刪除與查看公告；修改時帶入既有資料。
- 公告內文提供精簡文字編輯器：14–32 px 字級、粗體／斜體／底線／刪除線與文字顏色；既有公告已儲存的排版仍會保留。
- 標題、公布者、日期與內容的必填及長度檢查；截止日期不得早於發布日期。
- 上傳 PDF／PNG／JPEG 附件，單檔最多 5 MB；提供下載、保留、替換與移除操作。
- CSRF 保護、HTML 輸出跳脫、刪除確認及操作結果提示。
- MySQL schema 由 Flyway 管理；可選擇建立展示分頁用的範例資料。

這是沒有登入流程的測驗展示程式，能存取網站的人可操作公告。

前端採用固定明亮的「墨黑極簡」風格：黑白灰配色、方形按鈕、單欄公告表格與留白。手機版將每筆公告的標題、日期與操作直向排列。共用樣式位於 `src/main/resources/static/css/app.css`，頁面位於 `src/main/resources/templates`。

列表的「發布日期排序」與「發布月份」可同時使用，選好後按「套用」會從第一頁顯示結果；月份留白表示全部月份，「清除」可回到預設排序。分頁與公告新增／修改／刪除後會保留條件。網址例如 `/announcements?sort=oldest&month=2026-10`；月份以發布日期為準，接受 1000 至 9999 年的 `yyyy-MM` 格式。

## 技術與分層

| 項目 | 技術 |
| --- | --- |
| 執行環境 | Java 21 |
| Web | Spring Boot 4.1.2、Spring MVC、Thymeleaf |
| 資料存取 | JPA、Hibernate ORM、Spring Data JPA |
| 資料庫 | 正式環境 MySQL 8.4；本機開發 H2 |
| 前端 | Bootstrap 5、Quill 2.0.3、CSS、JavaScript |
| 內文處理 | jsoup 1.23.2 白名單清理 |
| 建置及部署 | Maven、可執行 WAR、外部 Tomcat 11 |

```text
Controller → Service → Repository → Hibernate → MySQL
                 ↘ 檔案儲存服務 → 外部附件目錄
```

Controller 處理請求與畫面，Service 集中處理資料規則與交易，Repository 透過 Hibernate 存取資料。附件不放在 WAR 內，重新部署不會清除上傳檔案。

內文最多 10,000 字，格式資料最多 100,000 字元。新增的 `content_html` 欄位保存清理過的格式內容，`content` 保留由 HTML 重新產生的純文字。既有公告沒有格式內容時，繼續以純文字與原有換行顯示。Flyway 的 V2 migration 會在啟動時自動加入欄位。

編輯器的程式與樣式放在 `static/vendor/quill`，使用 [Quill 官方發佈的 2.0.3 版](https://quilljs.com/docs/installation)，隨附 BSD 授權文件，不需編輯器雲端帳號或 API key。內文僅接受指定標籤與字級／顏色 class，儲存與顯示時皆使用 [jsoup Cleaner](https://jsoup.org/apidocs/org/jsoup/safety/Cleaner) 清理；不接受任意樣式、腳本、圖片或嵌入網頁。

## 本機執行

先安裝 Java 21。以下使用 Maven Wrapper；也可用 Maven 3.9.x 的 `mvn` 取代 `bash mvnw` 或 `mvnw.cmd`。首次建置需要連線下載 Maven 與相依套件。Linux／macOS 使用 `bash mvnw`，不依賴 Git checkout 後的執行權限。

Windows PowerShell：

```powershell
.\mvnw.cmd clean verify
java -jar target\announcements.war
```

Linux／macOS：

```bash
bash mvnw clean verify
java -jar target/announcements.war
```

開啟 <http://localhost:8080/announcements>。預設 `dev` profile 使用 `./var/dev-db` 的 H2 檔案資料庫，關閉後仍保留資料，附件使用應用程式設定的外部目錄。資料庫與附件產物不應提交到 Git。

需要展示分頁資料時，啟動 `dev,demo`：

```bash
java -jar target/announcements.war --spring.profiles.active=dev,demo
```

`demo` 只在資料庫沒有公告時建立 23 筆資料，不應在一般正式啟動時加入。

## 本機使用 MySQL

先建立 MySQL 8.4 資料庫與專用帳號，步驟見 [CentOS 部署說明](docs/centos-deployment.md)。Flyway 會在啟動時建立資料表，不需手動執行 schema SQL。

PowerShell 範例：

```powershell
$env:SPRING_PROFILES_ACTIVE = 'mysql'
$env:DB_URL = 'jdbc:mysql://127.0.0.1:3306/announcements?connectionTimeZone=%2B08:00&forceConnectionTimeZoneToSession=true'
$env:DB_USERNAME = 'announcement_app'
# 在目前終端機互動輸入密碼，避免把密碼寫入指令歷程。
$dbCredential = Get-Credential -UserName 'announcement_app' -Message '輸入本機 MySQL 帳號密碼'
$env:DB_PASSWORD = $dbCredential.GetNetworkCredential().Password
$env:APP_UPLOAD_DIR = Join-Path $PWD 'var\uploads'
java -jar target\announcements.war
```

連線設定適用單機 MySQL／Tomcat 部署。JDBC 使用台灣的固定 `+08:00` 時差（URL 中以 `%2B08:00` 表示），不依賴 MySQL 額外載入具名時區資料表。MySQL 放在其他主機時，請依該環境的 TLS 與帳號權限配置調整 JDBC URL。

## 環境設定

| 變數 | 說明 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` 或 `mysql`；需建立範例資料時另加 `demo` |
| `DB_URL` | MySQL JDBC URL，僅 `mysql` profile 使用 |
| `DB_USERNAME` | MySQL 專用帳號 |
| `DB_PASSWORD` | MySQL 密碼，正式設定必須提供 |
| `APP_UPLOAD_DIR` | 附件實體儲存目錄；服務帳號需有寫入權限 |

不要將密碼寫入程式碼、GitHub、WAR 或截圖；CentOS 使用獨立且限權限的 environment file。

## 建置、測試與交付

```bash
bash mvnw clean verify
```

產物為 `target/announcements.war`。已配置 GitHub Actions 使用 Java 21 執行 `verify` 並保存 WAR；推送後可在 repository 的 Actions 頁面查看該次執行結果。

### 已驗證的範圍

- Java 21 的整合測試通過，成功打包 WAR，並以 `java -jar` 啟動可執行 WAR。
- 實際 HTTP multipart 請求：有效的小附件儲存後回傳重新導向（302），超過 5 MiB 的上傳回傳 413。
- `dev` profile 的 H2 檔案資料庫正常建立，`demo` profile 正常建立 23 筆展示公告。
- 以瀏覽器檢視公告列表與新增表單，畫面正常顯示。
- 兩支部署腳本通過 Git Bash 的 `bash -n` 語法檢查。

另外已在 Windows 的外部 Tomcat 11.0.27 以 `ROOT.war`、`unpackWARs=false` 與 `ExtractingRoot` 成功啟動，驗證健康端點、公告新增、附件下載內容及刪除後的附件清理。完整記錄見 [驗證結果](docs/verification.md)。

2026-11-02 已部署至 Google Cloud 的 CentOS Stream 10 主機；主機上的 MySQL 與網站服務正常啟動，健康檢查回報 `UP`。從公開網址讀取公告列表與健康端點均回應 HTTP 200。雲端完整功能及資料保存的手動驗收步驟，見 [Google Cloud 部署教學](docs/google-cloud-deployment.md)；各項實際驗證範圍見 [驗證結果](docs/verification.md)。

CentOS 的完整指令、MySQL 初始化、systemd 設定、更新及問題排查見 [CentOS Stream 10 部署文件](docs/centos-deployment.md)。

交付時提供 GitHub repository 與網站網址。若部署成 `ROOT.war`，公告列表網址為 `http://主機IP:8080/announcements`；若保留 `announcements.war` 檔名，則是 `http://主機IP:8080/announcements/announcements`。

## 目錄

```text
src/main/java/             Controller、Service、Entity、Repository 與設定
src/main/resources/        畫面、靜態資源、profile 設定與 Flyway migration
src/test/                  自動測試
deploy/                    Tomcat 安裝、WAR 部署與環境設定範例
docs/centos-deployment.md   CentOS 完整部署說明
docs/google-cloud-deployment.md  Google Cloud 主機建立、上傳與部署教學
docs/github-submission.md  Windows 上傳 GitHub、下載原始碼與回信交件
.github/workflows/          Java 21 建置與測試
```
