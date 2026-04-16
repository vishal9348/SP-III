package com.vishal.sp_III.service;

import com.vishal.sp_III.dto.ApprovalStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.FlowElement;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.bpmn.instance.UserTask;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BpmnParserService {

    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;
    private final TaskService taskService;  // Add this!

    /**
     * Get the approval sequence (ordered list of user tasks) from BPMN
     */
    public List<ApprovalStep> getApprovalSequence(String processDefinitionId) {
        List<ApprovalStep> approvalSteps = new ArrayList<>();

        try {
            // Get BPMN model instance
            BpmnModelInstance modelInstance = repositoryService.getBpmnModelInstance(processDefinitionId);

            // Get the main process
            Process process = modelInstance.getModelElementsByType(Process.class)
                    .stream()
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No process found in BPMN"));

            // Get all user tasks
            Collection<UserTask> userTasks = process.getChildElementsByType(UserTask.class);

            // Get start event to trace the flow
            String startEventId = getStartEventId(process);

            // Traverse the sequence flow to get ordered tasks
            List<String> orderedTaskIds = traverseSequenceFlow(process, startEventId);

            // Build approval steps with order
            int order = 1;
            for (String taskId : orderedTaskIds) {
                UserTask userTask = findUserTaskById(userTasks, taskId);
                if (userTask != null) {
                    ApprovalStep step = new ApprovalStep();
                    step.setOrder(order++);
                    step.setTaskId(taskId);
                    step.setTaskName(userTask.getName());
                    step.setAssignee(getAssignee(userTask));
                    step.setCandidateGroups(getCandidateGroups(userTask));
                    approvalSteps.add(step);
                }
            }

            log.info("Found {} approval steps for process: {}", approvalSteps.size(), processDefinitionId);

        } catch (Exception e) {
            log.error("Failed to parse BPMN for process: {}", processDefinitionId, e);
        }

        return approvalSteps;
    }

    /**
     * Get current approval step for a process instance
     */
    public ApprovalStep getCurrentApprovalStep(String processInstanceId) {
        // Get current active task - FIXED: use taskService
        Task currentTask = taskService.createTaskQuery()  // Changed from runtimeService to taskService
                .processInstanceId(processInstanceId)
                .active()
                .singleResult();

        if (currentTask == null) {
            return null;
        }

        // Get process definition
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        if (processInstance == null) {
            return null;
        }

        String processDefinitionId = processInstance.getProcessDefinitionId();
        List<ApprovalStep> approvalSteps = getApprovalSequence(processDefinitionId);

        // Find current step
        return approvalSteps.stream()
                .filter(step -> step.getTaskId().equals(currentTask.getTaskDefinitionKey()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get next approval step after current task
     */
    public ApprovalStep getNextApprovalStep(String processInstanceId) {
        ApprovalStep currentStep = getCurrentApprovalStep(processInstanceId);
        if (currentStep == null) return null;

        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        if (processInstance == null) return null;

        List<ApprovalStep> allSteps = getApprovalSequence(processInstance.getProcessDefinitionId());

        int nextOrder = currentStep.getOrder() + 1;
        return allSteps.stream()
                .filter(step -> step.getOrder() == nextOrder)
                .findFirst()
                .orElse(null);
    }

    /**
     * Get total number of approval steps
     */
    public int getTotalApprovalSteps(String processDefinitionId) {
        return getApprovalSequence(processDefinitionId).size();
    }

    /**
     * Get approval progress (e.g., "2 of 4")
     */
    public String getApprovalProgress(String processInstanceId) {
        ApprovalStep currentStep = getCurrentApprovalStep(processInstanceId);
        if (currentStep == null) {
            // Check if process is completed
            ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();

            if (processInstance == null) {
                return "COMPLETED";
            }
            return "NOT_STARTED";
        }

        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        if (processInstance == null) {
            return "UNKNOWN";
        }

        int total = getTotalApprovalSteps(processInstance.getProcessDefinitionId());
        return currentStep.getOrder() + " of " + total;
    }

    /**
     * Get assignee from user task
     */
    private String getAssignee(UserTask userTask) {
        String assignee = userTask.getCamundaAssignee();
        if (assignee == null || assignee.isEmpty()) {
            // If no assignee, use task ID as role
            return userTask.getId().replace("-task", "");
        }
        return assignee;
    }

    /**
     * Get candidate groups from user task
     */
    private List<String> getCandidateGroups(UserTask userTask) {
        String candidateGroups = userTask.getCamundaCandidateGroups();
        if (candidateGroups == null || candidateGroups.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(candidateGroups.split(","));
    }

    /**
     * Get start event ID from process
     */
    private String getStartEventId(Process process) {
        return process.getChildElementsByType(org.camunda.bpm.model.bpmn.instance.StartEvent.class)
                .stream()
                .findFirst()
                .map(org.camunda.bpm.model.bpmn.instance.FlowNode::getId)
                .orElse(null);
    }

    /**
     * Traverse sequence flow to get ordered task IDs
     */
    private List<String> traverseSequenceFlow(Process process, String startElementId) {
        List<String> orderedTaskIds = new ArrayList<>();
        String currentElementId = startElementId;
        Set<String> visited = new HashSet<>();

        while (currentElementId != null && !visited.contains(currentElementId)) {
            visited.add(currentElementId);

            // Get outgoing sequence flows
            String finalCurrentElementId = currentElementId;
            Collection<SequenceFlow> outgoingFlows = process.getChildElementsByType(SequenceFlow.class)
                    .stream()
                    .filter(flow -> flow.getSource().getId().equals(finalCurrentElementId))
                    .collect(Collectors.toList());

            if (outgoingFlows.isEmpty()) {
                break;
            }

            // Take the first outgoing flow (assuming linear process)
            SequenceFlow nextFlow = outgoingFlows.iterator().next();
            String targetId = nextFlow.getTarget().getId();

            // Check if target is a user task
            FlowElement targetElement = process.getChildElementsByType(FlowElement.class)
                    .stream()
                    .filter(el -> el.getId().equals(targetId))
                    .findFirst()
                    .orElse(null);

            if (targetElement instanceof UserTask) {
                orderedTaskIds.add(targetId);
            }

            currentElementId = targetId;
        }

        return orderedTaskIds;
    }

    /**
     * Find user task by ID
     */
    private UserTask findUserTaskById(Collection<UserTask> userTasks, String taskId) {
        return userTasks.stream()
                .filter(task -> task.getId().equals(taskId))
                .findFirst()
                .orElse(null);
    }
}