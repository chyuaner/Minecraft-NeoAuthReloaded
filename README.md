# 🔐 NeoAuthReloaded

<div align="center">

**輕量級 Minecraft 伺服器登入驗證模組**  
同時支援 **Minecraft 1.20.1 (Forge)** 與 **Minecraft 1.21.1 (NeoForge)**

</div>

---

本專案大量借鑒 **AuthMeReloaded** 的風格與傳統設計，重新復刻出 **NeoAuthReloaded** 模組，並提供 AuthMeReloaded 沒有提供的 Forge 與 NeoForge 模組支援。採用的資料庫相容於原 AuthMe 的資料庫結構，並支援使用原 AuthMe 的資料庫，讓原本使用 AuthMe 的服主可以直接無縫切換到 NeoAuthReloaded。設定檔風格也比照 AuthMeReloaded 設計，讓原本使用 AuthMe 的服主能快速上手。

### 核心優勢亮點：
- **全模式正版自動登入 (Dynamic Premium Handshake)**：無論伺服器處於 `online-mode: false` 還是 `online-mode: true`，正版玩家皆能經由 Mojang Session 驗證，**享有完全免輸入密碼的絲滑自動登入體驗**！
- **離線模式存檔零重設 (Zero-Wipe Guarantee)**：在 `online-mode: false` 下所有玩家統一採用離線 UUID (v3)，玩家從離線升級正版或遇到 Mojang 官方斷線時，背包存檔與進度永遠 100% 相同且永不重設！
- **多平台無縫支援**：單一核心架構同時支援主流模組載入器 **Minecraft 1.20.1 (Forge)** 與 **Minecraft 1.21.1 (NeoForge)**。
- **無縫接軌 AuthMeReloaded**：資料庫結構與加密格式（加鹽 SHA-256、BCrypt 等）100% 相容 AuthMe，舊有伺服器資料庫（SQLite / MariaDB / MySQL）可無痛平滑遷移。

---

## 📊 `online-mode` 雙模式特性對照表

NeoAuthReloaded 支援原廠 `server.properties` 中的兩種連線模式，服主可依據伺服器型態自由選擇：

| 伺服器模式 (`online-mode`) | 適用場景 | UUID 機制 | 皮膚載入途徑 | 正版玩家體驗 | 離線玩家體驗 | 存檔安全性 (Playerdata) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`online-mode: false`**<br>*(混合服/模組服推薦首選)* | 離線服<br>正版/離線混合服 | 所有玩家統一使用<br>**離線 UUID (v3)** | 由伺服端皮膚模組<br>(如 SkinRestorer) 自動抓取與同步 | **✅ 免密碼自動登入**<br>(NeoAuth 動態密鑰握手) | ✅ 提示密碼登入或註冊<br>(`/login` 或 `/register`) | 🛡️ **最高**：所有存檔永遠綁定唯一離線 UUID，升級正版或官方斷網永不掉檔！ |
| **`online-mode: true`**<br>*(純官方原生模式)* | 原生正版伺服器 | 正版為 Mojang UUID (v4)<br>離線為離線 UUID (v3) | Minecraft 官方客戶端<br>原廠自動下載 | **✅ 免密碼自動登入**<br>(原生 Mojang 驗證) | ✅ 透過 `allowOfflinePlayers`<br>放行並提示密碼驗證 | 需注意正版/離線切換時因 UUID 不同造成的背包存檔分離。 |

---

## 🌟 模組特色

- **雙平台架構抽象化**：基於模組化設計，一套核心邏輯 (`neoauth-core`) 同時驅動 **Forge 1.20.1** 與 **NeoForge 1.21.1**。
- **動態正版加密握手 (On-Demand Premium Handshake)**：
  - 在 `online-mode: false` 離線伺服器下，自主生成 RSA 加密金鑰並動態發起 Mojang 密鑰握手（`ClientboundHelloPacket`）。
  - **正版玩家免密碼自動登入**：通過 Mojang Session 驗證後直接自動登入，無需輸入密碼，提供最順暢的原生遊玩體驗。
  - **離線玩家自動降級放行**：離線客戶端自動進入密碼登入流程，支援 `/login` 與 `/register`。
- **開箱即用 SQLite & 高效能連線池**：
  - 預設提供零配置的 SQLite 支援（檔案位於 `config/neoauth/neoauth.db`），支援相對路徑跨目錄讀取舊 AuthMe 檔案。
  - 支援 MariaDB 與 MySQL，透過 HikariCP 高效連線池進行執行緒安全的資料操作（SQLite 啟用 WAL 高效並行模式）。
