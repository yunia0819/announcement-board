# CentOS Stream 10 部署說明

本文件部署 Java 21、MySQL 8.4、外部 Tomcat 11 與 `ROOT.war`，網址為 `http://主機IP:8080/announcements`。需要一台可 SSH 登入、有 sudo 權限的 CentOS Stream 10 主機。本專案提供程式及部署資源，尚未代為登入或配置實際遠端主機。

CentOS Stream 10 的 x86_64 映像需要 x86-64-v3 CPU。官方列有 OpenJDK 21 與 MySQL 8.4；這份腳本專為 Stream 10 撰寫，不直接套用到 CentOS Linux 7／8。[CentOS 官方發行說明](https://www.centos.org/centos10/)

Tomcat 安裝腳本預設固定 `11.0.27`，於 2026/10/27 由官方下載頁查核，並驗證官方 SHA-512。日後可先查閱官方下載頁及安全更新，再用 `TOMCAT_VERSION=11.0.x` 指定更新版本。[Tomcat 官方下載與驗證說明](https://tomcat.apache.org/download-11.cgi)

## 1. 建置與上傳

在開發電腦安裝 Java 21 後執行：

```powershell
.\mvnw.cmd clean verify
```

Linux 則執行 `bash mvnw clean verify`，不依賴 checkout 後的執行權限。產生 `target/announcements.war` 後，上傳 WAR 及整個 `deploy` 目錄；請把範例登入名稱與主機位置換成實際資料：

```powershell
ssh user@SERVER_IP "mkdir -p ~/announcement-release"
scp target/announcements.war user@SERVER_IP:~/announcement-release/
scp -r deploy user@SERVER_IP:~/announcement-release/
```

也可以在 CentOS clone 自己的 GitHub repository 並建置。此時另安裝 Maven：`sudo dnf install -y maven`，確認 `java -version` 為 21，再執行 `mvn clean verify`。

## 2. 安裝 Tomcat 與 Java

SSH 登入後：

```bash
cd ~/announcement-release
sudo bash deploy/install-tomcat.sh
```

安裝腳本會下載 Tomcat、驗證 checksum、建立專用 `tomcat` 服務帳號與 systemd 服務，但不會在資料庫設定完成前啟動網站。首次安裝後檢查 Java：

```bash
/usr/lib/jvm/jre-21-openjdk/bin/java -version
```

安裝腳本只管理帶有標記的 `/opt/announcement-board` 與 `/var/lib/announcement-board`；遇到同名且非本專案建立的目錄會停止。重新執行會保留既有環境設定、Tomcat 設定與附件。升級 Tomcat 時會停止這個專用服務，請隨後重新部署或啟動，並依官方遷移說明檢查原有 `conf`。

| 路徑 | 用途 |
| --- | --- |
| `/opt/announcement-board/apache-tomcat-11.0.27` | Tomcat 程式，root 管理 |
| `/opt/announcement-board/tomcat-current` | 指向使用中的 Tomcat 版本 |
| `/var/lib/announcement-board/tomcat/conf` | 專案 Tomcat 設定 |
| `/var/lib/announcement-board/tomcat/webapps/ROOT.war` | 部署檔案 |
| `/var/lib/announcement-board/tomcat/logs` | Tomcat access log 等檔案 |
| `/var/lib/announcement-board/uploads` | 公告附件，重新部署時保留 |
| `/etc/announcement-board/announcement.env` | MySQL 與應用程式設定 |
| `/var/backups/announcement-board` | 更新前保留的 WAR |

## 3. 初始化 MySQL

```bash
sudo dnf install -y mysql8.4-server
sudo systemctl enable --now mysqld
sudo mysql_secure_installation
```

依安裝工具提示設定 root 密碼與初始安全設定。若安裝版本產生初始暫時密碼，先從 `sudo journalctl -u mysqld` 或 `/var/log/mysqld.log` 查詢，再依提示修改。完成後使用互動式密碼提示登入：

```bash
mysql -u root -p
```

在 MySQL 提示字元建立資料庫及專用帳號；`替換為一組獨立強密碼` 是占位文字，執行前換成實際密碼：

```sql
CREATE DATABASE IF NOT EXISTS announcements
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'announcement_app'@'127.0.0.1'
  IDENTIFIED BY '替換為一組獨立強密碼';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES
  ON announcements.* TO 'announcement_app'@'127.0.0.1';
EXIT;
```

`CREATE USER IF NOT EXISTS` 不會更新既有帳號密碼；帳號已存在且要更改密碼時，另外執行 `ALTER USER 'announcement_app'@'127.0.0.1' IDENTIFIED BY '新密碼';`。這些 schema 權限供 Flyway migration 使用，應用程式不使用 root 登入。

同機部署建議讓 MySQL 只監聽 loopback。在 `/etc/my.cnf.d/announcement-bind.cnf` 加入：

```ini
[mysqld]
bind-address=127.0.0.1
```

先用 `sudoedit /etc/my.cnf.d/announcement-bind.cnf` 編輯，再執行 `sudo systemctl restart mysqld`。確認資料庫帳號能透過 TCP 登入：

```bash
mysql -h 127.0.0.1 -u announcement_app -p announcements
```

Flyway 會在應用程式第一次啟動時建立資料表及歷史記錄，無需手動建立公告表。

## 4. 填寫應用程式環境設定

```bash
sudoedit /etc/announcement-board/announcement.env
```

檔案來自 `deploy/announcement.env.example`，內容大致如下：

```ini
SPRING_PROFILES_ACTIVE=mysql
DB_URL="jdbc:mysql://127.0.0.1:3306/announcements?connectionTimeZone=%2B08:00&forceConnectionTimeZoneToSession=true"
DB_USERNAME=announcement_app
DB_PASSWORD="與剛才建立帳號相同的密碼"
APP_UPLOAD_DIR=/var/lib/announcement-board/uploads
JAVA_OPTS="-Xms128m -Xmx512m -Djava.awt.headless=true -Duser.timezone=Asia/Taipei"
```

這是 systemd `EnvironmentFile`，不加 `export`，不執行 shell 展開。JDBC 的 `%2B08:00` 是 URL 編碼後的 `+08:00`，不依賴 MySQL 尚未載入的具名時區資料表；Java 仍以 `Asia/Taipei` 顯示及處理本地時間。密碼若含雙引號或反斜線，請依 systemd environment file 語法跳脫。設定完成後確認權限：

```bash
sudo chown root:tomcat /etc/announcement-board/announcement.env
sudo chmod 0640 /etc/announcement-board/announcement.env
```

若要初次建立 23 筆展示資料，可暫時使用 `SPRING_PROFILES_ACTIVE=mysql,demo`。啟動成功後改回 `mysql` 並重啟，避免日後清空資料庫時自動重新產生展示內容。

## 5. 部署 WAR 與驗證

```bash
sudo bash deploy/deploy-war.sh "$PWD/announcements.war"
sudo systemctl status announcement-board --no-pager
curl --fail http://127.0.0.1:8080/actuator/health
```

健康端點應回傳 `{"status":"UP"}`。部署腳本會先驗證 WAR 格式、保存上一版 WAR、停止服務，再替換 `ROOT.war`、啟動及檢查健康狀態。預設等候約 180 秒，讓首次 Flyway migration 與相依套件掃描有時間完成；可用 `sudo HEALTH_TIMEOUT_SECONDS=300 bash deploy/deploy-war.sh "$PWD/announcements.war"` 調整，允許範圍為 30–900 秒。資料庫或附件不會被清除，服務不健康時腳本回傳失敗並保留現況供排查。

這個實例關閉 Tomcat 熱部署及 WAR 自動解壓縮，所以更新時不會因舊的 `ROOT/` 解壓縮目錄而繼續讀到舊版檔案。

為避免壓縮 WAR 中的巢狀 JAR 在啟動掃描時反覆解壓縮，首次安裝會在 `/var/lib/announcement-board/tomcat/conf/context.xml` 的 `<Context>` 內加入：

```xml
<Resources className="org.apache.catalina.webresources.ExtractingRoot" />
```

`ExtractingRoot` 會把 `WEB-INF/lib` 的 JAR 放到應用程式 `work` 下的 `application-jars` 目錄，並於應用程式停止時清除；systemd 已允許 tomcat 帳號寫入 `work`，`webapps` 仍維持唯讀。這可降低大型 Spring Boot WAR 的啟動掃描成本。[Tomcat 官方 Resources 說明](https://tomcat.apache.org/tomcat-11.0-doc/config/resources.html)

重新執行安裝腳本會保留既有 `conf`，不自動覆寫自訂 `<Resources>`；若收到提醒，請先備份並用 `sudoedit` 檢查 `context.xml`。沒有 `<Resources>` 時可加入上述設定；已有自訂資源時，需人工確認 className 與原有子設定如何保留，不要再加第二個 `<Resources>`。儲存後重啟 `announcement-board`。

## 6. 允許外部連線

```bash
sudo dnf install -y firewalld
sudo systemctl enable --now firewalld
sudo firewall-cmd --permanent --add-port=8080/tcp
sudo firewall-cmd --reload
```

也要在雲端主機的 security group／網路防火牆允許 TCP 8080，依展示需求限制來源。MySQL 的 3306 不需要對外開放。

瀏覽器開啟：

```text
http://SERVER_IP:8080/announcements
```

此為測驗的直接 Tomcat 展示方式。需要正式網域或 HTTPS 時，可另外設定反向代理與憑證；目前提供的腳本不含 DNS／TLS 配置。

保留 SELinux 啟用狀態，不必執行 `setenforce 0`。若遇到拒絕，先檢查 `sudo ausearch -m AVC -ts recent`，依實際 audit 訊息調整檔案標籤或政策。腳本會在部署時對管理目錄執行 `restorecon`，不修改全域 SELinux 政策。

## 7. 更新、備份與還原

再次上傳新 WAR，執行相同的 `deploy-war.sh`。環境設定變更後重啟服務：

```bash
sudo systemctl restart announcement-board
```

部署腳本只備份 WAR。更新 schema 前另外備份 MySQL，並備份附件；下面使用 MySQL 互動輸入密碼：

```bash
mysqldump -u root -p --single-transaction --no-tablespaces announcements > announcements-backup.sql
sudo tar -czf announcements-uploads-backup.tar.gz -C /var/lib/announcement-board uploads
```

要還原舊 WAR，可把 `/var/backups/announcement-board/` 中的檔案重新交給部署腳本。schema migration 不會自動倒退；若版本不相容，需按備份及 migration 設計一起還原資料庫和附件。備份內含資料與檔案，請限制權限並另存安全位置。

## 8. 常見問題

| 現象 | 檢查方式 |
| --- | --- |
| 啟動失敗或健康檢查逾時 | `sudo journalctl -u announcement-board -n 150 --no-pager` |
| Access denied for user | 核對 environment file 密碼、MySQL 使用者 host 是否為 `127.0.0.1` |
| Flyway migration 失敗 | 查看日誌與 migration 版本；不要任意刪除 `flyway_schema_history` |
| 附件無法寫入 | 檢查 uploads 擁有者為 tomcat、目錄權限，以及 SELinux audit |
| 本機可開，外部連不上 | 檢查 `sudo ss -ltnp`、firewalld 及雲端 security group |
| 網站 404 | 確認 WAR 部署為 `ROOT.war`，列表路徑為 `/announcements` |
| HTTP 403 | 表單需要 CSRF token；請由網站表單操作，過期時重新整理 |
| 8080 被占用 | 停止衝突的服務，或同步修改 Tomcat `conf/server.xml`、健康檢查與防火牆埠號 |

交付前應在這台實際主機驗證中文 CRUD、跨頁、最後一筆刪除、附件上下載與服務重啟後資料仍存在，再提供網站網址給面試方。
