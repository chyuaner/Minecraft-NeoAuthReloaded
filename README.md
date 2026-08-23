# 🔐 NeoAuthReloaded

<div align="center">

**輕量級 Minecraft 伺服器登入驗證模組**  
同時支援 **Minecraft 1.20.1 (Forge)** 與 **Minecraft 1.21.1 (NeoForge)**

</div>

---

本專案是大量借鑒 **AuthMeReloaded** 的風格與傳統設計，重新復刻出 **NeoAuthReloaded** 模組，並提供 AuthMeReloaded 沒有提供的 Forge 與 NeoForge 模組支援。採用的資料庫都相容於原AuthMe的資料庫結構，並支援使用原AuthMe的資料庫，讓原本使用AuthMe的服主可以直接無縫切換到NeoAuthReloaded。設定檔風格也比照AuthMeReloaded設計，讓原本使用AuthMe的服主能快速上手。

### 核心優勢亮點：
- **`online-mode=true` 完美混合驗證**：支援在標準 `online-mode=true` 模式下運行，同時相容官方正版玩家與離線（非官方）玩家！
- **正版玩家順暢自動登入**：正版玩家連線時將由 Mojang 官方伺服器進行驗證，通過後直接自動登入，**享有完全免輸入密碼的絲滑體驗**（多數其他同類模組皆未實現此功能）。
- **完善的 Online / Offline UUID 處理機制**：深度處理線上版 UUID (v4) 與離線版 UUID (v3) 的對應與相容，徹底解決玩家更換啟動器或連線模式時的存檔分裂與衝突問題。
- **無縫接軌 AuthMeReloaded**：資料庫結構與加密格式（加鹽 SHA-256、BCrypt 等）100% 相容 AuthMe，舊有伺服器資料庫與設定可無痛平滑遷移。

---

## 🌟 模組特色

- **雙平台架構抽象化**：基於模組化設計，一套核心邏輯 (`neoauth-core`) 同時驅動 **Forge 1.20.1** 與 **NeoForge 1.21.1**。
- **極致順暢的正版自動登入與混合模式**：
  - 在伺服器開啟 `online-mode=true` 模式下，智慧攔截並鑑別玩家身分。
  - **正版玩家免密碼自動登入**：正版（Mojang）玩家通過線上驗證後直接自動登入，無需頻繁輸入密碼，提供最順暢的原生遊玩體驗。
  - **離線玩家安全放行**：支援 `allowOfflinePlayers` 混合模式，離線玩家連線後強制進行密碼註冊與登入驗證。
- **線上版 (Online) 與離線版 (Offline) UUID 智慧相容機制**：
  - 完整支援並妥善處理正版 Online UUID (v4) 與離線 Offline UUID (v3)。
  - 提供 `keepOfflineUuidCompatibility` 設定：可強制統一使用離線 UUID 儲存背包存檔與模組資料，同時完整保留正版玩家官方 Skin 造型與自動登入特權，徹底解決換啟動器或斷網時存檔遺失與分裂問題。
- **預設開箱即用 SQLite**：為一般服主提供零配置的 SQLite 支援，預設檔案為 `config/neoauth/neoauth.db`，亦支援絕對路徑與相對路徑（支援 `../` 跨目錄讀取舊 AuthMe 檔案）。
- **AuthMeReloaded 無縫相容**：無論是 SQLite 或 MySQL/MariaDB，資料表結構與欄位均 100% 與 AuthMeReloaded (`authme.db` 或 SQL 資料庫) 相容，加密格式預設雙重加鹽 SHA-256 / BCrypt，可直接共用！
- **可擴充之資料庫抽象層 (IDataSource)**：統一封裝 ANSI SQL 操作，並透過 HikariCP 提供執行緒安全且高效能的連線管理（SQLite 啟用 WAL 高效並行模式）。
- **全方位登入前防護**：
  - 🚫 **對話隔離**：未登入玩家無法在聊天頻道發言。
  - 🚫 **指令白名單**：未登入玩家僅可執行白名單指令 (`/login`, `/register`, `/l`, `/reg` 等)。
  - 🚫 **世界防護**：全面禁止方塊破壞、放置、點擊箱子/工作台、丟棄物品與實體互動。
  - 🛡️ **傷害保護**：未登入玩家無法受到任何傷害，亦無法攻擊其他玩家或生物。
  - 🧊 **原地凍結**：自動套用高強度定身效果（緩速、跳躍抑制、失明），防止未驗證玩家移動或窺探地圖。
