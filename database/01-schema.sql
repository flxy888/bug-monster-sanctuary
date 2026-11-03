-- MySQL 8.4; execute in an empty dedicated schema. No DROP / automatic migration.
CREATE DATABASE IF NOT EXISTS bug_monster CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE bug_monster;

CREATE TABLE IF NOT EXISTS users (
  id BIGINT PRIMARY KEY,
  username VARCHAR(100) NOT NULL UNIQUE,
  total_xp BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS projects (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_id BIGINT NOT NULL,
  name VARCHAR(100) NOT NULL,
  source_code MEDIUMTEXT NOT NULL,
  last_successful_scan_id BIGINT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  KEY ix_project_owner (owner_id, updated_at),
  CONSTRAINT fk_project_owner FOREIGN KEY(owner_id) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS monster_types (
  id BIGINT PRIMARY KEY,
  rule_code VARCHAR(40) NOT NULL UNIQUE,
  monster_name VARCHAR(100) NOT NULL,
  deduction INT NOT NULL,
  base_xp INT NOT NULL DEFAULT 20,
  enabled BOOLEAN NOT NULL DEFAULT FALSE,
  description VARCHAR(500) NOT NULL,
  suggestion VARCHAR(1000) NOT NULL DEFAULT ''
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS scan_runs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  baseline_scan_id BIGINT NULL,
  status VARCHAR(20) NOT NULL,
  score INT NULL,
  source_code MEDIUMTEXT NOT NULL,
  ruleset_version VARCHAR(40) NOT NULL,
  enabled_rule_codes JSON NOT NULL,
  metadata_snapshot JSON NULL,
  parse_problems JSON NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  UNIQUE KEY uk_scan_project (id, project_id),
  KEY ix_scan_history (project_id, id),
  CONSTRAINT fk_scan_project FOREIGN KEY(project_id) REFERENCES projects(id),
  CONSTRAINT fk_scan_baseline FOREIGN KEY(baseline_scan_id,project_id) REFERENCES scan_runs(id,project_id),
  CONSTRAINT ck_scan_status CHECK (
    (status='SUCCESS' AND score IS NOT NULL AND score BETWEEN 0 AND 100)
    OR (status='PARSE_ERROR' AND score IS NULL))
) ENGINE=InnoDB;

-- Added only once, safe to re-run schema on existing week-one database.
SET @has_baseline_fk = (SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA=DATABASE() AND CONSTRAINT_NAME='fk_project_success');
SET @baseline_ddl = IF(@has_baseline_fk=0,
  'ALTER TABLE projects ADD CONSTRAINT fk_project_success FOREIGN KEY(last_successful_scan_id,id) REFERENCES scan_runs(id,project_id)',
  'SELECT 1');
PREPARE baseline_stmt FROM @baseline_ddl;
EXECUTE baseline_stmt;
DEALLOCATE PREPARE baseline_stmt;

CREATE TABLE IF NOT EXISTS scan_issues (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  scan_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  issue_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  rule_code VARCHAR(40) NOT NULL,
  monster_name VARCHAR(100) NOT NULL,
  start_line INT NOT NULL,
  end_line INT NOT NULL,
  message VARCHAR(1000) NOT NULL,
  suggestion VARCHAR(1000) NOT NULL,
  deduction INT NOT NULL,
  type_name TEXT NOT NULL,
  method_signature TEXT NOT NULL,
  ast_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  match_status VARCHAR(30) NOT NULL DEFAULT 'UNMATCHED',
  UNIQUE KEY uk_scan_issue (scan_id,issue_key),
  KEY ix_issue_lineage (project_id,issue_key),
  CONSTRAINT fk_issue_scan FOREIGN KEY(scan_id,project_id) REFERENCES scan_runs(id,project_id),
  CONSTRAINT fk_issue_rule FOREIGN KEY(rule_code) REFERENCES monster_types(rule_code),
  CONSTRAINT ck_issue_lines CHECK(start_line > 0 AND end_line >= start_line)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS user_monsters (
  owner_id BIGINT NOT NULL,
  monster_type_id BIGINT NOT NULL,
  found_count BIGINT NOT NULL DEFAULT 0,
  contained_count BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY(owner_id,monster_type_id),
  CONSTRAINT fk_um_owner FOREIGN KEY(owner_id) REFERENCES users(id),
  CONSTRAINT fk_um_type FOREIGN KEY(monster_type_id) REFERENCES monster_types(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS reward_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  issue_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  source_scan_id BIGINT NOT NULL,
  resolved_scan_id BIGINT NOT NULL,
  monster_type_id BIGINT NOT NULL,
  xp INT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_reward_once (project_id,issue_key),
  CONSTRAINT fk_reward_owner FOREIGN KEY(owner_id) REFERENCES users(id),
  CONSTRAINT fk_reward_project FOREIGN KEY(project_id) REFERENCES projects(id),
  CONSTRAINT fk_reward_source FOREIGN KEY(source_scan_id,project_id) REFERENCES scan_runs(id,project_id),
  CONSTRAINT fk_reward_resolved FOREIGN KEY(resolved_scan_id,project_id) REFERENCES scan_runs(id,project_id),
  CONSTRAINT fk_reward_monster FOREIGN KEY(monster_type_id) REFERENCES monster_types(id)
) ENGINE=InnoDB;
