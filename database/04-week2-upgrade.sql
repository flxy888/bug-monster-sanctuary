-- Additive, re-runnable MySQL 8.4 upgrade; never rewrite historical scan data.
USE bug_monster;
SET @has_suggestion = (SELECT COUNT(*) FROM information_schema.columns
 WHERE table_schema=DATABASE() AND table_name='monster_types' AND column_name='suggestion');
SET @ddl = IF(@has_suggestion=0,
 'ALTER TABLE monster_types ADD COLUMN suggestion VARCHAR(1000) NOT NULL DEFAULT ''''', 'SELECT 1');
PREPARE upgrade_stmt FROM @ddl;
EXECUTE upgrade_stmt;
DEALLOCATE PREPARE upgrade_stmt;
SET @has_snapshot = (SELECT COUNT(*) FROM information_schema.columns
 WHERE table_schema=DATABASE() AND table_name='scan_runs' AND column_name='metadata_snapshot');
SET @ddl = IF(@has_snapshot=0,
 'ALTER TABLE scan_runs ADD COLUMN metadata_snapshot JSON NULL', 'SELECT 1');
PREPARE upgrade_stmt FROM @ddl;
EXECUTE upgrade_stmt;
DEALLOCATE PREPARE upgrade_stmt;
