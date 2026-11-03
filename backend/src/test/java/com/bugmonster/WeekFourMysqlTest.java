package com.bugmonster;

import com.bugmonster.service.*;
import com.bugmonster.api.ApiException;
import com.bugmonster.persistence.*;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties={"spring.datasource.url=${MYSQL_TEST_URL}","spring.datasource.username=${MYSQL_TEST_USER:root}","spring.datasource.password=${MYSQL_TEST_PASSWORD:}","sanctuary.scan-min-interval-ms=0"})
@EnabledIfEnvironmentVariable(named="MYSQL_TEST_URL",matches=".+")
class WeekFourMysqlTest {
 @Autowired ProjectService projects; @Autowired ScanService scans; @Autowired AstService ast;
 @Autowired GrowthService growth; @Autowired JdbcTemplate jdbc; @Autowired MockMvc mvc;
 @Autowired ObjectMapper json; @Autowired SanctuaryMapper mapper; @Autowired PlatformTransactionManager tx;
 static final String BAD="class A {void m(){try{work();}catch(Exception e){}}}";
 static final String GOOD="class A {void m(){try{work();}catch(Exception e){handle(e);}}}";
 @BeforeEach void ownDatabaseOnly(){assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).matches("bug_monster_w4_[0-9_]+");}
 long project(){return projects.create("W4 test "+UUID.randomUUID(),BAD).id;}
 long count(String table,long id){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE project_id=?",Long.class,id);}
 int depth(AstService.TreeNode n){return n.children().stream().mapToInt(c->1+depth(c)).max().orElse(0);}
 int nodes(AstService.TreeNode n){assertThat(n.label().length()).isLessThanOrEqualTo(80);return 1+n.children().stream().mapToInt(this::nodes).sum();}
 @Test void astBudgetAndDepthAreEnforcedBeforeSerialization() throws Exception {
  var many=scans.scan(project(),"class A {void m(){"+"work();".repeat(1500)+"}}");
  var tree=ast.load(many.scanId());assertThat(tree.nodesReturned()).isEqualTo(500);assertThat(nodes(tree.root())).isEqualTo(500);assertThat(tree.truncated()).isTrue();assertThat(depth(tree.root())).isLessThanOrEqualTo(12);
  var deep=scans.scan(project(),"class A {void m(){"+"if(a){".repeat(24)+"work();"+"}".repeat(24)+"}}");
  var t=ast.load(deep.scanId());assertThat(depth(t.root())).isEqualTo(12);assertThat(t.truncated()).isTrue();
 }
 @Test void astUsesImmutableDistinctSnapshotsAndNeverChangesStateOrRewards() throws Exception {
  long id=project();var a=scans.scan(id,BAD);var b=scans.scan(id,"\n\nclass Other { int value; }");
  var stats=growth.stats();var state=jdbc.queryForObject("SELECT state_json FROM project_identity_state WHERE project_id=?",String.class,id);
  var first=ast.load(a.scanId());var second=ast.load(b.scanId());
  assertThat(first.scanId()).isNotEqualTo(second.scanId());assertThat(first.root().range().startLine()).isEqualTo(1);assertThat(second.root().children().getFirst().range().startLine()).isEqualTo(3);
  assertThat(json.writeValueAsString(first)).contains("SimpleName","SNAPSHOT_REPARSED","3.28.2");
  assertThat(second.nodesReturned()).isNotEqualTo(first.nodesReturned());assertThat(growth.stats()).isEqualTo(stats);
  assertThat(jdbc.queryForObject("SELECT state_json FROM project_identity_state WHERE project_id=?",String.class,id)).isEqualTo(state);
  assertThat(projects.require(id).lastSuccessfulScanId).isEqualTo(b.scanId());
 }
 @Test void parseErrorAstIsEmptyAndForeignOrMissingResourcesAreHidden() throws Exception {
  var s=scans.scan(project(),"class A { void broken(");var t=ast.load(s.scanId());
  assertThat(t.status()).isEqualTo("PARSE_ERROR");assertThat(t.root()).isNull();assertThat(t.nodesReturned()).isZero();assertThat(t.parseProblems()).isEqualTo(s.parseProblems());
  long id=project();long scan=scans.scan(id,BAD).scanId();jdbc.update("INSERT IGNORE INTO users(id,username,total_xp) VALUES(2,'w4-other',0)");jdbc.update("UPDATE projects SET owner_id=2 WHERE id=?",id);
  mvc.perform(get("/api/scans/"+scan+"/ast")).andExpect(status().isNotFound());mvc.perform(delete("/api/projects/"+id)).andExpect(status().isNotFound());
  mvc.perform(get("/api/scans/9223372036854775807/ast")).andExpect(status().isNotFound());
 }
 @Test void deletePurgesAllSourceAndDerivedStateButKeepsAuditAndXp() throws Exception {
  long id=project();var a=scans.scan(id,BAD);var b=scans.scan(id,GOOD);scans.scan(id,"class SecretType {void broken(");
  var stats=growth.stats();var audit=jdbc.queryForList("SELECT * FROM reward_logs WHERE project_id=?",id);var lineage=jdbc.queryForList("SELECT * FROM issue_lineages WHERE project_id=?",id);
  projects.delete(id);projects.delete(id);
  assertThat(growth.stats()).isEqualTo(stats);assertThat(jdbc.queryForList("SELECT * FROM reward_logs WHERE project_id=?",id)).isEqualTo(audit);assertThat(jdbc.queryForList("SELECT * FROM issue_lineages WHERE project_id=?",id)).isEqualTo(lineage);
  assertThat(count("scan_issues",id)).isZero();assertThat(count("project_identity_state",id)).isZero();
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM scan_runs WHERE project_id=? AND (source_code<>'' OR parse_problems<>JSON_ARRAY() OR containment_snapshot IS NOT NULL)",Long.class,id)).isZero();
  assertThat(jdbc.queryForObject("SELECT source_code FROM projects WHERE id=?",String.class,id)).isEmpty();
  assertThat(projects.list()).noneMatch(p->p.id==id);assertThatThrownBy(()->projects.update(id,"revive",BAD)).isInstanceOf(ApiException.class);assertThatThrownBy(()->scans.scan(id,BAD)).isInstanceOf(ApiException.class);
  for(String path:List.of("/api/projects/"+id+"/scans","/api/scans/"+a.scanId(),"/api/scans/"+b.scanId()+"/ast"))mvc.perform(get(path)).andExpect(status().isNotFound());
  mvc.perform(delete("/api/projects/"+id)).andExpect(status().isNoContent());mvc.perform(delete("/api/projects/9223372036854775807")).andExpect(status().isNotFound());
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reward_logs r LEFT JOIN scan_runs s ON s.id=r.source_scan_id LEFT JOIN issue_lineages l ON l.project_id=r.project_id AND l.lineage_key=r.lineage_key WHERE s.id IS NULL OR (r.lineage_key IS NOT NULL AND l.lineage_key IS NULL)",Long.class)).isZero();
 }
 @Test void failureMidDeletionRollsBackEveryChange() {
  long id=project();var s=scans.scan(id,BAD);var original=scans.detail(s.scanId());String state=jdbc.queryForObject("SELECT state_json FROM project_identity_state WHERE project_id=?",String.class,id);
  jdbc.execute("CREATE TRIGGER week4_fail_delete BEFORE UPDATE ON projects FOR EACH ROW BEGIN IF NEW.deleted_at IS NOT NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='week4 injected deletion failure after scrub'; END IF; END");
  try{assertThatThrownBy(()->projects.delete(id)).isInstanceOf(org.springframework.dao.DataAccessException.class);}finally{jdbc.execute("DROP TRIGGER week4_fail_delete");}
  assertThat(scans.detail(s.scanId())).usingRecursiveComparison().isEqualTo(original);assertThat(projects.require(id).sourceCode).isEqualTo(BAD);assertThat(jdbc.queryForObject("SELECT state_json FROM project_identity_state WHERE project_id=?",String.class,id)).isEqualTo(state);
 }
 @ParameterizedTest @ValueSource(strings={"scanFirst","saveFirst","deleteFirstScan","deleteFirstSave"})
 void deletionAndWritersSerializeOnSameProjectLock(String mode) throws Exception {
  long id=project();scans.scan(id,BAD);var locked=new CountDownLatch(1);var release=new CountDownLatch(1);
  boolean firstDelete=mode.startsWith("delete");boolean scan=mode.contains("Scan")||mode.equals("scanFirst");
  try(var pool=Executors.newFixedThreadPool(2)) {
   var first=pool.submit(()->new TransactionTemplate(tx).execute(status->{mapper.lockProject(id,1);locked.countDown();try{release.await(10,TimeUnit.SECONDS);}catch(InterruptedException e){throw new RuntimeException(e);}if(firstDelete)projects.delete(id);else if(scan)scans.scan(id,GOOD);else projects.update(id,"updated",GOOD);return true;}));
   assertThat(locked.await(10,TimeUnit.SECONDS)).isTrue();
   var second=pool.submit(()->{if(firstDelete){assertThatThrownBy(()->{if(scan)scans.scan(id,GOOD);else projects.update(id,"late",GOOD);}).isInstanceOf(ApiException.class);}else projects.delete(id);});
   release.countDown();first.get(20,TimeUnit.SECONDS);second.get(20,TimeUnit.SECONDS);
  }
  assertThat(jdbc.queryForObject("SELECT source_code FROM projects WHERE id=?",String.class,id)).isEmpty();assertThat(count("project_identity_state",id)).isZero();assertThat(count("scan_issues",id)).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM scan_runs WHERE project_id=? AND source_code<>''",Long.class,id)).isZero();
 }
 @Test void historicalScoringConfigurationRemainsStableAndUnknownIsExplicit() {
  long id=project();var first=scans.scan(id,BAD);var a=scans.history(id).getFirst();assertThat(a.scoringConfigId()).hasSize(64);
  jdbc.update("UPDATE monster_types SET deduction=16 WHERE rule_code='EMPTY_CATCH'");
  try{var second=scans.scan(id,BAD);assertThat(second.score()).isEqualTo(84);var history=scans.history(id);assertThat(history.getFirst().scoringConfigId()).isNotEqualTo(a.scoringConfigId());assertThat(history.get(1)).isEqualTo(a);}
  finally{jdbc.update("UPDATE monster_types SET deduction=15 WHERE rule_code='EMPTY_CATCH'");}
  jdbc.update("UPDATE scan_runs SET metadata_snapshot=NULL WHERE id=?",first.scanId());assertThat(scans.history(id).get(1).scoringConfigId()).isNull();
 }
 @Test void actualHealthIdentifiesBuiltPoliciesAndParser() throws Exception {
  mvc.perform(get("/api/health")).andExpect(jsonPath("$.matchingPolicyVersion").value("week3-match-v2")).andExpect(jsonPath("$.rulesetVersion").value("week2-v1")).andExpect(jsonPath("$.parserVersion").value("3.28.2")).andExpect(jsonPath("$.buildId").value(org.hamcrest.Matchers.startsWith("local-build-")));
 }
}
