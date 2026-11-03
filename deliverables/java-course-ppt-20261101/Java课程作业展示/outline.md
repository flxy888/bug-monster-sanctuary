# Bug 怪兽收容所｜Java 课程作业展示

任务 ID：PPT-JAVA02。全新制作，不读取或复用既有 PPT 页面、图片、版式、大纲与讲稿。依据项目源码、当前 README、技术裁定及真实业务样例撰写。

受众：Java 课程教师与同学。12 页，建议讲解 6～8 分钟，现场操作可另留 1～2 分钟。顺序：整体架构 → Java 后端 → 功能与案例 → 扩展与总结。

用户 2026-11-01 明确要求“不要用那个ppt”“不要管那个ppt你就自己做”，据此由总控独立决定内容、风格、页数及样页，跳过逐阶段征询。样页由总控快速检查，不记录为用户看过或亲自批准。

## Slide 1：Bug 怪兽收容所
- Java 课程作业展示。
- Spring Boot + JavaParser：把代码检测、修复验证与成长反馈连接起来。
- 本机单用户课程项目，用户源码仅作静态分析。
- 版式角色：封面；代码文档、AST 分支和小怪兽组成主题插画。
- 必需原图：无。

## Slide 2：整体架构：从 Java 源码到收容反馈
- Vue 3 / Monaco：编辑源码、显示结果。
- REST API → Spring Boot：接收与校验请求。
- CodeAnalyzer / JavaParser：AST 解析、五条规则、评分。
- ScanService / IdentityMatcher：比较成功基线、确认修复。
- MyBatis / MySQL：快照、问题谱系、奖励与成长。
- 版式角色：架构图；上层请求链、下层业务反馈闭环，箭头方向清晰。
- 必需原图：无。

## Slide 3：Java 后端：按职责分层
- api：ApiController，参数校验与 HTTP 响应。
- service：ScanService、IdentityMatcher，编排扫描和收容。
- analysis：CodeAnalyzer、AnalysisRule，解析与规则。
- persistence：Mapper / Rows，SQL 与数据映射。
- 版式角色：源码包结构与调用链，突出业务由服务层组织。
- 必需原图：无。

## Slide 4：面向对象：一套接口，五条规则
- AnalysisRule 定义 code() 与 inspect(CompilationUnit)。
- 五个规则类实现同一接口；Spring 注入 List<AnalysisRule>。
- 引擎使用 Stream 汇总问题、排序与评分。
- AnalysisResult / Finding 使用 record 表达结果数据。
- 版式角色：真实接口代码 + 多态关系图。
- 必需原图：无。

## Slide 5：AST 如何识别空 catch
- JavaParser 显式设置 JAVA_21，每次请求独立创建解析器。
- 找到 CatchClause，检查其 BlockStmt 是否只有空语句。
- Finding 携带规则、说明、起止行号与匹配线索。
- 解析失败返回 PARSE_ERROR，分数为空，不推进成功基线。
- 版式角色：源码 → AST → Finding 的三段图；突出结构检测。
- 必需原图：无。

## Slide 6：五条规则与可复算评分
- EMPTY_CATCH：空 catch，扣 15。
- LONG_METHOD：方法体超过 30 行，扣 10。
- DEEP_NESTING：控制结构达到 4 层，扣 10。
- TOO_MANY_PARAMETERS：方法参数超过 5 个，扣 8。
- EMPTY_IF：then 分支为空，扣 8。
- 版式角色：五行规则表；底部公式 max(0, 100 − 问题扣分总和)。阈值属于项目定义。
- 必需原图：无。

## Slide 7：数据设计：草稿、快照与问题谱系
- projects 保存当前草稿；scan_runs 保存每次源码快照。
- scan_issues 记录某次观察；issue_lineages 连接同一问题的生命周期。
- reward_logs 记录奖励来源；users / user_monsters 保留成长统计。
- 扫描观察 issueKey 与持久 lineageKey 分开，行号只用于定位。
- 版式角色：简化实体关系图，标注并非完整九表 ER 图。
- 必需原图：无。

## Slide 8：同一问题，只奖励一次
- 两次成功扫描间确认方法连续且问题消失，首次有效收容 +20 XP。
- 删除方法、匹配歧义、解析失败均不自动奖励。
- @Transactional + 项目行锁；奖励唯一键为 project_id + lineage_key。
- 奖励记录、XP 与图鉴同事务提交，重复扫描与复发再修不重奖。
- 版式角色：首次修复 / 重复修复时间线 + 事务范围条。
- 必需原图：无。

## Slide 9：真实功能：编辑、定位、历史联动
- Monaco 编辑完整 Java 文件；点击问题联动代码位置。
- 历史查询保留当次源码与 AST；当前草稿和历史快照分开。
- 图鉴与成长依据后端结果更新；日志记录真实操作事件。
- 版式角色：真实主控台大图 + 三个简短标注。
- 必需原图：历史真实运行截图，仅作为功能证据，数字不代表制作时实时状态。
  ![订单导入主控台](assets/orders-browser.png)

## Slide 10：两个案例：小改动，明确反馈
- 订单导入：空 catch 遗漏金额错误反馈；补行号与原因。样例 85 → 100。
- VIP 优惠：if 条件后的多余分号使折扣脱离条件；删除分号。样例 92 → 100。
- 展示源码片段为课堂节选；完整 before/after 在 tests/samples/showcase。
- 分数为已验证样例结果，静态检查不替代业务测试。
- 版式角色：两个案例并列，各含改动片段、业务影响、分数变化。
- 必需原图：无。

## Slide 11：AI 扩展：建议仍需独立验证
- DeepSeekClient 调用模型；AiRepairService 约束与校验输出。
- 当前草稿 → 修复预览 → 用户确认应用 → 原扫描验证。
- AI 调用本身不保存扫描、不结算 XP。
- 一次真实 API 调用已验证五规则问题 1 → 0；浏览器点击验收仍待补充。
- 版式角色：四步流程，AI 支路与原扫描主链分开。
- 必需原图：无；不把 AI 接入前的主题截图当成 AI 界面证据。

## Slide 12：完成情况与 Java 课程收获
- 接口与多态：五规则复用统一契约。
- 集合、Stream 与 record：组织 AST 结果及数据传输。
- REST、MyBatis 与事务：形成可追溯的业务闭环。
- 已完成本机课程 MVP；下一步补业务回归与更多规则，再评估多用户。
- 版式角色：三项课程能力映射与结束语；保留“只做静态分析”的交付边界。
- 必需原图：无。

## 依据与检查边界
- 版本：backend/pom.xml、frontend/package.json。
- 实现：backend/src/main/java/com/bugmonster 下 api、analysis、service、persistence 源码。
- SQL：database/01-schema.sql 与 05-week3-upgrade.sql 等迁移。
- 样例：tests/samples/showcase；既有专项结果记录在 deliverables/demo-realistic/。
- 功能状态：README.md、docs/02-任务看板.md、docs/03-技术与关键裁定.md。
- 本次只制作演示材料，不执行用户源码，不扫描业务项目，不改变 XP 或原库。
