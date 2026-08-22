# 🔐 NeoAuth

<div align="center">

**輕量級 Minecraft 伺服器身分驗證與登入防護模組**  
同時支援 **Minecraft 1.20.1 (Forge)** 與 **Minecraft 1.21.1 (NeoForge)**

</div>

---

## 🌟 模組特色

- **雙平台架構抽象化**：基於模組化設計，一套核心邏輯 (`neoauth-core`) 同時驅動 **Forge 1.20.1** 與 **NeoForge 1.21.1**。
- **AuthMeReloaded 無縫相容**：使用相同的 MariaDB 資料表結構與加密格式（預設加鹽 SHA-256、BCrypt），可直接與既有 AuthMe 資料庫共用！
- **高效能資料庫連線池**：內建 HikariCP 高效連線池，連線快速、穩定且支援自動斷線重連與伺服器關閉時安全釋放。
- **正版自動登入 / 混合模式支援**：
  - 正版（Mojang Online-Mode）玩家進入伺服器時自動辨識並通過驗證，無需輸入密碼。
  - 支援 `allowOfflinePlayers` 混合模式：即使伺服器開啟 `online-mode=true`，亦可放行離線玩家並要求密碼驗證。
- **全方位登入前防護**：
  - 🚫 **對話隔離**：未登入玩家無法在聊天頻道發言。
  - 🚫 **指令白名單**：未登入玩家僅可執行登入與註冊指令 (`/login`, `/register`, `/l`, `/reg`)。
  - 🚫 **世界防護**：全面禁止方塊破壞、放置、點擊箱子/工作台、丟棄物品與實體互動。
  - 🛡️ **傷害保護**：未登入玩家無法受到任何傷害，亦無法攻擊其他玩家或生物。
  - 🧊 **原地凍結**：自動套用高強度定身效果（緩速、跳躍抑制、失明），防止未驗證玩家移動或窺探地圖。

---

## 📁 專案架構說明

本專案採用清晰的多模組 Gradle 架構：

```
neoauth/
├── neoauth-core/                         # 共用核心模組 (純 Java 業務邏輯與抽象層)
│   └── src/main/java/tw/yuaner/neoauth/
│       ├── AuthManager.java              # 登入狀態與 Mojang 正版狀態管理 (執行緒安全)
│       ├── DatabaseManager.java          # MariaDB / HikariCP 連線池與 SQL 操作
│       ├── PasswordManager.java          # 密碼加密與比對 (AuthMe 相容加鹽 SHA256 / BCrypt)
│       ├── config/
│       │   └── IAuthConfig.java          # 設定檔抽象介面
│       ├── platform/
│       │   ├── IPlatformHelper.java      # 平台服務介面 (藥水效果、訊息發送、設定實例等)
│       │   └── Services.java             # Java SPI (ServiceLoader) 平台服務載入器
│       └── core/
│           └── AuthLogic.java            # 核心驗證流程 (登入/註冊判定、指令過濾、提示生成)
├── neoauth-forge/                        # Minecraft 1.20.1 Forge 專用模組
│   ├── src/main/java/tw/yuaner/neoauth/forge/
│   │   ├── ForgeAuthMod.java             # Forge 模組進入點 (@Mod)
│   │   ├── ForgeConfig.java              # ForgeConfigSpec 伺服器設定實作
│   │   ├── ForgePlatformHelper.java      # Forge 1.20.1 平台效果與訊息實作
│   │   ├── ForgeEvents.java              # Forge 匯流排事件監聽器與防護轉發
│   │   └── mixin/ForgeServerLoginMixin.java # 1.20.1 登入握手 Mixin
│   └── src/main/resources/
│       ├── META-INF/mods.toml
│       └── neoauth-forge.mixins.json
├── neoauth-neoforge/                     # Minecraft 1.21.1 NeoForge 專用模組
│   ├── src/main/java/tw/yuaner/neoauth/neoforge/
│   │   ├── NeoForgeAuthMod.java          # NeoForge 模組進入點 (@Mod)
│   │   ├── NeoForgeConfig.java           # ModConfigSpec 伺服器設定實作
│   │   ├── NeoForgePlatformHelper.java   # NeoForge 1.21.1 平台效果與訊息實作
│   │   ├── NeoForgeEvents.java           # NeoForge 匯流排事件監聽器與防護轉發
│   │   └── mixin/NeoForgeServerLoginMixin.java # 1.21.1 登入握手 Mixin
│   └── src/main/resources/
│       ├── META-INF/neoforge.mods.toml
│       └── neoauth-neoforge.mixins.json
├── samples/
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

伺服器首次啟動後，將會在伺服器根目錄的 `config/` 資料夾下產生 `neoauth-server.toml` 設定檔：

```toml
#NeoAuth 伺服器驗證設定檔
[Database]
    # MariaDB 資料庫主機位址 (預設: 127.0.0.1)
    host = "127.0.0.1"
    # MariaDB 資料庫連接埠 (預設: 3306)
    port = "3306"
    # MariaDB 資料庫名稱 (預設: neoauth)
    database = "neoauth"
    # MariaDB 資料庫使用者名稱 (預設: root)
    username = "neoauth"
    # MariaDB 資料庫密碼
    password = "a12345"
    # NeoAuth 資料表名稱 (預設: neoauth)
    table = "neoauth"

[Authentication]
    # 當伺服器 server.properties 設定 online-mode=true 時，
    # 是否仍允許離線（盜版/非官方）玩家進入並使用帳密登入 (true: 混合模式, false: 純正版)
    allowOfflinePlayers = true
```

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

| 指令 | 說明 | 範例 |
| :--- | :--- | :--- |
| `/login <password>` | 進行帳號登入 | `/login myPassword123` |
| `/l <password>` | 登入指令簡寫 | `/l myPassword123` |
| `/register <password> <confirm>` | 進行新帳號註冊 | `/register myPassword123 myPassword123` |
| `/reg <password> <confirm>` | 註冊指令簡寫 | `/reg myPassword123 myPassword123` |

---

## 📄 授權條款 (License)

本專案採用 [GPLv3](LICENSE) 條款開源發布。
