package com.bugmonster.service;

import com.bugmonster.analysis.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Conservative, durable identity matching. The caller persists State atomically with settlement. */
@Component
public class IdentityMatcher {
    public static final String POLICY = "week3-match-v2";
    public static class State {
        public String policy;
        public String compatibility;
        public Long baselineScanId;
        public List<Method> methods = new ArrayList<>();
        public List<Life> lives = new ArrayList<>();
        public Set<String> ambiguousBuckets = new HashSet<>();
    }
    public static class Method {
        public String key = UUID.randomUUID().toString();
        public String type;
        public String name;
        public Set<String> signatures = new HashSet<>();
        public String signature;
        public String astHash;
        public boolean present;
        public boolean trusted;
        // Sticky quarantine: contracting a candidate graph cannot prove which branch survived.
        public boolean rewardBlocked;
        public String family() { return type + "#" + name; }
    }
    public static class Life {
        public String key = UUID.randomUUID().toString();
        public String methodKey;
        public String rule;
        public Set<String> anchors = new HashSet<>();
        public boolean active;
        public boolean eligible;
        public boolean claimable;
        public String lastMethodAst;
    }
    public record Assignment(String lineageKey, List<String> candidateLineageKeys, String status, String reason) {}
    public record Resolution(String lineageKey, String ruleCode, String resolution, int xp, String reason, boolean rewardEligible) {
        public Resolution settled(int xp, String reason) { return new Resolution(lineageKey, ruleCode, resolution, xp, reason, rewardEligible); }
    }
    public record Comparison(State state, String status, String reason, List<Assignment> assignments,
                             List<Resolution> resolutions, List<Life> discovered, int ambiguousCount) {}

