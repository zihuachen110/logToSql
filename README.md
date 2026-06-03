# Log To SQL

IntelliJ IDEA 插件：将 MyBatis、Hibernate、Spring JDBC 等 ORM 日志中的 **SQL + 参数** 合并为可直接在数据库客户端执行的 SQL。

---

## 功能

| 方式 | 说明 |
|------|------|
| **控制台自动打印** | 在 IDEA 中 **Run / Debug** 运行项目，MyBatis 输出 `==> Parameters:` 后，在 **Run / Debug → Console** 自动追加一行红色可执行 SQL |
| **选中转换** | 编辑器选中日志 → 右键 → **Log To SQL → Convert Selection to SQL** |
| **工具窗口** | **Tools → Log To SQL → Open Converter**，粘贴日志后转换并复制 |

---

## 支持的日志格式

### MyBatis

```
==>  Preparing: SELECT id, name FROM user WHERE id = ? AND status = ?
==> Parameters: 100(Long), ACTIVE(String)
```

输出：

```sql
SELECT id, name FROM user WHERE id = 100 AND status = 'ACTIVE'
```

无参数时也支持（`Parameters:` 为空时直接输出完整 SQL）：

```
==>  Preparing: SELECT id FROM ogsm_period WHERE deleted = 0
==> Parameters:
```

### Hibernate

```
Hibernate: select user0_.id from user user0_ where user0_.id=?
binding parameter [1] as [BIGINT] - [42]
```

### Spring JDBC

```
Executing SQL: SELECT * FROM orders WHERE user_id = ?
Parameters: [1001]
```

带 log4j 前缀的行会自动截取 `==> Preparing` / `==> Parameters` 段再解析。

---

## 安装

### 从 zip 安装（日常使用）

1. 构建插件（见下方「开发」章节）
2. 打开 IDEA：**Settings → Plugins → ⚙ → Install Plugin from Disk...**
3. 选择 `build/distributions/log-to-sql-1.0.0.zip`
4. 重启 IDEA

### 环境要求

- IntelliJ IDEA **2024.2+**（Community / Ultimate）
- 仅支持 IDEA，不支持 VS Code / Cursor

---

## 使用说明

### 1. 控制台自动模式（推荐）

适用于本地 **Run** 或 **Debug** 启动 Spring Boot / Java 项目。

1. 用 **Debug** 或 **Run** 启动应用
2. 打开底部 **Run** 或 **Debug** 窗口，选中 **Console** Tab
3. 触发 MyBatis 查询，看到类似日志：

   ```
   ==>  Preparing: select ...
   ==> Parameters: 965(Long)
   ```

4. 紧接着会出现红色一行：

   ```
   ▶ [Log To SQL] select ... WHERE id=965
   ```

可直接复制到 Navicat、DBeaver 等工具执行。

**不适用场景：**

- 外部 CMD / PowerShell 终端
- 单独打开的 `.log` 文件
- 仅 tail 远程服务器日志、未在 IDEA Run/Debug 控制台显示

以上场景请用「选中转换」或「工具窗口」。

### 2. 选中日志转换

1. 从控制台或日志文件复制 MyBatis 日志
2. 粘贴到 IDEA 任意编辑器并**选中**（需包含 `Preparing` 与 `Parameters` 行）
3. 右键 → **Log To SQL → Convert Selection to SQL**
4. 底部 **Log To SQL** 工具窗口显示结果，点 **复制 SQL**

### 3. 工具窗口

1. **Tools → Log To SQL → Open Converter**
2. 或底部 **Log To SQL** 面板
3. 粘贴日志 → **转换** → **复制 SQL** / **清空**

---

## 开发

### 环境要求

| 项目 | 版本 |
|------|------|
| JDK | **17+**（Gradle 与插件编译均需，Java 8 不可用） |
| IntelliJ IDEA | **2024.2+**（与 `gradle.properties` 中 `platformVersion` 一致） |

### 首次配置 JDK 17

**方式 A：IDEA 内配置（推荐）**

1. 安装 JDK 17（[清华镜像](https://mirrors.tuna.tsinghua.edu.cn/Adoptium/17/jdk/x64/windows/) 或 `winget install EclipseAdoptium.Temurin.17.JDK`）
2. **File → Settings → Build Tools → Gradle → Gradle JVM** 选 **JDK 17**
3. 刷新 Gradle 面板

**方式 B：命令行**

```powershell
# 自动写入 org.gradle.java.home
powershell -ExecutionPolicy Bypass -File setup-jdk.ps1

# 或使用脚本打包（自动查找 JDK 17）
.\build-plugin.bat
```

Gradle 下载慢时，已在 `gradle/wrapper/gradle-wrapper.properties` 使用国内镜像。

### 构建命令

```powershell
cd d:\work\idea-sql

# 打包安装 zip
.\gradlew.bat buildPlugin

# 启动沙箱 IDEA 调试插件
.\gradlew.bat runIde

# 运行单元测试
.\gradlew.bat test
```

打包产物：`build/distributions/log-to-sql-1.0.0.zip`

IDEA 中也可在 Gradle 面板执行 **intellijPlatform → buildPlugin** / **runIde**。

### 项目结构

```
src/main/kotlin/com/logtosql/
├── core/
│   └── SqlLogMerger.kt           # 日志解析、参数绑定、SQL 合并
├── console/
│   ├── MyBatisLogLineHandler.kt  # 控制台行处理与红色 SQL 输出
│   ├── MyBatisConsoleFilter.kt   # 拦截 Console 每一行
│   ├── MyBatisConsoleExecutionListener.kt  # Run 进程 stdout 监听
│   └── MyXDebuggerManagerListener.kt       # Debug 会话绑定
├── action/                       # 右键菜单、Tools 菜单
└── ui/                           # Log To SQL 工具窗口

src/test/kotlin/                  # SqlLogMerger 单元测试
```

### 扩展新的日志格式

在 `SqlLogMerger.kt` 中新增 `tryXxx()`，并在 `merge()` 里按优先级调用。参数替换复用 `bindParams()` 与 `formatValue()`。

### 常见问题

| 现象 | 处理 |
|------|------|
| Gradle 同步失败，提示需要 Java 17 | 安装 JDK 17，Gradle JVM 选 17 |
| Gradle 面板空白 | 确认 Gradle JVM 为 17，点刷新；或 **Link Gradle Project** 选 `build.gradle.kts` |
| 控制台没有红色 SQL | 确认日志在 **Run/Debug → Console**；重新安装最新插件 zip |
| `buildPlugin` 内存不足 | `gradle.properties` 已设 `-Xmx4096m`，可关闭其它占内存程序 |

---

## License

MIT

## Star History

<a href="https://www.star-history.com/?repos=zihuachen110%2FlogToSql&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=zihuachen110/logToSql&type=date&theme=dark&legend=top-left" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=zihuachen110/logToSql&type=date&legend=top-left" />
   <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=zihuachen110/logToSql&type=date&legend=top-left" />
 </picture>
</a>
