package com.bugmonster.api;

import com.bugmonster.service.AiRepairService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{id}/ai-repairs")
public class AiRepairController {
    public record Input(@NotBlank @Size(max=20000) String sourceCode) {}
    private final AiRepairService repairs;
    public AiRepairController(AiRepairService repairs){this.repairs=repairs;}
    @PostMapping public AiRepairService.Preview repair(@PathVariable long id,@Valid @RequestBody Input input){
        return repairs.repair(id,input.sourceCode());
    }
}
