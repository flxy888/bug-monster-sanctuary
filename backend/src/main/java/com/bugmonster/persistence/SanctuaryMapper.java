package com.bugmonster.persistence;

import java.time.Instant;
import java.util.List;
import com.bugmonster.analysis.RuleCatalog;
import org.apache.ibatis.annotations.*;

@Mapper
public interface SanctuaryMapper {
    @Select("SELECT * FROM projects WHERE owner_id=#{owner} AND deleted_at IS NULL ORDER BY updated_at DESC,id DESC")
    List<Rows.Project> projects(long owner);
    @Select("SELECT * FROM projects WHERE id=#{id} AND owner_id=#{owner} AND deleted_at IS NULL")
    Rows.Project project(@Param("id") long id, @Param("owner") long owner);
    @Select("SELECT * FROM projects WHERE id=#{id} AND owner_id=#{owner} FOR UPDATE")
    Rows.Project lockProject(@Param("id") long id, @Param("owner") long owner);
    @Insert("INSERT INTO projects(owner_id,name,source_code,created_at,updated_at) VALUES(#{ownerId},#{name},#{sourceCode},#{createdAt},#{updatedAt})")
    @Options(useGeneratedKeys=true, keyProperty="id")
    int insertProject(Rows.Project project);
    @Update("UPDATE projects SET name=#{name},source_code=#{sourceCode},updated_at=#{updatedAt} WHERE id=#{id} AND owner_id=#{ownerId}")
    int updateProject(Rows.Project project);
    @Update("UPDATE projects SET last_successful_scan_id=#{scanId} WHERE id=#{projectId}")
    int successful(@Param("projectId") long projectId, @Param("scanId") long scanId);
    @Select("SELECT MAX(created_at) FROM scan_runs WHERE project_id=#{id}")
    Instant lastScanAt(long id);
    @Insert("INSERT INTO scan_runs(project_id,baseline_scan_id,status,score,source_code,ruleset_version,enabled_rule_codes,metadata_snapshot,parse_problems,created_at) VALUES(#{projectId},#{baselineScanId},#{status},#{score},#{sourceCode},#{rulesetVersion},#{enabledRuleCodes},#{metadataSnapshot},#{parseProblems},#{createdAt})")
    @Options(useGeneratedKeys=true, keyProperty="id")
    int insertScan(Rows.Scan scan);
    @Insert("INSERT INTO scan_issues(scan_id,project_id,issue_key,rule_code,monster_name,start_line,end_line,message,suggestion,deduction,type_name,method_signature,ast_fingerprint,match_status,lineage_key,candidate_lineage_keys,match_reason) VALUES(#{scanId},#{projectId},#{issueKey},#{ruleCode},#{monsterName},#{startLine},#{endLine},#{message},#{suggestion},#{deduction},#{typeName},#{methodSignature},#{astFingerprint},#{matchStatus},#{lineageKey},#{candidateLineageKeys},#{matchReason})")
    int insertIssue(Rows.Issue issue);
    @Select("SELECT s.* FROM scan_runs s JOIN projects p ON p.id=s.project_id WHERE s.id=#{id} AND p.owner_id=#{owner} AND p.deleted_at IS NULL")
    Rows.Scan scan(@Param("id") long id, @Param("owner") long owner);
    @Select("SELECT scan_runs.id,project_id,baseline_scan_id,status,score,created_at,ruleset_version,enabled_rule_codes,metadata_snapshot FROM scan_runs WHERE project_id=#{id} ORDER BY id DESC LIMIT 200")
    List<Rows.Scan> history(long id);
    @Select("SELECT * FROM scan_issues WHERE scan_id=#{id} ORDER BY start_line,rule_code,id")
    List<Rows.Issue> issues(long id);
    @Delete("DELETE FROM scan_issues WHERE project_id=#{id}")
    int clearIssues(long id);
    @Delete("DELETE FROM project_identity_state WHERE project_id=#{id}")
    int clearIdentity(long id);
    @Update("UPDATE scan_runs SET source_code='',parse_problems=JSON_ARRAY(),containment_snapshot=NULL WHERE project_id=#{id}")
    int clearSnapshots(long id);
    @Update("UPDATE projects SET name='[deleted project]',source_code='',deleted_at=CURRENT_TIMESTAMP(6),updated_at=CURRENT_TIMESTAMP(6) WHERE id=#{id}")
    int tombstone(long id);
    @Select("SELECT 1")
    int ping();
    @Select("SELECT rule_code,monster_name,description,suggestion,deduction,base_xp,enabled FROM monster_types ORDER BY rule_code")
    List<RuleCatalog.Definition> monsterDefinitions();
}
