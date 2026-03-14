# 效率管理系统 — Java版代码说明文档

## 一、项目概述

本系统是一个企业员工电脑行为监控工具，核心功能是实时监控员工电脑上运行的进程，当检测到被标记为"游戏"的违规进程时，自动截屏保存证据。系统采用纯Java实现，使用Java Swing构建桌面GUI界面，SQLite作为本地数据库，是一个单机版桌面应用程序。

### 技术栈

| 技术 | 用途 |
|------|------|
| Java 17 | 开发语言 |
| Java Swing + FlatLaf | 桌面GUI界面（FlatLaf提供现代化外观） |
| SQLite + JDBC | 本地用户数据存储 |
| Gson | JSON文件读写（标记进程持久化） |
| JavaMail | SMTP邮件发送（注册验证码） |
| AWT Robot | 屏幕截图 |
| Maven | 项目构建与依赖管理 |

### 项目结构

```
EfficiencyManagementSystem-Java/
├── pom.xml                                    # Maven项目配置文件
└── src/main/java/com/efficiency/
    ├── Main.java                              # 程序入口
    ├── model/                                 # 实体类层
    │   ├── ProcessInfo.java                   # 进程信息实体
    │   └── MarkedGame.java                    # 标记游戏进程实体
    ├── service/                               # 业务逻辑层
    │   ├── UserManager.java                   # 用户管理（数据库操作）
    │   ├── AuthClient.java                    # 认证客户端（登录/注册/验证码）
    │   ├── EmailSender.java                   # 邮件发送器
    │   ├── ProcessMonitor.java                # 进程监控器
    │   ├── MarkedGameManager.java             # 标记游戏进程管理器
    │   └── ScreenCapture.java                 # 截屏工具
    └── ui/                                    # 界面层
        ├── MainWindow.java                    # 主窗口
        ├── LoginDialog.java                   # 登录对话框
        ├── RegisterDialog.java                # 注册对话框
        └── MarkedProcessManagerDialog.java    # 标记进程管理对话框
```

---

## 二、程序入口 — Main.java

```java
public class Main {
    public static void main(String[] args) {
        FlatLightLaf.setup();  // 设置FlatLaf现代化外观主题
        SwingUtilities.invokeLater(() -> {
            MainWindow window = new MainWindow();
            window.setVisible(true);
        });
    }
}
```

程序启动后做两件事：
1. 初始化FlatLaf外观主题，让Swing界面看起来更现代，不像默认的Java那么丑
2. 在Swing事件线程中创建并显示主窗口（Swing要求所有UI操作必须在EDT线程中执行）

---

## 三、实体类层（model包）

### 3.1 ProcessInfo.java — 进程信息

存储单个进程的运行信息，包含四个字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| processName | String | 进程名称，如"notepad.exe" |
| cpuUsage | double | CPU使用率，0.0~100.0 |
| pid | long | 进程ID |
| updateTime | LocalDateTime | 信息采集时间戳 |

这个类在进程监控模块中被大量使用，每次扫描系统进程时，每个进程都会被封装成一个ProcessInfo对象。

### 3.2 MarkedGame.java — 标记的游戏进程

存储被用户标记为"游戏"的进程信息：

| 字段 | 类型 | 说明 |
|------|------|------|
| processName | String | 进程名称 |
| markedTime | String | 标记时间（ISO格式） |
| description | String | 描述信息 |
| autoMonitor | boolean | 是否自动监控 |

这些数据会被持久化到marked_games.json文件中。

---

## 四、业务逻辑层（service包）

### 4.1 UserManager.java — 用户管理器

负责本地SQLite数据库的用户数据管理，是整个认证系统的数据层。

数据库初始化：
```java
public boolean initDatabase() {
    connection = DriverManager.getConnection("jdbc:sqlite:user_credentials.db");
    // 创建users表：id(自增主键), username(唯一), password_hash
    stmt.execute("CREATE TABLE IF NOT EXISTS users (...)");
}
```

