package com.bugmonster.service;

import com.bugmonster.analysis.CodeAnalyzer;
import com.bugmonster.api.ApiException;
import com.bugmonster.persistence.*;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {
    public static final long DEMO_OWNER = 1L;
    private final SanctuaryMapper mapper;
    public ProjectService(SanctuaryMapper mapper) { this.mapper = mapper; }
    public List<Rows.Project> list() { return mapper.projects(DEMO_OWNER); }
    public Rows.Project require(long id) {
        var project = mapper.project(id, DEMO_OWNER);
        if (project == null) throw ApiException.missing();
        return project;
    }
    @Transactional
    public void delete(long id) {
        var project=mapper.lockProject(id,DEMO_OWNER);
        if(project==null) throw ApiException.missing();
        if(project.deletedAt!=null) return;
        mapper.clearIssues(id);
        mapper.clearIdentity(id);
        mapper.clearSnapshots(id);
        if(mapper.tombstone(id)!=1) throw new IllegalStateException("Expected one project tombstone");
    }
    private void validateName(String name) {
        if (name == null || name.isBlank() || name.length() > 100)
            throw ApiException.invalid("项目名称必填且最多100字符");
    }
    @Transactional
    public Rows.Project create(String name, String source) {
        validateName(name);
        source = source == null ? "" : source;
        CodeAnalyzer.validateDraft(source);
        var project = new Rows.Project();
        project.ownerId = DEMO_OWNER;
        project.name = name.strip();
        project.sourceCode = source;
        project.createdAt = Instant.now();
        project.updatedAt = project.createdAt;
        mapper.insertProject(project);
        return require(project.id);
    }
    @Transactional
    public Rows.Project update(long id, String name, String source) {
        validateName(name);
        CodeAnalyzer.validateDraft(source);
        var project = mapper.lockProject(id, DEMO_OWNER);
        if (project == null || project.deletedAt != null) throw ApiException.missing();
        project.name = name.strip();
        project.sourceCode = source;
        project.updatedAt = Instant.now();
        mapper.updateProject(project);
        return require(id);
    }
}
