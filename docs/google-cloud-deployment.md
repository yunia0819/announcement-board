# Google Cloud：從 Windows 部署公告網站到 CentOS

本指南使用 **Google Cloud Compute Engine、CentOS Stream 10、Java 21、外部 Tomcat 11 與同機 MySQL 8.4**。Windows 繼續用來編輯、測試與打包；Google Cloud 主機負責持續執行網站。

本專案已於 **2026/11/02** 完成 Google Cloud 主機部署，確認網站可由外部開啟：<http://35.221.234.22:8080/announcements>。已確認的範圍與仍需人工驗收的項目見 [驗證記錄](verification.md)。

以下保留從建立主機到部署的完整操作流程，供重建環境時參考。已有上述網站的本次交付不必重新建立 VM；上傳自己的 GitHub 與交付步驟請見 [GitHub 原始碼上傳與交付指南](github-submission.md)。

## 1. 確認免費試用與建立 Google Cloud 專案

符合新客資格者可使用 **US$300、90 天**免費試用；註冊仍需要信用卡或其他有效付款方式驗證。維持 Free Trial billing account 時，不會自動轉為付費帳戶。未升級而額度用完或到期，資源會停止，網站也會無法使用。

VM、磁碟、公網 IPv4 與流量會產生用量，再由適用的試用額度抵扣。這份教學採用的 `e2-small` 不是永久免費機型。請在 Billing 查看剩餘額度、天數與目前用量。

