-- Incremental, repeatable. No data is erased by migration; deletion is explicit API-only.
USE bug_monster;
SET @exists_column=(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='projects' AND column_name='deleted_at');
SET @ddl=IF(@exists_column=0,'ALTER TABLE projects ADD COLUMN deleted_at TIMESTAMP(6) NULL','SELECT 1');
PREPARE week4_stmt FROM @ddl;
EXECUTE week4_stmt;
DEALLOCATE PREPARE week4_stmt;