    public Comparison compare(State state, String source, List<AnalysisResult.Finding> findings, String compatibility) {
        boolean first = state.baselineScanId == null;
        boolean reset = first || !POLICY.equals(state.policy) || !compatibility.equals(state.compatibility);
        String status = first ? "BASELINE_CREATED" : reset ? "INCOMPATIBLE_RULESET" : "COMPARED";
        String reason = first ? "FIRST_IDENTITY_BASELINE" : reset ? "RULESET_ENABLED_CONFIGURATION_OR_MATCHING_POLICY_CHANGED" : "COMPATIBLE_SUCCESSFUL_BASELINE";
        List<IdentitySupport.MethodSpec> specs = IdentitySupport.methods(source);
        var mapping = matchMethods(state, specs);
        var current = mapping.current;
        var specCounts = specs.stream().collect(Collectors.groupingBy(IdentitySupport.MethodSpec::descriptor, Collectors.counting()));
        var assignments = new ArrayList<Assignment>(Collections.nCopies(findings.size(), null));
        var resolutions = new ArrayList<Resolution>();
        var discovered = new ArrayList<Life>();
        var unsupportedRules = findings.stream().filter(f -> !f.supportedScope()).map(AnalysisResult.Finding::ruleCode).collect(Collectors.toSet());
        var indices = new HashMap<String, List<Integer>>();
        var uncertainObservations = new HashMap<String, Integer>();
        var uncertainLives = new HashMap<String, Integer>();
        for (int i = 0; i < findings.size(); i++) {
            var f = findings.get(i);
            var m = current.get(f.typeName() + "#" + f.methodSignature());
            if (!f.supportedScope()) {
                assignments.set(i, new Assignment(null, List.of(), "UNSUPPORTED_SCOPE", "NOT_A_DIRECT_METHOD_IN_A_NAMED_NONLOCAL_TYPE"));
            } else if (m == null || !m.trusted || m.rewardBlocked || specCounts.get(f.typeName() + "#" + f.methodSignature()) != 1) {
                String group = mapping.groupFor(f.typeName() + "#" + f.methodSignature());
                var keys = state.lives.stream().filter(l -> l.rule.equals(f.ruleCode()) && mapping.groupOf(l.methodKey).equals(group)).map(l -> l.key).sorted().toList();
                assignments.set(i, new Assignment(null, keys, "AMBIGUOUS", "METHOD_CONTINUITY_UNCERTAIN"));
                uncertainObservations.merge(group + ":" + f.ruleCode(), 1, Integer::sum);
            } else indices.computeIfAbsent(m.key + ":" + f.ruleCode(), k -> new ArrayList<>()).add(i);
        }
        var methods = state.methods.stream().collect(Collectors.toMap(m -> m.key, Function.identity()));
        for (var life : state.lives) {
            var method = methods.get(life.methodKey);
            if (!method.present && life.active) {
                resolutions.add(resolution(life, reset ? status : "REMOVED_WITH_SCOPE", reset ? reason : "METHOD_OR_TYPE_NOT_CONTINUOUS", false));
                life.active = false; life.claimable = false;
            } else if (method.present && (!method.trusted || method.rewardBlocked) && life.active) {
                resolutions.add(resolution(life, "AMBIGUOUS", "METHOD_IDENTITY_QUARANTINED", false));
                life.claimable = false;
                uncertainLives.merge(mapping.groupOf(method.key) + ":" + life.rule, 1, Integer::sum);
            }
            if (method.present && method.trusted && !method.rewardBlocked) indices.computeIfAbsent(life.methodKey + ":" + life.rule, k -> new ArrayList<>());
        }
        var uncertainBuckets = new HashSet<>(uncertainObservations.keySet()); uncertainBuckets.addAll(uncertainLives.keySet());
        int ambiguousCount = uncertainBuckets.stream().mapToInt(b -> Math.max(uncertainObservations.getOrDefault(b, 0), uncertainLives.getOrDefault(b, 0))).sum();
        for (var entry : indices.entrySet()) {
            String bucket = entry.getKey(); var obs = entry.getValue();
            var lives = state.lives.stream().filter(l -> (l.methodKey + ":" + l.rule).equals(bucket)).toList();
            if (obs.isEmpty()) {
                for (var l : lives) if (l.active) {
                    if (!reset && unsupportedRules.contains(l.rule)) {
                        resolutions.add(resolution(l, "AMBIGUOUS", "UNSUPPORTED_OBSERVATION_PREVENTS_CONFIRMATION", false));
                        l.claimable = false; ambiguousCount++; continue;
                    }
                    if (!reset && l.rule.equals("LONG_METHOD") && (l.lastMethodAst == null || Objects.equals(l.lastMethodAst, methods.get(l.methodKey).astHash))) {
                        resolutions.add(resolution(l, "AMBIGUOUS", "FORMAT_ONLY_RULE_DISAPPEARANCE", false));
                        ambiguousCount++; continue;
                    }
                    resolutions.add(resolution(l, reset ? status : "CONFIRMED_FIXED", reset ? reason : l.claimable ? "RULE_NO_LONGER_PRESENT_IN_RETAINED_METHOD" : "UNCONFIRMED_GROUP_MEMBERSHIP", !reset && l.eligible && l.claimable));
                    l.active = false; l.claimable = false;
                }
                state.ambiguousBuckets.remove(bucket); continue;
            }
            var matches = new HashMap<Integer, Life>();
            var claimed = new HashSet<String>();
            boolean ambiguous = state.ambiguousBuckets.contains(bucket);
            var anchorCounts = obs.stream().collect(Collectors.groupingBy(i -> findings.get(i).contextAnchor(), Collectors.counting()));
            if (anchorCounts.values().stream().anyMatch(c -> c > 1)) ambiguous = true;
            for (int index : obs) {
                String anchor = findings.get(index).contextAnchor();
                var candidates = lives.stream().filter(l -> l.anchors.contains(anchor)).toList();
                if (candidates.size() > 1) ambiguous = true;
                else if (candidates.size() == 1) {
                    Life life = candidates.getFirst();
                    if (!claimed.add(life.key)) ambiguous = true;
                    matches.put(index, life);
                }
            }
            var remainingObs = obs.stream().filter(i -> !matches.containsKey(i)).toList();
            var remainingLives = lives.stream().filter(l -> !claimed.contains(l.key)).toList();
            if (!remainingObs.isEmpty() && !remainingLives.isEmpty()) {
                if (remainingObs.size() == 1 && remainingLives.size() == 1 && remainingLives.getFirst().active) {
                    matches.put(remainingObs.getFirst(), remainingLives.getFirst()); claimed.add(remainingLives.getFirst().key);
                } else ambiguous = true;
            }
            if (ambiguous) {
                // Only a genuinely first observation group can establish a cardinality. Later extras are provisional.
                var group = new ArrayList<>(lives);
                if (group.isEmpty()) for (int index : obs) group.add(create(state, discovered, findings.get(index), current));
                state.ambiguousBuckets.add(bucket);
                List<String> keys = group.stream().map(l -> l.key).sorted().toList();
                for (var l : group) {
                    if (!l.active) l.claimable = false;
                    l.active = true;
                    resolutions.add(resolution(l, "AMBIGUOUS", "INDISTINGUISHABLE_GROUP_NO_NEW_REWARD_IDENTITIES", false));
                }
                for (int index : obs) assignments.set(index, new Assignment(null, keys, "AMBIGUOUS", "GROUP_FROZEN_UNTIL_ALL_OBSERVATIONS_DISAPPEAR"));
                ambiguousCount += Math.max(obs.size(), group.size()); continue;
            }
            for (int index : obs) {
                var f = findings.get(index); Life life = matches.get(index);
                boolean isNew = life == null;
                if (isNew) life = create(state, discovered, f, current);
                else resolutions.add(resolution(life, "PERSISTING", life.active ? "IDENTITY_CONTINUES" : "HISTORICAL_IDENTITY_RECURRED", false));
                assignments.set(index, new Assignment(life.key, List.of(life.key), isNew ? "NEW" : "PERSISTING", isNew ? "FIRST_CONFIRMED_LIFECYCLE" : life.active ? "IDENTITY_CONTINUES" : "HISTORICAL_IDENTITY_RECURRED"));
                life.active = true; life.claimable = true; life.anchors.add(f.contextAnchor());
                life.lastMethodAst = methods.get(life.methodKey).astHash;
            }
            for (var l : remainingLives) if (l.active && !claimed.contains(l.key)) {
                if (!reset && unsupportedRules.contains(l.rule)) {
                    resolutions.add(resolution(l, "AMBIGUOUS", "UNSUPPORTED_OBSERVATION_PREVENTS_CONFIRMATION", false));
                    l.claimable = false; ambiguousCount++; continue;
                }
                resolutions.add(resolution(l, reset ? status : "CONFIRMED_FIXED", reset ? reason : "RULE_NO_LONGER_PRESENT_IN_RETAINED_METHOD", !reset && l.eligible && l.claimable));
                l.active = false; l.claimable = false;
            }
        }
        state.policy = POLICY; state.compatibility = compatibility;
        return new Comparison(state, status, reason, assignments, resolutions, discovered, ambiguousCount);
    }
    private static Resolution resolution(Life l, String outcome, String reason, boolean rewardEligible) {
        return new Resolution(l.key, l.rule, outcome, 0, reason, rewardEligible);
    }
    private record MethodMapping(Map<String, Method> current, Map<String, List<Method>> candidates, Map<String, String> groups) {
        String groupOf(String key) { return root(groups, key); }
        String groupFor(String descriptor) {
            Method m = current.get(descriptor);
            return m != null ? groupOf(m.key) : candidates.getOrDefault(descriptor, List.of()).stream().findFirst().map(c -> groupOf(c.key)).orElse(descriptor);
        }
    }
    private static String root(Map<String, String> groups, String key) {
        while (!groups.getOrDefault(key, key).equals(key)) key = groups.get(key);
        return key;
    }
    private static void union(Map<String, String> groups, List<Method> methods) {
        if (!methods.isEmpty()) for (var m : methods) groups.put(root(groups, m.key), root(groups, methods.getFirst().key));
    }
    private static MethodMapping matchMethods(State state, List<IdentitySupport.MethodSpec> specs) {
        var history = List.copyOf(state.methods);
        var before = history.stream().filter(m -> m.present).toList();
        var counts = specs.stream().collect(Collectors.groupingBy(IdentitySupport.MethodSpec::descriptor, Collectors.counting()));
        var groups = new HashMap<String, String>(); history.forEach(m -> groups.put(m.key, m.key));
        // v1 may have persisted a winning identity and a provisional identity sharing an alias.
        // Detect that historical overlap even if only one current declaration remains at upgrade time.
        var aliases = new HashMap<String, List<Method>>();
        for (var m : history) for (var signature : m.signatures) aliases.computeIfAbsent(m.type + "#" + signature, k -> new ArrayList<>()).add(m);
        for (var shared : aliases.values()) if (shared.size() > 1) {
            union(groups, shared); shared.forEach(m -> m.rewardBlocked = true);
        }
        var forward = new HashMap<String, List<Method>>();
        var reverse = new HashMap<String, Set<String>>();
        // Complete both directions BEFORE consuming or mutating any method's present/signature fields.
        for (var spec : specs) {
            var candidates = history.stream().filter(m -> m.type.equals(spec.type()) && m.signatures.contains(spec.signature())).toList();
            if (candidates.isEmpty()) {
                var family = before.stream().filter(m -> m.family().equals(spec.family())).toList();
                if (family.size() == 1 && specs.stream().filter(s -> s.family().equals(spec.family())).count() == 1) candidates = family;
            }
            forward.put(spec.descriptor(), candidates);
            for (var m : candidates) reverse.computeIfAbsent(m.key, k -> new HashSet<>()).add(spec.descriptor());
        }
        var current = new HashMap<String, Method>();
        for (var spec : specs) {
            var candidates = forward.get(spec.descriptor());
            if (candidates.isEmpty()) continue;
            boolean unique = counts.get(spec.descriptor()) == 1 && candidates.size() == 1 && reverse.get(candidates.getFirst().key).size() == 1;
            if (unique) current.put(spec.descriptor(), candidates.getFirst());
            else { union(groups, candidates); candidates.forEach(m -> m.rewardBlocked = true); }
        }
        var blockedGroups = history.stream().filter(m -> m.rewardBlocked).map(m -> root(groups, m.key)).collect(Collectors.toSet());
        history.forEach(m -> { if (blockedGroups.contains(root(groups, m.key))) m.rewardBlocked = true; });
        boolean missingMethod = before.stream().anyMatch(m -> !reverse.containsKey(m.key));
        var uniquelyMappedKeys = current.values().stream().map(m -> m.key).collect(Collectors.toSet());
        for (var spec : specs) {
            // A contested declaration has candidates, so it must NEVER create a replacement identity.
            if (current.containsKey(spec.descriptor()) || !forward.get(spec.descriptor()).isEmpty()) continue;
            var m = new Method(); m.type = spec.type(); m.name = spec.name();
            boolean familyConfirmed = history.stream().filter(h -> h.family().equals(spec.family()))
                    .allMatch(h -> uniquelyMappedKeys.contains(h.key) && h.trusted && !h.rewardBlocked);
            m.trusted = !missingMethod && familyConfirmed && counts.get(spec.descriptor()) == 1;
            state.methods.add(m); groups.put(m.key, m.key); current.put(spec.descriptor(), m);
        }
        state.methods.forEach(m -> m.present = false);
        forward.values().forEach(cs -> cs.forEach(m -> m.present = true));
        for (var spec : specs) {
            var m = current.get(spec.descriptor());
            if (m != null) { m.present = true; m.signature = spec.signature(); m.astHash = spec.astHash(); m.signatures.add(spec.signature()); }
        }
        return new MethodMapping(current, forward, groups);
    }
    private static Life create(State state, List<Life> discovered, AnalysisResult.Finding f, Map<String, Method> methods) {
        var life = new Life(); life.methodKey = methods.get(f.typeName() + "#" + f.methodSignature()).key;
        life.rule = f.ruleCode(); life.anchors.add(f.contextAnchor()); life.active = true; life.eligible = true; life.claimable = true;
        life.lastMethodAst = methods.get(f.typeName() + "#" + f.methodSignature()).astHash;
        state.lives.add(life); discovered.add(life); return life;
    }
}
