-- Run as administrator, replace password before use. Dedicated local demo account.
CREATE USER IF NOT EXISTS 'bug_monster'@'127.0.0.1' IDENTIFIED BY 'replace-with-local-password';
GRANT SELECT,INSERT,UPDATE ON bug_monster.* TO 'bug_monster'@'127.0.0.1';
