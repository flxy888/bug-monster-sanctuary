package com.bugmonster.analysis;

import com.bugmonster.api.ApiException;
import com.github.javaparser.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class CodeAnalyzer {
    public static final int MAX_SOURCE_LENGTH = 20_000;
    private final List<AnalysisRule> rules;
    private final RuleCatalog catalog;
    public CodeAnalyzer(List<AnalysisRule> rules, RuleCatalog catalog) {
        this.catalog = catalog;
        this.rules = rules.stream().sorted(Comparator.comparing(AnalysisRule::code)).toList();
    }
    public static void validateSource(String source) {
        if (source == null || source.isBlank()) throw ApiException.invalid("源码不能为空白");
        validateDraft(source);
    }
    public static void validateDraft(String source) {
        if (source == null || source.length() > MAX_SOURCE_LENGTH)
            throw ApiException.invalid("源码不得超过 20000 个 UTF-16 字符单位，且不能为 null");
    }
    public AnalysisResult analyze(String source) {
        return analyze(source, catalog.snapshot());
    }
    public AnalysisResult analyze(String source, RuleCatalog.Snapshot snapshot) {
        validateSource(source);
        var config = new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
        var parsed = new JavaParser(config).parse(source);
        if (!parsed.isSuccessful() || parsed.getResult().isEmpty()) {
            var problems = parsed.getProblems().stream().map(p -> {
                var range = p.getLocation().flatMap(TokenRange::toRange);
                return new AnalysisResult.ParseProblem(p.getMessage(),
                        range.map(r -> r.begin.line).orElse(null), range.map(r -> r.begin.column).orElse(null),
                        range.map(r -> r.end.line).orElse(null), range.map(r -> r.end.column).orElse(null));
            }).toList();
            return new AnalysisResult("PARSE_ERROR", null, List.of(), problems);
        }
        var unit = parsed.getResult().orElseThrow();
        var findings = rules.stream().filter(r -> snapshot.enabledRuleCodes().contains(r.code()))
                .flatMap(r -> r.inspect(unit).stream()).map(f -> {
                    var d = snapshot.definition(f.ruleCode());
                    return new AnalysisResult.Finding(f.ruleCode(), d.monsterName(), f.startLine(), f.endLine(),
                            f.message(), d.suggestion(), d.deduction(), f.typeName(), f.methodSignature(), f.astFingerprint(),
                            f.supportedScope(), f.methodName(), f.contextAnchor());
                })
                .sorted(Comparator.comparingInt(AnalysisResult.Finding::startLine)
                        .thenComparing(AnalysisResult.Finding::ruleCode)).toList();
        return new AnalysisResult("SUCCESS",
                Math.max(0, 100 - findings.stream().mapToInt(AnalysisResult.Finding::deduction).sum()), findings, List.of());
    }
}
