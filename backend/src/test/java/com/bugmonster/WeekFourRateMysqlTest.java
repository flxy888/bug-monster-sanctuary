package com.bugmonster;
import com.bugmonster.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties={"spring.datasource.url=${MYSQL_TEST_URL}","spring.datasource.username=${MYSQL_TEST_USER:root}","spring.datasource.password=${MYSQL_TEST_PASSWORD:}","sanctuary.scan-min-interval-ms=60000"})
@EnabledIfEnvironmentVariable(named="MYSQL_TEST_URL",matches=".+")
class WeekFourRateMysqlTest {
 @Autowired ProjectService projects;@Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired GrowthService growth;
 @Test void positiveIntervalRejectsWithoutScanOrRewardAndDoesNotBlockOtherProjects() throws Exception {
  assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).matches("bug_monster_w4_[0-9_]+");
  long a=projects.create("W4 rate","").id,b=projects.create("W4 other rate","").id;String body="{\"sourceCode\":\"class A {}\"}";
  mvc.perform(post("/api/projects/"+a+"/scans").contentType("application/json").content(body)).andExpect(status().isCreated());var stats=growth.stats();
  mvc.perform(post("/api/projects/"+a+"/scans").contentType("application/json").content(body)).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("SCAN_RATE_LIMIT"));
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM scan_runs WHERE project_id=?",Long.class,a)).isEqualTo(1);assertThat(growth.stats()).isEqualTo(stats);
  mvc.perform(post("/api/projects/"+b+"/scans").contentType("application/json").content(body)).andExpect(status().isCreated());
 }
}