來源：[Google 免費試用規則](https://docs.cloud.google.com/free/docs/free-cloud-features)、[網路及公網 IP 計費](https://cloud.google.com/vpc/network-pricing)。

1. 開啟 [Google Cloud 免費試用](https://cloud.google.com/free)，登入自己的 Google 帳號。
2. 依畫面完成資格、身分與付款方式驗證，確認目前是 Free Trial 並有試用額度。
3. 進入 [Google Cloud Console](https://console.cloud.google.com/)。
4. 點上方專案選單 → **New project／新增專案**，名稱可填 `announcement-board`。
5. Project ID 會由 Google 產生，可保留它給的唯一值。建立後，確認上方已選到這個專案。
6. 搜尋 **Compute Engine** → **VM instances／VM 執行個體**。首次使用時依提示按 **Enable／啟用**，等待 API 啟用完成。

## 2. 建立 CentOS Stream 10 主機

在 VM instances 按 **Create instance／建立執行個體**。目前建立畫面使用左側的 **Machine configuration、OS and storage、Networking** 等分區。

### Machine configuration：主機規格

| 選項 | 本次建議 |
| --- | --- |
| Name | `announcement-board` |
| Region | 台灣 `asia-east1`，或選有可用 E2 資源的地區 |
| Zone | 該地區可用的 zone |
| Machine family | General purpose |
| Series | E2 |
| Machine type | `e2-small`，2 GB 記憶體 |
| Provisioning model | Standard，供持續展示使用 |

2 GB 是這份 Java＋Tomcat＋MySQL 測驗的起步建議；若實際測試需要更多記憶體，可評估 `e2-medium` 的 4 GB，並確認估價。不要把機型規格與免費試用額度混為一談。

### OS and storage：系統與磁碟

選擇 Google 提供的公開系統映像：

| 選項 | 設定 |
| --- | --- |
| OS／Version | **CentOS Stream 10** |
| Architecture | `x86_64` |
| Image project／family | `centos-cloud`／`centos-stream-10` |
| Boot disk type | Standard persistent disk（`pd-standard`） |
| Size | 20 GB 起 |

本專案安裝腳本限定 CentOS Stream 10。請核對版本，不要用 Ubuntu、Amazon Linux 或舊 CentOS 版本執行這份腳本。

### Networking：公網 IP 與標籤

1. 使用一個 VPC network；本指南以 `default` 為例。
2. 在 **Network interfaces** 展開網路介面。
3. **External IPv4 address** 選 **Ephemeral**，讓 VM 有外部可連入的 IP。
4. 在 **Network tags／網路標記** 加入 `announcement-web`，後面會用它設定網站防火牆。
5. 建立主機前查看右側估價，確認上述設定後按 **Create**。

建立成功後，在 VM instances 清單記下 **External IP**。這是之後交付網站網址使用的 IP；Internal IP 是主機內部網路位址。

來源：[建立 VM 的現行介面](https://docs.cloud.google.com/compute/docs/instances/create-start-instance)、[CentOS 映像與機型支援](https://docs.cloud.google.com/compute/docs/images/os-details)、[E2 規格](https://docs.cloud.google.com/compute/docs/general-purpose-machines#e2_shared-core)、[外部 IP 設定](https://docs.cloud.google.com/compute/docs/ip-addresses/configure-static-external-ip-address)。

## 3. 開啟 VM 的 SSH 視窗

在 **Compute Engine → VM instances** 找到剛建立的主機，點它那一列的 **SSH**。瀏覽器會開啟新的 SSH 終端機視窗。

這個視窗連到 CentOS，可以執行：

```bash
whoami
pwd
cat /etc/os-release
```

- `whoami`：目前的 Linux 使用者名稱，由 Google 的連線機制設定。
- `pwd`：目前目錄，通常是 `/home/你的Linux使用者`。
- 系統資訊應顯示 CentOS Stream 10。

**這個視窗是 VM 的 SSH，和 Google Console 右上角開啟的 Cloud Shell 是不同環境。後面所有安裝指令都在 VM 的 SSH 執行。**

Google 的 SSH-in-browser 會處理連線金鑰；這條操作路線不需要 AWS 的 `.pem` 私鑰，也不要把 Linux 使用者寫死為 AWS 的 `ec2-user`。如果 SSH 連不上，檢查 VM 狀態、VPC 的 SSH 規則與官方 SSH-in-browser 要求；若使用 default network，先確認其既有 SSH 規則是否存在。

來源：[SSH-in-browser](https://docs.cloud.google.com/compute/docs/connect/ssh-in-browser)、[SSH 使用者與金鑰機制](https://docs.cloud.google.com/compute/docs/instances/ssh)。

## 4. 在 Windows 建置 WAR 與打包部署腳本

回到 **Windows PowerShell／IntelliJ Terminal**，切換到放有 `pom.xml` 的專案根目錄。請替換第一行的專案路徑：

```powershell
Set-Location -LiteralPath 'C:\你的專案路徑\碩網資訊'
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
.\mvnw.cmd clean verify
```

等待 `BUILD SUCCESS`。Java 21 安裝位置不同時，依實際位置調整 `JAVA_HOME`。

接著才打包部署腳本：

```powershell
tar -czf target/deploy-scripts.tar.gz deploy
```

現在要上傳的檔案有兩個：

```text
target/announcements.war
target/deploy-scripts.tar.gz
```

WAR 包含網站程式與畫面，tar.gz 包含整個 `deploy` 資料夾。**先完成 `clean verify` 再建立 tar.gz**，因為 Maven 的 `clean` 會清除 `target`。

## 5. 用瀏覽器 SSH 上傳兩個檔案

回到第 3 步的 **VM SSH 視窗**：

1. 點工具列上的 **Upload／上傳圖示**。
2. 選擇 Windows 專案 `target` 裡的 `announcements.war`。
3. 按 **Upload Files**，等待上傳完成。
4. 再上傳 `deploy-scripts.tar.gz`。

檔案會放在目前 Linux 使用者的家目錄 `/home/USER`。在 SSH 裡確認：

```bash
ls -lh ~/announcements.war ~/deploy-scripts.tar.gz
```

若找不到，確認上傳的是 VM SSH 視窗，而不是 Cloud Shell，並檢查上傳後的實際檔名。

接著整理到發佈資料夾：

```bash
mkdir -p ~/announcement-release
cp ~/announcements.war ~/announcement-release/
tar -xzf ~/deploy-scripts.tar.gz -C ~/announcement-release
cd ~/announcement-release
ls -l
```

這裡應看到 `announcements.war` 與 `deploy` 資料夾。

來源：[SSH-in-browser 上傳檔案與目的地](https://docs.cloud.google.com/compute/docs/instances/transfer-files)。

## 6. 在 CentOS 安裝 Java、Tomcat 與 MySQL

以下指令都在 **VM SSH 視窗**執行：

```bash
cd ~/announcement-release
sudo bash deploy/install-tomcat.sh
/usr/lib/jvm/jre-21-openjdk/bin/java -version
```

最後應顯示 Java 21。腳本會安裝專用 Tomcat、建立服務與附件目錄；資料庫尚未設定完成前，先不啟動網站。

安裝 MySQL 8.4 與文字編輯器 nano：

```bash
sudo dnf install -y mysql8.4-server nano
sudo systemctl enable --now mysqld
sudo systemctl status mysqld --no-pager
```

MySQL 狀態應為 `active (running)`。接著執行：

```bash
sudo mysql_secure_installation
```

依初始化工具提示設定 root 管理者密碼與初始安全設定。輸入密碼時不會顯示字元或星號，這是正常現象。若安裝版本要求暫時密碼，先依 MySQL 日誌查詢；不要把不知道的密碼當成空白。

## 7. 建立公告資料庫與網站帳號

登入 MySQL：

```bash
mysql -u root -p
```

輸入 root 管理者密碼。看到 `mysql>` 後，再執行以下 SQL；先把密碼占位文字換成自己設定的一組獨立密碼：

```sql
CREATE DATABASE IF NOT EXISTS announcements
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'announcement_app'@'127.0.0.1'
  IDENTIFIED BY '替換成網站專用資料庫密碼';

GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES
  ON announcements.* TO 'announcement_app'@'127.0.0.1';

exit;
```

`root` 是管理者，`announcement_app` 是網站使用的帳號。`CREATE USER IF NOT EXISTS` 不會更改已存在帳號的密碼。

在 CentOS 提示字下測試網站帳號：

```bash
mysql -h 127.0.0.1 -u announcement_app -p announcements
```

輸入 `announcement_app` 的密碼。成功進入後可執行：

```sql
SELECT DATABASE();
exit;
```

結果應為 `announcements`。這時公告資料表尚未建立是正常的，會由網站啟動時的 Flyway migration 建立。

## 8. 編輯網站環境設定

在 CentOS 執行：

```bash
sudo nano /etc/announcement-board/announcement.env
```

安裝腳本已從範本建立這份檔案。確認內容並替換 `DB_PASSWORD`：

```ini
SPRING_PROFILES_ACTIVE=mysql
DB_URL="jdbc:mysql://127.0.0.1:3306/announcements?connectionTimeZone=%2B08:00&forceConnectionTimeZoneToSession=true"
DB_USERNAME=announcement_app
DB_PASSWORD="與剛才網站帳號相同的實際密碼"
APP_UPLOAD_DIR=/var/lib/announcement-board/uploads
JAVA_OPTS="-Xms128m -Xmx512m -Djava.awt.headless=true -Duser.timezone=Asia/Taipei"
```

這是 systemd `EnvironmentFile` 格式，不加 `export`；密碼若含雙引號或反斜線，需依該格式跳脫。實際密碼只放在主機這份檔案，不要寫回 repository 的範本或上傳 GitHub。

nano 儲存方法：按 **Ctrl+O → Enter**，再按 **Ctrl+X** 離開。接著設定權限：

```bash
sudo chown root:tomcat /etc/announcement-board/announcement.env
sudo chmod 0640 /etc/announcement-board/announcement.env
```

若要產生範例公告，可暫時設定 `SPRING_PROFILES_ACTIVE=mysql,demo`。它只會在公告表完全為空時建立 23 筆範例；產生後改回 `mysql` 並重啟網站。

這裡的 `127.0.0.1` 是 Google Cloud VM 本身。Windows 的 H2／MySQL 公告與上傳附件不會因為上傳 WAR 自動搬過來。

## 9. 部署網站並確認主機內部可開啟

```bash
cd ~/announcement-release
sudo HEALTH_TIMEOUT_SECONDS=300 bash deploy/deploy-war.sh "$PWD/announcements.war"
sudo systemctl status announcement-board --no-pager
curl --fail http://127.0.0.1:8080/actuator/health
```

此處給共享 CPU 主機最多約 300 秒的健康檢查等待時間。第一次啟動請等待腳本完成。預期看到服務為 `active (running)`，健康端點回傳：

```json
{"status":"UP"}
```

腳本會將網站安裝成 `ROOT.war`，並透過 systemd 啟動，服務已設為開機自動啟動。之後可以關閉 SSH 視窗與 Windows，只要雲端 VM 持續運作，網站就會繼續執行。

若未成功，先看網站日誌：

```bash
sudo journalctl -u announcement-board -n 150 --no-pager
```

此部署方式由外部 Tomcat 執行，不要同時另開 `java -jar` 占用同一個 8080。

## 10. 開放 CentOS 與 Google Cloud 的 8080

### CentOS 防火牆

在 VM SSH 視窗執行：

```bash
sudo dnf install -y firewalld
sudo systemctl enable --now firewalld
sudo firewall-cmd --permanent --add-port=8080/tcp
sudo firewall-cmd --reload
sudo firewall-cmd --list-ports
```

最後應包含 `8080/tcp`。

### Google Cloud VPC 防火牆

回到 Google Cloud Console：搜尋 **Firewall／防火牆**，進入 VPC 的防火牆頁面，按 **Create firewall rule**。設定：

| 欄位 | 值 |
| --- | --- |
| Name | `allow-announcement-8080` |
| Network | 與 VM 相同，例如 `default` |
| Direction of traffic | Ingress |
| Action on match | Allow |
| Targets | Specified target tags |
| Target tags | `announcement-web` |
| Source filter | IPv4 ranges |
| Source IPv4 ranges | `0.0.0.0/0`，供公開測驗展示 |
| Protocols and ports | Specified protocols and ports → TCP → `8080` |

按 **Create**。再到 VM 詳細資料的 **Edit → Network tags**，確認有 `announcement-web`，儲存。

只有相同 network、且具有匹配標籤的 VM，才會套用此規則。Allow HTTP／HTTPS 對應的是 80／443，仍須另外建立本專案的 8080 規則。MySQL 在同機使用，3306 不必對外開放。

目前網站沒有登入限制，能連入的人可操作公告，請放置展示資料。這份流程提供直接 Tomcat 的 HTTP 展示；網域、HTTPS 與登入驗證可依後續用途另行設定。

來源：[建立 VPC 防火牆規則](https://docs.cloud.google.com/firewall/docs/using-firewalls)、[網路標籤與目標 VM](https://docs.cloud.google.com/vpc/docs/add-remove-network-tags)。

## 11. 用外部網路驗收網站

在 VM instances 清單取得 **External IP**，於 Windows 瀏覽器開啟：

```text
http://你的External-IP:8080/announcements
```

請使用 `http`。面試方要使用公開 IP 網址，`localhost` 只代表開啟瀏覽器的人自己的電腦。

再用手機行動網路開啟相同網址，確認能從外部操作。依序測試中文公告新增、修改、刪除、分頁、排序、月份篩選、內文格式、附件上傳與下載。

新增公告後，可以在 CentOS 的 MySQL 查詢：

```bash
mysql -h 127.0.0.1 -u announcement_app -p announcements
```

```sql
SHOW TABLES;
SELECT id, title, publisher FROM announcement ORDER BY id DESC;
SELECT id, announcement_id, original_filename, storage_name FROM attachment ORDER BY id DESC;
SELECT installed_rank, version, script, success FROM flyway_schema_history ORDER BY installed_rank;
exit;
```

重啟網站，再確認公告與附件仍然可用：

```bash
sudo systemctl restart announcement-board
curl --fail http://127.0.0.1:8080/actuator/health
```

網站重新啟動需要一段時間；若剛重啟的第一次 curl 尚未成功，查看日誌，等啟動完成後再確認。

## 12. 資料位置、更新與交付

| 內容 | 位置 |
| --- | --- |
| 網站部署檔 | `/var/lib/announcement-board/tomcat/webapps/ROOT.war` |
| 資料庫連線設定 | `/etc/announcement-board/announcement.env` |
| 公告與附件資訊 | MySQL 的 `announcements` 資料庫 |
| 附件實體檔案 | `/var/lib/announcement-board/uploads` |
| 舊版 WAR 備份 | `/var/backups/announcement-board` |

更新網站時，在 Windows 重新建置並上傳 WAR，於 VM 整理到 release 目錄後，再執行同一支 `deploy-war.sh`。備份及搬移時需一併保留 **MySQL＋uploads**；WAR 本身不包含使用者的公告資料與附件。

最後交付兩個實際可開啟的網址：自己的 GitHub 原始碼 repository，以及此 VM 的公告網站。評閱期間維持 VM 運作。Ephemeral 外部 IP 在停止主機後可能改變，交付前應再次核對。

常見排查：

| 現象 | 先檢查 |
| --- | --- |
| SSH 中找不到上傳檔案 | 是否上傳到 VM SSH，而非 Cloud Shell；查看 `ls -lh ~` |
| MySQL `Access denied` | root 與網站帳號是否混用；密碼及帳號 host 是否正確 |
| 健康檢查失敗 | `sudo journalctl -u announcement-board -n 150 --no-pager` |
| VM 內可開，外部連不上 | External IP、VPC 的 network／target tag／8080、CentOS 防火牆 |
| 修改 env 後沒有生效 | 儲存後執行 `sudo systemctl restart announcement-board` |
| 網站沒有範例公告 | `demo` 是否啟用，以及公告表是否完全為空 |
| 附件無法讀寫 | uploads 權限、服務日誌及既有附件是否已搬移 |

費用請在 **Billing** 的試用資訊查看剩餘額度與天數，在 **Reports** 查看目前用量；一般 [alerts-only 預算提醒](https://docs.cloud.google.com/billing/docs/how-to/budgets) 只是通知，不是自動停止所有資源的保證。未升級的 Free Trial 到期會停止資源；若已升級付費，超過適用額度的費用需自行負擔。刪除 VM 會影響網站與資料，結束展示前先完成資料庫及附件備份，再檢查是否仍保留磁碟、快照或靜態 IP。

更詳細的主機備份與 Tomcat 設定，見 [CentOS 部署文件](centos-deployment.md)。
