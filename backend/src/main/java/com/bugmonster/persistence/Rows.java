package com.bugmonster.persistence;

import java.time.Instant;

public final class Rows {
    private Rows() {}
    public static class Project {
        public Long id;
        public Long ownerId;
        public String name;
        public String sourceCode;
        public Long lastSuccessfulScanId;
        public Instant deletedAt;
        public Instant createdAt;
        public Instant updatedAt;
    }
    public static class Scan {
        public Long id;
        public Long projectId;
        public Long baselineScanId;
        public String status;
        public Integer score;
        public String sourceCode;
        public String rulesetVersion;
        public String enabledRuleCodes;
        public String parseProblems;
        public String metadataSnapshot;
        public String containmentSnapshot;
        public Instant createdAt;
    }
    public static class Issue {
        public Long id;
        public Long scanId;
        public Long projectId;
        public String issueKey;
        public String ruleCode;
        public String monsterName;
        public Integer startLine;
        public Integer endLine;
        public String message;
        public String suggestion;
        public Integer deduction;
        public String typeName;
        public String methodSignature;
        public String astFingerprint;
        public String matchStatus;
        public String lineageKey;
        @com.fasterxml.jackson.annotation.JsonRawValue
        public String candidateLineageKeys;
        public String matchReason;
    }
}
