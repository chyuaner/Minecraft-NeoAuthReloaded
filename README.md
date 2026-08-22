# 🔐 NeoAuth

<div align="center">

**輕量級 Minecraft 伺服器身分驗證與登入防護模組**  
同時支援 **Minecraft 1.20.1 (Forge)** 與 **Minecraft 1.21.1 (NeoForge)**

</div>

---

## 🌟 模組特色

- **雙平台架構抽象化**：基於模組化設計，一套核心邏輯 (`neoauth-core`) 同時驅動 **Forge 1.20.1** 與 **NeoForge 1.21.1**。
- **預設開箱即用 SQLite**：為一般服主提供零配置的 SQLite 支援，預設檔案為 `config/neoauth/neoauth.db`，亦支援絕對路徑與相對路徑（支援 `../` 跨目錄）。
- **AuthMeReloaded 無縫相容**：無論是 SQLite 或 MySQL/MariaDB，資料表結構與欄位均 100% 與 AuthMeReloaded (`authme.db` 或 SQL 資料庫) 相容，加密格式預設加鹽 SHA-256 / BCrypt，可直接共用！
- **可擴充之資料庫抽象層 (IDataSource)**：統一封裝 ANSI SQL 操作，並透過 HikariCP 提供執行緒安全且高效能的連線管理（SQLite 啟用 WAL 高效並行模式）。
- **正版自動登入 / 混合模式支援**：
  - 正版（Mojang Online-Mode）玩家進入伺服器時自動辨識並通過驗證，無需輸入密碼。
  - 支援 `allowOfflinePlayers` 混合模式：即使伺服器開啟 `online-mode=true`，亦可放行離線玩家並要求密碼驗證。
- **全方位登入前防護**：
  - 🚫 **對話隔離**：未登入玩家無法在聊天頻道發言。
  - 🚫 **指令白名單**：未登入玩家僅可執行白名單指令 (`/login`, `/register`, `/l`, `/reg` 等)。
  - 🚫 **世界防護**：全面禁止方塊破壞、放置、點擊箱子/工作台、丟棄物品與實體互動。
  - 🛡️ **傷害保護**：未登入玩家無法受到任何傷害，亦無法攻擊其他玩家或生物。
  - 🧊 **原地凍結**：自動套用高強度定身效果（緩速、跳躍抑制、失明），防止未驗證玩家移動或窺探地圖。
- **AuthMeReloaded 風格設定檔與多國語言 (i18n)**：
  - 採用 `config/neoauth/` 資料夾架構，包含 `config.yml`、`commands.yml`、`welcome.txt` 與 `messages/` 目錄。
  - 內建繁體中文 (`zhtw`) 與英文 (`en`) 語言檔與幫助指南。
  - 支援熱重載管理員指令 `/neoauth reload`。

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
- `settings.security`：密碼最短與最長長度設定、密碼雜湊方式 (BCRYPT)。
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
| `/neoauth reload` | - | 熱重載所有設定檔 (`config.yml`, `commands.yml`, `welcome.txt`) 與訊息檔 | `/neoauth reload` |
| `/neoauth version` | - | 顯示 NeoAuth 模組版本與目前運行的平台 (Forge / NeoForge) | `/neoauth version` |
| `/neoauth recent` | - | 顯示伺服器最近登入的玩家清單與登入時間 | `/neoauth recent` |

---

## 📄 授權條款 (License)

本專案採用 [GPLv3](LICENSE) 條款開源發布。
