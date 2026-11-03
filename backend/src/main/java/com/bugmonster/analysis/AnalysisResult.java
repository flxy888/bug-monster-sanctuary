package com.bugmonster.analysis;

import java.util.List;

public record AnalysisResult(String status, Integer score, List<Finding> issues,
                             List<ParseProblem> parseProblems) {
    public record ParseProblem(String message, Integer startLine, Integer startColumn,
                               Integer endLine, Integer endColumn) {}
    public record Finding(String ruleCode, String monsterName, int startLine, int endLine,
                          String message, String suggestion, int deduction,
                          String typeName, String methodSignature, String astFingerprint,
                          boolean supportedScope, String methodName, String contextAnchor) {}
}
