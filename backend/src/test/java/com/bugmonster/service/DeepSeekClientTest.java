package com.bugmonster.service;
import com.bugmonster.api.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class DeepSeekClientTest {
    ObjectMapper json=new ObjectMapper();
    byte[] body(String finish,String content)throws Exception{return json.writeValueAsBytes(Map.of("choices",List.of(Map.of("finish_reason",finish,"message",Map.of("content",content,"reasoning_content","not code")))));}
    DeepSeekClient client(int status,byte[] response){return new DeepSeekClient(json,"unit-token","deepseek-flash",(body,key)->new DeepSeekClient.WireReply(status,response));}
    @Test void usesCurrentModelNonThinkingJsonAndOnlyFinalContent() throws Exception {
        var response=body("stop","{\"sourceCode\":\"class A {}\",\"summary\":\"未建议修改\"}");
        var client=new DeepSeekClient(json,"unit-token","deepseek-flash",(body,key)->{
            var request=json.readTree(body);assertThat(request.path("model").asText()).isEqualTo("deepseek-flash");
            assertThat(request.path("thinking").path("type").asText()).isEqualTo("disabled");
            assertThat(request.path("stream").asBoolean()).isFalse();assertThat(request.path("max_tokens").asInt()).isBetween(1,12000);
            assertThat(request.path("response_format").path("type").asText()).isEqualTo("json_object");
            assertThat(request.path("messages").path(1).path("content").asText()).contains("class A {}");
            return new DeepSeekClient.WireReply(200,response);
        });
        assertThat(client.repair("class A {}","context").sourceCode()).isEqualTo("class A {}");
    }
    @Test void rejectsTruncationMissingOrMalformedFinalJson() throws Exception {
        for(byte[] response:List.of(body("length","{}"),body("stop",""),body("stop","```java code```"),body("stop","{}"),body("stop","{\"sourceCode\":4,\"summary\":\"x\"}")))
            assertThatThrownBy(()->client(200,response).repair("code","context")).isInstanceOf(ApiException.class);
    }
    @Test void sanitizesUpstreamErrorsAndMissingConfiguration(){
        for(int status:new int[]{401,402,429,500}) assertThatThrownBy(()->client(status,"secret upstream body".getBytes(StandardCharsets.UTF_8)).repair("code","context"))
                .isInstanceOf(ApiException.class).hasMessageNotContaining("secret");
        assertThatThrownBy(()->new DeepSeekClient(json,"","deepseek-flash",(b,k)->{throw new AssertionError();}).repair("code","context")).hasMessageContaining("未配置");
    }
}
