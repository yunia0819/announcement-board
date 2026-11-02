# 公告管理系統

依技術測驗需求實作的 Spring MVC＋Hibernate 公告管理程式，使用 Bootstrap 與 Thymeleaf 顯示畫面，可在本機執行，也可打包 WAR 部署至 CentOS 的外部 Tomcat。

線上展示：[公告管理網站](http://35.221.234.22:8080/announcements)。目前部署環境為 Google Cloud、CentOS Stream 10、Java 21、Tomcat 11 與 MySQL 8.4。


## 功能

- 公告列表：標題、發布日期、截止日期，後端分頁，每頁 10 筆；支援發布日期由新到舊／由舊到新排序與指定發布月份篩選。
- 新增、修改、刪除與查看公告；修改時帶入既有資料。
- 公告內文提供精簡文字編輯器：14–32 px 字級、粗體／斜體／底線／刪除線與文字顏色；既有公告已儲存的排版仍會保留。
- 標題、公布者、日期與內容的必填及長度檢查；截止日期不得早於發布日期。
- 上傳 PDF／PNG／JPEG 附件，單檔最多 5 MB；提供下載、保留、替換與移除操作。
- CSRF 保護、HTML 輸出跳脫、刪除確認及操作結果提示。
- MySQL schema 由 Flyway 管理；可選擇建立展示分頁用的範例資料。

這是沒有登入流程的測驗展示程式，能存取網站的人可操作公告。

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
