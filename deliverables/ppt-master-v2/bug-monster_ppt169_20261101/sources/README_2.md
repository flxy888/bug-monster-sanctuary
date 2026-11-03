# 真实业务场景展示样例 · DEMO-REAL01

这五组代码是开发者为演示编写的受控案例，模拟常见编码疏漏和业务逐步增长后的维护性问题，并非从真实生产事故中摘录。每组 before/after 都是完整的 Java 21 类，只依赖 JDK，包含所用类型、方法和业务逻辑，可以独立编译。没有 `main` 也不影响作为业务组件编译和供调用方使用。

推荐先展示 **空 catch** 和 **if 后误加分号**：它们是容易不小心遗漏的局部处理。多参数、长方法、深嵌套属于维护性坏味道，代码可以正常完成业务；触发这些规则不等于业务结果已经出错。当前五条 AST 检查不能识别任意“少写一行”的业务 Bug，也不证明业务正确、异常策略合理或重构完全等价。

## 文件与演示顺序

| 场景 | before / after 文件 | 目标规则 | 修复/重构内容 |
|---|---|---|---|
| 金额错误被吞掉的订单导入 | [before](empty-catch/before/OrderCsvImporter.java) / [after](empty-catch/after/OrderCsvImporter.java) | EMPTY_CATCH | 补上错误行号和金额原因，保留批量继续导入行为 |
| VIP 优惠条件后多一个分号 | [before](empty-if/before/OrderPricingService.java) / [after](empty-if/after/OrderPricingService.java) | EMPTY_IF | 只移除多余分号，让原优惠代码块受条件控制 |
| 运费计算参数逐步增长 | [before](parameters/before/ShippingQuoteService.java) / [after](parameters/after/ShippingQuoteService.java) | TOO_MANY_PARAMETERS | 六个有业务含义的参数聚合为 ShipmentRequest record |
| 校验、汇总、导出集中在一个方法 | [before](long-method/before/SalesReportExporter.java) / [after](long-method/after/SalesReportExporter.java) | LONG_METHOD | 按校验、汇总、明细、汇总行提取职责，保留输出内容 |
| 客户、订单、商品的缺货提醒 | [before](deep-nesting/before/BackorderReminderPlanner.java) / [after](deep-nesting/after/BackorderReminderPlanner.java) | DEEP_NESTING | 卫语句过滤未订阅客户，再提取单个订单的提醒收集 |

同组类名和主业务方法名保持一致，before/after 放在不同目录以避免同名类冲突。每次向产品编辑器复制**一份完整文件**，不要把两份或五个场景拼成一个方法。独立案例各自只有一个目标问题；after 不命中这五条规则。实际行号和分数以 `deliverables/demo-realistic/rules-evidence.json` 为准。

2026-10-28 专项实测：

| 场景 | before 命中行（从 1 开始） | before → after 分数 |
|---|---|---|
| 订单导入 | catch 第 26–27 行 | 85 → 100 |
| VIP 优惠 | 多余分号第 14 行 | 92 → 100 |
| 运费参数 | 方法第 6–30 行，6 个参数 | 92 → 100 |
| 销售日报 | export 第 10–45 行，方法体 36 行 | 90 → 100 |
| 缺货提醒 | 第四层控制结构第 15–20 行 | 90 → 100 |

## 业务意图与审阅输入

以下输入/输出是便于人工审阅的**预期说明，未运行样例进行验证**。本轮只编译并进行静态规则/身份比较，没有执行这些业务组件。

### 1. 订单导入：遗漏了错误反馈

`OrderCsvImporter.importLines` 接受无表头、每行 `orderId,sku,amount` 的简单三列文本；空白行忽略，列数错误已有失败记录。它不是支持引号/字段内逗号的通用 CSV 解析器。输入行及列表应非 null；金额为 BigDecimal 可解析的十进制数。

审阅输入：

```text
ORD-101,SKU-A,19.90
ORD-102,SKU-B,19x90
ORD-103,SKU-C,8.50
```

before 能导入第 1、3 行，但第 2 行金额格式错误被吞掉，结果中既没有这笔订单，也没有它的失败原因。after 保留另外两行的导入结果，增加第 2 行的失败项 `Invalid amount: 19x90`。返回的是结构化 `ImportResult`，调用方可以展示成功与失败列表；不是只打印一下后继续假装成功。

### 2. VIP 优惠：多余分号让折扣无条件执行

