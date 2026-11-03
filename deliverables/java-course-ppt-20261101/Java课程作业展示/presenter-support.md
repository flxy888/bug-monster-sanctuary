# 演示准备与答问速查

建议先用 PPT 讲 6～8 分钟。需要现场演示时另留 1～2 分钟，提前通过项目根目录“启动演示.cmd”启动，打开 http://127.0.0.1:5173/ 。不为 PPT 制作提前提交扫描、修复领奖或重置数据。

## 现场操作

1. 选择订单导入或 VIP 优惠项目，查看当前实际状态。若已修复，照实展示当前结果，不声称仍是首次修复。
2. 定位问题与 AST，说明源码位置只是定位线索。
3. 如果现场确有待修复代码，由演示者决定是否提交修复。首次有效修复的奖励以真实返回为准。
4. 重复提交同一已修复版本时说明新增 0 XP；不要改数据库来复原领奖资格。

## 常见提问

- 为什么不用正则？JavaParser 能区分节点结构，不把注释、字符串里的关键字直接当成代码结构；本项目仍是有限的五规则检测。
- 接口和多态在哪里？AnalysisRule 统一契约，五个规则类实现，CodeAnalyzer 接收 List<AnalysisRule> 并依接口调用。
- 如何支持新规则？增加规则实现，同时补充规则元数据、数据库迁移或种子、界面映射、阈值样例和兼容性决策。
- 为什么不能只比较问题数量？旧问题修复的同时可能新增同类问题；需要跟踪身份与方法连续性。
- issueKey 与 lineageKey 的区别？前者是扫描观察 UUID，后者是同项目问题生命周期身份；奖励按后者去重。
- 删除方法会不会奖励？不会。删除和确认修复分开处理；歧义也不自动奖励。
- AI 能替代检测吗？不能。AI 返回建议，用户确认后仍由原来的 AST 与奖励链路独立判断。
- 是否可以执行任意 Java？产品不提供该功能，只静态解析。仓库中的受控展示样例曾离线编译验证，不代表支持用户在线编译。

## 内容依据

- 技术版本：backend/pom.xml、frontend/package.json。
- 接口与引擎：backend/src/main/java/com/bugmonster/analysis/AnalysisRule.java、CodeAnalyzer.java、AnalysisResult.java。
- 规则：同目录 EmptyCatchRule、EmptyIfRule、LongMethodRule、DeepNestingRule、TooManyParametersRule。
- 事务与身份：backend/src/main/java/com/bugmonster/service/ScanService.java、IdentityMatcher.java。
- SQL 与约束：backend/src/main/java/com/bugmonster/persistence/IdentityMapper.java、SanctuaryMapper.java；database/05-week3-upgrade.sql。
- 案例：tests/samples/showcase 与 deliverables/demo-realistic 的既有专项结果。
- AI：deliverables/ai-repair/REPORT.md 与 README-AI.md。
- 产品截图：deliverables/demo-realistic-live/orders-browser.png，历史运行证据；图片本身未来自任何旧 PPT。
- 交付状态：README.md、docs/02-任务看板.md、docs/03-技术与关键裁定.md。
