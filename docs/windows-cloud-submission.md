# Windows 開發、GitHub 上傳與 CentOS 雲端交付

> **本次交付已使用 Google Cloud，網站為 <http://35.221.234.22:8080/announcements>。** GitHub 上傳與回覆作業請依 [GitHub 原始碼上傳與交付指南](github-submission.md)；目前主機的部署方式見 [Google Cloud 部署教學](google-cloud-deployment.md)。本頁保留 AWS 替代方案，本次交付不必再建立 AWS 主機。

你可以繼續使用 Windows 與 IntelliJ，不必更換電腦的作業系統。Windows 負責編輯、測試及打包；雲端 CentOS 主機負責持續執行網站與 MySQL，讓評測者透過網址操作。

本指南以 **AWS 東京區域的 CentOS Stream 10、Java 21、Tomcat 11、同機 MySQL 8.4** 為替代部署範例，供之後另選 AWS 時參考；本專案未依本頁建立 AWS 主機。

## 1. 在 Windows 建置專案

在 IntelliJ 的 Terminal 使用 PowerShell，切換至包含 `pom.xml` 的專案根目錄：

```powershell
Set-Location -LiteralPath 'C:\你的專案路徑\碩網資訊'
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
.\mvnw.cmd clean verify
```

`JAVA_HOME` 請依實際 Java 21 安裝位置調整。看到 `BUILD SUCCESS` 後，部署檔為 `target/announcements.war`。WAR 已包含 Java 程式、HTML、CSS 與 JavaScript。

## 2. 把原始碼上傳自己的 GitHub

