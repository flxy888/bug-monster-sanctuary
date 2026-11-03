-- Incremental MySQL 8.4 upgrade. Existing rows remain unchanged; back up before applying.
USE bug_monster;
SET @exists_column = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scan_runs' AND column_name='containment_snapshot');
SET @ddl = IF(@exists_column=0, 'ALTER TABLE scan_runs ADD COLUMN containment_snapshot JSON NULL', 'SELECT 1');
PREPARE week3_stmt FROM @ddl;
EXECUTE week3_stmt;
DEALLOCATE PREPARE week3_stmt;
SET @exists_column = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scan_issues' AND column_name='lineage_key');
SET @ddl = IF(@exists_column=0, 'ALTER TABLE scan_issues ADD COLUMN lineage_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL', 'SELECT 1');
PREPARE week3_stmt FROM @ddl;
EXECUTE week3_stmt;
DEALLOCATE PREPARE week3_stmt;
SET @exists_column = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scan_issues' AND column_name='candidate_lineage_keys');
SET @ddl = IF(@exists_column=0, 'ALTER TABLE scan_issues ADD COLUMN candidate_lineage_keys JSON NULL', 'SELECT 1');
PREPARE week3_stmt FROM @ddl;
EXECUTE week3_stmt;
DEALLOCATE PREPARE week3_stmt;
SET @exists_column = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scan_issues' AND column_name='match_reason');
SET @ddl = IF(@exists_column=0, 'ALTER TABLE scan_issues ADD COLUMN match_reason VARCHAR(200) NULL', 'SELECT 1');
PREPARE week3_stmt FROM @ddl;
EXECUTE week3_stmt;
DEALLOCATE PREPARE week3_stmt;
SET @exists_column = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='reward_logs' AND column_name='lineage_key');
SET @ddl = IF(@exists_column=0, 'ALTER TABLE reward_logs ADD COLUMN lineage_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL', 'SELECT 1');
PREPARE week3_stmt FROM @ddl;
EXECUTE week3_stmt;
DEALLOCATE PREPARE week3_stmt;
SET @exists_column = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='reward_logs' AND column_name='identity_policy');
SET @ddl = IF(@exists_column=0, 'ALTER TABLE reward_logs ADD COLUMN identity_policy VARCHAR(40) NULL', 'SELECT 1');
PREPARE week3_stmt FROM @ddl;
EXECUTE week3_stmt;
DEALLOCATE PREPARE week3_stmt;

CREATE TABLE IF NOT EXISTS project_identity_state (
  project_id BIGINT PRIMARY KEY,
  state_json JSON NOT NULL,
  CONSTRAINT fk_identity_project FOREIGN KEY(project_id) REFERENCES projects(id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS issue_lineages (
  project_id BIGINT NOT NULL,
  lineage_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  method_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  rule_code VARCHAR(40) NOT NULL,
  first_scan_id BIGINT NOT NULL,
  reward_eligible BOOLEAN NOT NULL,
  PRIMARY KEY(project_id,lineage_key),
  CONSTRAINT fk_lineage_project FOREIGN KEY(project_id) REFERENCES projects(id),
  CONSTRAINT fk_lineage_rule FOREIGN KEY(rule_code) REFERENCES monster_types(rule_code),
  CONSTRAINT fk_lineage_scan FOREIGN KEY(first_scan_id,project_id) REFERENCES scan_runs(id,project_id)
) ENGINE=InnoDB;
SET @exists_key = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='reward_logs' AND index_name='uk_reward_lineage');
SET @ddl = IF(@exists_key=0, 'ALTER TABLE reward_logs ADD UNIQUE KEY uk_reward_lineage(project_id,lineage_key)', 'SELECT 1');
PREPARE week3_stmt FROM @ddl;
EXECUTE week3_stmt;
DEALLOCATE PREPARE week3_stmt;
SET @exists_fk = (SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='reward_logs' AND CONSTRAINT_NAME='fk_reward_lineage');
SET @ddl = IF(@exists_fk=0, 'ALTER TABLE reward_logs ADD CONSTRAINT fk_reward_lineage FOREIGN KEY(project_id,lineage_key) REFERENCES issue_lineages(project_id,lineage_key)', 'SELECT 1');
PREPARE week3_stmt FROM @ddl;
EXECUTE week3_stmt;
DEALLOCATE PREPARE week3_stmt;
SET @exists_fk = (SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='scan_issues' AND CONSTRAINT_NAME='fk_observation_lineage');
SET @ddl = IF(@exists_fk=0, 'ALTER TABLE scan_issues ADD CONSTRAINT fk_observation_lineage FOREIGN KEY(project_id,lineage_key) REFERENCES issue_lineages(project_id,lineage_key)', 'SELECT 1');
PREPARE week3_stmt FROM @ddl;
EXECUTE week3_stmt;
DEALLOCATE PREPARE week3_stmt;

DELIMITER $$
CREATE TRIGGER IF NOT EXISTS reward_requires_lineage BEFORE INSERT ON reward_logs FOR EACH ROW
BEGIN
  IF NEW.lineage_key IS NULL OR NEW.identity_policy IS NULL OR NEW.issue_key <> NEW.lineage_key THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='New rewards require a stable lineage and matching policy';
  END IF;
END$$
DELIMITER ;

