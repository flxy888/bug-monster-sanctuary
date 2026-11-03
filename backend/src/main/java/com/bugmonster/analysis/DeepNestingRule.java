package com.bugmonster.analysis;

import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.stmt.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class DeepNestingRule implements AnalysisRule {
    @Override public String code() { return "DEEP_NESTING"; }
    @Override public List<AnalysisResult.Finding> inspect(CompilationUnit unit) {
        var findings = new ArrayList<AnalysisResult.Finding>();
        visit(unit, 0, findings);
        return findings;
    }
    private void visit(Node node, int depth, List<AnalysisResult.Finding> findings) {
        if (node instanceof CallableDeclaration<?> || node instanceof CompactConstructorDeclaration
                || node instanceof InitializerDeclaration || node instanceof LambdaExpr || node instanceof TypeDeclaration<?>) depth = 0;
        boolean control = node instanceof IfStmt || node instanceof ForStmt || node instanceof ForEachStmt
                || node instanceof WhileStmt || node instanceof DoStmt;
        int inside = depth + (control ? 1 : 0);
        if (control && inside == 4) findings.add(FindingFactory.create(node, code(), "控制结构嵌套首次达到 4 层"));
        for (var child : node.getChildNodes()) {
            // else-if is a sibling branch, not an additional nesting level.
            boolean elseIf = node instanceof IfStmt i && child instanceof IfStmt && i.getElseStmt().orElse(null) == child;
            visit(child, elseIf ? depth : inside, findings);
        }
    }
}
