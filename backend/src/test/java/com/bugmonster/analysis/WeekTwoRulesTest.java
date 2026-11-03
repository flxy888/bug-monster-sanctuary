package com.bugmonster.analysis;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WeekTwoRulesTest {
    private final CodeAnalyzer analyzer = TestRules.analyzer(TestRules.strategies());
    private List<AnalysisResult.Finding> hits(String source, String code) {
        var result = analyzer.analyze(source);
        assertThat(result.status()).as(source).isEqualTo("SUCCESS");
        return result.issues().stream().filter(f -> f.ruleCode().equals(code)).toList();
    }
    private String method(String body) { return "class A { void m(){" + body + "} }"; }
    private String chain(int depth) { return "if(true){".repeat(depth) + "work();" + "}".repeat(depth); }
    private void deep(String body, int count) { assertThat(hits(method(body), "DEEP_NESTING")).hasSize(count); }
    @Test void fiveRulesOnceAndFortyNineScore() throws Exception {
        var r = analyzer.analyze(Files.readString(Path.of("../tests/samples/WeekTwoDemo.java")));
        assertThat(r.status()).isEqualTo("SUCCESS");
        assertThat(r.issues()).hasSize(5).extracting(AnalysisResult.Finding::ruleCode)
                .containsExactlyInAnyOrderElementsOf(TestRules.strategies().stream().map(AnalysisRule::code).toList());
        assertThat(r.issues().stream().mapToInt(AnalysisResult.Finding::deduction).sum()).isEqualTo(51);
        assertThat(r.score()).isEqualTo(49);
        for (var i : r.issues()) {
            assertThat(i.monsterName()).isNotBlank(); assertThat(i.suggestion()).isNotBlank();
            assertThat(i.typeName()).isEqualTo("WeekTwoDemo"); assertThat(i.methodSignature()).startsWith("inspect(");
            assertThat(i.astFingerprint()).hasSize(64);
        }
    }
    @Test void parameterFiveSixAndVarargsBoundaries() {
        String five = "int a,int b,int c,int d,String... e";
        assertThat(hits("class A { void m("+five+"){} }", "TOO_MANY_PARAMETERS")).isEmpty();
        var r = hits("abstract class A {\n abstract void m(int z,"+five+");\n}", "TOO_MANY_PARAMETERS");
        assertThat(r).singleElement().satisfies(f -> {
            assertThat(f.startLine()).isEqualTo(2); assertThat(f.endLine()).isEqualTo(2);
            assertThat(f.deduction()).isEqualTo(8); assertThat(f.monsterName()).isEqualTo("多手触手怪");
        });
        assertThat(hits("class A { A(int z,"+five+"){} }", "TOO_MANY_PARAMETERS")).isEmpty();
    }
    @Test void emptyThenNormalizationAndElseIndependence() {
        for (var body : List.of("{}", "{ /* explanation */ }", ";", "{;;}", "{{{;};} ;}")) {
            assertThat(hits(method("if(true)"+body+" else work();"), "EMPTY_IF")).as(body).hasSize(1);
        }
        for (var body : List.of("{work();}", "{return;}", "{if(true) work();}"))
            assertThat(hits(method("if(true)"+body+"else {}"), "EMPTY_IF")).as(body).isEmpty();
        assertThat(hits("class A { void m(){\nif(true) {\n// comment\n;\n}\n} }", "EMPTY_IF"))
                .singleElement().satisfies(f -> { assertThat(f.startLine()).isEqualTo(2); assertThat(f.endLine()).isEqualTo(5); });
    }
    @Test void deepThreeFourFiveAndNoRepeatOnSameChain() {
        deep(chain(3),0); deep(chain(4),1); deep(chain(5),1); deep(chain(8),1);
        assertThat(hits("class A { void m(){\nif(true){\nfor(;;){\nwhile(true){\ndo { work(); } while(true);\n}}}\n}}", "DEEP_NESTING"))
                .singleElement().satisfies(f -> {assertThat(f.startLine()).isEqualTo(5); assertThat(f.endLine()).isEqualTo(5);});
    }
    @Test void enhancedForCountsAndParallelFourthBranchesReportSeparately() {
        deep("if(true){for(var x:xs){while(true){if(true) work(); else if(false) work(); else for(;;) work();}}}",2);
        deep(chain(4)+chain(4),2);
        deep("if(true){if(true){if(true){if(true){while(true) work();}} else {for(;;) work();}}}",2);
    }
    @Test void elseIfChainsStayAtSameLevel() {
        deep("if(true) work(); else if(false) work(); else if(false) work(); else if(false) work();",0);
        deep("if(true){if(true){if(true)work();else if(false){while(true)work();}}}",1);
    }
    @Test void blocksTrySwitchSynchronizedAndTernaryDoNotIncreaseDepth() {
        deep("{{try { synchronized(this) { switch(x) {case 1: "+chain(3)+" break;default:work();} } } finally {work();}}}",0);
        deep("boolean b=true?false:true; try {"+chain(4)+"} catch(Exception e){work();}",1);
    }
    @Test void lambdaStartsAtZeroInBodyAndCondition() {
        deep("if(true){if(true){if(true){Runnable r=()->{"+chain(3)+"};}}}",0);
        deep("if(true){if(true){if(true){Runnable r=()->{"+chain(4)+"};}}}",1);
        deep("if(test(()->{"+chain(4)+"})) work();",1);
    }
    @Test void localAndAnonymousExecutablesDoNotInheritOuterDepth() {
        String bodies = "class Local { Local(){"+chain(3)+"} {"+chain(3)+"} void n(){"+chain(3)+"} }"
                +"Object o=new Object(){ {"+chain(3)+"} void n(){"+chain(4)+"} };";
        deep("if(true){if(true){if(true){"+bodies+"}}}",1);
    }
    @Test void constructorsInitializersMethodsAndRecordConstructorsAreIndependent() {
        String source = "class A { A(){"+chain(4)+"} {"+chain(4)+"} static {"+chain(4)+"} void m(){"+chain(4)+"} }"
                +"record R(int x){R{"+chain(4)+"}}";
        assertThat(hits(source,"DEEP_NESTING")).hasSize(5);
    }
    @Test void commentsAndStringsDoNotInventAnyOfFiveStructures() {
        var r = analyzer.analyze("class A { /* if(true){} void m(int a,int b,int c,int d,int e,int f){} */"
                +"String s=\"if(true){while(true){for(;;){if(true){} }}} catch(Exception e){}\"; void n(){work();} }");
        assertThat(r.status()).isEqualTo("SUCCESS"); assertThat(r.issues()).isEmpty();
    }
    @Test void normalFiveRuleCodeHasNoHits() { assertThat(analyzer.analyze(method("if(true){work();}else{}" )).issues()).isEmpty(); }
}
