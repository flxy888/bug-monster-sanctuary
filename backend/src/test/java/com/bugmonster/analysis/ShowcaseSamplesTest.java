package com.bugmonster.analysis;

import com.bugmonster.service.IdentityMatcher;
import com.bugmonster.persistence.SanctuaryMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.body.MethodDeclaration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.assertj.core.api.Assertions.*;

class ShowcaseSamplesTest {
    record Example(String directory, String className, String method, String rule, int beforeScore) {}

    static Stream<Example> examples() {
        return Stream.of(
                new Example("empty-catch", "OrderCsvImporter", "importLines", "EMPTY_CATCH", 85),
                new Example("empty-if", "OrderPricingService", "totalDue", "EMPTY_IF", 92),
                new Example("parameters", "ShippingQuoteService", "quoteShipping", "TOO_MANY_PARAMETERS", 92),
                new Example("long-method", "SalesReportExporter", "export", "LONG_METHOD", 90),
                new Example("deep-nesting", "BackorderReminderPlanner", "plan", "DEEP_NESTING", 90));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("examples")
    void completeShowcaseHasOnlyItsTargetRuleAndKeepsMethodContinuity(Example example) throws Exception {
        Path root = Path.of("../tests/samples/showcase", example.directory());
        String before = Files.readString(root.resolve("before/" + example.className() + ".java"));
        String after = Files.readString(root.resolve("after/" + example.className() + ".java"));
        var strategies = TestRules.strategies();
        SanctuaryMapper metadataOnly = (SanctuaryMapper) java.lang.reflect.Proxy.newProxyInstance(
                SanctuaryMapper.class.getClassLoader(), new Class<?>[]{SanctuaryMapper.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("monsterDefinitions")) return TestRules.definitions();
                    throw new UnsupportedOperationException("Showcase test forbids database access: " + method.getName());
                });
        CodeAnalyzer analyzer = new CodeAnalyzer(strategies, new RuleCatalog(metadataOnly, strategies));
        AnalysisResult initial = analyzer.analyze(before);
        AnalysisResult fixed = analyzer.analyze(after);
        assertThat(initial.status()).isEqualTo("SUCCESS");
        assertThat(initial.score()).isEqualTo(example.beforeScore());
        assertThat(initial.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.ruleCode()).isEqualTo(example.rule());
            assertThat(issue.typeName()).isEqualTo(example.className());
            assertThat(issue.methodName()).isEqualTo(example.method());
            assertThat(issue.supportedScope()).isTrue();
        });
        assertThat(fixed.status()).isEqualTo("SUCCESS");
        assertThat(fixed.score()).isEqualTo(100);
        assertThat(fixed.issues()).isEmpty();
        var retained = IdentitySupport.methods(after).stream()
                .filter(m -> m.type().equals(example.className()) && m.name().equals(example.method())).toList();
        assertThat(retained).hasSize(1);

        // In-memory identity comparison only: no service, database, settlement, or awarded XP.
        var matcher = new IdentityMatcher();
        var state = new IdentityMatcher.State();
        var baseline = matcher.compare(state, before, initial.issues(), "showcase-five-rules-week2-v1");
        assertThat(baseline.status()).isEqualTo("BASELINE_CREATED");
        String methodKey = state.lives.getFirst().methodKey;
        state.baselineScanId = 1L;
        var comparison = matcher.compare(state, after, fixed.issues(), "showcase-five-rules-week2-v1");
        assertThat(comparison.status()).isEqualTo("COMPARED");
        assertThat(comparison.ambiguousCount()).isZero();
        assertThat(comparison.resolutions()).singleElement().satisfies(resolution -> {
            assertThat(resolution.ruleCode()).isEqualTo(example.rule());
            assertThat(resolution.resolution()).isEqualTo("CONFIRMED_FIXED");
            assertThat(resolution.rewardEligible()).isTrue();
            assertThat(resolution.xp()).isZero();
        });
        assertThat(state.methods.stream().filter(m -> m.key.equals(methodKey))).singleElement()
                .satisfies(method -> assertThat(method.signature).isEqualTo(retained.getFirst().signature()));

        Map<String,Object> evidence = new LinkedHashMap<>();
        evidence.put("case", example);
        evidence.put("rulesetVersion", RuleCatalog.VERSION);
        evidence.put("metadataSource", "repository database/02-seed.sql via TestRules; no DB connection");
        evidence.put("parserLanguageLevel", "JAVA_21");
        evidence.put("before", analysisEvidence(before, initial));
        evidence.put("after", analysisEvidence(after, fixed));
        evidence.put("matchingPolicy", IdentityMatcher.POLICY);
        evidence.put("comparisonStatus", comparison.status());
        evidence.put("resolutions", comparison.resolutions());
        evidence.put("sameMethodIdentity", true);
        evidence.put("sampleExecuted", false);
        evidence.put("databaseAccessed", false);
        Path output = Path.of(System.getProperty("showcase.evidence.dir", "target-demo-realistic/showcase-evidence"));
        Files.createDirectories(output);
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.resolve(example.directory()+".json").toFile(), evidence);
    }

    private Map<String,Object> analysisEvidence(String source, AnalysisResult result) throws Exception {
        var parsed = new JavaParser(new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21))
                .parse(source).getResult().orElseThrow();
        var methods = parsed.findAll(MethodDeclaration.class).stream().map(method -> Map.of(
                "name", method.getNameAsString(), "parameters", method.getParameters().size(),
                "bodyLines", method.getBody().map(body -> body.getEnd().orElseThrow().line - body.getBegin().orElseThrow().line + 1).orElse(0))).toList();
        return Map.of("sourceSha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(java.nio.charset.StandardCharsets.UTF_8))),
                "sourceLines", source.lines().count(), "status", result.status(), "score", result.score(),
                "issues", result.issues(), "methods", methods);
    }
}
