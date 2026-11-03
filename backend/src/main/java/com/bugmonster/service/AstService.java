package com.bugmonster.service;

import com.bugmonster.analysis.AnalysisResult;
import com.bugmonster.api.ApiException;
import com.bugmonster.persistence.SanctuaryMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.*;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.expr.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AstService {
    public static final int MAX_NODES=500, MAX_DEPTH=12, MAX_LABEL=80;
    public record SourceRange(int startLine,int startColumn,int endLine,int endColumn) {}
    public record TreeNode(String id,String type,String label,SourceRange range,List<TreeNode> children) {}
    public record Tree(long scanId,String status,String languageLevel,String parserVersion,String parserVersionSource,
                       String source,TreeNode root,int nodesReturned,boolean truncated,List<AnalysisResult.ParseProblem> parseProblems) {}
    private final SanctuaryMapper mapper;
    private final ObjectMapper json;
    private final VersionService version;
    public AstService(SanctuaryMapper mapper,ObjectMapper json,VersionService version) {
        this.mapper=mapper;this.json=json;this.version=version;
    }
    @Transactional(readOnly=true)
    public Tree load(long id) throws com.fasterxml.jackson.core.JsonProcessingException {
        var scan=mapper.scan(id,ProjectService.DEMO_OWNER);
        if(scan==null) throw ApiException.missing();
        if("PARSE_ERROR".equals(scan.status)) return tree(id,null,0,false,
            json.readValue(scan.parseProblems,new TypeReference<List<AnalysisResult.ParseProblem>>(){}));
        var parsed=new JavaParser(new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21)).parse(scan.sourceCode);
        if(!parsed.isSuccessful() || parsed.getResult().isEmpty()) {
            var problems=parsed.getProblems().stream().map(p -> {
                var r=p.getLocation().flatMap(TokenRange::toRange);
                return new AnalysisResult.ParseProblem(p.getMessage(),r.map(v->v.begin.line).orElse(null),r.map(v->v.begin.column).orElse(null),r.map(v->v.end.line).orElse(null),r.map(v->v.end.column).orElse(null));
            }).toList();
            return tree(id,null,0,false,problems);
        }
        var budget=new Budget();
        var root=budget.visit(parsed.getResult().orElseThrow(),0);
        return tree(id,root,budget.count,budget.truncated,List.of());
    }
    private Tree tree(long id,TreeNode root,int count,boolean cut,List<AnalysisResult.ParseProblem> problems) {
        return new Tree(id,root==null?"PARSE_ERROR":"SUCCESS","JAVA_21",version.parserVersion(),version.parserVersionSource(),
            "SNAPSHOT_REPARSED_WITH_CURRENT_PARSER",root,count,cut,problems);
    }
    private static class Budget {
        int count;boolean truncated;
        TreeNode visit(Node node,int depth) {
            String id="n"+(++count),type=node.getClass().getSimpleName();
            String label=type;
            if(node instanceof SimpleName n) label=n.getIdentifier();
            else if(node instanceof Name n) label=n.getIdentifier();
            else if(node instanceof LiteralStringValueExpr n) label=n.getValue();
            if(label.length()>MAX_LABEL) label=label.substring(0,MAX_LABEL-1)+"…";
            var range=node.getRange().map(r->new SourceRange(r.begin.line,r.begin.column,r.end.line,r.end.column)).orElse(null);
            var children=new ArrayList<TreeNode>();
            for(var child:node.getChildNodes()) {
                if(count>=MAX_NODES || depth>=MAX_DEPTH) { truncated=true;break; }
                children.add(visit(child,depth+1));
            }
            return new TreeNode(id,type,label,range,List.copyOf(children));
        }
    }
}
