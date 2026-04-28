package com.vishal.sp_III.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.vishal.sp_III.dto.MultiDepartmentRequest;
import com.vishal.sp_III.service.MultiDepartmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/multi-dept")
@RequiredArgsConstructor
public class MultiDepartmentController {

    private final MultiDepartmentService multiDepartmentService;

    @PostMapping("/submit")
    public ResponseEntity<Map<String, Object>> submitMultiDepartmentForm(@RequestBody MultiDepartmentRequest request) throws JsonProcessingException {
        Map<String, Object> result = multiDepartmentService.submitMultiDepartmentForm(
                request.getMasterFormTypeId(),
                request.getMasterData(),
                request.getSubFormsData()
        );
        return ResponseEntity.ok(result);
    }

    @GetMapping("/tasks/{departmentName}/{role}")
    public ResponseEntity<List<Map<String, Object>>> getDepartmentTasks(
            @PathVariable String departmentName,
            @PathVariable String role) {
        return ResponseEntity.ok(multiDepartmentService.getDepartmentTasks(departmentName, role));
    }

    @GetMapping("/progress/{masterFormId}")
    public ResponseEntity<Map<String, Object>> getProgress(@PathVariable Long masterFormId) {
        return ResponseEntity.ok(multiDepartmentService.getMasterFormProgress(masterFormId));
    }
}