- **AuthMeReloaded 100% 無縫相容**：資料表結構與欄位均相容於 AuthMeReloaded (`authme.db` 或 SQL 資料庫)，加密格式支援雙重加鹽 SHA-256、BCrypt 等。
- **全方位登入前防護**：
  - 🚫 **對話隔離**：未登入玩家無法在聊天頻道發言。
  - 🚫 **指令白名單**：未登入玩家僅可執行白名單指令 (`/login`, `/register`, `/l`, `/reg` 等)。
  - 🚫 **世界防護**：全面禁止方塊破壞、放置、點擊箱子/工作台、丟棄物品與實體互動。
  - 🛡️ **傷害保護**：未登入玩家無法受到任何傷害，亦無法攻擊其他玩家或生物。
  - 🧊 **原地凍結**：自動套用高強度定身效果（緩速、跳躍抑制、失明），防止未驗證玩家移動或窺探地圖。
- **多國語言 (i18n) 與熱重載**：
  - 採用 `config/neoauth/` 資料夾架構，包含 `config.yml`、`commands.yml`、`welcome.txt` 與 `messages/` 目錄。
  - 內建正體中文 (`zhtw`) 與英文 (`en`) 語言檔與幫助指南。
  - 支援熱重載指令 `/neoauth reload`。

---

## 📥 安裝步驟 (Installation)

### 步驟 1：下載並安裝模組
1. 依據您的伺服器端核心與版本，下載對應的 JAR 檔案：
   - **Minecraft 1.20.1 (Forge)**：`neoauth-forge-1.20.1-<version>.jar`
   - **Minecraft 1.21.1 (NeoForge)**：`neoauth-neoforge-1.21.1-<version>.jar`
2. 將下載的 JAR 檔案放入伺服器根目錄下的 `mods/` 資料夾中。

---

### 步驟 2：伺服器模式設定 (`server.properties`)
NeoAuthReloaded 支援兩種運作模式：
* **推薦模式（混合服/模組服）**：
  ```properties
  online-mode=false
  ```
  所有玩家統一採用離線 UUID 存檔（永不掉檔），正版玩家自動免密登入，皮膚交由 SkinRestorer 模組接管。
* **原生線上模式**：
  ```properties
  online-mode=true
  ```
  正版玩家採用 Mojang 原生 UUID 登入，離線玩家由 NeoAuth 放行。

---

## ⚙️ 設定檔說明 (Configuration)

伺服器首次啟動後，將會在伺服器根目錄的 `config/` 資料夾下自動建立目錄結構：

```
config/neoauth/
 ├── commands.yml          # 指令白名單與登入/註冊/登出時自動執行之指令掛鉤
 ├── config.yml            # 主設定檔 (資料庫連線、密碼規則、防護限制、語言切換等)
 ├── welcome.txt           # 玩家進服顯示之彩色歡迎公告 (支援 {PLAYER} 變數與顏色代碼)
 └── messages/             # 多國語言訊息與幫助指南目錄
     ├── help_en.yml       # 英文幫助說明
     ├── help_zhtw.yml     # 正體中文幫助說明
     ├── messages_en.yml   # 英文系統提示訊息
     └── messages_zhtw.yml # 正體中文系統提示訊息
```

### `config.yml` 重點設定一覽：
- `DataSource.backend`：資料庫後端類型，預設 `SQLITE`（開箱即用），亦可設定為 `MARIADB` 或 `MYSQL`。
- `DataSource.sqLiteFile`：SQLite 資料庫檔案路徑，預設 `config/neoauth/neoauth.db`，支援相對路徑（如 `../plugins/AuthMe/authme.db`）與絕對路徑。
- `DataSource`：配置 MariaDB / MySQL 連線主機、埠號、資料庫帳密、資料表名稱與連線池參數。
- `settings.messagesLanguage`：設定提示訊息語言，預設 `zhtw` (正體中文)，可設為 `en` (英文)。
- `settings.allowOfflinePlayers`：是否允許離線（非官方）玩家在線上模式伺服器進入並進行帳密驗證（預設 `true`）。
- `settings.dynamicPremiumVerification`：是否在離線模式 (`online-mode: false`) 下啟用動態正版驗證（預設 `true`）。開啟時，正版玩家將自動經由 Mojang 握手享有免密碼自動登入。
- `settings.registration`：AuthMeReloaded 相容註冊設定區塊：
  - `enabled`：是否開放遊戲內註冊 (`/register`)，預設 `true`。若關閉 (設為 `false`)，則玩家僅能於外部網站或論壇註冊帳號。
  - `messageInterval`：未驗證玩家定期提示間隔秒數（預設 `5` 秒）。
  - `force`：是否強制玩家註冊/登入（設為 `false` 允許未註冊訪客自由遊玩）。
  - `forceKickAfterRegister`：註冊成功後是否直接踢出伺服器（預設 `false`）。
  - `forceLoginAfterRegister`：註冊成功後是否強制要求重新執行 `/login`（預設 `false`）。
