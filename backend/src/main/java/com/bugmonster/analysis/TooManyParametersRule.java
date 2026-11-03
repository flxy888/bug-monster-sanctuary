package com.bugmonster.analysis;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TooManyParametersRule implements AnalysisRule {
    @Override public String code() { return "TOO_MANY_PARAMETERS"; }
    @Override public List<AnalysisResult.Finding> inspect(CompilationUnit unit) {
        return unit.findAll(MethodDeclaration.class).stream().filter(m -> m.getParameters().size() > 5)
                .map(m -> FindingFactory.create(m, code(), "方法有 " + m.getParameters().size() + " 个参数，超过 5 个")).toList();
    }
}