构造函数中自动调用initDatabase()，连接SQLite数据库文件user_credentials.db，如果不存在会自动创建。users表有三个字段：自增ID、唯一用户名、SHA-256加密后的密码哈希。

密码加密：
```java
public String hashPassword(String password) {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
    // 转为十六进制字符串返回
}
```

使用Java标准库的MessageDigest实现SHA-256哈希，与原C++版本中QCryptographicHash::Sha256功能完全一致。密码从不以明文存储。

注册逻辑：
- 检查用户名和密码非空
- 用PreparedStatement防止SQL注入
- 捕获UNIQUE约束异常判断用户名是否已存在
- 返回null表示成功，返回错误信息字符串表示失败

登录逻辑：
- 根据用户名查询数据库
- 对比输入密码的哈希值与存储的哈希值
- 返回null表示成功，返回具体错误信息（"用户名不存在"/"密码错误"）表示失败

### 4.2 AuthClient.java — 认证客户端

这是认证系统的核心协调类，整合了用户管理和验证码功能。

设计思路：原C++版本中，AuthClient通过HTTP请求与Flask后端通信，Flask用Redis存验证码。Java单机版中，AuthClient直接调用本地UserManager，验证码存在内存的ConcurrentHashMap中（线程安全），省去了网络通信和Redis依赖。

验证码机制：
```java
private final Map<String, CodeEntry> verificationCodes = new ConcurrentHashMap<>();

private static class CodeEntry {
    String code;        // 6位数字验证码
    long expireTime;    // 过期时间戳
}

public void requestVerificationCode(String email) {
    String code = String.valueOf((int)(Math.random() * 900000) + 100000);  // 生成100000~999999
    verificationCodes.put(email, new CodeEntry(code, System.currentTimeMillis() + 300_000));  // 5分钟有效
}
```

注册时验证流程：
1. 用户输入邮箱 -> 调用requestVerificationCode() -> 生成6位验证码存入内存（5分钟过期）
2. 同时调用EmailSender发送验证码到邮箱
3. 用户输入验证码 -> 调用registerUser() -> 校验验证码是否匹配且未过期 -> 调用UserManager写入数据库

回调机制：使用Java的BiConsumer<Boolean, String>函数式接口替代Qt的信号槽机制。例如：
```java
private BiConsumer<Boolean, String> onLoginResult;

public void loginUser(String email, String password) {
    String error = userManager.loginUser(email, password);
    if (error == null) {
        onLoginResult.accept(true, "登录成功");   // 相当于Qt的 emit loginResult(true, "登录成功")
    } else {
        onLoginResult.accept(false, error);
    }
}
```

### 4.3 EmailSender.java — 邮件发送器

通过SMTP协议发送验证码邮件，使用JavaMail库实现。

SMTP配置：
- 默认使用QQ邮箱SMTP服务器：smtp.qq.com，端口465（SSL加密）
- 需要配置发件人邮箱和授权码（不是邮箱密码，是QQ邮箱设置中生成的授权码）

发送流程：
```java
public void sendVerificationCode(String recipientEmail, String code) {
    new Thread(() -> {  // 异步发送，不阻塞UI线程
        Properties props = new Properties();
        props.put("mail.smtp.ssl.enable", "true");  // 启用SSL
        // ... 配置SMTP参数
        Session session = Session.getInstance(props, authenticator);
        MimeMessage message = new MimeMessage(session);
        message.setSubject("你的验证码");
        message.setText("你的验证码是：" + code + "，5分钟内有效。");
        Transport.send(message);
    }).start();
}
```

关键点：邮件发送是耗时操作（需要网络通信），所以放在新线程中异步执行，避免阻塞Swing UI线程导致界面卡死。发送成功/失败通过回调通知UI层。

### 4.4 ProcessMonitor.java — 进程监控器（核心模块）

这是系统最核心的模块，负责定时扫描系统进程并检测违规游戏进程。

