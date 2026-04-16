package com.vishal.sp_III.controller;

import com.vishal.sp_III.service.CamundaQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/camunda/db")
@RequiredArgsConstructor
public class CamundaQueryController {

    private final CamundaQueryService camundaQueryService;

    // 1. Get active tasks by assignee (ACT_RU_TASK)
    @GetMapping("/tasks/active/{assignee}")
    public ResponseEntity<List<Map<String, Object>>> getActiveTasksByAssignee(@PathVariable String assignee) {
        return ResponseEntity.ok(camundaQueryService.getActiveTasksByAssignee(assignee));
    }

    // 2. Get active tasks by process instance ID (ACT_RU_TASK)
    @GetMapping("/tasks/active/process/{processInstanceId}")
    public ResponseEntity<List<Map<String, Object>>> getActiveTasksByProcessInstance(@PathVariable String processInstanceId) {
        return ResponseEntity.ok(camundaQueryService.getActiveTasksByProcessInstance(processInstanceId));
    }

    // 3. Get all active tasks (ACT_RU_TASK)
    @GetMapping("/tasks/active")
    public ResponseEntity<List<Map<String, Object>>> getAllActiveTasks() {
        return ResponseEntity.ok(camundaQueryService.getAllActiveTasks());
    }

    // 4. Get completed/historic tasks by process instance (ACT_HI_TASKINST)
    @GetMapping("/tasks/historic/process/{processInstanceId}")
    public ResponseEntity<List<Map<String, Object>>> getHistoricTasksByProcessInstance(@PathVariable String processInstanceId) {
        return ResponseEntity.ok(camundaQueryService.getHistoricTasksByProcessInstance(processInstanceId));
    }

    // 5. Get all historic tasks (ACT_HI_TASKINST)
    @GetMapping("/tasks/historic")
    public ResponseEntity<List<Map<String, Object>>> getAllHistoricTasks() {
        return ResponseEntity.ok(camundaQueryService.getAllHistoricTasks());
    }

    // 6. Get process variables by process instance ID (ACT_RU_VARIABLE)
    @GetMapping("/variables/process/{processInstanceId}")
    public ResponseEntity<Map<String, Object>> getProcessVariables(@PathVariable String processInstanceId) {
        return ResponseEntity.ok(camundaQueryService.getProcessVariables(processInstanceId));
    }

    // 7. Get task variables by task ID (ACT_RU_VARIABLE)
    @GetMapping("/variables/task/{taskId}")
    public ResponseEntity<Map<String, Object>> getTaskVariables(@PathVariable String taskId) {
        return ResponseEntity.ok(camundaQueryService.getTaskVariables(taskId));
    }

    // 8. Get complete task details with variables (JOIN query)
    @GetMapping("/tasks/details/{taskId}")
    public ResponseEntity<Map<String, Object>> getCompleteTaskDetails(@PathVariable String taskId) {
        return ResponseEntity.ok(camundaQueryService.getCompleteTaskDetails(taskId));
    }

    // 9. Get all tasks (active + historic) for a process instance
    @GetMapping("/tasks/all/process/{processInstanceId}")
    public ResponseEntity<Map<String, Object>> getAllTasksForProcess(@PathVariable String processInstanceId) {
        return ResponseEntity.ok(camundaQueryService.getAllTasksForProcess(processInstanceId));
    }

    // 10. Get task summary statistics
    @GetMapping("/tasks/statistics")
    public ResponseEntity<Map<String, Object>> getTaskStatistics() {
        return ResponseEntity.ok(camundaQueryService.getTaskStatistics());
    }
}
