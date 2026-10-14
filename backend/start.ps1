param([switch]$Background, [int]$Port = 8080)
$ErrorActionPreference = 'Stop'
$backendPath = $PSScriptRoot
$settingsPath = Join-Path $backendPath '.local/settings.json'
if (!(Test-Path -LiteralPath $settingsPath)) { throw '缺少 .local/settings.json，请参考 README 配置连接与密钥。' }
$settings = Get-Content -LiteralPath $settingsPath -Raw -Encoding UTF8 | ConvertFrom-Json
foreach ($property in $settings.PSObject.Properties) { [Environment]::SetEnvironmentVariable($property.Name, [string]$property.Value, 'Process') }
$env:SERVER_PORT = [string]$Port
$jarPath = Join-Path $backendPath 'target/novelgraph-api-1.0.0.jar'
if (!(Test-Path -LiteralPath $jarPath)) { throw '请先运行 mvn package 构建项目。' }
if ($Background) {
    # 日志统一交由 Logback 按天归档，避免额外产生无限增长的重定向日志。
    $process = Start-Process -FilePath 'java' -ArgumentList @('-Dfile.encoding=UTF-8','-jar', $jarPath) -WorkingDirectory $backendPath -WindowStyle Hidden -PassThru
    $process.Id | Set-Content -LiteralPath (Join-Path $backendPath '.local/server.pid')
    Write-Output "后端已启动，进程 $($process.Id)，端口 $Port。"
} else {
    & java '-Dfile.encoding=UTF-8' -jar $jarPath
}
