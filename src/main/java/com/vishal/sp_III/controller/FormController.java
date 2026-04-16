package com.vishal.sp_III.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.vishal.sp_III.dto.ActionRequest;
import com.vishal.sp_III.service.FormSubmissionService;
import com.vishal.sp_III.service.GenericApprovalService;
import lombok.RequiredArgsConstructor;
import org.camunda.bpm.engine.task.Task;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/forms")
@RequiredArgsConstructor
public class FormController {

    private final FormSubmissionService formSubmissionService;
    private final GenericApprovalService genericApprovalService;

    // Original endpoint - no validation
    @PostMapping("/submit")
    public ResponseEntity<Map<String, Object>> submit(@RequestBody Map<String, Object> req) throws JsonProcessingException {
        String processInstanceId = formSubmissionService.submit(
                Long.valueOf(req.get("formTypeId").toString()),
                req.get("data").toString()
        );

        Map<String, Object> response = new HashMap<>();
        response.put("processInstanceId", processInstanceId);
        response.put("message", "Form submitted successfully");

        return ResponseEntity.ok(response);
    }

    // NEW: Submit with validation (using Map)
    @PostMapping("/submit-with-validation")
    public ResponseEntity<Map<String, Object>> submitWithValidation(@RequestBody Map<String, Object> req) throws JsonProcessingException {
        Long formTypeId = Long.valueOf(req.get("formTypeId").toString());

        // Extract data - could be Map or String
        Object dataObj = req.get("data");
        Map<String, Object> formData;

        if (dataObj instanceof Map) {
            formData = (Map<String, Object>) dataObj;
        } else if (dataObj instanceof String) {
            // If data is passed as JSON string, you might need to parse it
            throw new RuntimeException("Please pass data as JSON object, not string");
        } else {
            throw new RuntimeException("Invalid data format");
        }

        String processInstanceId = formSubmissionService.submitWithValidation(formTypeId, formData);

        Map<String, Object> response = new HashMap<>();
        response.put("processInstanceId", processInstanceId);
        response.put("message", "Form submitted and validated successfully");

        return ResponseEntity.ok(response);
    }

    // Alternative: Submit with validation (JSON string version)
//    @PostMapping("/submit-validated")
//    public ResponseEntity<Map<String, Object>> submitValidated(@RequestBody Map<String, Object> req) throws JsonProcessingException {
//        String processInstanceId = formSubmissionService.submitWithValidationJson(
//                Long.valueOf(req.get("formTypeId").toString()),
//                req.get("data").toString()
//        );
//
//        Map<String, Object> response = new HashMap<>();
//        response.put("processInstanceId", processInstanceId);
//        response.put("message", "Form submitted with validation");
//
//        return ResponseEntity.ok(response);
//    }

    // Rest of your existing endpoints...

    @GetMapping("/tasks/{assignee}")
    public ResponseEntity<Map<String, Object>> getTasks(@PathVariable String assignee) {
        List<Map<String, Object>> tasks = formSubmissionService.getTasksByAssignee(assignee);

        Map<String, Object> response = new HashMap<>();
        response.put("assignee", assignee);
        response.put("count", tasks.size());
        response.put("tasks", tasks);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/task/{taskId}")
    public ResponseEntity<Map<String, Object>> getTaskDetails(@PathVariable String taskId) {
        return ResponseEntity.ok(formSubmissionService.getFormDetails(taskId));
    }

    @PostMapping("/approve/{taskId}")
    public ResponseEntity<Map<String, String>> approve(
            @PathVariable String taskId,
            @RequestParam String approver,
            @RequestParam String comments) {

        formSubmissionService.approveTask(taskId, approver, comments, true);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Form approved by " + approver);
        response.put("taskId", taskId);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/progress/{processInstanceId}")
    public ResponseEntity<Map<String, Object>> getApprovalProgress(@PathVariable String processInstanceId) {
        Map<String, Object> progress = formSubmissionService.getApprovalProgress(processInstanceId);
        return ResponseEntity.ok(progress);
    }

    @PostMapping("/reject/{taskId}")
    public ResponseEntity<Map<String, String>> reject(
            @PathVariable String taskId,
            @RequestParam String approver,
            @RequestParam String reason) {

        formSubmissionService.approveTask(taskId, approver, reason, false);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Form rejected by " + approver);
        response.put("reason", reason);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/action/{taskId}")
    public ResponseEntity<Map<String, Object>> processAction(
            @PathVariable String taskId,
            @RequestBody ActionRequest request) {

        Map<String, Object> result = genericApprovalService.processAction(
                taskId,
                request.getAction(),
                request.getActor(),
                request.getComments(),
                request.getAdditionalData()
        );

        return ResponseEntity.ok(result);
    }

    // Get available actions for a task (so UI knows what buttons to show)
    @GetMapping("/task/{taskId}/available-actions")
    public ResponseEntity<Map<String, Object>> getAvailableActions(@PathVariable String taskId) {
        List<String> actions = genericApprovalService.getAvailableActions(taskId);

        Map<String, Object> response = new HashMap<>();
        response.put("taskId", taskId);
        response.put("availableActions", actions);

        // Also return task details
        Map<String, Object> taskDetails = formSubmissionService.getFormDetails(taskId);
        response.put("taskName", taskDetails.get("taskName"));
        response.put("currentApprover", taskDetails.get("currentApprover"));
        response.put("status", taskDetails.get("status"));

        return ResponseEntity.ok(response);
    }
}