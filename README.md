# Bug 怪兽收容所 · 最终版

冻结日期：2026-11-01。Java课程项目，源码、数据库、运行依赖及最终课件保留。

## 启动与使用

双击根目录 **启动演示.cmd**，等待就绪后访问 http://127.0.0.1:5173/ 。启动器复用本项目已运行服务，保留原数据库；日常演示无需重新安装或初始化。

- 编辑完整Java代码，提交扫描，点击问题定位；修复后重新扫描确认收容。
- 狼尊主题：编辑右栏显示名，保存 **狼尊999**。可跳过过场，刷新保留主题，也可恢复默认。
- 紫色主题的 **AI修复** 调用DeepSeek生成预览，确认后应用草稿，再由原扫描判定收容和奖励。详见[AI使用与配置](README-AI.md)。

## 课堂材料

当前使用[Java课程作业展示可编辑PPT](deliverables/java-course-ppt-20261101/Java课程作业展示/editable-output/Java课程作业展示_可编辑版.pptx)：12页，正文、代码、规则表格和技术图示均为可编辑原生对象，讲稿备注完整保留。产品截图和怪兽插画为独立可替换图片。原图片式PPT保留作视觉参考；speech.md、outline.md和presenter-support.md仍在原目录。

[演示讲稿](docs/演示讲稿.md) · [技术说明](docs/技术说明.md)

## 保留目录

| 目录 | 用途 |
|---|---|
| backend/src、frontend/src | 最终后端与前端源码 |
| backend/target-week4、frontend/dist | 日常演示的已构建产物 |
| frontend/node_modules、.tools | 安装好的依赖、JDK/Maven/MySQL、原数据库及本机私有配置 |
| database | 建表、种子与增量迁移SQL |
| tests、前后端测试目录 | 回归测试及完整修复前后样例 |
| tools | 启动、构建、初始化及依赖校验工具 |
| deliverables/demo-ready | 启动器所需收据及当前日志，正常启动会继续生成运行记录 |

旧阶段交付、规划、预览与构建已归档到项目外，位置见清理记录。安装压缩包、重复依赖副本及npm下载缓存属于可重新生成的文件，已删除。原数据库、本机密钥和依赖仓库保留。

系统只静态解析Java代码，不编译或执行用户源码。当前为本机单用户课程演示，规则与AI结果均不能证明业务完全正确；分数、XP和历史以实际页面为准。

## 从 GitHub 源码启动

公开仓库包含源码、测试、数据库结构与迁移、课程课件和报告。本机密钥、数据库数据、安装依赖、构建产物及运行日志不上传。上面的“一键启动”面向已经准备好的本机演示环境；首次克隆请按以下方式启动。

准备 JDK 21、Maven 3.9.9、Node.js 22.18.0 或更高版本，以及 MySQL 8.4。Windows 可使用 `./tools/bootstrap-tools.ps1` 下载项目锁定的 JDK、Maven 和 MySQL；具体版本与校验值见 `tools/runtime.lock.json`。

1. 在自己的 MySQL 实例中，依次导入 `database/01-schema.sql`、`04-week2-upgrade.sql`、`02-seed.sql`、`05-week3-upgrade.sql`、`06-week4-upgrade.sql`。脚本包含建表和迁移操作，请使用专用的本地演示数据库。需要独立账号时修改并执行 `database/03-user.example.sql`。
2. 参考 `backend/.env.example`，在后端终端设置进程环境变量 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`；Spring 不会自动加载该示例文件。例如在 PowerShell 中设置 `$env:DB_PASSWORD='你的本地密码'`。默认地址为 `127.0.0.1:3306/bug_monster`。
3. 在项目根目录构建并启动后端：

   ```powershell
   ./tools/mvn.ps1 -B -DskipTests package
   ./tools/start-backend.ps1
   ```

4. 另开终端，在 `frontend` 目录执行：

   ```powershell
   npm ci
   npm run dev -- --host 127.0.0.1 --port 5173
   ```

访问 http://127.0.0.1:5173/ 。前端默认代理至本机后端 8080 端口，可参考 `frontend/.env.example` 调整。AI 修复为可选功能，需要自行配置后端的 `DEEPSEEK_API_KEY`，详见 [README-AI.md](README-AI.md)。公开源码不代表已部署为公网多用户服务。
