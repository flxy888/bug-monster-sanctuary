package com.bugmonster.api;
import com.bugmonster.service.AiRepairService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
class AiRepairControllerTest {
    @Test void endpointRejectsInvalidPayloadBeforeService() throws Exception {
        var service=mock(AiRepairService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new AiRepairController(service)).setControllerAdvice(new ApiErrors()).build();
        mvc.perform(post("/api/projects/1001/ai-repairs").contentType("application/json").content("{\"sourceCode\":\" \"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/projects/1001/ai-repairs").contentType("application/json").content("{\"sourceCode\":\""+"x".repeat(20001)+"\"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
