# 运行与验证

## 环境

JDK 8、Maven、Node.js / npm、MySQL。前端依赖版本以 package-lock.json 为准。

先创建独立的空数据库 `novelgraph`，不要连接现有业务数据库。迁移中的 V5 会删除旧版本业务表。

## 本地配置

在项目根目录运行：

```powershell
New-Item -ItemType Directory -Force backend/.local
Copy-Item backend/local-settings.example.json backend/.local/settings.json
```

编辑 settings.json：将 DB_URL 的数据库名改为 novelgraph，填写自己的数据库账号密码；设置 NG_ADMIN_EMAIL 和至少 12 位的 NG_ADMIN_PASSWORD。本地不配置邮件服务时可将 NG_VERIFY_EMAIL 设为 false。邮件验证与找回密码需要配置 SMTP。禁止提交真实配置和凭据。

## 分别启动

```powershell
cd backend
mvn clean package
./start.ps1
```

另开终端：

```powershell
cd frontend
npm ci
npm run dev
```

浏览器访问 http://127.0.0.1:5173，后端默认 8080，接口文档位于 http://localhost:8080/doc.html#/default。

## 验证与完整打包

```powershell
mvn -f backend/pom.xml test
cd frontend
npm test
npm run build
```

安装前端依赖后，也可以在根目录执行 `./build.ps1`，将前端构建产物打包进后端；然后执行 `./backend/start.ps1`，访问 http://localhost:8080。

测试使用 H2 数据库。本次验证结果见 [作品整理验证记录](VALIDATION.md)。

## 公开部署前

使用独立演示数据库和模拟数据，配置域名、HTTPS、允许来源和 secure-cookie。不得把平台管理员账号作为公共试用账号；演示账号需要限制权限并定期重置。部署与线上演示尚未完成。
