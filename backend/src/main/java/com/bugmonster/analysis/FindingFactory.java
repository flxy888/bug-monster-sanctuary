package com.bugmonster.analysis;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.printer.configuration.PrettyPrinterConfiguration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

final class FindingFactory {
    private FindingFactory() {}
    static AnalysisResult.Finding create(Node node, String rule, String message) {
        List<String> types = new ArrayList<>();
        String method = "<initializer-or-lambda>";
        for (Node cursor = node; cursor != null; cursor = cursor.getParentNode().orElse(null)) {
            if (cursor instanceof TypeDeclaration<?> type) types.addFirst(type.getNameAsString());
            if (method.equals("<initializer-or-lambda>") && cursor instanceof CallableDeclaration<?> callable) {
                method = callable.getSignature().asString();
            }
        }
        String pkg = node.findCompilationUnit().flatMap(c -> c.getPackageDeclaration())
                .map(p -> p.getNameAsString() + ".").orElse("");
        PrettyPrinterConfiguration format = new PrettyPrinterConfiguration().setPrintComments(false);
        String ast = node.toString(format);
        String fingerprint;
        try {
            fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(ast.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        var range = node.getRange().orElseThrow();
        var identity = IdentitySupport.method(node);
        return new AnalysisResult.Finding(rule, "", range.begin.line, range.end.line,
                message, "", 0, identity == null ? pkg + String.join(".", types) : identity.type(),
                identity == null ? method : identity.signature(), fingerprint,
                identity != null, identity == null ? null : identity.name(), IdentitySupport.anchor(node, rule));
    }
}