- **AuthMeReloaded 風格設定檔與多國語言 (i18n)**：
  - 採用 `config/neoauth/` 資料夾架構，包含 `config.yml`、`commands.yml`、`welcome.txt` 與 `messages/` 目錄。
  - 內建正體中文 (`zhtw`) 與英文 (`en`) 語言檔與幫助指南。
  - 支援熱重載管理員指令 `/neoauth reload`。

---

## 📥 安裝步驟 (Installation)

### 步驟 1：下載並安裝模組
1. 依據您的伺服器端核心與版本，下載對應的 JAR 檔案：
   - **Minecraft 1.20.1 (Forge)**：`neoauth-forge-1.20.1-<version>.jar`
   - **Minecraft 1.21.1 (NeoForge)**：`neoauth-neoforge-1.21.1-<version>.jar`
2. 將下載的 JAR 檔案放入伺服器根目錄下的 `mods/` 資料夾中。

---

### 步驟 2：伺服器核心設定（⚠️ 極重要關鍵）
請開啟伺服器根目錄下的 `server.properties` 檔案，確認並設置：
```properties
online-mode=true
```

> [!IMPORTANT]
> **請務必保持 `online-mode=true`！**  
> 許多服主在安裝傳統登入驗證插件/模組時，習慣將 `online-mode` 改為 `false`。然而 **NeoAuthReloaded 核心專為 `online-mode=true` 環境設計**：
> - 伺服器在 `online-mode=true` 下才能向 Mojang 伺服器驗證正版玩家身分，實現**正版玩家免密碼自動登入**並正確載入官方 Skin 造型。
> - 離線（非官方）玩家則會由 NeoAuthReloaded 自動在握手階段放行並強制執行密碼驗證。
> - 若將 `online-mode` 設為 `false`，所有玩家都將被視為離線玩家，失去正版自動登入與官方驗證的優勢！

---

### 步驟 3：啟動伺服器以生成設定檔
1. 啟動伺服器，NeoAuthReloaded 將自動在伺服器目錄生成 `config/neoauth/` 配置資料夾。
2. 檢查控制台確認模組已成功載入並完成 SQLite 資料庫初始化。

---

### 步驟 4：調整模組設定檔 (`config/neoauth/config.yml`)
開啟 `config/neoauth/config.yml`，主要可調整以下設定：

#### 1. UUID 相容性設定 (`keepOfflineUuidCompatibility`)
```yaml
settings:
  keepOfflineUuidCompatibility: false # (預設值)
```
- **關於 `keepOfflineUuidCompatibility` 的說明與建議**：
  - **強烈建議服主開啟 (`true`)**：在實際開服營運中，建議將此項設為 `true`。啟用後，不論正版或離線玩家，伺服器一律使用固定的「離線 UUID (v3)」來儲存玩家存檔、背包物品與各模組資料（正版玩家仍享有自動登入與官方 Skin）。這能徹底避免玩家偶爾更換啟動器、Mojang 驗證伺服器暫時斷線或網路切換時，導致 UUID 改變而引發存檔重設或分裂。
  - **為何本專案預設為 `false`**：為了讓原本使用 AuthMeReloaded 或官方純淨伺服器的服主能夠**最無痛、平滑地直接轉移既有資料庫與存檔**，因此預設維持 `false`。服主可依據自身伺服器架構評估開啟。

#### 2. 資料庫後端配置 (`DataSource`)
- **SQLite (預設)**：開箱即用，無需額外安裝資料庫，檔案位於 `config/neoauth/neoauth.db`（亦可指定 AuthMe 的 `authme.db` 路徑）。
- **MySQL / MariaDB**：若有多伺服器同步需求，可將 `backend` 改為 `MARIADB` 或 `MYSQL` 並設定連線資訊。

---

### 步驟 5：套用設定
完成設定檔修改後，您可以在遊戲內或控制台執行指令立即生效，無需重啟伺服器：
```bash
/neoauth reload
```

---

## 📁 專案架構說明

本專案採用清晰的多模組 Gradle 架構：

