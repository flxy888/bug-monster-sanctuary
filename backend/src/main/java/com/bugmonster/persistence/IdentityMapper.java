package com.bugmonster.persistence;

import com.bugmonster.service.IdentityMatcher;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface IdentityMapper {
    @Select("SELECT state_json FROM project_identity_state WHERE project_id=#{project}")
    String loadState(long project);
    @Insert("INSERT INTO project_identity_state(project_id,state_json) VALUES(#{project},#{state}) ON DUPLICATE KEY UPDATE state_json=VALUES(state_json)")
    int saveState(@Param("project") long project, @Param("state") String state);
    @Select("SELECT total_xp FROM users WHERE id=#{owner} FOR UPDATE")
    long lockUser(long owner);
    @Select("SELECT total_xp FROM users WHERE id=#{owner}")
    long totalXp(long owner);
    @Insert("INSERT INTO issue_lineages(project_id,lineage_key,method_key,rule_code,first_scan_id,reward_eligible) VALUES(#{project},#{life.key},#{life.methodKey},#{life.rule},#{scan},#{life.eligible})")
    int lineage(@Param("project") long project, @Param("scan") long scan, @Param("life") IdentityMatcher.Life life);
    @Insert("INSERT INTO user_monsters(owner_id,monster_type_id,found_count,contained_count) SELECT #{owner},id,1,0 FROM monster_types WHERE rule_code=#{rule} ON DUPLICATE KEY UPDATE found_count=found_count+1")
    int found(@Param("owner") long owner, @Param("rule") String rule);
    @Select("SELECT COUNT(*) FROM reward_logs WHERE project_id=#{project} AND lineage_key=#{key}")
    int rewarded(@Param("project") long project, @Param("key") String key);
    @Insert("INSERT INTO reward_logs(owner_id,project_id,issue_key,lineage_key,identity_policy,source_scan_id,resolved_scan_id,monster_type_id,xp) SELECT #{owner},l.project_id,l.lineage_key,l.lineage_key,#{policy},l.first_scan_id,#{scan},m.id,20 FROM issue_lineages l JOIN monster_types m ON m.rule_code=l.rule_code WHERE l.project_id=#{project} AND l.lineage_key=#{key} AND l.reward_eligible=TRUE")
    int reward(@Param("owner") long owner, @Param("project") long project, @Param("key") String key,
               @Param("scan") long scan, @Param("policy") String policy);
    @Update("UPDATE users SET total_xp=total_xp+#{xp} WHERE id=#{owner}")
    int addXp(@Param("owner") long owner, @Param("xp") int xp);
    @Update("UPDATE user_monsters u JOIN monster_types m ON m.id=u.monster_type_id SET u.contained_count=u.contained_count+1 WHERE u.owner_id=#{owner} AND m.rule_code=#{rule}")
    int contained(@Param("owner") long owner, @Param("rule") String rule);
    @Update("UPDATE scan_runs SET containment_snapshot=#{snapshot} WHERE id=#{scan} AND containment_snapshot IS NULL")
    int snapshot(@Param("scan") long scan, @Param("snapshot") String snapshot);
    record Counters(String ruleCode, long foundCount, long containedCount) {}
    @Select("SELECT m.rule_code,COALESCE(u.found_count,0) found_count,COALESCE(u.contained_count,0) contained_count FROM monster_types m LEFT JOIN user_monsters u ON u.monster_type_id=m.id AND u.owner_id=#{owner} ORDER BY m.rule_code")
    List<Counters> counters(long owner);
}
