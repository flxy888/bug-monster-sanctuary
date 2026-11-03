USE bug_monster;
INSERT INTO users(id,username,total_xp) VALUES(1,'demo',0)
ON DUPLICATE KEY UPDATE username='demo';
INSERT INTO monster_types(id,rule_code,monster_name,deduction,base_xp,enabled,description,suggestion) VALUES
(1,'EMPTY_CATCH','吞异常兽',15,20,TRUE,'catch 无实际处理语句，注释、空分号、递归空块不算处理','记录异常、转换后重新抛出，或实现明确的恢复处理。'),
(2,'LONG_METHOD','长舌章鱼',10,20,TRUE,'方法体含大括号起止行超过30行，不计构造器','按单一职责提取小方法，减少方法体行数。'),
(3,'DEEP_NESTING','套娃怪',10,20,TRUE,'if/for/foreach/while/do 首次达到4层；else-if 同层，独立可执行体归零','使用卫语句、提前返回或提取方法，减少控制结构嵌套。'),
(4,'TOO_MANY_PARAMETERS','多手触手怪',8,20,TRUE,'方法声明参数超过5个；可变参数计一个，不计构造器','聚合相关参数为对象，或按职责拆分方法。'),
(5,'EMPTY_IF','空壳潜伏者',8,20,TRUE,'if 的 then 仅含注释、空语句或递归空块；不以 else 是否非空豁免','实现 then 的有效逻辑，或移除无意义分支并简化条件。')
ON DUPLICATE KEY UPDATE monster_name=VALUES(monster_name),deduction=VALUES(deduction),
base_xp=VALUES(base_xp),enabled=VALUES(enabled),description=VALUES(description),suggestion=VALUES(suggestion);
-- XP is definition-only. Do not touch source, scans, issues, XP or counters.
