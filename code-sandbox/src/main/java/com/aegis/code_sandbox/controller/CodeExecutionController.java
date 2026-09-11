package com.aegis.code_sandbox.controller;

import com.aegis.code_sandbox.dto.ExecutionRequest;
import com.aegis.code_sandbox.service.CodeExecutionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sandbox")
public class CodeExecutionController {

    private CodeExecutionService executionService;

    CodeExecutionController(CodeExecutionService codeExecutionService){
        this.executionService = codeExecutionService;
    }

    @PostMapping("/execute")
    public ResponseEntity<String> executeCode(@RequestBody ExecutionRequest request) {
        // Pass the entire record object to the service now!
        String result = executionService.executeCode(request);
        return ResponseEntity.ok(result);
    }
}