```
neoauth/
├── neoauth-core/                         # 共用核心模組 (純 Java 業務邏輯、設定檔管理器與抽象層)
│   ├── src/main/java/tw/yuaner/neoauth/
│   │   ├── AuthManager.java              # 登入狀態與 Mojang 正版狀態管理 (執行緒安全)
│   │   ├── DatabaseManager.java          # 資料庫操作靜態 Facade 門面與工廠
│   │   ├── PasswordManager.java          # 密碼加密與比對 (AuthMe 相容加鹽 SHA256 / BCrypt)
│   │   ├── database/                     # 資料庫抽象架構層
│   │   │   ├── IDataSource.java          # 資料庫操作抽象合約介面
│   │   │   ├── AbstractSqlDataSource.java# 共用 ANSI SQL 核心實作
│   │   │   ├── SqliteDataSource.java     # SQLite 資料來源實作 (WAL 模式、路徑自動解析)
│   │   │   ├── MySqlDataSource.java      # MariaDB / MySQL 資料來源實作 (HikariCP 連線池)
│   │   │   └── PlayerAuthData.java       # 玩家帳號資料模型
│   │   ├── config/
│   │   │   ├── ConfigManager.java        # 設定檔載入、熱重載與範本釋出管理器
│   │   │   ├── IAuthConfig.java          # 設定檔抽象介面
│   │   │   ├── NeoAuthConfig.java        # config.yml 主設定實作 (支援 sqLiteFile 等)
│   │   │   ├── CommandsConfig.java       # commands.yml 指令設定實作
│   │   │   └── MessagesManager.java      # 多國語言訊息與顏色碼轉換管理器
│   │   ├── platform/
│   │   │   ├── IPlatformHelper.java      # 平台服務介面 (藥水效果、訊息發送、設定實例等)
│   │   │   └── Services.java             # Java SPI (ServiceLoader) 平台服務載入器
│   │   └── core/
│   │       └── AuthLogic.java            # 核心驗證流程 (登入/註冊判定、指令過濾、提示生成)
│   └── src/main/resources/defaults/      # 內建預設範本檔案 (首啟時自動釋放)
├── neoauth-forge/                        # Minecraft 1.20.1 Forge 專用模組
│   ├── src/main/java/tw/yuaner/neoauth/forge/
│   │   ├── ForgeAuthMod.java             # Forge 模組進入點 (@Mod)
│   │   ├── ForgePlatformHelper.java      # Forge 1.20.1 平台效果與訊息實作
│   │   ├── ForgeEvents.java              # Forge 匯流排事件監聽器與防護轉發
│   │   └── mixin/ForgeServerLoginMixin.java # 1.20.1 登入握手 Mixin
│   └── src/main/resources/
│       ├── META-INF/mods.toml
│       └── neoauth-forge.mixins.json
├── neoauth-neoforge/                     # Minecraft 1.21.1 NeoForge 專用模組
│   ├── src/main/java/tw/yuaner/neoauth/neoforge/
│   │   ├── NeoForgeAuthMod.java          # NeoForge 模組進入點 (@Mod)
│   │   ├── NeoForgePlatformHelper.java   # NeoForge 1.21.1 平台效果與訊息實作
│   │   ├── NeoForgeEvents.java           # NeoForge 匯流排事件監聽器與防護轉發
│   │   └── mixin/NeoForgeServerLoginMixin.java # 1.21.1 登入握手 Mixin
│   └── src/main/resources/
│       ├── META-INF/neoforge.mods.toml
│       └── neoauth-neoforge.mixins.json
├── samples/
│   ├── config/neoauth/                   # 預設設定檔與語言檔範例
│   └── databases/
│       └── mariadb/                      # MariaDB 本機快速測試環境 (Docker Compose)
├── build.gradle                          # 根專案建置與聚合任務腳本
├── settings.gradle                       # 多專案模組定義
└── gradle.properties                     # 全域版本設定
```

---

## 🛠️ 建置需求 (Prerequisites)

- **JDK 版本**：
  - 建置 Forge 1.20.1 需要 **Java 17**。
  - 建置 NeoForge 1.21.1 需要 **Java 21**。
  - 專案已配置 Foojay Toolchain Resolver，若系統缺少對應版本 JDK，Gradle 將自動嘗試下載。