双定时器设计：
```java
private Timer gameTimer;        // 游戏进程检测定时器，默认每2秒执行一次
private Timer allProcessTimer;  // 全进程刷新定时器，默认每5秒执行一次
```

使用Swing的javax.swing.Timer（在EDT线程中执行回调，天然线程安全），两个定时器各司其职：
- gameTimer：高频检测，每2秒扫描一次进程列表，看有没有匹配黑名单的游戏进程
- allProcessTimer：低频刷新，每5秒获取一次全部进程信息（含CPU使用率），更新到界面表格

跨平台进程获取：

原C++版本使用Windows API（CreateToolhelp32Snapshot），Java版改为调用系统命令，自动适配操作系统：

```java
if (os.contains("win")) {
    // Windows: 执行 tasklist /FO CSV /NH 获取进程列表
    // 再执行 wmic 获取CPU使用率
} else {
    // macOS/Linux: 执行 ps aux 获取进程列表和CPU
}
```

Windows下分两步：先用tasklist获取进程名和PID，再用wmic获取CPU使用率，最后合并数据。macOS/Linux下用ps aux一次性获取所有信息。

违规检测逻辑：
```java
private void checkProcesses() {
    for (ProcessInfo info : processList) {
        for (String gameName : gameProcessNames) {
            if (name.equalsIgnoreCase(gameName)) {  // 不区分大小写匹配
                onGameProcessDetected.accept(info);  // 触发回调 -> 截屏
                return;  // 检测到一个就返回
            }
        }
    }
}
```

遍历当前进程列表，与游戏进程黑名单逐一比对（不区分大小写），一旦匹配就触发回调通知主窗口截屏。

### 4.5 MarkedGameManager.java — 标记游戏进程管理器

管理游戏进程"黑名单"，使用JSON文件持久化存储。

JSON文件格式：
```json
{
  "marked_games": [
    {
      "processName": "game.exe",
      "markedTime": "2026-03-06T14:30:00",
      "description": "默认描述",
      "autoMonitor": true
    }
  ]
}
```

核心操作：
- loadMarkedGames()：从marked_games.json读取所有标记的游戏进程，用Gson解析JSON
- addMarkedGame()：添加新的标记进程，先检查是否已存在（按进程名不区分大小写比对），不存在则追加并保存
- removeMarkedGame()：按进程名删除标记，使用List.removeIf()简洁实现
- getAllProcessNames()：返回所有标记进程的名称列表，供ProcessMonitor使用

每次增删操作后都会触发onMarkedListUpdated回调，通知UI层刷新显示。

### 4.6 ScreenCapture.java — 截屏工具

检测到违规进程后自动截取全屏并保存为PNG图片。

截屏实现：
```java
private void doCapture(String processName) {
    Robot robot = new Robot();  // AWT Robot，可以模拟键鼠操作和截屏
    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    Rectangle screenRect = new Rectangle(screenSize);
    BufferedImage screenshot = robot.createScreenCapture(screenRect);  // 截取全屏

    String filePath = generateFilePath(processName);  // 生成文件路径
    ImageIO.write(screenshot, "PNG", new File(filePath));  // 保存为PNG
}
```

使用Java AWT的Robot.createScreenCapture()截取全屏，替代原C++中Qt的QScreen::grabWindow(0)。

文件命名规则：
```
MonitoringScreenshots/Device_001_{进程名}_{时间戳}.png
```
例如：MonitoringScreenshots/Device_001_game.exe_20260306_143025_123.png

支持延迟截屏（delayMs参数），延迟时在新线程中sleep后再截屏，避免阻塞UI。

---

## 五、界面层（ui包）

### 5.1 MainWindow.java — 主窗口

主窗口是整个系统的核心界面，布局分为三个区域：

左侧区域：
- "开始监控"按钮 -> 调用processMonitor.startMonitor(2000, 5000)启动双定时器
- "停止监控"按钮 -> 调用processMonitor.stopMonitor()停止定时器
- 监控状态标签 -> 绿色"运行中" / 红色"已停止"
- 截屏记录表格 -> 三列：时间、进程名、截图路径，每次截屏成功后新增一行

