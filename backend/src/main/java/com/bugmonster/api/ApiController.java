package com.bugmonster.api;

import com.bugmonster.persistence.*;
import com.bugmonster.service.*;
import com.bugmonster.analysis.RuleCatalog;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ApiController {
    public record ProjectInput(@NotBlank @Size(max=100) String name, @Size(max=20000) String sourceCode) {}
    public record ProjectUpdate(@NotBlank @Size(max=100) String name, @NotNull @Size(max=20000) String sourceCode) {}
    public record ScanInput(@NotBlank @Size(max=20000) String sourceCode) {}
    private final ProjectService projects;
    private final ScanService scans;
    private final SanctuaryMapper mapper;
    private final RuleCatalog catalog;
    private final GrowthService growth;
    private final VersionService version;
    private final AstService ast;
    public ApiController(ProjectService projects, ScanService scans, SanctuaryMapper mapper, RuleCatalog catalog, GrowthService growth, VersionService version, AstService ast) {
        this.projects = projects; this.scans = scans; this.mapper = mapper;
        this.catalog = catalog;
        this.growth = growth; this.version = version; this.ast = ast;
    }
    @GetMapping("/health")
    public Map<String, Object> health() {
        mapper.ping();
        var snapshot = catalog.snapshot();
        var response = new LinkedHashMap<String,Object>(Map.of("status", "UP", "database", "UP", "time", Instant.now(),
                "enabledRuleCodes", snapshot.enabledRuleCodes(), "rulesetVersion", snapshot.rulesetVersion()));
        response.putAll(version.info());
        return response;
    }
    @GetMapping("/monsters/catalog")
    public RuleCatalog.Snapshot catalog() { return catalog.snapshot(); }
    @GetMapping("/users/me/stats")
    public GrowthService.Stats stats() { return growth.stats(); }
    @GetMapping("/users/me/monsters")
    public List<GrowthService.Monster> monsters() { return growth.monsters(); }
    @GetMapping("/projects")
    public List<Rows.Project> projects() { return projects.list(); }
    @PostMapping("/projects")
    public ResponseEntity<Rows.Project> create(@Valid @RequestBody ProjectInput input) {
        return ResponseEntity.status(201).body(projects.create(input.name(), input.sourceCode()));
    }
    @PutMapping("/projects/{id}")
    public Rows.Project save(@PathVariable long id, @Valid @RequestBody ProjectUpdate input) {
        return projects.update(id, input.name(), input.sourceCode());
    }
    @DeleteMapping("/projects/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) { projects.delete(id); return ResponseEntity.noContent().build(); }
    @GetMapping("/scans/{id}/ast")
    public AstService.Tree ast(@PathVariable long id) throws com.fasterxml.jackson.core.JsonProcessingException { return ast.load(id); }
    @PostMapping("/projects/{id}/scans")
    public ResponseEntity<ScanService.ScanResult> scan(@PathVariable long id, @Valid @RequestBody ScanInput input) {
        return ResponseEntity.status(201).body(scans.scan(id, input.sourceCode()));
    }
    @GetMapping("/projects/{id}/scans")
    public List<ScanService.ScanSummary> history(@PathVariable long id) { return scans.history(id); }
    @GetMapping("/scans/{id}")
    public ScanService.ScanResult detail(@PathVariable long id) { return scans.detail(id); }
}
