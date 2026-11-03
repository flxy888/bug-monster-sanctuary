package com.bugmonster.analysis;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class LongMethodRule implements AnalysisRule {
    @Override public String code() { return "LONG_METHOD"; }
    @Override public List<AnalysisResult.Finding> inspect(CompilationUnit unit) {
        return unit.findAll(MethodDeclaration.class).stream()
                .flatMap(m -> m.getBody().stream())
                .filter(b -> b.getEnd().orElseThrow().line - b.getBegin().orElseThrow().line + 1 > 30)
                .map(b -> FindingFactory.create(b, code(),
                        "方法体共 " + (b.getEnd().orElseThrow().line - b.getBegin().orElseThrow().line + 1) + " 行，超过 30 行")).toList();
    }
}
