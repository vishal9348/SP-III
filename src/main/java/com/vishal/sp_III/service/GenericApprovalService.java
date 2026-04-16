package com.vishal.sp_III.service;

import com.vishal.sp_III.entity.FormData;
import com.vishal.sp_III.repository.FormRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.task.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GenericApprovalService {

    private final TaskService taskService;
    private final RuntimeService runtimeService;
    private final FormRepository formRepo;

    // Define approval flow mapping (can be moved to database for more flexibility)
    private static final Map<String, Map<String, String>> FLOW_MAPPING = Map.of(
            "maker-task", Map.of(
                    "approve", "checker-task",
                    "reject", "END"
            ),
            "checker-task", Map.of(
                    "approve", "cochecker-task",
                    "reject", "END",
                    "send_back_to_maker", "maker-task"
            ),
            "cochecker-task", Map.of(
                    "approve", "admin-task",
                    "reject", "END",
                    "send_back_to_checker", "checker-task"
            ),
            "admin-task", Map.of(
                    "approve", "END",
                    "reject", "END"
            )
    );

    private static final Map<String, String> ASSIGNEE_MAPPING = Map.of(
            "maker-task", "maker",
            "checker-task", "checker",
            "cochecker-task", "cochecker",
            "admin-task", "admin"
    );

    @Transactional
    public Map<String, Object> processAction(String taskId, String action, String actor,
                                             String comments, Map<String, Object> additionalData) {

        // Get the task
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new RuntimeException("Task not found: " + taskId);
        }

        String currentTaskKey = task.getTaskDefinitionKey();
        String processInstanceId = task.getProcessInstanceId();

        log.info("Processing action '{}' on task '{}' by '{}'", action, currentTaskKey, actor);

        // Prepare variables
        Map<String, Object> variables = new HashMap<>();
        variables.put(actor + "Action", action);
        variables.put(actor + "Comments", comments);
        variables.put("lastAction", action);
        variables.put("lastActor", actor);
        variables.put("lastComments", comments);
        variables.put("lastActionTime", System.currentTimeMillis());

        if (additionalData != null) {
            variables.putAll(additionalData);
        }

        // Handle rejection
        if ("reject".equalsIgnoreCase(action)) {
            variables.put("finalStatus", "REJECTED");
            variables.put("rejectedBy", actor);
            variables.put("rejectionReason", comments);

            taskService.complete(taskId, variables);
            updateFormStatus(processInstanceId, "REJECTED", null);

            return Map.of(
                    "status", "REJECTED",
                    "message", "Application rejected by " + actor,
                    "taskId", taskId
            );
        }

        // Get next task based on action
        String nextTaskKey = FLOW_MAPPING.getOrDefault(currentTaskKey, Map.of()).get(action);

        // Handle send back actions
        if (action.startsWith("send_back_to_")) {
            String targetRole = action.replace("send_back_to_", "");
            variables.put("sendBackReason", comments);
            variables.put("sendBackBy", actor);

            taskService.complete(taskId, variables);
            updateFormStatus(processInstanceId, "SENT_BACK_TO_" + targetRole.toUpperCase(), targetRole);

            return Map.of(
                    "status", "SENT_BACK",
                    "message", "Application sent back to " + targetRole + " by " + actor,
                    "nextAssignee", targetRole,
                    "taskId", taskId
            );
        }

        // Handle approval
        if ("approve".equalsIgnoreCase(action)) {
            // Check if this is the final step
            if ("END".equals(nextTaskKey)) {
                variables.put("finalStatus", "APPROVED");
                variables.put("approvedBy", actor);
                variables.put("approvedAt", System.currentTimeMillis());

                taskService.complete(taskId, variables);
                updateFormStatus(processInstanceId, "APPROVED", null);

                return Map.of(
                        "status", "APPROVED",
                        "message", "Application approved by " + actor,
                        "final", true,
                        "taskId", taskId
                );
            }

            // Move to next task
            String nextAssignee = ASSIGNEE_MAPPING.get(nextTaskKey);
            variables.put("nextAssignee", nextAssignee);

            taskService.complete(taskId, variables);
            updateFormStatus(processInstanceId, "WITH_" + nextAssignee.toUpperCase(), nextAssignee);

            return Map.of(
                    "status", "APPROVED",
                    "message", "Application approved by " + actor,
                    "nextStep", "MOVED_TO_" + nextAssignee.toUpperCase(),
                    "nextAssignee", nextAssignee,
                    "nextTaskKey", nextTaskKey,
                    "taskId", taskId
            );
        }

        // Unknown action
        taskService.complete(taskId, variables);
        return Map.of(
                "status", "PROCESSED",
                "message", "Action '" + action + "' completed",
                "taskId", taskId
        );
    }

    public List<String> getAvailableActions(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        String taskKey = task.getTaskDefinitionKey();

        Map<String, List<String>> actionsMap = Map.of(
                "maker-task", List.of("approve", "reject"),
                "checker-task", List.of("approve", "reject", "send_back_to_maker"),
                "cochecker-task", List.of("approve", "reject", "send_back_to_checker"),
                "admin-task", List.of("approve", "reject")
        );

        return actionsMap.getOrDefault(taskKey, List.of("approve", "reject"));
    }

    private void updateFormStatus(String processInstanceId, String status, String currentApprover) {
        FormData form = formRepo.findByProcessInstanceId(processInstanceId).orElse(null);
        if (form != null) {
            form.setStatus(status);
            if (currentApprover != null) {
                form.setCurrentApprover(currentApprover);
            }
            formRepo.save(form);
            log.info("Updated form {} status to: {}", form.getId(), status);
        }
    }
}