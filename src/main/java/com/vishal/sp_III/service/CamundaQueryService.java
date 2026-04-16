package com.vishal.sp_III.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.history.HistoricTaskInstance;
import org.camunda.bpm.engine.task.Task;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CamundaQueryService {

    private final TaskService taskService;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;

    // 1. Get active tasks by assignee (ACT_RU_TASK)
    public List<Map<String, Object>> getActiveTasksByAssignee(String assignee) {
        List<Task> tasks = taskService.createTaskQuery()
                .taskAssignee(assignee)
                .active()
                .orderByTaskCreateTime()
                .desc()
                .list();

        return tasks.stream()
                .map(this::convertTaskToMap)
                .collect(Collectors.toList());
    }

    // 2. Get active tasks by process instance ID (ACT_RU_TASK)
    public List<Map<String, Object>> getActiveTasksByProcessInstance(String processInstanceId) {
        List<Task> tasks = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .active()
                .list();

        return tasks.stream()
                .map(this::convertTaskToMap)
                .collect(Collectors.toList());
    }

    // 3. Get all active tasks (ACT_RU_TASK)
    public List<Map<String, Object>> getAllActiveTasks() {
        List<Task> tasks = taskService.createTaskQuery()
                .active()
                .orderByTaskCreateTime()
                .desc()
                .list();

        return tasks.stream()
                .map(this::convertTaskToMap)
                .collect(Collectors.toList());
    }

    // 4. Get historic tasks by process instance (ACT_HI_TASKINST) - FIXED
    public List<Map<String, Object>> getHistoricTasksByProcessInstance(String processInstanceId) {
        List<HistoricTaskInstance> tasks = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByHistoricActivityInstanceStartTime()
                .desc()
                .list();

        return tasks.stream()
                .map(this::convertHistoricTaskToMap)
                .collect(Collectors.toList());
    }

    // 5. Get all historic tasks (ACT_HI_TASKINST) - FIXED
    public List<Map<String, Object>> getAllHistoricTasks() {
        List<HistoricTaskInstance> tasks = historyService.createHistoricTaskInstanceQuery()
                .orderByHistoricTaskInstanceEndTime()
                .desc()
                .list();

        return tasks.stream()
                .limit(100)
                .map(this::convertHistoricTaskToMap)
                .collect(Collectors.toList());
    }

    // 6. Get completed tasks only (that have end time) - FIXED
    public List<Map<String, Object>> getCompletedTasksByProcessInstance(String processInstanceId) {
        List<HistoricTaskInstance> tasks = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .finished()  // Correct method for completed tasks
                .orderByHistoricTaskInstanceEndTime()
                .asc()
                .list();

        return tasks.stream()
                .map(this::convertHistoricTaskToMap)
                .collect(Collectors.toList());
    }

    // 7. Get process variables by process instance ID (ACT_RU_VARIABLE)
    public Map<String, Object> getProcessVariables(String processInstanceId) {
        return runtimeService.getVariables(processInstanceId);
    }

    // 8. Get task variables by task ID (ACT_RU_VARIABLE)
    public Map<String, Object> getTaskVariables(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task != null) {
            return runtimeService.getVariables(task.getProcessInstanceId());
        }
        return Collections.emptyMap();
    }

    // 9. Get complete task details with variables and history - FIXED
    public Map<String, Object> getCompleteTaskDetails(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();

        if (task == null) {
            return Collections.singletonMap("error", "Task not found: " + taskId);
        }

        Map<String, Object> details = new HashMap<>();

        // Task details from ACT_RU_TASK
        details.put("task", convertTaskToMap(task));

        // Variables from ACT_RU_VARIABLE
        details.put("variables", runtimeService.getVariables(task.getProcessInstanceId()));

        // Process instance info
        details.put("processInstanceId", task.getProcessInstanceId());

        // Get completed/previous tasks (using finished() instead of taskCompletedBefore)
        List<HistoricTaskInstance> historicTasks = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(task.getProcessInstanceId())
                .finished()  // Correct method
                .orderByHistoricTaskInstanceEndTime()
                .asc()
                .list();

        details.put("previousTasks", historicTasks.stream()
                .map(this::convertHistoricTaskToMap)
                .collect(Collectors.toList()));

        return details;
    }

    // 10. Get all tasks (active + historic) for a process instance - FIXED
    public Map<String, Object> getAllTasksForProcess(String processInstanceId) {
        Map<String, Object> result = new HashMap<>();

        // Active tasks from ACT_RU_TASK
        List<Task> activeTasks = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .active()
                .list();
        result.put("activeTasks", activeTasks.stream()
                .map(this::convertTaskToMap)
                .collect(Collectors.toList()));

        // Historic tasks from ACT_HI_TASKINST (all tasks that have ended)
        List<HistoricTaskInstance> historicTasks = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .finished()
                .orderByHistoricTaskInstanceEndTime()
                .asc()
                .list();
        result.put("historicTasks", historicTasks.stream()
                .map(this::convertHistoricTaskToMap)
                .collect(Collectors.toList()));

        // Variables from ACT_RU_VARIABLE
        result.put("variables", runtimeService.getVariables(processInstanceId));

        // Summary
        result.put("summary", Map.of(
                "totalActiveTasks", activeTasks.size(),
                "totalHistoricTasks", historicTasks.size(),
                "processInstanceId", processInstanceId
        ));

        return result;
    }

    // 11. Get task statistics
    public Map<String, Object> getTaskStatistics() {
        Map<String, Object> stats = new HashMap<>();

        // Active tasks count by assignee
        List<Task> allActiveTasks = taskService.createTaskQuery().active().list();
        Map<String, Long> tasksByAssignee = allActiveTasks.stream()
                .filter(t -> t.getAssignee() != null)
                .collect(Collectors.groupingBy(
                        Task::getAssignee,
                        Collectors.counting()
                ));

        // Total counts
        long totalActive = allActiveTasks.size();
        long totalHistoric = historyService.createHistoricTaskInstanceQuery().finished().count();

        // Tasks created in last 24 hours
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR, -24);
        long tasksLast24Hours = historyService.createHistoricTaskInstanceQuery()
                .startedAfter(cal.getTime())
                .count();

        stats.put("totalActiveTasks", totalActive);
        stats.put("totalHistoricTasks", totalHistoric);
        stats.put("tasksCreatedLast24Hours", tasksLast24Hours);
        stats.put("tasksByAssignee", tasksByAssignee);

        // Tasks by status
        Map<String, Long> tasksByStatus = new HashMap<>();
        tasksByStatus.put("ACTIVE", totalActive);
        tasksByStatus.put("COMPLETED", totalHistoric);
        stats.put("tasksByStatus", tasksByStatus);

        return stats;
    }

    // 12. Get active tasks by process definition key
    public List<Map<String, Object>> getActiveTasksByProcessKey(String processDefinitionKey) {
        List<Task> tasks = taskService.createTaskQuery()
                .processDefinitionKey(processDefinitionKey)
                .active()
                .orderByTaskCreateTime()
                .desc()
                .list();

        return tasks.stream()
                .map(this::convertTaskToMap)
                .collect(Collectors.toList());
    }

    // Helper method to convert Task to Map
    private Map<String, Object> convertTaskToMap(Task task) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", task.getId());
        map.put("name", task.getName());
        map.put("assignee", task.getAssignee());
        map.put("owner", task.getOwner());
        map.put("createTime", task.getCreateTime());
        map.put("dueDate", task.getDueDate());
        map.put("priority", task.getPriority());
        map.put("processInstanceId", task.getProcessInstanceId());
        map.put("processDefinitionId", task.getProcessDefinitionId());
        map.put("taskDefinitionKey", task.getTaskDefinitionKey());
        map.put("description", task.getDescription());
        map.put("delegationState", task.getDelegationState());
        map.put("suspended", task.isSuspended());
        return map;
    }

    // Helper method to convert HistoricTaskInstance to Map
    private Map<String, Object> convertHistoricTaskToMap(HistoricTaskInstance task) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", task.getId());
        map.put("name", task.getName());
        map.put("assignee", task.getAssignee());
        map.put("owner", task.getOwner());
        map.put("startTime", task.getStartTime());
        map.put("endTime", task.getEndTime());
        map.put("durationInMillis", task.getDurationInMillis());
        map.put("deleteReason", task.getDeleteReason());
        map.put("processInstanceId", task.getProcessInstanceId());
        map.put("processDefinitionId", task.getProcessDefinitionId());
        map.put("taskDefinitionKey", task.getTaskDefinitionKey());
        map.put("description", task.getDescription());
        map.put("priority", task.getPriority());
        map.put("dueDate", task.getDueDate());
        return map;
    }
}