右侧区域：
- "标记为游戏"按钮 -> 将输入框或下拉框中的进程名添加到黑名单
- "管理标记进程"按钮 -> 打开MarkedProcessManagerDialog
- 进程选择下拉框 -> 自动填充当前系统运行的所有进程名（去重）
- 手动输入框 -> 手动输入进程名
- 全部进程表格 -> 两列：进程名、CPU占用率，每5秒自动刷新

右上角：
- "Log in"按钮 -> 打开登录对话框，登录成功后按钮隐藏

信号连接（connectSignals方法）：

这是MainWindow中最关键的方法，建立了所有模块之间的联动关系：

```java
// 检测到游戏进程 -> 自动截屏
processMonitor.setOnGameProcessDetected(info -> {
    screenCapture.captureScreen(info.getProcessName());
});

// 进程列表更新 -> 刷新表格和下拉框
processMonitor.setOnAllProcessUpdated(processList -> {
    onAllProcessUpdated(processList);
    refreshProcessSelectionCombo(processList);
});

// 截屏完成 -> 添加记录到截屏表格
screenCapture.setOnCaptureFinished((path, success, errorMsg) -> {
    onCaptureFinished(path, success, errorMsg);
});
```

所有回调都通过SwingUtilities.invokeLater()确保在EDT线程中执行UI更新，避免线程安全问题。

### 5.2 LoginDialog.java — 登录对话框

模态对话框（JDialog，modal=true），打开后主窗口不可操作。

界面包含：用户名输入框、密码输入框（密文显示）、登录按钮、注册按钮、忘记密码按钮。

登录流程：
1. 用户输入用户名和密码，点击"登录"
2. 调用authClient.loginUser(username, password)
3. AuthClient内部调用UserManager查询SQLite数据库
4. 通过回调返回结果：成功则关闭对话框并通知主窗口隐藏登录按钮，失败则弹出错误提示

点击"注册"按钮会打开RegisterDialog。

### 5.3 RegisterDialog.java — 注册对话框

界面包含：邮箱输入框、获取验证码按钮（带60秒倒计时）、用户名输入框、密码输入框、确认密码输入框、验证码输入框、注册按钮。

注册流程：
1. 用户输入邮箱，点击"获取验证码"
2. 调用authClient.requestVerificationCode(email) -> 生成6位验证码存内存
3. 调用emailSender.sendVerificationCode(email, code) -> 异步发送邮件
4. 获取验证码按钮进入60秒倒计时（防止频繁请求）
5. 用户收到邮件，输入验证码和密码，点击"注册"
6. 调用authClient.registerUser(email, password, code) -> 校验验证码 -> 写入数据库
7. 注册成功关闭对话框，失败弹出错误提示

倒计时实现：
```java
codeTimer = new Timer(1000, e -> updateCodeTimer());  // 每秒触发一次
private void updateCodeTimer() {
    remainingTime--;
    if (remainingTime <= 0) {
        getCodeBtn.setEnabled(true);
        getCodeBtn.setText("获取验证码");
        codeTimer.stop();
    } else {
        getCodeBtn.setText("倒计时 " + remainingTime + " 秒");
    }
}
```

### 5.4 MarkedProcessManagerDialog.java — 标记进程管理对话框

用于查看和管理已标记的游戏进程黑名单。

界面包含：进程列表（JList，显示进程名、标记时间、描述、是否自动监控）、删除按钮、刷新按钮、关闭按钮。

列表为空时显示"暂无标记的游戏进程"提示，隐藏列表并禁用删除按钮。

删除流程：选中列表项 -> 从显示文本中解析出进程名 -> 弹出确认对话框 -> 调用markedManager.removeMarkedGame() -> 刷新列表 -> 通过回调通知主窗口更新ProcessMonitor的黑名单。

