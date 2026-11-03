package com.bugmonster.analysis;

import com.bugmonster.api.ApiException;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CodeAnalyzerTest {
    private final CodeAnalyzer analyzer = TestRules.analyzer(List.of(new EmptyCatchRule(), new LongMethodRule()));
    private String caught(String body) {
        return "class A { void m() { try { work(); } catch (Exception ex) { " + body + " } } }";
    }
    private String method(int lines) {
        return "abstract class A { abstract void absent();\nvoid m() {\n"
                + "// comment or blank line still counts\n".repeat(lines - 2) + "}\n}";
    }
    @Test void normalCode() {
        var result = analyzer.analyze("class A { int sum(int a, int b) { return a+b; } }");
        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.score()).isEqualTo(100);
        assertThat(result.issues()).isEmpty();
        assertThat(result.parseProblems()).isEmpty();
    }
    @Test void emptyCatch() {
        var result = analyzer.analyze(caught(""));
        assertThat(result.score()).isEqualTo(85);
        assertThat(result.issues()).singleElement().satisfies(i -> {
            assertThat(i.ruleCode()).isEqualTo("EMPTY_CATCH");
            assertThat(i.monsterName()).isEqualTo("吞异常兽");
            assertThat(i.startLine()).isEqualTo(1);
            assertThat(i.endLine()).isEqualTo(1);
            assertThat(i.methodSignature()).isEqualTo("m()");
            assertThat(i.astFingerprint()).hasSize(64);
        });
    }
    @Test void commentsSemicolonsAndEmptyBlocksDoNotHandleExceptions() {
        for (String body : List.of("/* explanation */", ";", "; ; /* nothing */ ;", "{ { ; } }", "// only comment\n")) {
            assertThat(analyzer.analyze(caught(body)).issues()).as(body).hasSize(1);
        }
    }
    @Test void actualHandlingStatements() {
        for (String body : List.of("ex.printStackTrace();", "throw ex;", "return;", "System.err.println(ex);")) {
            assertThat(analyzer.analyze(caught(body)).issues()).as(body).isEmpty();
        }
    }
    @Test void methodBodyThirtyLinesPasses() {
        assertThat(analyzer.analyze(method(30)).issues()).isEmpty();
    }
    @Test void methodBodyThirtyOneLinesFailsWithExactRange() {
        var result = analyzer.analyze(method(31));
        assertThat(result.score()).isEqualTo(90);
        assertThat(result.issues()).singleElement().satisfies(i -> {
            assertThat(i.ruleCode()).isEqualTo("LONG_METHOD");
            assertThat(i.startLine()).isEqualTo(2);
            assertThat(i.endLine()).isEqualTo(32);
        });
    }
    @Test void bodyWithoutImplementationAndConstructorAreSkipped() {
        String code = "abstract class A { abstract void m();\n A() {\n" + "\n".repeat(40) + "} }";
        assertThat(analyzer.analyze(code).issues()).isEmpty();
    }
    @Test void stringsAndCommentsAreNotCodeStructures() {
        String code = """
                class A {
                  // catch (Exception x) { }  void fake() {
                  String text = "try { } catch (Exception ex) { }";
                  String block = \"\"\"
                    void fake() { catch (Exception ex) { ; } }
                    \"\"\";
                  void real() { System.out.println("} catch (Error e) {"); }
                }
                """;
        assertThat(analyzer.analyze(code).issues()).isEmpty();
    }
    @Test void bothRulesCombineDeductions() {
        String code = "class A {\nvoid m() {\n"
                + "try { work(); } catch (Exception ex) { /* swallowed */ ; }\n"
                + "\n".repeat(28) + "}\n}";
        var result = analyzer.analyze(code);
        assertThat(result.issues()).extracting(AnalysisResult.Finding::ruleCode)
                .containsExactlyInAnyOrder("EMPTY_CATCH", "LONG_METHOD");
        assertThat(result.score()).isEqualTo(75);
    }
    @Test void scoreCannotBeNegative() {
        var result = analyzer.analyze("class A { void m() {" + "try {} catch(Exception e) {}".repeat(9) + "} }");
        assertThat(result.issues()).hasSize(9);
        assertThat(result.score()).isZero();
    }
    @Test void syntaxErrorHasNullScoreAndStructuredProblems() {
        var result = analyzer.analyze("class A { void broken( {");
        assertThat(result.status()).isEqualTo("PARSE_ERROR");
        assertThat(result.score()).isNull();
        assertThat(result.issues()).isEmpty();
        assertThat(result.parseProblems()).isNotEmpty();
        assertThat(result.parseProblems().getFirst().message()).isNotBlank();
        assertThat(result.parseProblems().getFirst().startLine()).isPositive();
    }
    @Test void blankAndNullAreRejected() {
        for (String source : Arrays.asList(null, "", " \n\t\r")) {
            assertThatThrownBy(() -> analyzer.analyze(source)).isInstanceOf(ApiException.class);
        }
    }
    @Test void exactSizeBoundaryAndUtf16Units() {
        String prefix = "class A {}";
        String max = prefix + " ".repeat(20_000 - prefix.length());
        assertThat(analyzer.analyze(max).status()).isEqualTo("SUCCESS");
        assertThatThrownBy(() -> analyzer.analyze(max + " ")).isInstanceOf(ApiException.class);
        String emoji = "//" + "😀".repeat(9_994) + "\nclass A{}";
        assertThat(emoji.length()).isEqualTo(20_000);
        assertThat(analyzer.analyze(emoji).status()).isEqualTo("SUCCESS");
    }
    @Test void java21RecordPatternsAndSwitchAreExplicitlySupported() {
        String source = """
                record Box(Object value) {}
                class A {
                    String show(Object o) {
                        return switch (o) {
                            case Box(String s) -> s;
                            case null -> "null";
                            default -> "other";
                        };
                    }
                }
                """;
        assertThat(analyzer.analyze(source).status()).isEqualTo("SUCCESS");
    }
    @Test void nestedScopesAndLambdaCatchAreVisited() {
        String source = "class A { A(){try{}catch(Exception e){}}"
                + " void m(){Runnable r=()->{try{}catch(Exception e){;}};"
                + " class Local { void n(){try{}catch(Exception e){}} } } }";
        assertThat(analyzer.analyze(source).issues()).hasSize(3);
    }
    @Test void independentParsersAreSafeAcrossParallelCalls() throws Exception {
        try (var executor = Executors.newFixedThreadPool(8)) {
            var tasks = new ArrayList<Callable<String>>();
            for (int i=0;i<80;i++) {
                boolean valid = i%2 == 0;
                tasks.add(() -> analyzer.analyze(valid ? caught("") : "class {").status());
            }
            var values = executor.invokeAll(tasks);
            for (int i=0;i<values.size();i++)
                assertThat(values.get(i).get()).isEqualTo(i%2 == 0 ? "SUCCESS" : "PARSE_ERROR");
        }
    }
}