以 `yunia0819` 帳號登入 [GitHub 建立 repository 頁面](https://github.com/new)。建議名稱為 `announcement-board`，此名稱是建議，尚未代為建立。

- 選 Public 可讓評測者直接檢視；若選 Private，需邀請評測者取得權限。
- 建立空白 repository，**不要勾選新增 README、.gitignore 或 license**，避免與本機既有檔案衝突。

回到 Windows 專案根目錄，明確選取交付檔案：

```powershell
git add .gitattributes .gitignore .github .mvn mvnw mvnw.cmd pom.xml README.md src deploy docs
git diff --cached --stat
git status --short
```

確認清單包含程式碼、建置設定、測試與部署說明。請排除私人規劃、信件內容、暫存檔、資料庫、上傳附件實體、私鑰及實際密碼；不要改成直接執行 `git add .`。專案 `.gitignore` 已排除常見產物，但不會自動辨識所有私人檔案。

確認後提交：

```powershell
git commit -m "Complete announcement board implementation"
```

若出現作者身分未設定，先設定此專案的身分，再重試 commit。請替換範例內容；可使用 GitHub Settings → Emails 顯示的隱私用 noreply 信箱：

```powershell
git config --local user.name "你的顯示名稱"
git config --local user.email "你的GitHub提交信箱"
```

先查看遠端設定：

```powershell
git remote -v
```

若沒有 `origin`，且你剛才確實建立上述名稱的 repository，執行：

```powershell
git remote add origin https://github.com/yunia0819/announcement-board.git
git push -u origin HEAD
```

若已存在 `origin`，先確認它指向正確 repository，勿重複新增。本指南保留目前的分支名稱，`HEAD` 代表推送目前分支；推送後到 GitHub 選取該分支，確認可看到 `src`、`pom.xml`、`README.md` 與 `deploy`。

若跳出 GitHub 登入畫面，依瀏覽器或 Git Credential Manager 完成驗證。不要把 GitHub 網站密碼或 token 寫進指令、remote URL 或專案檔案。

## 3. 建立 CentOS 雲端主機

登入 AWS Console → EC2 → **Launch instance**。雲端會產生費用，包含主機、磁碟與公網 IP，請以建立畫面與帳戶方案為準，不保證免費。可對照 [AWS 建立主機教學](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/EC2_GetStarted.html)、[EC2 計費說明](https://aws.amazon.com/ec2/pricing/on-demand/) 與 [公網 IPv4 計費說明](https://aws.amazon.com/vpc/pricing/)。

| 選項 | 此專案的起步設定 |
| --- | --- |
| 區域 | 東京 `ap-northeast-1` |
| 作業系統映像 | CentOS Stream 10，`x86_64` |
| 執行個體 | `t3.small` 起，2 GiB 記憶體 |
| 系統磁碟 | 20 GiB 起 |
| Public IPv4 | 啟用，以供外部連線 |
| Key pair | 建立並下載 `.pem` 私鑰 |
| SSH TCP 22 | 來源限 **My IP** |
| 網站 TCP 8080 | 公開展示可選 Anywhere-IPv4（`0.0.0.0/0`）；若知道評測者 IP，可限制來源 |

請從 [CentOS 官方 AWS 映像清單](https://www.centos.org/download/aws-images/) 查詢當時東京區域的 **CentOS Stream 10 x86_64 AMI**，依官方表列選擇，不使用本文件固定的 AMI ID，也不要誤選 ARM 映像。

下載的私鑰可放在 Windows 的 `Downloads` 或其他專案外的私人目錄，**不要放進專案或上傳 GitHub**。本例假設檔名為 `announcement-board.pem`。

主機建立後，記下 **Public IPv4 address**。下方所有 `SERVER_IP` 都要換成該 IP。官方 CentOS 映像的 SSH 使用者為 `ec2-user`。

## 4. 從 Windows 上傳網站與登入

仍在 Windows 的專案根目錄執行。首次 SSH 連線時，依 AWS Console 的連線資訊核對主機後再接受提示：

```powershell
ssh -i "$env:USERPROFILE\Downloads\announcement-board.pem" ec2-user@SERVER_IP "mkdir -p ~/announcement-release"
scp -i "$env:USERPROFILE\Downloads\announcement-board.pem" target/announcements.war ec2-user@SERVER_IP:~/announcement-release/
scp -i "$env:USERPROFILE\Downloads\announcement-board.pem" -r deploy ec2-user@SERVER_IP:~/announcement-release/
ssh -i "$env:USERPROFILE\Downloads\announcement-board.pem" ec2-user@SERVER_IP
```

若無法連線，先檢查主機已啟動、IP、私鑰檔案及 TCP 22 的來源規則。不要把 SSH 金鑰或密碼提供給評測者。

## 5. 在 CentOS 安裝 Java、Tomcat 與 MySQL

**從本節開始，指令在 SSH 登入後的 CentOS 終端機執行。**

```bash
cat /etc/os-release
cd ~/announcement-release
sudo bash deploy/install-tomcat.sh
/usr/lib/jvm/jre-21-openjdk/bin/java -version
```

安裝腳本限定 CentOS Stream 10，會建立專用 Tomcat 帳號、目錄與 systemd 服務。此時還沒有啟動網站。

安裝並啟動 MySQL：

以下使用明確的 `mysql8.4-server` 套件名稱，可對照 [Red Hat 10 的 MySQL 安裝說明](https://docs.redhat.com/en/documentation/red_hat_enterprise_linux/10/html-single/configuring_and_using_database_servers/configuring_and_using_database_servers)。

```bash
sudo dnf install -y mysql8.4-server
sudo systemctl enable --now mysqld
sudo mysql_secure_installation
mysql -u root -p
```

依初始化工具提示設定管理者密碼。若安裝版本要求初始暫時密碼，可從 MySQL 錯誤日誌或 `sudo journalctl -u mysqld` 查詢。

進入 MySQL 後執行以下 SQL，先將密碼替換為自己設定的值：

```sql
CREATE DATABASE IF NOT EXISTS announcements
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'announcement_app'@'127.0.0.1'
  IDENTIFIED BY '替換成自己設定的資料庫密碼';

GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES
  ON announcements.* TO 'announcement_app'@'127.0.0.1';

exit;
```

`CREATE USER IF NOT EXISTS` 不會更改既有帳號密碼。網站使用專用帳號，不使用 root。MySQL 的 TCP 3306 不必對外開放。

## 6. 設定網站的資料庫連線

```bash
sudoedit /etc/announcement-board/announcement.env
```

保留範本設定，將 `DB_PASSWORD` 換成剛才建立帳號的密碼：

```ini
SPRING_PROFILES_ACTIVE=mysql
DB_URL="jdbc:mysql://127.0.0.1:3306/announcements?connectionTimeZone=%2B08:00&forceConnectionTimeZoneToSession=true"
DB_USERNAME=announcement_app
DB_PASSWORD="與剛才設定相同的資料庫密碼"
APP_UPLOAD_DIR=/var/lib/announcement-board/uploads
JAVA_OPTS="-Xms128m -Xmx512m -Djava.awt.headless=true -Duser.timezone=Asia/Taipei"
```

這份檔案使用 systemd 環境設定格式，不加 `export`。密碼中的雙引號或反斜線需依該格式跳脫。儲存後確認檔案權限：

若開啟的是 `vi` 編輯器，按 `i` 開始編輯；完成後按 `Esc`，輸入 `:wq` 再按 Enter 儲存離開。

```bash
sudo chown root:tomcat /etc/announcement-board/announcement.env
sudo chmod 0640 /etc/announcement-board/announcement.env
```

這裡的 `127.0.0.1` 指 CentOS 本身。Windows 的 H2／MySQL 資料不會隨 WAR 自動搬過來；新資料庫初次沒有公告是正常的。資料表由 Flyway 在網站啟動時建立，不必手動建立公告表。

## 7. 部署並開放網站

```bash
cd ~/announcement-release
sudo bash deploy/deploy-war.sh "$PWD/announcements.war"
sudo systemctl status announcement-board --no-pager
curl --fail http://127.0.0.1:8080/actuator/health
```

健康檢查應回傳 `{"status":"UP"}`。腳本會安裝 `ROOT.war`、啟動網站並等待健康狀態，服務已設為開機自動啟動。

開啟 CentOS 防火牆的網站連接埠：

```bash
sudo dnf install -y firewalld
sudo systemctl enable --now firewalld
sudo firewall-cmd --permanent --add-port=8080/tcp
sudo firewall-cmd --reload
```

AWS Security group 也必須允許評測者來源連入 TCP 8080。現在可在 Windows 瀏覽器輸入：

```text
http://SERVER_IP:8080/announcements
```

網站目前沒有登入限制，可存取者能操作公告，請依展示需求設定可連入的來源。這是直接使用 Tomcat 的測驗展示方式，網域與 HTTPS 需另行設定。

若啟動失敗或修改環境設定後需要重啟：

```bash
sudo journalctl -u announcement-board -n 150 --no-pager
sudo systemctl restart announcement-board
```

不要同時另開 `java -jar` 與此服務，以免占用同一個 8080。更多資料庫備份、附件與故障排查步驟見 [CentOS 部署文件](centos-deployment.md)。

## 8. 驗收並回覆作業

先使用手機行動網路或另一個外部網路開啟網站，確認不是只有自己的電腦能開。驗證中文公告新增、修改、刪除、分頁、月份篩選、文字格式及附件上下載；重啟網站後，公告與附件應仍可使用。

回覆作業時提供兩個實際可開啟的網址：

| 交付項目 | 內容 |
| --- | --- |
| 原始碼 | `https://github.com/yunia0819/announcement-board`，需已實際建立並推送 |
| 可操作網站 | `http://SERVER_IP:8080/announcements`，將 IP 換成實際值 |
| 補充資料 | 簡述使用技術、功能與 README 的執行／部署方式 |
| 可選附件 | 原始碼 ZIP；也可讓對方從 GitHub 的 Code → Download ZIP 下載 |

若使用既有 `target/announcement-board-source.zip`，先確認它與最後提交的程式版本一致。原始碼 repository 已能提供程式內容，WAR 主要供伺服器執行，通常不必提交 Git。

評測期間保持主機運作；若停止後再啟動，Public IPv4 可能改變，交付前應再確認網址。完成評閱後再依需要停止或刪除資源，磁碟等保留資源可能持續計費。

**GitHub Pages 僅提供靜態網站，無法直接執行本專案的 Java 與 MySQL。GitHub Actions 建置成功也不代表網站已上線；仍需完成上述伺服器部署與外部驗收。**
