package com.bugmonster.analysis;

import com.bugmonster.api.ApiException;
import com.bugmonster.persistence.SanctuaryMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RuleCatalogTest {
    private final SanctuaryMapper mapper = mock(SanctuaryMapper.class);
    private final RuleCatalog catalog = new RuleCatalog(mapper, TestRules.strategies());
    @Test void shippedDefinitionsResolveAllStrategies() {
        when(mapper.monsterDefinitions()).thenReturn(TestRules.definitions());
        assertThat(catalog.snapshot().enabledRuleCodes()).containsExactly("DEEP_NESTING","EMPTY_CATCH","EMPTY_IF","LONG_METHOD","TOO_MANY_PARAMETERS");
        assertThat(catalog.snapshot().rulesetVersion()).isEqualTo("week2-v1");
    }
    @Test void missingDefinitionAndEnabledUnimplementedStrategyAreExplicitConfigurationErrors() {
        var rows = new ArrayList<>(TestRules.definitions()); rows.removeFirst();
        when(mapper.monsterDefinitions()).thenReturn(rows);
        assertThatThrownBy(catalog::snapshot).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code).isEqualTo("RULE_CONFIGURATION_ERROR"));
        rows = new ArrayList<>(TestRules.definitions());
        rows.add(new RuleCatalog.Definition("UNKNOWN","未知","描述","建议",1,20,true));
        when(mapper.monsterDefinitions()).thenReturn(rows);
        assertThatThrownBy(catalog::snapshot).hasMessageContaining("未实现");
    }
    @Test void databaseControlsEnabledSetNamesSuggestionsAndDeductions() {
        var rows = TestRules.definitions().stream().map(d -> new RuleCatalog.Definition(d.ruleCode(), "数据库名称", d.description(),
                "数据库建议", 7, d.baseXp(), d.ruleCode().equals("EMPTY_IF"))).toList();
        when(mapper.monsterDefinitions()).thenReturn(rows);
        var frozen = catalog.snapshot();
        assertThat(frozen.enabledRuleCodes()).containsExactly("EMPTY_IF");
        var analyzer = new CodeAnalyzer(TestRules.strategies(), catalog);
        var result = analyzer.analyze("class A { void m(){if(true){} try{}catch(Exception e){}} }", frozen);
        assertThat(result.score()).isEqualTo(93);
        assertThat(result.issues()).singleElement().satisfies(i -> {
            assertThat(i.monsterName()).isEqualTo("数据库名称"); assertThat(i.suggestion()).isEqualTo("数据库建议");
        });
        when(mapper.monsterDefinitions()).thenReturn(TestRules.definitions());
        assertThat(analyzer.analyze("class A {void m(){if(true){}}}", frozen).score()).isEqualTo(93);
    }
}