- `settings.security`：密碼最短與最長長度設定、密碼雜湊方式 (SHA256 / BCRYPT 等)。
- `settings.restrictions`：登入超時、錯誤密碼次數限制、定身失明與緩速效果開關、歡迎公告顯示開關。

---

## 🛠️ 建置步驟 (Build Instructions)

```bash
# 建置全部平台版本 (產出至 build/libs/)
./gradlew buildAll

# 僅建置 Forge 1.20.1
./gradlew buildForge

# 僅建置 NeoForge 1.21.1
./gradlew buildNeoForge
```

---

## 🎮 遊戲內指令 (In-Game Commands)

### 👤 玩家指令 (Player Commands)

| 指令 | 別名 | 權限需求 | 說明 | 範例 |
| :--- | :--- | :--- | :--- | :--- |
| `/login <password>` | `/l` | 全體玩家 | 進行帳號登入驗證 | `/login myPassword123` |
| `/register <password> [confirm]` | `/reg` | 全體玩家 | 註冊新帳號 | `/register myPassword123 myPassword123` |
| `/changepassword <old> <new> <confirm>` | `/cp` | 已登入玩家 | 修改當前帳號密碼（含二次確認） | `/changepassword old123 new456 new456` |
| `/logout` | - | 已登入玩家 | 登出當前帳號並重新套用防護 | `/logout` |
| `/email` | `/email show` | 已登入玩家 | 查看當前帳號綁定的電子信箱 | `/email` |
| `/email set <newEmail>` | - | 已登入玩家 | 綁定或更新當前帳號的電子信箱 | `/email set player@example.com` |
| `/lastlogin` | - | 已登入玩家 | 查詢自己最後登入時間與註冊日期 | `/lastlogin` |
| `/getip` | - | 已登入玩家 | 查詢自己目前的連線 IP 位址 | `/getip` |

---

### 🛡️ 管理員指令 (Admin Commands - `/neoauth`)

> 所有 `/neoauth` 管理指令皆需要管理員權限 (OP 等級 2 以上)。

| 子指令 | 參數 | 說明 | 範例 |
| :--- | :--- | :--- | :--- |
| `/neoauth help` | `[query]` | 顯示管理員指令幫助清單 | `/neoauth help` |
| `/neoauth register` | `<player> <password>` | 強制為指定玩家註冊帳號 | `/neoauth register Steve pass123` |
| `/neoauth forcelogin` | `[player]` | 強制登入指定玩家（未填則為自己） | `/neoauth forcelogin Steve` |
| `/neoauth password` | `<player> <newPassword>` | 強制變更指定玩家的密碼（別名 `changepassword`, `pass`） | `/neoauth password Steve newPass123` |
| `/neoauth lastlogin` | `[player]` | 查詢指定玩家的最後登入時間、IP 與註冊日期 | `/neoauth lastlogin Steve` |
| `/neoauth accounts` | `[player \| IP]` | 查詢與指定玩家名稱或 IP 關聯的所有同 IP 帳號 | `/neoauth accounts Steve` |
| `/neoauth email` | `[player]` | 查詢指定玩家設定的電子信箱 | `/neoauth email Steve` |
| `/neoauth email set` | `<player> <email>` | 為指定玩家設定或更新電子信箱（別名 `/neoauth setemail`） | `/neoauth email set Steve steve@example.com` |
| `/neoauth getip` | `<player>` | 查詢指定玩家的 IP 位址（線上或最後紀錄） | `/neoauth getip Steve` |
| `/neoauth setenableregister` | `<true \| false>` | 動態切換是否開放遊戲內註冊，並自動保存至 `config.yml` | `/neoauth setenableregister false` |
| `/neoauth reload` | - | 熱重載所有設定檔 (`config.yml`, `commands.yml`, `welcome.txt`) 與訊息檔 | `/neoauth reload` |
| `/neoauth version` | - | 顯示 NeoAuth 模組版本與目前運行的平台 (Forge / NeoForge) | `/neoauth version` |
| `/neoauth recent` | - | 顯示伺服器最近登入的玩家清單與登入時間 | `/neoauth recent` |

---

## 📄 授權條款 (License)

本專案採用 [GPLv3](LICENSE) 條款開源發布。
