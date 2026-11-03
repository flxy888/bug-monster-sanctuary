package com.bugmonster.analysis;

import com.bugmonster.persistence.SanctuaryMapper;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import static org.mockito.Mockito.*;

public final class TestRules {
    private TestRules() {}
    public static List<AnalysisRule> strategies() {
        return List.of(new EmptyCatchRule(), new LongMethodRule(), new DeepNestingRule(), new TooManyParametersRule(), new EmptyIfRule());
    }
    // Test fixture reads the shipped seed, so assertions exercise the same metadata as initialization.
    public static List<RuleCatalog.Definition> definitions() {
        try {
            var matches = Pattern.compile("\\(\\d+,'([^']+)','([^']+)',(\\d+),(\\d+),TRUE,'([^']+)','([^']+)'\\)")
                    .matcher(Files.readString(Path.of("../database/02-seed.sql")));
            var rows = new ArrayList<RuleCatalog.Definition>();
            while (matches.find()) rows.add(new RuleCatalog.Definition(matches.group(1), matches.group(2), matches.group(5),
                    matches.group(6), Integer.parseInt(matches.group(3)), Integer.parseInt(matches.group(4)), true));
            if (rows.size() != 5) throw new IllegalStateException("Expected five shipped seed definitions");
            return rows;
        } catch (java.io.IOException ex) { throw new IllegalStateException(ex); }
    }
    public static CodeAnalyzer analyzer(List<AnalysisRule> rules) {
        var mapper = mock(SanctuaryMapper.class);
        var codes = rules.stream().map(AnalysisRule::code).toList();
        when(mapper.monsterDefinitions()).thenReturn(definitions().stream().map(d ->
                new RuleCatalog.Definition(d.ruleCode(), d.monsterName(), d.description(), d.suggestion(), d.deduction(), d.baseXp(), codes.contains(d.ruleCode()))).toList());
        return new CodeAnalyzer(rules, new RuleCatalog(mapper, rules));
    }
}
