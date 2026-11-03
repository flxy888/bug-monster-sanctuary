package com.bugmonster.analysis;

import com.github.javaparser.*;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.printer.configuration.PrettyPrinterConfiguration;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Lexical AST identity hints, deliberately independent of source ranges and observation UUIDs. */
public final class IdentitySupport {
    private IdentitySupport() {}
    public record MethodSpec(String type, String name, String signature, String astHash) {
        public String descriptor() { return type + "#" + signature; }
        public String family() { return type + "#" + name; }
    }
    public static MethodSpec method(Node node) {
        MethodDeclaration method = null;
        for (Node p = node; p != null; p = p.getParentNode().orElse(null)) {
            if (p instanceof LambdaExpr || p instanceof InitializerDeclaration || p instanceof CompactConstructorDeclaration
                    || p instanceof ConstructorDeclaration) return null;
            if (p instanceof MethodDeclaration m) { method = m; break; }
        }
        if (method == null || !(method.getParentNode().orElse(null) instanceof TypeDeclaration<?>)) return null;
        var types = new ArrayList<String>();
        Node p = method.getParentNode().orElseThrow();
        while (p instanceof TypeDeclaration<?> type) {
            types.addFirst(type.getNameAsString());
            p = p.getParentNode().orElse(null);
        }
        if (!(p instanceof CompilationUnit unit)) return null; // local/anonymous type or its nested members
        String pkg = unit.getPackageDeclaration().map(d -> d.getNameAsString() + ".").orElse("");
        // Observations inside anonymous/local bodies must not accidentally claim an enclosing method.
        for (Node q = node; q != method; q = q.getParentNode().orElse(null)) {
            if (q == null || q instanceof TypeDeclaration<?> || q instanceof ObjectCreationExpr o && o.getAnonymousClassBody().isPresent()) return null;
        }
        return new MethodSpec(pkg + String.join(".", types), method.getNameAsString(), method.getSignature().asString(), hash(ast(method)));
    }
    public static List<MethodSpec> methods(String source) {
        var parser = new JavaParser(new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));
        var unit = parser.parse(source).getResult().orElseThrow();
        return unit.findAll(MethodDeclaration.class).stream().map(IdentitySupport::method).filter(Objects::nonNull).toList();
    }
    private static String ast(Node node) {
        return node.toString(new PrettyPrinterConfiguration().setPrintComments(false).setPrintJavadoc(false));
    }
    private static String control(Node n) {
        if (n instanceof IfStmt s) return "if:" + ast(s.getCondition());
        if (n instanceof ForStmt s) return "for:" + s.getInitialization().stream().map(IdentitySupport::ast).toList() + ":" + s.getCompare().map(IdentitySupport::ast).orElse("") + ":" + s.getUpdate().stream().map(IdentitySupport::ast).toList();
        if (n instanceof ForEachStmt s) return "each:" + ast(s.getVariable()) + ":" + ast(s.getIterable());
        if (n instanceof WhileStmt s) return "while:" + ast(s.getCondition());
        if (n instanceof DoStmt s) return "do:" + ast(s.getCondition());
        if (n instanceof SwitchStmt s) return "switch:" + ast(s.getSelector());
        if (n instanceof SynchronizedStmt s) return "synchronized:" + ast(s.getExpression());
        return "";
    }
    public static String anchor(Node node, String rule) {
        if (rule.equals("LONG_METHOD") || rule.equals("TOO_MANY_PARAMETERS")) return rule;
        var pieces = new ArrayList<String>(); pieces.add(rule);
        if (node instanceof CatchClause c && c.getParentNode().orElse(null) instanceof TryStmt t) {
            pieces.add(ast(t.getTryBlock()));
            pieces.add(t.getResources().stream().map(IdentitySupport::ast).toList().toString());
            pieces.add(ast(c.getParameter().getType()));
        }
        for (Node p = node; p != null && !(p instanceof MethodDeclaration); p = p.getParentNode().orElse(null)) {
            String c = control(p); if (!c.isEmpty()) pieces.add(c);
        }
        return hash(String.join("\n", pieces));
    }
    public static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
