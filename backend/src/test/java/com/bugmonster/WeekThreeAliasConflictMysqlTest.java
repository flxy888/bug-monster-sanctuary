package com.bugmonster;

import com.bugmonster.service.*;
import com.bugmonster.persistence.IdentityMapper;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties={"spring.datasource.url=${MYSQL_TEST_URL}","spring.datasource.username=${MYSQL_TEST_USER:root}","spring.datasource.password=${MYSQL_TEST_PASSWORD:}"})
@EnabledIfEnvironmentVariable(named="MYSQL_TEST_URL", matches=".+")
class WeekThreeAliasConflictMysqlTest {
    @Autowired ScanService scans;
    @Autowired ProjectService projects;
    @Autowired GrowthService growth;
    @Autowired IdentityMapper identities;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    record Prepared(long project, String catchKey, Set<String> lineageKeys, long xp, long contained) {}
    @BeforeEach void ownDatabaseOnly() {
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).matches("bug_monster_(w3_r01|w4)_[0-9_]+");
    }
    static String method(int params, boolean fixed) {
        var args=new ArrayList<String>(); for(int i=0;i<params;i++) args.add("int a"+i);
        return "void m("+String.join(",",args)+"){try{work();}catch(Exception ex){"+(fixed?"handle(ex);":"")+"}}";
    }
    static String source(String...methods) { return "class A {"+String.join("\n",methods)+"}"; }
    long project() { return projects.create("W3-R01 "+UUID.randomUUID(),"").id; }
    Set<String> lineageKeys(long id) { return new HashSet<>(jdbc.queryForList("SELECT lineage_key FROM issue_lineages WHERE project_id=?",String.class,id)); }
    long rewards(long id) { return jdbc.queryForObject("SELECT COUNT(*) FROM reward_logs WHERE project_id=?",Long.class,id); }
    Prepared prepare() {
        long id=project(); var baseline=scans.scan(id,source(method(6,false)));
        assertThat(baseline.comparisonStatus()).isEqualTo("BASELINE_CREATED");assertThat(baseline.xpEarned()).isZero();
        var second=scans.scan(id,source(method(5,false)));
        assertThat(second.comparisonStatus()).isEqualTo("COMPARED");assertThat(second.xpEarned()).isEqualTo(20);
        String key=baseline.issues().stream().filter(i->i.ruleCode.equals("EMPTY_CATCH")).findFirst().orElseThrow().lineageKey;
        assertThat(second.issues().getFirst().lineageKey).isEqualTo(key);
        return new Prepared(id,key,lineageKeys(id),growth.stats().totalXp(),growth.stats().containedCount());
    }
    void assertFrozen(Prepared p, ScanService.ScanResult r) {
        assertThat(r.comparisonStatus()).isEqualTo("COMPARED");assertThat(r.xpEarned()).isZero();assertThat(r.containedCount()).isZero();
        assertThat(r.resolutions()).noneMatch(x -> x.lineageKey().equals(p.catchKey) && x.resolution().equals("CONFIRMED_FIXED"));
        assertThat(rewards(p.project)).isEqualTo(1);assertThat(lineageKeys(p.project)).isEqualTo(p.lineageKeys);
        assertThat(growth.stats().totalXp()).isEqualTo(p.xp);assertThat(growth.stats().containedCount()).isEqualTo(p.contained);
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void bothDeclarationOrdersMustFreezeBeforeAnyIdentityIsClaimed(boolean cleanFirst) {
        var p=prepare();String five=method(5,true),six=method(6,false);
        var r=scans.scan(p.project,cleanFirst?source(five,six):source(six,five));
        assertThat(r.score()).isEqualTo(77);assertThat(r.issues()).hasSize(2).allSatisfy(i->assertThat(i.matchStatus).isEqualTo("AMBIGUOUS"));
        assertFrozen(p,r);assertThat(r.resolutions()).anySatisfy(x->{assertThat(x.lineageKey()).isEqualTo(p.catchKey);assertThat(x.resolution()).isEqualTo("AMBIGUOUS");});
    }
    @Test void repetitionAndOrderFlipsDoNotExpandRewardIdentities() {
        var p=prepare();
        for(int i=0;i<8;i++) assertFrozen(p,scans.scan(p.project,i%2==0?source(method(5,true),method(6,false)):source(method(6,false),method(5,true))));
        assertThat(jsonState(p.project).methods).hasSize(1);
    }
    @Test void threeHistoricalSignaturesHaveTheSameResultForEveryPermutation() {
        var p=prepare();assertThat(scans.scan(p.project,source(method(4,false))).xpEarned()).isZero();
        String a=method(4,true),b=method(5,false),c=method(6,false);
        for(String[] order:List.of(new String[]{a,b,c},new String[]{a,c,b},new String[]{b,a,c},new String[]{b,c,a},new String[]{c,a,b},new String[]{c,b,a}))
            assertFrozen(p,scans.scan(p.project,source(order)));
        assertThat(jsonState(p.project).methods).hasSize(1);
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void contractionMigrationDisappearanceAndRecurrenceNeverBackfillAnUncertainReward(boolean cleanFirst) {
        var p=prepare();scans.scan(p.project,cleanFirst?source(method(5,true),method(6,false)):source(method(6,false),method(5,true)));
        for(String code:List.of(source(method(5,true)),source(method(6,false)),source(method(6,true)),source(method(5,false)),source(method(5,true))))
            assertFrozen(p,scans.scan(p.project,code));
        var removed=scans.scan(p.project,source());assertThat(removed.xpEarned()).isZero();assertThat(removed.resolvedCount()).isZero();
        assertFrozen(p,scans.scan(p.project,source(method(5,false))));assertFrozen(p,scans.scan(p.project,source(method(5,true))));
        assertFrozen(p,scans.scan(p.project,source(method(7,false))));assertFrozen(p,scans.scan(p.project,source(method(7,true))));
        assertFrozen(p,scans.scan(p.project,source(method(5,false))));assertFrozen(p,scans.scan(p.project,source(method(5,true))));
    }
    @Test void aNewThirdSignatureDuringConflictIsNeverGivenARewardableIdentity() {
        var p=prepare();
        for(String code:List.of(source(method(5,true),method(6,false),method(7,false)),source(method(7,true)),source(method(7,false)),source(method(7,true))))
            assertFrozen(p,scans.scan(p.project,code));
    }
    @Test void unambiguousOverloadsContinueToRewardIndependently() {
        long id=project();String intBad="void m(int x){try{one();}catch(Exception e){}}",stringBad="void m(String x){try{two();}catch(Exception e){}}";
        var b=scans.scan(id,source(intBad,stringBad));assertThat(b.issues()).hasSize(2).allSatisfy(i->assertThat(i.matchStatus).isEqualTo("NEW"));
        var fixed=scans.scan(id,source(stringBad,intBad.replace("catch(Exception e){}","catch(Exception e){handle(e);}")));
        assertThat(fixed.xpEarned()).isEqualTo(20);assertThat(fixed.containedCount()).isEqualTo(1);
    }
    @Test void anUnrelatedExactOverloadStillRewardsBesideTheConflictingComponent() {
        var p=prepare();String other="void m(String value){try{otherWork();}catch(Exception ex){}}";
        var found=scans.scan(p.project,source(method(5,false),other));assertThat(found.xpEarned()).isZero();
        String otherKey=found.issues().stream().filter(i->i.methodSignature.contains("String")).findFirst().orElseThrow().lineageKey;
        assertThat(otherKey).isNotNull();
        var fixed=scans.scan(p.project,source(method(5,true),method(6,false),other.replace("catch(Exception ex){}","catch(Exception ex){handle(ex);}")));
        assertThat(fixed.xpEarned()).isEqualTo(20);assertThat(fixed.containedCount()).isEqualTo(1);
        assertThat(fixed.resolutions()).anyMatch(x->x.lineageKey().equals(otherKey)&&x.resolution().equals("CONFIRMED_FIXED"));
        assertThat(fixed.resolutions()).noneMatch(x->x.lineageKey().equals(p.catchKey)&&x.resolution().equals("CONFIRMED_FIXED"));
    }
    @Test void conflictingIdentityKeepsItsPreviouslyPaidHistory() {
        var p=prepare();assertThat(scans.scan(p.project,source(method(5,true))).xpEarned()).isEqualTo(20);
        scans.scan(p.project,source(method(5,false)));long xp=growth.stats().totalXp();
        for(String code:List.of(source(method(5,true),method(6,false)),source(method(6,false),method(5,true)),source(method(5,false)),source(method(5,true))))
            assertThat(scans.scan(p.project,code).xpEarned()).isZero();
        assertThat(rewards(p.project)).isEqualTo(2);assertThat(growth.stats().totalXp()).isEqualTo(xp);assertThat(lineageKeys(p.project)).isEqualTo(p.lineageKeys);
    }
    @Test void oldStateWithoutNewFieldCreatesOneBaselineAndPreservesNormalRepairEligibility() throws Exception {
        long id=project();var baseline=scans.scan(id,source(method(6,false)));var keys=lineageKeys(id);
        ObjectNode old=(ObjectNode)json.readTree(identities.loadState(id));old.put("policy","week3-match-v1");
        old.withArray("methods").forEach(m->((ObjectNode)m).remove("rewardBlocked"));identities.saveState(id,json.writeValueAsString(old));
        var upgrade=scans.scan(id,source(method(6,false)));assertThat(upgrade.comparisonStatus()).isEqualTo("INCOMPATIBLE_RULESET");assertThat(upgrade.xpEarned()).isZero();
        assertThat(lineageKeys(id)).isEqualTo(keys);assertThat(scans.detail(baseline.scanId())).usingRecursiveComparison().isEqualTo(baseline);
        var fixed=scans.scan(id,source(method(5,false)));assertThat(fixed.comparisonStatus()).isEqualTo("COMPARED");assertThat(fixed.xpEarned()).isEqualTo(20);
    }
    @Test void oldWinnerAndPlaceholderAliasOverlapRemainFrozenEvenAfterCandidateRemoval() throws Exception {
        var p=prepare();ObjectNode old=(ObjectNode)json.readTree(identities.loadState(p.project));old.put("policy","week3-match-v1");
        ObjectNode winner=(ObjectNode)old.withArray("methods").get(0);winner.remove("rewardBlocked");
        ObjectNode placeholder=winner.deepCopy();placeholder.put("key",UUID.randomUUID().toString());placeholder.put("trusted",false);
        var signatures=placeholder.putArray("signatures");signatures.add(jsonState(p.project).methods.getFirst().signatures.stream().filter(s->s.split("int",-1).length==7).findFirst().orElseThrow());
        old.withArray("methods").add(placeholder);identities.saveState(p.project,json.writeValueAsString(old));
        var upgrade=scans.scan(p.project,source(method(5,true)));assertThat(upgrade.comparisonStatus()).isEqualTo("INCOMPATIBLE_RULESET");assertThat(upgrade.xpEarned()).isZero();
        assertFrozen(p,scans.scan(p.project,source(method(5,false))));assertFrozen(p,scans.scan(p.project,source(method(5,true))));
        for(var m:json.readTree(identities.loadState(p.project)).withArray("methods")) assertThat(m.path("rewardBlocked").asBoolean()).isTrue();
    }
    IdentityMatcher.State jsonState(long id) {
        try { return json.readValue(identities.loadState(id),IdentityMatcher.State.class); }
        catch(Exception e){throw new IllegalStateException(e);}
    }
}
