package com.bugmonster.analysis;

import com.github.javaparser.ast.CompilationUnit;
import java.util.List;

public interface AnalysisRule {
    String code();
    List<AnalysisResult.Finding> inspect(CompilationUnit unit);
}
