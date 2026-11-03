package com.bugmonster;

import com.bugmonster.service.*;
import com.bugmonster.persistence.*;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.TestPropertySource;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties={"spring.datasource.url=${MYSQL_TEST_URL}","spring.datasource.username=${MYSQL_TEST_USER:root}","spring.datasource.password=${MYSQL_TEST_PASSWORD:}"})
@EnabledIfEnvironmentVariable(named="MYSQL_TEST_URL", matches=".+")
class WeekThreeMysqlTest {
    @Autowired ScanService scans;
    @Autowired ProjectService projects;
    @Autowired GrowthService growth;
    @Autowired IdentityMapper identities;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    static final String BAD="class A { void m(){try{work();}catch(Exception e){}} }";
    static final String GOOD="class A { void m(){try{work();}catch(Exception e){handle(e);}} }";
    long project() { return projects.create("Week3 "+UUID.randomUUID(),"class SavedDraft {}").id; }
    long count(String table, long id) { return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE project_id=?",Long.class,id); }
    long found() { return growth.stats().foundCount(); }
    @Test void baselineRepairRepeatRecurrenceAndImmutableHistory() {
        long id=project(),xp=growth.stats().totalXp(),found=found();
        var baseline=scans.scan(id,BAD); assertThat(baseline.score()).isEqualTo(85);
        assertThat(baseline.comparisonStatus()).isEqualTo("BASELINE_CREATED"); assertThat(baseline.xpEarned()).isZero();
        String lineage=baseline.issues().getFirst().lineageKey;
        assertThat(lineage).isNotEqualTo(baseline.issues().getFirst().issueKey);
        var fixed=scans.scan(id,GOOD); assertThat(fixed.score()).isEqualTo(100);
        assertThat(fixed.resolvedCount()).isEqualTo(1); assertThat(fixed.containedCount()).isEqualTo(1); assertThat(fixed.xpEarned()).isEqualTo(20);
        assertThat(scans.scan(id,GOOD).xpEarned()).isZero();
        var recurrence=scans.scan(id,BAD); assertThat(recurrence.issues().getFirst().lineageKey).isEqualTo(lineage);
        var fixedAgain=scans.scan(id,GOOD); assertThat(fixedAgain.resolvedCount()).isEqualTo(1); assertThat(fixedAgain.containedCount()).isZero();
        assertThat(fixedAgain.resolutions()).anySatisfy(r -> assertThat(r.reason()).isEqualTo("ALREADY_REWARDED"));
        assertThat(count("reward_logs",id)).isEqualTo(1); assertThat(growth.stats().totalXp()).isEqualTo(xp+20); assertThat(found()).isEqualTo(found+1);
        assertThat(scans.detail(baseline.scanId())).usingRecursiveComparison().isEqualTo(baseline);
        assertThat(scans.detail(fixed.scanId())).usingRecursiveComparison().isEqualTo(fixed);
        assertThat(projects.require(id).sourceCode).isEqualTo("class SavedDraft {}");
    }
    @Test void commentsFormattingAndChangedSingleAnchorStayPersistent() {
        long id=project(); var b=scans.scan(id,BAD); String key=b.issues().getFirst().lineageKey;
        var formatted=scans.scan(id,"// header\nclass A {\nvoid m() { try { work(); /*same*/ } catch (Exception e) { ; { /*still empty*/ } } } }");
        assertThat(formatted.xpEarned()).isZero(); assertThat(formatted.issues().getFirst().lineageKey).isEqualTo(key);
        var changed=scans.scan(id,BAD.replace("work()","differentWork()"));
        assertThat(changed.xpEarned()).isZero(); assertThat(changed.issues().getFirst().lineageKey).isEqualTo(key);
        assertThat(count("issue_lineages",id)).isEqualTo(1);
    }
    @Test void failureDoesNotAdvanceEitherBaseline() {
        long id=project(); var b=scans.scan(id,BAD); String state=identities.loadState(id);
        var failure=scans.scan(id,"class A { void m(");
        assertThat(failure.status()).isEqualTo("PARSE_ERROR"); assertThat(failure.score()).isNull(); assertThat(failure.xpEarned()).isZero();
        assertThat(identities.loadState(id)).isEqualTo(state); assertThat(projects.require(id).lastSuccessfulScanId).isEqualTo(b.scanId());
        var fixed=scans.scan(id,GOOD); assertThat(fixed.baselineScanId()).isEqualTo(b.scanId()); assertThat(fixed.xpEarned()).isEqualTo(20);
    }
    @Test void formattingOnlyShorteningDoesNotEarnLongMethodReward() {
        long id=project();String longForm="class A {void m(){\n"+"work();\n".repeat(30)+"}}";
        scans.scan(id,longForm);String compact=longForm.replace("\n", " ");var formatted=scans.scan(id,compact);
        assertThat(formatted.score()).isEqualTo(100);assertThat(formatted.xpEarned()).isZero();assertThat(formatted.ambiguousCount()).isEqualTo(1);
        assertThat(scans.scan(id,compact).xpEarned()).isZero();assertThat(scans.scan(id,"class A {void m(){extractedWork();}}").xpEarned()).isEqualTo(20);
    }
    @Test void fixAAndNewBDoNotCancelByCounting() {
        long id=project(); scans.scan(id,BAD);
        var r=scans.scan(id,"class A { void m(){try{work();}catch(Exception e){handle(e);}} void b(){try{newWork();}catch(Exception e){}} }");
        assertThat(r.score()).isEqualTo(85); assertThat(r.xpEarned()).isEqualTo(20); assertThat(r.containedCount()).isEqualTo(1);
        assertThat(r.issues()).hasSize(1); assertThat(r.issues().getFirst().matchStatus).isEqualTo("NEW"); assertThat(count("issue_lineages",id)).isEqualTo(2);
    }
    @ParameterizedTest @ValueSource(strings={"class A {}","class Other {}"})
    void scopeRemovalIsNotContainment(String removed) {
        long id=project(); scans.scan(id,BAD); var r=scans.scan(id,removed);
        assertThat(r.removedCount()).isEqualTo(1); assertThat(r.resolvedCount()).isZero(); assertThat(r.containedCount()).isZero(); assertThat(r.xpEarned()).isZero();
    }
    static Stream<Arguments> repairs() {
        return Stream.of(
            Arguments.of("EMPTY_CATCH",BAD,GOOD),
            Arguments.of("EMPTY_IF","class A {void m(){if(ok){}}}","class A {void m(){if(ok){work();}}}"),
            Arguments.of("TOO_MANY_PARAMETERS","class A {void m(int a,int b,int c,int d,int e,int f){work();}}","class A {void m(int a,int b,int c,int d,int e){work();}}"),
            Arguments.of("LONG_METHOD","class A {void m(){\n"+"work();\n".repeat(30)+"}}","class A {void m(){work();}}"),
            Arguments.of("DEEP_NESTING","class A {void m(){if(a){while(b){for(;;){if(c){work();}}}}}}","class A {void m(){if(a){while(b){for(;;){work();}}}}}")
        );
    }
    @ParameterizedTest @MethodSource("repairs")
    void eachRuleHasAnIndependentComparableRepair(String rule,String bad,String good) {
        long id=project(); var b=scans.scan(id,bad); assertThat(b.issues()).hasSize(1); assertThat(b.issues().getFirst().ruleCode).isEqualTo(rule);
        var r=scans.scan(id,good); assertThat(r.issues()).isEmpty(); assertThat(r.xpEarned()).isEqualTo(20);
        var restored=scans.scan(id,bad); assertThat(restored.issues().getFirst().lineageKey).isEqualTo(b.issues().getFirst().lineageKey);
        assertThat(scans.scan(id,good).xpEarned()).isZero();
    }
    @Test void overloadRenameAndMoveNeverAutomaticallyReward() {
        for(String replacement:List.of("class A {void renamed(){work();}}","class B {void m(){work();}}")) {
            long id=project();scans.scan(id,BAD);var r=scans.scan(id,replacement);assertThat(r.xpEarned()).isZero();assertThat(r.removedCount()).isEqualTo(1);
        }
        long id=project();scans.scan(id,"class A {void m(int a,int b,int c,int d,int e,int f){} void m(String a,String b,String c,String d,String e,String f){}}");
        var r=scans.scan(id,"class A {void m(int a,int b,int c,int d,int e){} void m(String a,String b,String c,String d,String e){}}");
        assertThat(r.xpEarned()).isZero();assertThat(r.containedCount()).isZero();
        long renamed=project();scans.scan(renamed,BAD);var stillBad=scans.scan(renamed,BAD.replace("void m()","void n()"));
        assertThat(stillBad.issues().getFirst().matchStatus).isEqualTo("AMBIGUOUS");assertThat(scans.scan(renamed,GOOD.replace("void m()","void n()")).xpEarned()).isZero();
    }
    @Test void distinguishableCatchesRepairOnlyOne() {
        long id=project();String two="class A {void m(){try{one();}catch(Exception e){} try{two();}catch(Exception e){}}}";
        var b=scans.scan(id,two); assertThat(b.issues()).hasSize(2);
        var r=scans.scan(id,two.replace("one();}catch(Exception e){}","one();}catch(Exception e){handle(e);}"));
        assertThat(r.xpEarned()).isEqualTo(20);assertThat(r.resolvedCount()).isEqualTo(1);
        assertThat(r.issues().getFirst().lineageKey).isEqualTo(b.issues().get(1).lineageKey);
    }
    @Test void identicalGroupDoesNotMintIdentitiesDuringPartialRepairOrExpansion() {
        long id=project();String block="try{same();}catch(Exception e){} ";String head="class A {void m(){";
        var b=scans.scan(id,head+block.repeat(2)+"}}");assertThat(b.ambiguousCount()).isEqualTo(2);assertThat(count("issue_lineages",id)).isEqualTo(2);
        var partial=scans.scan(id,head+block+"}}");assertThat(partial.xpEarned()).isZero();assertThat(partial.ambiguousCount()).isEqualTo(2);
        var expanded=scans.scan(id,head+block.repeat(3)+"}}");assertThat(expanded.xpEarned()).isZero();assertThat(count("issue_lineages",id)).isEqualTo(2);
        var fixed=scans.scan(id,head+"work();}}");assertThat(fixed.xpEarned()).isEqualTo(40);assertThat(fixed.containedCount()).isEqualTo(2);
        scans.scan(id,head+block+"}}");assertThat(scans.scan(id,head+"work();}}").xpEarned()).isZero();assertThat(count("reward_logs",id)).isEqualTo(2);
    }
    @Test void ambiguousChangedManyToManyPreservesOriginalGroup() {
        long id=project();String before="class A {void m(){try{one();}catch(Exception e){} try{two();}catch(Exception e){}}}";
        scans.scan(id,before);var r=scans.scan(id,before.replace("one()","newOne()").replace("two()","newTwo()"));
        assertThat(r.ambiguousCount()).isEqualTo(2);assertThat(r.xpEarned()).isZero();assertThat(count("issue_lineages",id)).isEqualTo(2);
        assertThat(scans.scan(id,"class A {void m(){work();}}").xpEarned()).isEqualTo(40);
    }
    @Test void unsupportedExecutableScopesNeverClaimOuterMethod() {
        for(String source:List.of(
            "class A {A(){try{work();}catch(Exception e){}}}",
            "class A {{try{work();}catch(Exception e){}}}",
            "class A {void m(){Runnable r=()->{try{work();}catch(Exception e){}};}}",
            "class A {void m(){class Local {void n(){try{work();}catch(Exception e){}}}}}",
            "class A {void m(){Object o=new Object(){void n(){try{work();}catch(Exception e){}}};}}")) {
            long id=project();var b=scans.scan(id,source);assertThat(b.issues()).hasSize(1);assertThat(b.issues().getFirst().matchStatus).isEqualTo("UNSUPPORTED_SCOPE");
            assertThat(count("issue_lineages",id)).isZero();assertThat(scans.scan(id,source.replace("catch(Exception e){}","catch(Exception e){handle(e);}")).xpEarned()).isZero();
        }
    }
    @Test void movingAnUnpaidIssueIntoLambdaDoesNotTurnItIntoAReward() {
        long id=project();scans.scan(id,BAD);
        String moved="class A {void m(){Runnable r=()->{try{work();}catch(Exception e){}};}}";
        var r=scans.scan(id,moved);assertThat(r.xpEarned()).isZero();assertThat(r.resolvedCount()).isZero();assertThat(r.ambiguousCount()).isEqualTo(1);
        assertThat(r.resolutions()).anySatisfy(x -> assertThat(x.reason()).isEqualTo("UNSUPPORTED_OBSERVATION_PREVENTS_CONFIRMATION"));
        assertThat(scans.scan(id,moved.replace("catch(Exception e){}","catch(Exception e){handle(e);}")).xpEarned()).isZero();
    }
    @Test void configurationChangeCreatesBaselineWithoutWipingRewardHistory() {
        long id=project();scans.scan(id,BAD);
        jdbc.update("UPDATE monster_types SET enabled=FALSE WHERE rule_code='EMPTY_CATCH'");
        try {var r=scans.scan(id,GOOD);assertThat(r.comparisonStatus()).isEqualTo("INCOMPATIBLE_RULESET");assertThat(r.xpEarned()).isZero();}
        finally {jdbc.update("UPDATE monster_types SET enabled=TRUE WHERE rule_code='EMPTY_CATCH'");}
        assertThat(scans.scan(id,BAD).xpEarned()).isZero();assertThat(scans.scan(id,GOOD).xpEarned()).isEqualTo(20);
        jdbc.update("UPDATE monster_types SET deduction=16 WHERE rule_code='EMPTY_CATCH'");
        try {assertThat(scans.scan(id,BAD).comparisonStatus()).isEqualTo("INCOMPATIBLE_RULESET");}
        finally {jdbc.update("UPDATE monster_types SET deduction=15 WHERE rule_code='EMPTY_CATCH'");}
        assertThat(scans.scan(id,BAD).xpEarned()).isZero();assertThat(scans.scan(id,GOOD).xpEarned()).isZero();
        jdbc.update("UPDATE project_identity_state SET state_json=JSON_SET(state_json,'$.policy','old-matching-policy') WHERE project_id=?",id);
        assertThat(scans.scan(id,BAD).comparisonStatus()).isEqualTo("INCOMPATIBLE_RULESET");
    }
    @Test void noEnabledTypesHasZeroProgressAndNoDivisionByZero() {
        jdbc.update("UPDATE monster_types SET enabled=FALSE");
        try {assertThat(growth.stats().enabledTypes()).isZero();assertThat(growth.stats().catalogProgress()).isZero();assertThat(scans.scan(project(),BAD).score()).isEqualTo(100);}
        finally {jdbc.update("UPDATE monster_types SET enabled=TRUE");}
    }
    @Test void sameProjectConcurrencyAndStableRewardUniqueKey() throws Exception {
        long id=project();scans.scan(id,BAD);long xp=growth.stats().totalXp();
        var results=concurrent(id,id);assertThat(results.stream().mapToInt(ScanService.ScanResult::xpEarned).sum()).isEqualTo(20);
        assertThat(growth.stats().totalXp()).isEqualTo(xp+20);assertThat(count("reward_logs",id)).isEqualTo(1);
        String columns="owner_id,project_id,issue_key,lineage_key,identity_policy,source_scan_id,resolved_scan_id,monster_type_id,xp";
        assertThatThrownBy(()->jdbc.update("INSERT INTO reward_logs("+columns+") SELECT "+columns+" FROM reward_logs WHERE project_id=?",id)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("INSERT INTO reward_logs(owner_id,project_id,issue_key,source_scan_id,resolved_scan_id,monster_type_id,xp) SELECT owner_id,project_id,UUID(),source_scan_id,resolved_scan_id,monster_type_id,xp FROM reward_logs WHERE project_id=?",id)).isInstanceOf(DataAccessException.class);
    }
    @Test void twoProjectsOfSameOwnerKeepBothAwards() throws Exception {
        long a=project(),b=project();scans.scan(a,BAD);scans.scan(b,BAD);long xp=growth.stats().totalXp(),contained=growth.stats().containedCount();
        var results=concurrent(a,b);assertThat(results.stream().mapToInt(ScanService.ScanResult::xpEarned).sum()).isEqualTo(40);
        assertThat(growth.stats().totalXp()).isEqualTo(xp+40);assertThat(growth.stats().containedCount()).isEqualTo(contained+2);
        assertThat(count("reward_logs",a)).isEqualTo(1);assertThat(count("reward_logs",b)).isEqualTo(1);
    }
    List<ScanService.ScanResult> concurrent(long a,long b) throws Exception {
        var latch=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var x=pool.submit(()->{latch.await();return scans.scan(a,GOOD);});var y=pool.submit(()->{latch.await();return scans.scan(b,GOOD);});latch.countDown();
            return List.of(x.get(30,TimeUnit.SECONDS),y.get(30,TimeUnit.SECONDS));
        }
    }
    @Test void failureAfterRewardInsertionRollsBackEntireSettlement() {
        long id=project();var baseline=scans.scan(id,BAD);String state=identities.loadState(id);var stats=growth.stats();long runs=count("scan_runs",id);
        jdbc.execute("CREATE TRIGGER week3_test_fail_counts BEFORE UPDATE ON user_monsters FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='week3 intentional catalog statistics failure after XP update'");
        try {assertThatThrownBy(()->scans.scan(id,GOOD)).isInstanceOf(DataAccessException.class);}
        finally {jdbc.execute("DROP TRIGGER week3_test_fail_counts");}
        assertThat(count("scan_runs",id)).isEqualTo(runs);assertThat(count("reward_logs",id)).isZero();assertThat(count("issue_lineages",id)).isEqualTo(1);
        assertThat(identities.loadState(id)).isEqualTo(state);assertThat(growth.stats()).isEqualTo(stats);assertThat(projects.require(id).lastSuccessfulScanId).isEqualTo(baseline.scanId());
        assertThat(scans.scan(id,GOOD).xpEarned()).isEqualTo(20);
    }
}
