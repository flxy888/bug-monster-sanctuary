package com.bugmonster.service;

import com.bugmonster.analysis.RuleCatalog;
import com.bugmonster.persistence.IdentityMapper;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GrowthService {
    public record Stats(long totalXp, long level, long levelXp, int xpPerLevel, long xpToNextLevel,
                        long foundCount, long containedCount, long containedTypes, long enabledTypes, double catalogProgress) {}
    public record Monster(String ruleCode, String monsterName, String description, String suggestion, boolean enabled,
                          long foundCount, long containedCount, String state) {}
    private final IdentityMapper identities;
    private final RuleCatalog catalog;
    public GrowthService(IdentityMapper identities, RuleCatalog catalog) { this.identities = identities; this.catalog = catalog; }
    @Transactional(readOnly=true)
    public List<Monster> monsters() {
        var counts = identities.counters(ProjectService.DEMO_OWNER);
        return catalog.snapshot().definitions().stream().map(d -> {
            var count = counts.stream().filter(c -> c.ruleCode().equals(d.ruleCode())).findFirst().orElseThrow();
            return new Monster(d.ruleCode(), d.monsterName(), d.description(), d.suggestion(), d.enabled(), count.foundCount(), count.containedCount(),
                    count.containedCount() > 0 ? "CONTAINED" : count.foundCount() > 0 ? "DISCOVERED" : "UNDISCOVERED");
        }).toList();
    }
    @Transactional(readOnly=true)
    public Stats stats() {
        long xp = identities.totalXp(ProjectService.DEMO_OWNER);
        var monsters = monsters();
        long enabled = monsters.stream().filter(Monster::enabled).count();
        long contained = monsters.stream().filter(m -> m.enabled && m.containedCount > 0).count();
        return new Stats(xp, 1 + xp / 100, xp % 100, 100, 100 - xp % 100,
                monsters.stream().mapToLong(Monster::foundCount).sum(), monsters.stream().mapToLong(Monster::containedCount).sum(),
                contained, enabled, enabled == 0 ? 0 : (double)contained / enabled);
    }
}
