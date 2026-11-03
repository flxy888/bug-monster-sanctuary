package com.bugmonster.analysis;

import com.bugmonster.api.ApiException;
import com.bugmonster.persistence.SanctuaryMapper;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class RuleCatalog {
    public static final String VERSION = "week2-v1";
    public record Definition(String ruleCode, String monsterName, String description, String suggestion,
                             int deduction, int baseXp, boolean enabled) {}
    public record Snapshot(String rulesetVersion, List<String> enabledRuleCodes, List<Definition> definitions) {
        public Snapshot { enabledRuleCodes = List.copyOf(enabledRuleCodes); definitions = List.copyOf(definitions); }
        public Definition definition(String code) {
            return definitions.stream().filter(d -> d.ruleCode().equals(code)).findFirst().orElseThrow();
        }
    }
    private final SanctuaryMapper mapper;
    private final Set<String> implemented;
    public RuleCatalog(SanctuaryMapper mapper, List<AnalysisRule> strategies) {
        this.mapper = mapper;
        var codes = strategies.stream().map(AnalysisRule::code).toList();
        implemented = Set.copyOf(codes);
        if (implemented.size() != codes.size()) throw new IllegalStateException("Duplicate analysis strategies");
    }
    private static void invalid(String reason) {
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "RULE_CONFIGURATION_ERROR", "规则配置错误：" + reason);
    }
    public Snapshot snapshot() {
        var definitions = mapper.monsterDefinitions().stream().sorted(Comparator.comparing(Definition::ruleCode)).toList();
        var found = new HashSet<String>();
        for (var d : definitions) {
            if (!found.add(d.ruleCode())) invalid("重复定义 " + d.ruleCode());
            if (d.monsterName() == null || d.monsterName().isBlank() || d.description() == null || d.description().isBlank()
                    || d.suggestion() == null || d.suggestion().isBlank() || d.deduction() < 0 || d.baseXp() < 0)
                invalid("元数据不完整 " + d.ruleCode());
            if (d.enabled() && !implemented.contains(d.ruleCode())) invalid("启用了未实现策略 " + d.ruleCode());
        }
        var missing = new TreeSet<>(implemented); missing.removeAll(found);
        if (!missing.isEmpty()) invalid("缺少定义 " + missing);
        return new Snapshot(VERSION, definitions.stream().filter(Definition::enabled).map(Definition::ruleCode).toList(), definitions);
    }
}
