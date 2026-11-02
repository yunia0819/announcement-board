# GitHub 原始碼與網站交件教學

本教學接續已部署完成的 Google Cloud CentOS 主機。GitHub 保存原始碼，網站由 Google Cloud 主機執行。以下上傳指令全部在 **Windows PowerShell 或 IntelliJ 的 PowerShell Terminal** 執行。

## 1. 最後要交付什麼

| 項目 | 內容 |
| --- | --- |
| GitHub repository | 自己帳號底下的原始碼專案網址 |
| 原始碼 ZIP | 可從上傳完成的 GitHub 專案下載；需要附件時再附上 |
| 網站 | <http://35.221.234.22:8080/announcements> |

本教學以已連接的 GitHub 帳號 `yunia0819`、建議 repository 名稱 `announcement-board` 為例。`https://github.com/yunia0819/announcement-board` 是依此名稱組成的範例，實際建立成功並完成 push 後才能作為交件連結。若使用不同帳號或名稱，請替換下方遠端網址。

目前本機已初始化 Git，分支是 `master`，尚未建立提交，也尚未設定遠端。本教學保留現有分支名稱。

## 2. 在 GitHub 建立空白專案

1. 用瀏覽器登入自己的 GitHub 帳號，開啟 [建立 repository 頁面](https://github.com/new)。
2. Owner 選自己的帳號，例如 `yunia0819`。
3. Repository name 填 `announcement-board`。
4. Description 可填「Spring MVC、Hibernate、MySQL 公告管理系統，部署於 CentOS Stream 10 與 Tomcat」。
5. 選 Public，讓評閱者能直接檢視。若需保密可選 Private，並另外邀請對方提供的 GitHub 帳號取得權限；單純提供 Private 網址不會授予存取權。
6. 保持空白專案：不要新增 README、不要選 .gitignore 範本、不要新增 license。這個本機專案已有 README 與 .gitignore。
7. 按 Create repository，保留建立後的頁面。

若同名專案已存在且裡面有其他內容，先確認內容與用途，勿用強制推送覆蓋。

## 3. 在 Windows 開啟專案目錄

開啟 Windows PowerShell；也可以用 IntelliJ 下方的 Terminal，確認終端機是 PowerShell。執行：

```powershell
Set-Location -LiteralPath 'C:\Users\jeffl\OneDrive\文件\ChatGPT\碩網資訊'
git --version
git status
```

此處應能看到 Git 版本，以及 `On branch master`、`No commits yet`。本機已初始化，不必重複執行 `git init`。

## 4. 選取要上傳的檔案

完整原始碼包含 Java、HTML、CSS、JavaScript、Maven 設定、測試及部署文件。執行：

```powershell
git add .gitattributes .gitignore .github .mvn mvnw mvnw.cmd pom.xml README.md src deploy docs
git diff --cached --stat
git status --short
```

`git add` 是選取要放入下一次提交的檔案，還沒有上傳 GitHub。

預期會包含：

```text
.gitattributes
.gitignore
.github/
.mvn/
README.md
mvnw
mvnw.cmd
pom.xml
src/
deploy/
docs/
```

其中 `.mvn/wrapper/maven-wrapper.jar` 是 Maven Wrapper 的一部分，需要保留。

專案已透過 `.gitignore` 排除 `target/`、`var/`、`.tools/`、`.m2/`、`.idea/`、實際環境設定檔、私鑰及私人規劃。部署範例 `deploy/announcement.env.example` 只有密碼占位文字，可以上傳；CentOS 實際使用的 `/etc/announcement-board/announcement.env` 留在主機上。

使用上面的明確檔案清單即可。提交清單應包含原始碼，而不是只上傳 WAR。

## 5. 建立本機提交

```powershell
git commit -m "Complete announcement board implementation"
```

`commit` 會在自己的電腦建立版本紀錄，成功時會列出新增的檔案。可用以下指令確認：

```powershell
git log -1 --oneline
```

本機已設定作者身分。只有遇到 `Author identity unknown` 時，才需要在本專案設定後重試；請自行替換範例內容，也可使用 GitHub Settings → Emails 中的 noreply 提交信箱：

```powershell
git config --local user.name "你的顯示名稱"
git config --local user.email "你的GitHub提交信箱"
git commit -m "Complete announcement board implementation"
```

## 6. 連結 GitHub 並上傳

先檢查遠端：

```powershell
git remote -v
```

如果沒有任何輸出，且第 2 步已在 `yunia0819` 建立空白的 `announcement-board`，執行：

```powershell
git remote add origin https://github.com/yunia0819/announcement-board.git
git remote -v
git push -u origin HEAD
```

`origin` 是這個遠端 GitHub repository 的簡稱；`HEAD` 代表目前的分支。依目前狀態，上傳後的分支是 `master`，不需要另外改成 `main`。

如果 `origin` 已存在，先核對網址。正確時直接執行 push；若指向不同專案，先確認要使用的 repository，避免推送到別人的或其他用途的專案。

本機使用 Git Credential Manager。第一次上傳可能顯示登入視窗，選擇透過瀏覽器登入，完成自己的 GitHub 驗證與授權。不要在 remote URL 或程式中放入密碼或 token。

成功輸出通常包含：

```text
To https://github.com/yunia0819/announcement-board.git
 * [new branch]      HEAD -> master
```

如果本次登入已完成，不一定會再次跳出瀏覽器。

## 7. 在 GitHub 確認結果

1. 重新整理自己的 repository 頁面。
2. 分支選單應能選到 `master`。
3. 開啟 `src/main/java`，應能看到 Java 程式。
4. 開啟 `src/main/resources/templates`，應能看到 HTML；`static` 內有 CSS、JavaScript 與前端資源。
5. `pom.xml`、Maven Wrapper、`deploy/` 與 `docs/` 應存在。
6. 主頁下方應顯示 README，內有功能、執行與部署說明，以及線上網站連結。
7. 進入 Actions，開啟這次 push 觸發的 **Build and test**，等待成功結果。若失敗，點入工作步驟查看原因。

Actions 執行 Java 21 的建置與測試，並保存 WAR。這份工作流程不會自動更新 Google Cloud 網站；之後改程式時，GitHub 上傳與 WAR 重新部署是兩個步驟。

Public 專案可用瀏覽器無痕視窗開啟 repository，確認評閱者不登入也能看見。Private 專案則需由已取得權限的帳號驗證。

## 8. 提供原始碼 ZIP

在 GitHub repository 的 Code 頁面，按綠色 **Code → Download ZIP**，即可下載原始碼壓縮檔。解壓縮後應看到 `src/`、`pom.xml`、`mvnw.cmd` 與 `README.md`。

GitHub repository 本身就提供可瀏覽、可下載的原始碼。回信提供 repository 網址即可讓對方取得；若希望更方便，另附這份 ZIP，並保留 repository 連結，符合「上傳至自己的 GitHub」要求。

`announcements.war` 是伺服器部署產物；`deploy-scripts.tar.gz` 是部署腳本包。它們都不能取代包含 Java 原始碼的 ZIP 或 GitHub repository。

## 9. 確認網站並回覆測驗信

交件前再次用 Windows 瀏覽器及手機行動網路開啟：

```text
http://35.221.234.22:8080/announcements
```

確認公告操作、排序、月份、分頁、文字格式及附件正常。評閱期間保持 VM、MySQL 與網站服務運作。暫時外部 IP 在主機停止後可能改變，交件前以 VM 清單的實際外部 IP 為準。

回到原本的技術測驗 Gmail，按「回覆」，保留原主旨與收件者。以下範本請在實際 push 完成、確認網址可開啟後使用，並把 repository 占位文字換成實際網址：

```text
陳小姐您好：

我是呂學哲，已完成公告管理系統技術測驗，提供以下資料供您查閱：

1. GitHub 原始碼：
【貼上已成功上傳的 GitHub repository 網址】

2. 網站：
http://35.221.234.22:8080/announcements

專案使用 Spring MVC、Hibernate、MySQL、Thymeleaf 與 Bootstrap，
部署於 Google Cloud 的 CentOS Stream 10，使用 Tomcat 11 執行。
README 已提供功能、執行及部署說明，原始碼可由 GitHub 的 Code → Download ZIP 下載。

感謝您的審閱。

呂學哲
```

若實際附了 ZIP，可再加「另附原始碼 ZIP，方便下載檢視」。附件上傳完成、檢查兩個網址後，再寄出。

## 10. 之後修改程式如何更新

在 Windows 專案目錄完成修改與必要測試後，執行：

```powershell
git add .gitattributes .gitignore .github .mvn mvnw mvnw.cmd pom.xml README.md src deploy docs
git diff --cached --stat
git commit -m "Update announcement board"
git push
```

如果修改了網站程式，依 [Google Cloud 部署教學](google-cloud-deployment.md) 重新建置 WAR、上傳到 VM，並執行部署腳本。只更新文件時，不必重新部署網站。

## 11. 常見問題

| 訊息或情況 | 處理方式 |
| --- | --- |
| `remote origin already exists` | 執行 `git remote -v` 檢查現有網址，正確時直接 push。 |
| `Repository not found` | 確認專案已建立、遠端帳號與名稱正確，以及登入帳號有權限。 |
| `src refspec HEAD does not match any` | 用 `git log -1 --oneline` 確認已成功 commit。 |
| `nothing to commit, working tree clean` | 已沒有尚未提交的變更；檢查紀錄後繼續 push。 |
| `non-fast-forward`／`fetch first` | 遠端有本機尚未取得的提交；先查看遠端內容，保留錯誤訊息再處理，勿強制覆蓋。 |
| 顯示 LF／CRLF 換行警告 | 這是換行格式提示，查看後續是否仍成功執行即可。 |
| GitHub 看得到檔案，但網站沒更新 | Actions 未配置自動部署，需要重新建置並部署 WAR。 |
| Private 網址對方開不起來 | 邀請對方的 GitHub 帳號，並等對方接受邀請。 |

## 官方參考

- [將本機程式上傳 GitHub](https://docs.github.com/en/migrations/importing-source-code/using-the-command-line-to-import-source-code/adding-locally-hosted-code-to-github)
- [Git Credential Manager 與登入](https://docs.github.com/en/get-started/git-basics/caching-your-github-credentials-in-git)
- [下載原始碼 ZIP](https://docs.github.com/en/repositories/working-with-files/using-files/downloading-source-code-archives)
- [Repository 與可見性](https://docs.github.com/en/repositories/creating-and-managing-repositories/about-repositories)
