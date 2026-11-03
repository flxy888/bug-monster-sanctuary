package com.bugmonster.service;

import com.bugmonster.analysis.*;
import com.bugmonster.api.ApiException;
import com.bugmonster.persistence.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;

@Service
public class ScanService {
    public record ScanResult(Long scanId, Long projectId, Long baselineScanId, String status, Integer score,
                             List<String> enabledRuleCodes, List<Rows.Issue> issues,
                             List<AnalysisResult.ParseProblem> parseProblems,
                             String sourceCode, String rulesetVersion, RuleCatalog.Snapshot metadataSnapshot, Instant createdAt,
                             String comparisonStatus, String comparisonReason, List<IdentityMatcher.Resolution> resolutions,
                             Integer resolvedCount, Integer containedCount, Integer removedCount, Integer ambiguousCount,
                             Integer xpEarned, GrowthService.Stats growthSnapshot) {}
    public record Settlement(String comparisonStatus, String comparisonReason, List<IdentityMatcher.Resolution> resolutions,
                             int resolvedCount, int containedCount, int removedCount, int ambiguousCount,
                             int xpEarned, GrowthService.Stats growthSnapshot) {}
    public record ScanSummary(Long scanId, Long projectId, Long baselineScanId, String status,
                              Integer score, Instant createdAt, String rulesetVersion, List<String> enabledRuleCodes, String scoringConfigId) {}
    private final SanctuaryMapper mapper;
    private final ProjectService projects;
    private final CodeAnalyzer analyzer;
    private final ObjectMapper json;
    private final RuleCatalog catalog;
    private final IdentityMapper identities;
    private final IdentityMatcher matcher;
    private final GrowthService growth;
    private final long minInterval;
    public ScanService(SanctuaryMapper mapper, ProjectService projects, CodeAnalyzer analyzer,
                       ObjectMapper json, RuleCatalog catalog, IdentityMapper identities, IdentityMatcher matcher,
                       GrowthService growth, @Value("${sanctuary.scan-min-interval-ms:0}") long minInterval) {
        this.mapper = mapper; this.projects = projects; this.analyzer = analyzer; this.json = json;
        this.minInterval = minInterval;
        this.catalog = catalog;
        this.identities = identities; this.matcher = matcher; this.growth = growth;
    }
    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }
    private <T> T decode(String value, TypeReference<T> type) {
        try { return json.readValue(value, type); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Stored scan JSON is invalid", e); }
    }
    public ScanResult detail(long id) {
        var scan = mapper.scan(id, ProjectService.DEMO_OWNER);
        if (scan == null) throw ApiException.missing();
        Settlement settlement = scan.containmentSnapshot == null ? null : decode(scan.containmentSnapshot, new TypeReference<>() {});
        return new ScanResult(scan.id, scan.projectId, scan.baselineScanId, scan.status, scan.score,
                decode(scan.enabledRuleCodes, new TypeReference<>() {}), mapper.issues(id),
                decode(scan.parseProblems, new TypeReference<>() {}), scan.sourceCode, scan.rulesetVersion,
                scan.metadataSnapshot == null ? null : decode(scan.metadataSnapshot, new TypeReference<>() {}), scan.createdAt,
                settlement == null ? "NOT_RECORDED" : settlement.comparisonStatus(),
                settlement == null ? "LEGACY_SCAN_WITHOUT_SETTLEMENT" : settlement.comparisonReason(),
                settlement == null ? null : settlement.resolutions(), settlement == null ? null : settlement.resolvedCount(),
                settlement == null ? null : settlement.containedCount(), settlement == null ? null : settlement.removedCount(),
                settlement == null ? null : settlement.ambiguousCount(), settlement == null ? null : settlement.xpEarned(),
                settlement == null ? null : settlement.growthSnapshot());
    }
    public List<ScanSummary> history(long projectId) {
        projects.require(projectId);
        return mapper.history(projectId).stream().map(s ->
                new ScanSummary(s.id, s.projectId, s.baselineScanId, s.status, s.score, s.createdAt, s.rulesetVersion, decode(s.enabledRuleCodes,new TypeReference<>() {}), scoringConfig(s))).toList();
    }
    private String scoringConfig(Rows.Scan scan) {
        if(scan.metadataSnapshot==null) return null;
        RuleCatalog.Snapshot snapshot=decode(scan.metadataSnapshot,new TypeReference<>() {});
        return IdentitySupport.hash(encode(List.of(snapshot.rulesetVersion(),snapshot.enabledRuleCodes(),
            snapshot.definitions().stream().filter(RuleCatalog.Definition::enabled).map(d->List.of(d.ruleCode(),d.deduction())).toList())));
    }
    @Transactional
    public ScanResult scan(long projectId, String sourceCode) {
        CodeAnalyzer.validateSource(sourceCode);
        var project = mapper.lockProject(projectId, ProjectService.DEMO_OWNER);
        if (project == null || project.deletedAt != null) throw ApiException.missing();
        identities.lockUser(ProjectService.DEMO_OWNER);
        Instant now = Instant.now();
        if (minInterval > 0) {
            Instant last = mapper.lastScanAt(projectId);
            if (last != null && Duration.between(last, now).toMillis() < minInterval)
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "SCAN_RATE_LIMIT", "扫描过于频繁，请稍后再试");
        }
        var snapshot = catalog.snapshot();
        var result = analyzer.analyze(sourceCode, snapshot);
        var scan = new Rows.Scan();
        scan.projectId = projectId;
        scan.baselineScanId = project.lastSuccessfulScanId;
        scan.status = result.status(); scan.score = result.score(); scan.sourceCode = sourceCode;
        scan.rulesetVersion = snapshot.rulesetVersion();
        scan.enabledRuleCodes = encode(snapshot.enabledRuleCodes());
        scan.metadataSnapshot = encode(snapshot);
        scan.parseProblems = encode(result.parseProblems()); scan.createdAt = now;
        mapper.insertScan(scan);
        IdentityMatcher.Comparison comparison = null;
        if ("SUCCESS".equals(scan.status)) {
            String stored = identities.loadState(projectId);
            IdentityMatcher.State state = stored == null ? new IdentityMatcher.State() : decode(stored, new TypeReference<>() {});
            if (state.baselineScanId != null && !Objects.equals(state.baselineScanId, project.lastSuccessfulScanId))
                state.compatibility = "SUCCESS_BASELINE_CHANGED_OUTSIDE_IDENTITY_MECHANISM";
            String compatibility = IdentitySupport.hash(encode(List.of(snapshot.rulesetVersion(), snapshot.enabledRuleCodes(),
                    snapshot.definitions().stream().filter(RuleCatalog.Definition::enabled)
                            .map(d -> List.of(d.ruleCode(), d.deduction(), d.baseXp())).toList())));
            comparison = matcher.compare(state, sourceCode, result.issues(), compatibility);
            for (var life : comparison.discovered()) {
                identities.lineage(projectId, scan.id, life);
                if (identities.found(ProjectService.DEMO_OWNER, life.rule) < 1)
                    throw new IllegalStateException("Missing monster definition");
            }
        }
        for (int i = 0; i < result.issues().size(); i++) {
            var finding = result.issues().get(i);
            var issue = new Rows.Issue();
            issue.scanId = scan.id; issue.projectId = projectId;
            issue.issueKey = UUID.randomUUID().toString();
            var assignment = comparison.assignments().get(i);
            issue.matchStatus = assignment.status(); issue.lineageKey = assignment.lineageKey();
            issue.candidateLineageKeys = encode(assignment.candidateLineageKeys()); issue.matchReason = assignment.reason();
            issue.ruleCode = finding.ruleCode(); issue.monsterName = finding.monsterName();
            issue.startLine = finding.startLine(); issue.endLine = finding.endLine();
            issue.message = finding.message(); issue.suggestion = finding.suggestion();
            issue.deduction = finding.deduction(); issue.typeName = finding.typeName();
            issue.methodSignature = finding.methodSignature(); issue.astFingerprint = finding.astFingerprint();
            mapper.insertIssue(issue);
        }
        var resolutions = new ArrayList<IdentityMatcher.Resolution>();
        int contained = 0, earned = 0;
        if (comparison != null) for (var resolution : comparison.resolutions()) {
            if (resolution.resolution().equals("CONFIRMED_FIXED")) {
                if (identities.rewarded(projectId, resolution.lineageKey()) > 0) resolution = resolution.settled(0, "ALREADY_REWARDED");
                else if (resolution.rewardEligible()) {
                    boolean inserted;
                    try { inserted = identities.reward(ProjectService.DEMO_OWNER, projectId, resolution.lineageKey(), scan.id, IdentityMatcher.POLICY) == 1; }
                    catch (DuplicateKeyException e) {
                        if (identities.rewarded(projectId, resolution.lineageKey()) == 0) throw e;
                        inserted = false;
                    }
                    if (inserted) {
                        if (identities.addXp(ProjectService.DEMO_OWNER, 20) != 1 || identities.contained(ProjectService.DEMO_OWNER, resolution.ruleCode()) != 1)
                            throw new IllegalStateException("Growth update did not affect the expected owner/type");
                        contained++; earned += 20; resolution = resolution.settled(20, "FIRST_EFFECTIVE_CONTAINMENT");
                    } else resolution = resolution.settled(0, "NO_NEW_REWARD");
                }
            }
            resolutions.add(resolution);
        }
        int fixed = (int)resolutions.stream().filter(r -> r.resolution().equals("CONFIRMED_FIXED")).count();
        int removed = (int)resolutions.stream().filter(r -> r.resolution().equals("REMOVED_WITH_SCOPE")).count();
        var settlement = new Settlement(comparison == null ? "PARSE_ERROR" : comparison.status(),
                comparison == null ? "FAILED_SCAN_DOES_NOT_ADVANCE_BASELINE" : comparison.reason(), resolutions,
                fixed, contained, removed, comparison == null ? 0 : comparison.ambiguousCount(), earned, growth.stats());
        if (identities.snapshot(scan.id, encode(settlement)) != 1) throw new IllegalStateException("Scan settlement must be written exactly once");
        if (comparison != null) {
            comparison.state().baselineScanId = scan.id;
            identities.saveState(projectId, encode(comparison.state()));
            mapper.successful(projectId, scan.id);
        }
        return detail(scan.id);
    }
}
