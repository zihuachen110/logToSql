# 安装 JDK 17 后运行此脚本，自动写入 Gradle 使用的 Java 路径
# 用法: powershell -ExecutionPolicy Bypass -File setup-jdk.ps1

$jdkCandidates = @(
    (Get-ChildItem "$env:USERPROFILE\.jdks\jdk-17*" -ErrorAction SilentlyContinue | Sort-Object Name -Descending | Select-Object -First 1),
    (Get-ChildItem "$env:USERPROFILE\.jdks\ms-17*" -ErrorAction SilentlyContinue | Sort-Object Name -Descending | Select-Object -First 1),
    (Get-ChildItem "C:\Program Files\Eclipse Adoptium\jdk-17*" -ErrorAction SilentlyContinue | Sort-Object Name -Descending | Select-Object -First 1),
    (Get-ChildItem "C:\Program Files\Microsoft\jdk-17*" -ErrorAction SilentlyContinue | Sort-Object Name -Descending | Select-Object -First 1),
    (Get-ChildItem "C:\Program Files\Java\jdk-17*" -ErrorAction SilentlyContinue | Sort-Object Name -Descending | Select-Object -First 1),
    (Get-ChildItem (Join-Path $PSScriptRoot ".jdk\jdk-*") -ErrorAction SilentlyContinue | Sort-Object Name -Descending | Select-Object -First 1)
)

$jdk = $jdkCandidates | Where-Object { $_ -ne $null } | Select-Object -First 1

if (-not $jdk) {
    Write-Host ""
    Write-Host "未找到 JDK 17，请先安装：" -ForegroundColor Red
    Write-Host "  1. 浏览器打开: https://mirrors.tuna.tsinghua.edu.cn/Adoptium/17/jdk/x64/windows/"
    Write-Host "  2. 下载 .msi 安装包并安装"
    Write-Host "  3. 再运行本脚本"
    Write-Host ""
    exit 1
}

$jdkPath = $jdk.FullName -replace '\\', '/'
$gradleProps = Join-Path $PSScriptRoot "gradle.properties"
$content = Get-Content $gradleProps -Raw

if ($content -match "(?m)^org\.gradle\.java\.home=.*$") {
    $content = $content -replace "(?m)^org\.gradle\.java\.home=.*$", "org.gradle.java.home=$jdkPath"
} else {
    $content = "org.gradle.java.home=$jdkPath`n" + $content
}

Set-Content -Path $gradleProps -Value $content -NoNewline
Write-Host "已配置 Gradle 使用: $($jdk.FullName)" -ForegroundColor Green
Write-Host ""
Write-Host "接下来在 IDEA 中："
Write-Host "  1. File -> Settings -> Build Tools -> Gradle -> Gradle JVM -> 选 JDK 17"
Write-Host "  2. 点击 Gradle 面板刷新按钮"
Write-Host "  3. 双击 Tasks -> intellijPlatform -> buildPlugin"

& "$($jdk.FullName)\bin\java.exe" -version
