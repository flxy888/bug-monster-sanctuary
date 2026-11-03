package com.bugmonster.analysis;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.stmt.IfStmt;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class EmptyIfRule implements AnalysisRule {
    @Override public String code() { return "EMPTY_IF"; }
    @Override public List<AnalysisResult.Finding> inspect(CompilationUnit unit) {
        return unit.findAll(IfStmt.class).stream().filter(i -> EmptyStatements.isEmpty(i.getThenStmt()))
                .map(i -> FindingFactory.create(i.getThenStmt(), code(), "if 的 then 分支没有实际语句")).toList();
    }
}
