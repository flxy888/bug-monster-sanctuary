package com.bugmonster.service;
import com.bugmonster.analysis.*;
import com.bugmonster.api.ApiException;
import com.bugmonster.persistence.*;
import java.util.concurrent.*;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class AiRepairServiceTest {
    ProjectService projects; DeepSeekClient client; CodeAnalyzer analyzer; AiRepairService service;
    String before,after;
    @BeforeEach void setup() throws Exception {
        projects=mock(ProjectService.class);client=mock(DeepSeekClient.class);
        when(projects.require(1001)).thenReturn(new Rows.Project());
        analyzer=TestRules.analyzer(TestRules.strategies());service=new AiRepairService(projects,analyzer,client);
        before=Files.readString(Path.of("../tests/samples/showcase/empty-catch/before/OrderCsvImporter.java"));
        after=Files.readString(Path.of("../tests/samples/showcase/empty-catch/after/OrderCsvImporter.java"));
    }
    @Test void readonlyRepairUsesSubmittedDraftAndValidatesRuleDisappearance(){
        when(client.repair(eq(before),contains("EMPTY_CATCH"))).thenReturn(new DeepSeekClient.Reply(after,"记录导入失败原因","deepseek-flash"));
        var result=service.repair(1001,before);
        assertThat(result.syntaxValid()).isTrue();assertThat(result.changed()).isTrue();
        assertThat(result.beforeIssueCount()).isEqualTo(1);assertThat(result.afterIssueCount()).isZero();
        assertThat(result.sourceCode()).isEqualTo(after);verify(projects,times(2)).require(1001);verifyNoMoreInteractions(projects);
    }
    @Test void validatesInputAndOwnershipBeforePaidCall(){
        assertThatThrownBy(()->service.repair(1001," ")).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->service.repair(1001,"x".repeat(20001))).isInstanceOf(ApiException.class);
        when(projects.require(2)).thenThrow(ApiException.missing());
        assertThatThrownBy(()->service.repair(2,before)).isInstanceOf(ApiException.class);verifyNoInteractions(client);
    }
    @Test void rejectsInvalidOrMissingCode(){
        for(String code:new String[]{"class A {", "// no source", "", "x".repeat(20001)}){
            service=new AiRepairService(projects,analyzer,client);
            when(client.repair(anyString(),anyString())).thenReturn(new DeepSeekClient.Reply(code,"修改","deepseek-flash"));
            assertThatThrownBy(()->service.repair(1001,before)).isInstanceOf(ApiException.class).hasMessageContaining("未");
        }
    }
    @Test void refusesDeletedTypesAndPublicInterfaces(){
        when(client.repair(anyString(),anyString())).thenReturn(new DeepSeekClient.Reply("class Replaced {}","修改","deepseek-flash"));
        assertThatThrownBy(()->service.repair(1001,before)).isInstanceOf(ApiException.class).hasMessageContaining("类型");
        service=new AiRepairService(projects,analyzer,client);
        when(client.repair(anyString(),anyString())).thenReturn(new DeepSeekClient.Reply("class A {}","修改","deepseek-flash"));
        assertThatThrownBy(()->service.repair(1001,"class A { public int count(){ return 1; } }")).hasMessageContaining("公共接口");
    }
    @Test void unchangedCodeIsClearlyReported(){
        when(client.repair(anyString(),anyString())).thenReturn(new DeepSeekClient.Reply(after,"完成","deepseek-flash"));
        var result=service.repair(1001,after);assertThat(result.changed()).isFalse();assertThat(result.summary()).contains("未建议修改");
    }
    @Test void blocksDuplicateConcurrencyAndRechecksDeletedProject() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(client.repair(anyString(),anyString())).thenAnswer(call->{entered.countDown();release.await(3,TimeUnit.SECONDS);return new DeepSeekClient.Reply(after,"修复","deepseek-flash");});
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
            var pending=executor.submit(()->service.repair(1001,before));assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(()->service.repair(1001,before)).hasMessageContaining("处理中");
            when(projects.require(1001)).thenThrow(ApiException.missing());release.countDown();
            assertThatThrownBy(()->pending.get(3,TimeUnit.SECONDS)).hasCauseInstanceOf(ApiException.class);
        } finally {release.countDown();}
    }
}
