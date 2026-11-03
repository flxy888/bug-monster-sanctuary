package com.bugmonster.service;

import com.bugmonster.analysis.RuleCatalog;
import java.util.*;
import java.io.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class VersionService {
    private final Properties build = new Properties();
    private final long interval;
    public VersionService(@Value("${sanctuary.scan-min-interval-ms:0}") long interval) throws IOException {
        this.interval = interval;
        try (var stream = getClass().getResourceAsStream("/build-info.properties")) {
            if (stream != null) build.load(stream);
        }
    }
    public String parserVersion() { return build.getProperty("parser.version", "UNRECORDED"); }
    public String parserVersionSource() { return "packaged Maven javaparser.version (javaparser-core)"; }
    public Map<String,Object> info() {
        return Map.of("matchingPolicyVersion", IdentityMatcher.POLICY,
            "buildId", build.getProperty("build.id", "UNRECORDED"),
            "parserVersion", parserVersion(), "parserVersionSource", parserVersionSource(),
            "languageLevel", "JAVA_21", "scanMinIntervalMs", interval);
    }
}
