package com.bugmonster;

import com.bugmonster.service.*;
import com.bugmonster.analysis.*;
import com.bugmonster.persistence.*;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties={
        "spring.datasource.url=${MYSQL_TEST_URL}",
        "spring.datasource.username=${MYSQL_TEST_USER:root}",
        "spring.datasource.password=${MYSQL_TEST_PASSWORD:}"
})
@EnabledIfEnvironmentVariable(named="MYSQL_TEST_URL", matches=".+")
class ApiMysqlTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired ProjectService projects;
    @Autowired ScanService scans;
    @Autowired SanctuaryMapper mapper;
    private JsonNode response(ResultActions action) throws Exception {
        return json.readTree(action.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }
    private String source(String code) throws Exception { return json.writeValueAsString(Map.of("sourceCode",code)); }
    private long create() throws Exception {
        return response(mvc.perform(post("/api/projects").contentType("application/json")
                .content("{\"name\":\"API integration " + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())).path("id").asLong();
    }
    @Test void healthAndSevenTablesAndFiveMonsters() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.database").value("UP"));
        assertThat(jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE()", String.class))
                .contains("users","projects","monster_types","scan_runs","scan_issues","user_monsters","reward_logs");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM monster_types", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM monster_types WHERE enabled=TRUE", Integer.class)).isEqualTo(5);
        var catalog = response(mvc.perform(get("/api/monsters/catalog")).andExpect(status().isOk()));
        var health = response(mvc.perform(get("/api/health")).andExpect(status().isOk()));
        assertThat(catalog.path("enabledRuleCodes")).isEqualTo(health.path("enabledRuleCodes"));
        assertThat(catalog.path("definitions").size()).isEqualTo(5);
        var s = scans.scan(create(), "class A{}");
        assertThat((JsonNode)json.valueToTree(s.enabledRuleCodes())).isEqualTo(health.path("enabledRuleCodes"));
        assertThat(s.metadataSnapshot().definitions()).hasSize(5);
    }
    @Test void projectSaveSnapshotsAndParseFailureKeepSuccessfulBaseline() throws Exception {
        long id = create();
        String draft = "class Draft {}";
        mvc.perform(put("/api/projects/"+id).contentType("application/json")
                .content(json.writeValueAsString(Map.of("name","Saved project","sourceCode",draft))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sourceCode").value(draft));
        String brokenCatch = "class A { void m(){try{}catch(Exception e){;}} }";
        JsonNode first = response(mvc.perform(post("/api/projects/"+id+"/scans").contentType("application/json")
                .content(source(brokenCatch))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.score").value(85)).andExpect(jsonPath("$.baselineScanId").isEmpty()));
        long baseline = first.path("scanId").asLong();
        JsonNode failure = response(mvc.perform(post("/api/projects/"+id+"/scans").contentType("application/json")
                .content(source("class A { void broken("))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PARSE_ERROR")).andExpect(jsonPath("$.score").isEmpty())
                .andExpect(jsonPath("$.parseProblems[0].message").isNotEmpty()));
        assertThat(failure.path("baselineScanId").asLong()).isEqualTo(baseline);
        JsonNode next = response(mvc.perform(post("/api/projects/"+id+"/scans").contentType("application/json")
                .content(source("class A {}"))).andExpect(status().isCreated()).andExpect(jsonPath("$.score").value(100)));
        assertThat(next.path("baselineScanId").asLong()).isEqualTo(baseline);
        mvc.perform(get("/api/scans/"+baseline)).andExpect(jsonPath("$.sourceCode").value(brokenCatch))
                .andExpect(jsonPath("$.issues[0].monsterName").value("吞异常兽"));
        mvc.perform(get("/api/projects/"+id+"/scans")).andExpect(jsonPath("$.length()").value(3));
        assertThat(projects.require(id).sourceCode).isEqualTo(draft);
    }
    @Test void invalidInputDoesNotCreateScanAndOwnershipCannotBeSupplied() throws Exception {
        long id = create();
        for (String code : List.of(" \n\t", "x".repeat(20001))) {
            mvc.perform(post("/api/projects/"+id+"/scans").contentType("application/json").content(source(code)))
                    .andExpect(status().isBadRequest());
        }
        for (String owner : List.of("owner_id","ownerId")) {
            mvc.perform(post("/api/projects").contentType("application/json").content("{\"name\":\"spoof\",\""+owner+"\":2}"))
                    .andExpect(status().isBadRequest());
            mvc.perform(post("/api/projects/"+id+"/scans").contentType("application/json")
                    .content("{\"sourceCode\":\"class A{}\",\""+owner+"\":2}")).andExpect(status().isBadRequest());
        }
        assertThat(scans.history(id)).isEmpty();
        mvc.perform(post("/api/projects/"+id+"/scans").contentType("application/json")
                .content(source("class A{}"+" ".repeat(19991)))).andExpect(status().isCreated());
        mvc.perform(post("/api/projects/"+id+"/scans").contentType("application/json")
                .content(source("x".repeat(270000)))).andExpect(status().isPayloadTooLarge());
    }
    @Test void missingAndForeignResourcesAreHidden() throws Exception {
        jdbc.update("INSERT IGNORE INTO users(id,username,total_xp) VALUES(2,'integration-other',0)");
        long foreign = jdbc.queryForObject("SELECT COALESCE(MAX(id),0)+100 FROM projects",Long.class);
        jdbc.update("INSERT INTO projects(id,owner_id,name,source_code,created_at,updated_at) VALUES(?,2,'foreign','',NOW(6),NOW(6))",foreign);
        mvc.perform(put("/api/projects/"+foreign).contentType("application/json")
                .content("{\"name\":\"hijack\",\"sourceCode\":\"\"}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/projects/"+foreign+"/scans").contentType("application/json")
                .content(source("class A{}"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/projects/"+foreign+"/scans")).andExpect(status().isNotFound());
        mvc.perform(get("/api/scans/9223372036854775807")).andExpect(status().isNotFound());
    }
    @Test void concurrentScansFormASingleSuccessfulBaselineChain() throws Exception {
        long id = projects.create("Concurrency "+UUID.randomUUID(),"").id;
        var latch = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<ScanService.ScanResult> task = () -> { latch.await(); return scans.scan(id,"class A{}"); };
            var a = pool.submit(task); var b = pool.submit(task);
            latch.countDown();
            var results = new ArrayList<>(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS)));
            results.sort(Comparator.comparing(ScanService.ScanResult::scanId));
            assertThat(results.getFirst().baselineScanId()).isNull();
            assertThat(results.getLast().baselineScanId()).isEqualTo(results.getFirst().scanId());
        }
    }
    @Test void fiveRulesPersistImmutableMetadataAndOnlyDiscoverOnFirstScan() throws Exception {
        long id = create();
        var xp = jdbc.queryForList("SELECT * FROM users ORDER BY id");
        var rewards = jdbc.queryForObject("SELECT COUNT(*) FROM reward_logs", Long.class);
        long foundBefore = jdbc.queryForObject("SELECT COALESCE(SUM(found_count),0) FROM user_monsters",Long.class);
        var result = scans.scan(id, java.nio.file.Files.readString(java.nio.file.Path.of("../tests/samples/WeekTwoDemo.java")));
        assertThat(result.score()).isEqualTo(49);
        assertThat(result.issues()).hasSize(5).extracting(i -> i.ruleCode).containsExactlyInAnyOrderElementsOf(result.enabledRuleCodes());
        assertThat(result.rulesetVersion()).isEqualTo("week2-v1");
        var frozen = scans.detail(result.scanId());
        var counters = jdbc.queryForList("SELECT * FROM user_monsters ORDER BY owner_id,monster_type_id");
        assertThat(jdbc.queryForObject("SELECT COALESCE(SUM(found_count),0) FROM user_monsters",Long.class)).isEqualTo(foundBefore+5);
        jdbc.update("UPDATE monster_types SET monster_name='temporary-test-name',suggestion='temporary-test-advice' WHERE rule_code='EMPTY_IF'");
        try {
            assertThat(scans.detail(result.scanId())).usingRecursiveComparison().isEqualTo(frozen);
            var next = scans.scan(id, result.sourceCode());
            assertThat(next.metadataSnapshot().definition("EMPTY_IF").monsterName()).isEqualTo("temporary-test-name");
            assertThat(next.baselineScanId()).isEqualTo(result.scanId());
        } finally {
            var d = frozen.metadataSnapshot().definition("EMPTY_IF");
            jdbc.update("UPDATE monster_types SET monster_name=?,suggestion=? WHERE rule_code='EMPTY_IF'", d.monsterName(), d.suggestion());
        }
        assertThat(jdbc.queryForList("SELECT * FROM users ORDER BY id")).isEqualTo(xp);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reward_logs", Long.class)).isEqualTo(rewards);
        assertThat(jdbc.queryForList("SELECT * FROM user_monsters ORDER BY owner_id,monster_type_id")).isEqualTo(counters);
    }
    @Test void legacyScansRemainTwoRuleSnapshots() throws Exception {
        // Also exercise this contract on a newly initialized database without real week-one history.
        var fixture = new Rows.Scan();
        fixture.projectId = create(); fixture.sourceCode = "class A { void m(){try{}catch(Exception e){} if(true){} } }";
        fixture.status = "SUCCESS"; fixture.score = 85; fixture.rulesetVersion = "week1-v1";
        fixture.enabledRuleCodes = "[\"EMPTY_CATCH\",\"LONG_METHOD\"]"; fixture.parseProblems = "[]";
        fixture.createdAt = java.time.Instant.now(); mapper.insertScan(fixture);
        var old = TestRules.analyzer(List.of(new EmptyCatchRule(),new LongMethodRule())).analyze(fixture.sourceCode).issues().getFirst();
        var issue = new Rows.Issue(); issue.scanId=fixture.id; issue.projectId=fixture.projectId; issue.issueKey=UUID.randomUUID().toString();
        issue.ruleCode=old.ruleCode(); issue.monsterName=old.monsterName(); issue.startLine=old.startLine(); issue.endLine=old.endLine();
        issue.message=old.message(); issue.suggestion=old.suggestion(); issue.deduction=old.deduction(); issue.typeName=old.typeName();
        issue.methodSignature=old.methodSignature(); issue.astFingerprint=old.astFingerprint(); issue.matchStatus="UNMATCHED"; mapper.insertIssue(issue);
        mapper.successful(fixture.projectId,fixture.id);
        assertThat(scans.detail(fixture.id).score()).isEqualTo(85);
        var next = scans.scan(fixture.projectId,fixture.sourceCode);
        assertThat(next.score()).isEqualTo(77); assertThat(next.baselineScanId()).isEqualTo(fixture.id);
        var ids = jdbc.queryForList("SELECT id FROM scan_runs WHERE ruleset_version='week1-v1'", Long.class);
        for (var id : ids) {
            var scan = scans.detail(id);
            assertThat(scan.rulesetVersion()).isEqualTo("week1-v1");
            assertThat(scan.enabledRuleCodes()).containsExactlyInAnyOrder("EMPTY_CATCH","LONG_METHOD");
            assertThat(scan.metadataSnapshot()).isNull();
            if (scan.score() != null) assertThat(scan.score()).isEqualTo(Math.max(0,100-scan.issues().stream().mapToInt(i->i.deduction).sum()));
        }
    }
    @Test @org.springframework.transaction.annotation.Transactional
    void unimplementedEnabledDefinitionFailsAllActiveRuleEndpointsWithoutWritingScan() throws Exception {
        long id = create();
        jdbc.update("INSERT INTO monster_types(id,rule_code,monster_name,deduction,base_xp,enabled,description,suggestion) VALUES(9999,'NOT_IMPLEMENTED','测试',1,20,TRUE,'描述','建议')");
        for (String path : List.of("/api/health","/api/monsters/catalog"))
            mvc.perform(get(path)).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("RULE_CONFIGURATION_ERROR"));
        mvc.perform(post("/api/projects/"+id+"/scans").contentType("application/json").content(source("class A{}")))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("RULE_CONFIGURATION_ERROR"));
        assertThat(scans.history(id)).isEmpty();
    }
    @Test @org.springframework.transaction.annotation.Transactional
    void disabledDefinitionProducesTheSameEffectiveSetForHealthCatalogAndScan() throws Exception {
        jdbc.update("UPDATE monster_types SET enabled=FALSE WHERE rule_code='EMPTY_IF'");
        var health = response(mvc.perform(get("/api/health")).andExpect(status().isOk()));
        var catalog = response(mvc.perform(get("/api/monsters/catalog")).andExpect(status().isOk()));
        var result = scans.scan(create(),"class A {void m(){if(true){}}}");
        assertThat(result.score()).isEqualTo(100); assertThat(result.enabledRuleCodes()).hasSize(4).doesNotContain("EMPTY_IF");
        assertThat((JsonNode)json.valueToTree(result.enabledRuleCodes())).isEqualTo(health.path("enabledRuleCodes")).isEqualTo(catalog.path("enabledRuleCodes"));
    }
}
