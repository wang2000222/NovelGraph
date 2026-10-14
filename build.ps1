param()
$ErrorActionPreference = 'Stop'
$workspace = (Resolve-Path -LiteralPath $PSScriptRoot).Path
Push-Location (Join-Path $workspace 'frontend')
try {
    & npm run build
    if ($LASTEXITCODE -ne 0) { throw '前端构建失败' }
    & npm test
    if ($LASTEXITCODE -ne 0) { throw '前端测试失败' }
} finally { Pop-Location }
# 只覆盖当前项目内的生成目录，源代码与本地配置保持独立。
$staticPath = [IO.Path]::GetFullPath((Join-Path $workspace 'backend/src/main/resources/static'))
if (!$staticPath.StartsWith($workspace + '\', [StringComparison]::OrdinalIgnoreCase)) { throw '生成目录超出项目范围' }
if (Test-Path -LiteralPath $staticPath) { Remove-Item -LiteralPath $staticPath -Recurse -Force }
New-Item -ItemType Directory -Path $staticPath -Force | Out-Null
Copy-Item -Path (Join-Path $workspace 'frontend/dist/*') -Destination $staticPath -Recurse
& mvn -f (Join-Path $workspace 'backend/pom.xml') "-Dmaven.repo.local=$workspace/backend/.local/m2" clean package -q
if ($LASTEXITCODE -ne 0) { throw '后端测试或打包失败' }
Write-Output '前后端构建及测试完成。运行 backend/start.ps1 启动，访问 http://localhost:8080/。'
