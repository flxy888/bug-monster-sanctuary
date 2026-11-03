package com.bugmonster.analysis;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.stmt.*;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class EmptyCatchRule implements AnalysisRule {
    @Override public String code() { return "EMPTY_CATCH"; }
    @Override public List<AnalysisResult.Finding> inspect(CompilationUnit unit) {
        return unit.findAll(CatchClause.class).stream()
                .filter(c -> EmptyStatements.isEmpty(c.getBody()))
                .map(c -> FindingFactory.create(c, code(), "catch 中没有实际处理语句，异常被吞掉")).toList();
    }
}