---

## 六、模块间协作流程

### 6.1 完整监控流程

```
用户点击"开始监控"
    -> MainWindow.onStartMonitorClicked()
    -> ProcessMonitor.startMonitor(2000, 5000)
    -> 启动两个定时器

每2秒：
    -> ProcessMonitor.checkProcesses()
    -> 遍历processList，与gameProcessNames比对
    -> 匹配到违规进程 -> 触发onGameProcessDetected回调
    -> MainWindow收到回调 -> 调用ScreenCapture.captureScreen()
    -> Robot截全屏 -> 保存PNG文件
    -> 触发onCaptureFinished回调
    -> MainWindow收到回调 -> 在截屏记录表格中新增一行

每5秒：
    -> ProcessMonitor.refreshAllProcessInfo()
    -> 执行系统命令获取进程列表
    -> 触发onAllProcessUpdated回调
    -> MainWindow收到回调 -> 刷新进程表格和下拉框
```

### 6.2 完整注册流程

```
用户点击"注册"按钮（LoginDialog中）
    -> 打开RegisterDialog

用户输入邮箱，点击"获取验证码"
    -> AuthClient.requestVerificationCode(email)  -> 生成验证码存内存
    -> EmailSender.sendVerificationCode(email, code)  -> 异步发送邮件
    -> 按钮进入60秒倒计时

用户输入验证码和密码，点击"注册"
    -> AuthClient.registerUser(email, password, code)
    -> 校验验证码（内存中比对，检查是否过期）
    -> UserManager.registerUser(email, password)
    -> SHA-256加密密码 -> INSERT INTO users
    -> 注册成功 -> 关闭对话框
```

---

## 七、依赖说明（pom.xml）

| 依赖 | 版本 | 用途 |
|------|------|------|
| sqlite-jdbc | 3.44.1 | SQLite数据库JDBC驱动 |
| gson | 2.10.1 | JSON解析（标记进程文件读写） |
| flatlaf | 3.2.5 | Swing现代化外观主题 |
| javax.mail | 1.6.2 | SMTP邮件发送 |

打包方式：使用maven-shade-plugin将所有依赖打成一个可执行jar，运行命令：
```bash
mvn clean package
java -jar target/efficiency-management-system-1.0.0.jar
```

---

## 八、与原C++版本的对应关系

| 原C++/Python | Java版 | 改动说明 |
|-------------|--------|---------|
| main.cpp | Main.java | QApplication -> SwingUtilities.invokeLater |
| ProcessInfo.h | ProcessInfo.java | 结构体 -> Java类，加getter/setter |
| ProcessMonitor.cpp | ProcessMonitor.java | Windows API -> 系统命令(tasklist/ps)，QTimer -> javax.swing.Timer |
| ScreenCapture.cpp | ScreenCapture.java | QScreen::grabWindow -> AWT Robot.createScreenCapture |
| MarkedGameManager.cpp | MarkedGameManager.java | QJsonDocument -> Gson，QFile -> Java IO |
| UserManager.cpp | UserManager.java | QSqlDatabase -> JDBC，QCryptographicHash -> MessageDigest |
| AuthClient.cpp + app.py | AuthClient.java | HTTP请求+Flask+Redis -> 直接调用本地UserManager+内存Map |
| EmailSender.cpp | EmailSender.java | QSslSocket手写SMTP协议 -> JavaMail库 |
| mainwindow.cpp + .ui | MainWindow.java | Qt Designer UI -> Swing手写布局 |
| LoginDialog.cpp + .ui | LoginDialog.java | QDialog -> JDialog |
| RegisterDialog.cpp + .ui | RegisterDialog.java | QDialog -> JDialog |
| markedprocessmanagerdialog.cpp + .ui | MarkedProcessManagerDialog.java | QDialog -> JDialog |
| Qt信号槽(signal/slot) | Java回调(Consumer/Runnable) | connect(a, signal, b, slot) -> a.setOnXxx(callback) |