`OrderPricingService.totalDue` 对非负小计计算 VIP 九折，使用 BigDecimal 并按分四舍五入。before 的 `if (order.vip());` 是合法 Java，后面的独立代码块会无条件执行；after 仅删除这一字符，其余业务完全保留。

对小计 `100.00`、`vip=false` 的订单，before 预期为 `90.00`，after 为 `100.00`；`vip=true` 两版均预期为 `90.00`。这是纠正错误行为，不能宣称 before/after 行为等价。

### 3. 运费：参数对象保留字段含义

运费由地区基础费、重量、加急、易碎与保价组成；六个参数都有作用，没有用无意义变量凑数。地区限 `LOCAL` / `REGIONAL` / `REMOTE`，重量大于 0，申报价值非负，输入引用非 null。

例如 `LOCAL, 2kg, 100.00, express=true, fragile=true, insured=true`，预期费用 `6.00 + 2×1.20 + 8.00 + 3.00 + 100×0.005 = 19.90`。after 用同样六个值构造 `ShipmentRequest` 后交给 `quoteShipping`；原地区费率、附加费、校验、舍入保留。调用方需要改用参数对象。主类中只有一个同名 `quoteShipping`，便于既有匹配器确认身份连续性。

### 4. 销售日报：自然增长的方法按职责拆分

`SalesReportExporter.export` 接受日期与销售行，完成空输入检查、数量/价格/CSV 标识校验、数量和金额汇总、逐行导出、汇总行输出，返回报表字符串。标识不允许空白、逗号或换行；示例输入应非 null，不模拟外部数据库或文件系统。

例如两行销售分别为 `ORD-101 / SKU-A / 2 / 12.50` 和 `ORD-102 / SKU-B / 1 / 5.00`，两版预期输出同顺序明细以及 `total_lines=2`、`total_units=3`、`total_revenue=30.00`、`average_line_value=15.00`。after 提取职责，保留字段、校验条件、计算和舍入，并非删业务语句或压成单行。静态检查无法证明所有输入上的等价，未做运行级回归。

### 5. 缺货提醒：有限遍历与相同收集顺序

`BackorderReminderPlanner.plan` 只为启用提醒的客户收集缺货且数量大于 0 的订单商品。输入是非 null 的有限 List，成员及嵌套列表非 null，不在处理期间修改；无不终止循环、网络或发送通知等副作用。

before 顺序遍历客户、订单、商品并检查条件，嵌套较深。after 先跳过未订阅客户，再由辅助方法遍历订单内商品；客户、订单、商品的相对次序和返回字段保持。对未订阅客户，无论商品如何均不产生提醒；对订阅客户，仅正数量的缺货项进入返回列表。未在本轮运行验证所有边界输入。

## 验证方式与边界

在项目根执行：

```powershell
python deliverables/demo-realistic/verify_samples.py
```

该工具仅允许这十份开发者仓库样例，以本机 JDK 21 的 `javac --release 21 -encoding UTF-8 -proc:none` 分目录离线编译到 `backend/target-demo-realistic/javac/`；**不运行生成的 class，不编译数据库内用户代码，也不提供产品在线编译功能**。

随后 Maven 使用 `--offline` 和独立 `backend/target-demo-realistic`，仅运行 `ShowcaseSamplesTest` 五个参数化场景。真实 `CodeAnalyzer` / JavaParser / 五规则读取仓库种子元数据，验证 before 仅目标命中、after 无命中、评分和位置，并用当前 v2 匹配器在内存中验证方法身份连续与 `CONFIRMED_FIXED`。不连接 MySQL，不启动服务，不结算或发放 XP。解析和编译成功均不是业务正确性证明。

已运行服务的 `target-week4` 不作为输出目录，JAR 摘要在验证前后保持一致。专项日志、编译清单与规则证据归档于 `deliverables/demo-realistic/`，旧 `WeekTwoDemo.java`、week3 fixture 和交付证据仍保留为历史边界测试材料，不替换成这批样例。

## 与当前产品展示的关系

2026-10-31已接入前两例：订单导入项目1002/扫描541为85分，VIP优惠项目1003/扫描542为92分，各仅1条before基线、0XP，首次修复留给现场操作。旧975/1000已备份移出展示；22保护，累计2000XP不变。三个维护性案例保留为文件按需讲解。现场操作见[当前展示指南](../../../docs/13-作品使用与答辩速查.md)。