- **Gradle**：已隨附 `gradlew` (Gradle 8.10+)。

---

## 🚀 建置步驟 (Build Instructions)

本專案提供靈活的建置指令，您可以自由選擇單獨建置指定目標，或是同時輸出所有平台的模組 JAR 檔：

### 1. 同時建置所有版本 (1.20.1 Forge + 1.21.1 NeoForge)
```bash
./gradlew buildAll
# 或直接執行
./gradlew build
```
建置完成後，各模組產出的 JAR 檔案將自動集中收集至根目錄的 `build/libs/` 資料夾：
- `build/libs/neoauth-forge-1.20.1-1.0.0.jar`
- `build/libs/neoauth-neoforge-1.21.1-1.0.0.jar`

### 2. 僅建置 Minecraft 1.20.1 (Forge) 模組
```bash
./gradlew buildForge
# 或
./gradlew :neoauth-forge:build
```
產出檔案位置：`neoauth-forge/build/libs/neoauth-forge-1.20.1-1.0.0.jar`

### 3. 僅建置 Minecraft 1.21.1 (NeoForge) 模組
```bash
./gradlew buildNeoForge
# 或
./gradlew :neoauth-neoforge:build
```
產出檔案位置：`neoauth-neoforge/build/libs/neoauth-neoforge-1.21.1-1.0.0.jar`

---

## ⚙️ 設定檔說明 (Configuration)

伺服器首次啟動後，將會在伺服器根目錄的 `config/` 資料夾下自動建立 **AuthMeReloaded** 風格的目錄結構：

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
- `settings.allowOfflinePlayers`：是否允許離線（非官方）玩家在線上模式伺服器進入並進行帳密驗證。
- `settings.keepOfflineUuidCompatibility`：是否強制保持離線版 UUID 相容模式（預設 `false`；開服營運強烈建議設為 `true` 以維持最高存檔穩定性與一致性）。
- `settings.registration`：AuthMeReloaded 相容註冊設定區塊：
  - `enabled`：是否開放遊戲內註冊 (`/register`)，預設 `true`。若關閉 (設為 `false`)，則玩家僅能於外部網站或論壇註冊帳號。
  - `messageInterval`：未驗證玩家定期提示間隔秒數（預設 `5` 秒）。
  - `force`：是否強制玩家註冊/登入（設為 `false` 允許未註冊訪客自由遊玩）。
  - `forceKickAfterRegister`：註冊成功後是否直接踢出伺服器（預設 `false`）。
  - `forceLoginAfterRegister`：註冊成功後是否強制要求重新執行 `/login`（預設 `false`）。
- `settings.security`：密碼最短與最長長度設定、密碼雜湊方式 (SHA256 / BCRYPT 等)。
- `settings.restrictions`：登入超時、錯誤密碼次數限制、定身失明與緩速效果開關、歡迎公告顯示開關。

---

## 🐳 快速啟動 MariaDB 測試環境 (Docker Compose)

在 `samples/databases/mariadb` 目錄下已為您準備好快速測試用的 Docker Compose 配置：

```bash
cd samples/databases/mariadb

# 啟動 MariaDB 容器 (預設資料庫: neoauth, 使用者: neoauth / 密碼: a12345)
docker compose up -d

# 停止容器
docker compose down
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
| `/neoauth setemail` | `<player> <email>` | 為指定玩家設定或更新電子信箱 | `/neoauth setemail Steve steve@example.com` |
| `/neoauth getip` | `<player>` | 查詢指定玩家的 IP 位址（線上或最後紀錄） | `/neoauth getip Steve` |
| `/neoauth setenableregister` | `<true \| false>` | 動態切換是否開放遊戲內註冊，並自動保存至 `config.yml` | `/neoauth setenableregister false` |
| `/neoauth reload` | - | 熱重載所有設定檔 (`config.yml`, `commands.yml`, `welcome.txt`) 與訊息檔 | `/neoauth reload` |
| `/neoauth version` | - | 顯示 NeoAuth 模組版本與目前運行的平台 (Forge / NeoForge) | `/neoauth version` |
| `/neoauth recent` | - | 顯示伺服器最近登入的玩家清單與登入時間 | `/neoauth recent` |

---

## 📄 授權條款 (License)

本專案採用 [GPLv3](LICENSE) 條款開源發布。
