# 驗證記錄

初次驗證日期：2026/10/27；排序、月份篩選與文字編輯器更新驗證：2026/10/30。環境：Windows 11、Java 21.0.3、Maven 3.9.9、Spring Boot 4.1.2、Hibernate ORM 7.4.13.Final、H2 2.4.240。

## 建置與整合測試

執行 Maven `verify`，結果為 **BUILD SUCCESS**，28 項測試、0 失敗、0 錯誤。

測試串接真實的 Spring MVC、Thymeleaf、Security、Hibernate、Flyway 與 H2，涵蓋：

- 中文公告新增、讀取、修改與刪除。
- 必填、長度、日期先後、錯誤日期與 MySQL DATE 年份範圍。
- 日期 1000 與 9999 年邊界的 SQL 儲存字串、JDBC 4.2 讀值與 Hibernate 往返。
- 發布日期雙向排序、同日 ID 穩定排序、刪除最後頁唯一資料與極端頁碼校正。
- 發布月份篩選、閏年二月、跨年及 1000／9999 年邊界；總數與分頁只計算符合月份的公告。
- 分頁、詳情、表單驗證與新增／修改／刪除後保留排序及月份；非法篩選參數回傳 400。
- 不存在的公告／附件回傳 404，缺少 CSRF 的寫入回傳 403。
- HTML 輸出跳脫、外部 context path 的連結與 CSRF 欄位。
- 富文字新增／修改／顯示、字級／顏色／對齊 class 保留，儲存與顯示時再次清理不安全 HTML。
- 由清理後 HTML 產生純文字、拒絕空段落與空白內文、10,000 可見字及 100,000 格式字元限制。
- 舊純文字的字面 HTML 與換行保留；表單其他欄位驗證失敗時保留安全的格式內容。
- PDF 上傳、下載內容、更換、移除與實體檔案清理。
- 偽裝附件與超過 5 MiB 的附件拒絕。
- 新增／更換附件在資料庫交易回滾後的檔案一致性。
- 公開健康端點且不揭露資料庫連線資訊。

日期使用直接 JDBC 4.2 `java.time` 傳輸，避免舊 `java.sql.Date` 路徑的古代日期曆法轉換。設定為 `hibernate.type.java_time_use_direct_jdbc=true`，是本專案針對新程式與所用驅動的選擇。[Hibernate 日期與 JDBC 說明](https://docs.hibernate.org/stable/orm/introduction/html_single/)

## 真實 HTTP 與瀏覽器

可執行 WAR 已以 `java -jar` 啟動，H2 檔案資料庫、Flyway 初始化及 `dev,demo` 的 23 筆資料正常建立。瀏覽器確認 Bootstrap 公告列表與新增表單正常顯示。

2026/10/30 另以獨立的 H2 記憶體資料庫確認固定明亮模式、移除頁尾、排序與月份篩選、空月份提示，以及 320px 手機寬度下的欄位排列。

文字編輯器使用本機 Quill 2.0.3。瀏覽器實測選取文字後套用 24 px、紅色、粗體與置中，日期驗證失敗後格式仍保留；儲存後詳情頁與再次修改皆顯示相同格式。另確認清單對齊、背景標示、復原／重做及 320px 工具列無水平溢出，瀏覽器沒有 CSP 或 JavaScript 錯誤。字級與顏色使用固定 CSS class，沒有放寬既有 CSP。

工具列後續精簡為字級、粗體／斜體／底線／刪除線及文字顏色，其他排版控制已移除；既有公告的格式讀取仍保留。本次精簡完成 JavaScript 語法檢查與 WAR 重新打包，未重複執行後端整合測試。

使用實際 HTTP cookie 與表單隱藏 CSRF token 送出 multipart：

- 小型 PDF 上傳成功，回傳 302 導回列表。
- 超過 5 MiB 的真實 multipart 請求回傳 413。這項驗證會經過容器的 multipart 解析，補足 MockMvc 不執行該解析流程的限制。

## 外部 Tomcat

從 Apache 官方下載 Tomcat 11.0.27 ZIP，驗證官方 SHA-512 後，在 Windows 上建立獨立的 CATALINA_BASE，部署相同 `announcements.war` 為 `ROOT.war`，使用 H2 檔案資料庫。

驗證設定為 `unpackWARs=false`、`autoDeploy=false`，並於 Context 設定 `org.apache.catalina.webresources.ExtractingRoot`。本機啟動約 15 秒，健康狀態為 UP；實際耗時依主機而異。

驗證結果：新增公告 302、詳情頁 200、PDF 下載位元組一致、刪除公告 302、刪除後附件回傳 404、外部附件目錄剩餘測試檔案數為 0。

`ExtractingRoot` 將套件暫存於可寫的 work 目錄，以改善壓縮 WAR 的套件掃描；網站 WAR 目錄仍可維持唯讀。[Tomcat 官方說明](https://tomcat.apache.org/tomcat-11.0-doc/config/resources.html)

## 部署驗證的界線

兩支 Bash 部署腳本通過 `bash -n` 語法檢查。上述本機整合測試與 Windows 外部 Tomcat 驗證，和下方雲端部署確認的環境及範圍不同，應分別閱讀。

## Google Cloud 部署確認（2026/11/02）

使用者在 Google Cloud VM 的 SSH 中提供的執行結果確認：

- 作業系統為 CentOS Stream 10。
- MySQL 8.4 的 `mysqld` 服務為 `active (running)`。
- 網站的 `announcement-board` 服務為 `active (running)`。
- 主機上的健康檢查回傳 `{"status":"UP"}`。

另以公開網址進行唯讀 HTTP 檢查，確認下列端點皆回傳 HTTP 200：

- [公告列表](http://35.221.234.22:8080/announcements)。
- [健康端點](http://35.221.234.22:8080/actuator/health)。

這些結果確認雲端服務已啟動，且公告列表與健康端點可由外部連線。完整的雲端公告新增／修改／刪除、分頁與篩選、文字格式、附件上傳／下載，以及重啟後資料與附件保留，已提供操作驗收步驟，但未在這次雲端確認中逐項獨立重跑，不列為已全部通過。請依 [Google Cloud 部署教學的外部驗收步驟](google-cloud-deployment.md#11-用外部網路驗收網站) 完成人工驗收。
