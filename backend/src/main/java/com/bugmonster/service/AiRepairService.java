package com.bugmonster.service;

import com.bugmonster.analysis.*;
import com.bugmonster.api.ApiException;
import com.github.javaparser.*;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AiRepairService {
    public record Preview(String sourceCode,String summary,String model,boolean changed,boolean syntaxValid,
                          int beforeIssueCount,int afterIssueCount,List<String> remainingRules) {}
    private final ProjectService projects;
    private final CodeAnalyzer analyzer;
    private final DeepSeekClient client;
    private boolean busy; private long lastStarted=Long.MIN_VALUE;
    public AiRepairService(ProjectService projects,CodeAnalyzer analyzer,DeepSeekClient client){
        this.projects=projects;this.analyzer=analyzer;this.client=client;
    }
    private synchronized void enter(){
        long now=System.nanoTime();
        if(busy) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"AI_BUSY","已有AI修复请求处理中，请等待完成");
        if(lastStarted!=Long.MIN_VALUE && now-lastStarted<2_000_000_000L)
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"AI_RATE_LIMIT","请稍候两秒再请求AI修复");
        busy=true;lastStarted=now;
    }
    private synchronized void leave(){busy=false;}
    // Deliberately no transaction/row lock across the external paid request, and no write dependencies.
    public Preview repair(long projectId,String source){
        CodeAnalyzer.validateSource(source);projects.require(projectId);enter();
        try {
            var before=analyzer.analyze(source);
            String context="规则：EMPTY_CATCH空catch；EMPTY_IF空if/多余分号；LONG_METHOD长方法；DEEP_NESTING深层嵌套；TOO_MANY_PARAMETERS参数过多。\n"
                    +"解析状态："+before.status()+"\n"
                    +before.issues().stream().limit(30).map(i->i.ruleCode()+" L"+i.startLine()+": "+i.message()+"；"+i.suggestion()).toList()
                    +before.parseProblems().stream().limit(5).map(p->" L"+p.startLine()+": "+p.message().substring(0,Math.min(300,p.message().length()))).toList();
            var reply=client.repair(source,context);
            String fixed=reply.sourceCode();
            if(fixed==null||fixed.isBlank()||fixed.length()>CodeAnalyzer.MAX_SOURCE_LENGTH)
                throw DeepSeekClient.failure("AI_INVALID_SOURCE","AI未返回长度有效的完整源码，未应用代码，可重试");
            var parsed=parse(fixed);
            if(!parsed.isSuccessful()||parsed.getResult().isEmpty()||parsed.getResult().get().getTypes().isEmpty())
                throw DeepSeekClient.failure("AI_INVALID_SOURCE","AI源码未通过Java21静态解析，未应用代码，可重试");
            var original=parse(source);
            if(original.isSuccessful()&&original.getResult().isPresent()) retainStructure(original.getResult().get(),parsed.getResult().get());
            var after=analyzer.analyze(fixed);
            if(!"SUCCESS".equals(after.status())) throw DeepSeekClient.failure("AI_INVALID_SOURCE","AI源码未通过静态解析，未应用代码，可重试");
            projects.require(projectId); // A deleted project cannot receive a late preview.
            boolean changed=!source.equals(fixed);
            return new Preview(fixed,changed?reply.summary():"未建议修改：返回代码与当前草稿相同",reply.model(),changed,true,
                    before.issues().size(),after.issues().size(),after.issues().stream().map(AnalysisResult.Finding::ruleCode).distinct().toList());
        } finally {leave();}
    }
    private static ParseResult<CompilationUnit> parse(String code){return new JavaParser(new ParserConfiguration()
            .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21)).parse(code);}
    private static String owner(com.github.javaparser.ast.Node node){
        return node.findAncestor(TypeDeclaration.class).map(t->t.getNameAsString()).orElse("");
    }
    private static void retainStructure(CompilationUnit before,CompilationUnit after){
        var types=after.findAll(TypeDeclaration.class).stream().map(TypeDeclaration::getNameAsString).toList();
        for(var type:before.findAll(TypeDeclaration.class)) if(!types.contains(type.getNameAsString()))
            throw DeepSeekClient.failure("AI_STRUCTURE_CHANGED","AI删除了原有类型，未应用代码，请重试或手动修复");
        var methods=after.findAll(MethodDeclaration.class);
        for(var old:before.findAll(MethodDeclaration.class)){
            boolean retained=methods.stream().anyMatch(m->owner(m).equals(owner(old))&&m.getNameAsString().equals(old.getNameAsString())
                    &&(!(old.isPublic()||old.isProtected()) || (m.getSignature().equals(old.getSignature())&&m.getType().equals(old.getType())&&m.getAccessSpecifier()==old.getAccessSpecifier())));
            if(!retained) throw DeepSeekClient.failure("AI_STRUCTURE_CHANGED","AI删除了方法或改变公共接口，未应用代码，请重试或手动修复");
        }
        for(var old:before.findAll(ConstructorDeclaration.class)) if((old.isPublic()||old.isProtected())&&after.findAll(ConstructorDeclaration.class).stream()
                .noneMatch(c->owner(c).equals(owner(old))&&c.getSignature().equals(old.getSignature())&&c.getAccessSpecifier()==old.getAccessSpecifier()))
            throw DeepSeekClient.failure("AI_STRUCTURE_CHANGED","AI改变了公共构造接口，未应用代码，请重试或手动修复");
    }
}
