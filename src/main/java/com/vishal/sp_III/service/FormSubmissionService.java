package com.vishal.sp_III.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vishal.sp_III.dto.ApprovalStep;
import com.vishal.sp_III.entity.FormData;
import com.vishal.sp_III.entity.FormType;
import com.vishal.sp_III.entity.WorkflowMapping;
import com.vishal.sp_III.repository.FormRepository;
import com.vishal.sp_III.repository.FormTypeRepository;
import com.vishal.sp_III.repository.WorkflowMappingRepository;
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
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class FormSubmissionService {

    private final FormRepository formRepo;
    private final WorkflowMappingRepository mappingRepo;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final ObjectMapper objectMapper;
    private final BpmnParserService bpmnParserService;
    private final FormTypeRepository formTypeRepository;

    // Add these validation constants
    private static final Set<String> VALID_TYPES = Set.of("text", "textarea", "number", "email", "date", "select", "checkbox", "radio", "file");
    private static final String EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@(.+)$";


    @Transactional
    public String submit(Long formTypeId, String data) throws JsonProcessingException {
        // Save form data
        FormData form = new FormData();
        form.setFormTypeId(formTypeId);
        form.setDataJson(data);
        form.setStatus("SUBMITTED");
        formRepo.save(form);

        // Get workflow mapping
        WorkflowMapping mapping = mappingRepo.findByFormTypeIdAndActiveTrue(formTypeId);
        if (mapping == null) {
            throw new RuntimeException("No workflow mapping found for form type: " + formTypeId);
        }

        // Parse form data to extract all fields
        Map<String, Object> formDataMap = new HashMap<>();
        try {
            formDataMap = objectMapper.readValue(data, Map.class);
        } catch (Exception e) {
            log.error("Failed to parse form data", e);
            throw new RuntimeException("Invalid form data");
        }

        // Start workflow
        Map<String, Object> variables = new HashMap<>();
        variables.put("formId", form.getId());
        variables.put("formTypeId", formTypeId);
        variables.put("formData", data);
        variables.put("status", "SUBMITTED");

        // CRITICAL: Pass ALL form fields as process variables
        for (Map.Entry<String, Object> entry : formDataMap.entrySet()) {
            variables.put(entry.getKey(), entry.getValue());
            log.debug("Adding variable: {} = {}", entry.getKey(), entry.getValue());
        }

        org.camunda.bpm.engine.runtime.ProcessInstance instance =
                runtimeService.startProcessInstanceByKey(mapping.getWorkflowKey(), variables);

        // Get approval sequence from BPMN
        List<ApprovalStep> approvalSteps = bpmnParserService.getApprovalSequence(instance.getProcessDefinitionId());

        // Store approval steps as JSON
        String approvalStepsJson = objectMapper.writeValueAsString(approvalSteps);

        // Update form with process instance ID and approval sequence
        form.setProcessInstanceId(instance.getProcessInstanceId());
        form.setTotalSteps(approvalSteps.size());
        form.setCurrentStepNumber(1); // Start with step 1
        form.setApprovalSteps(approvalStepsJson);

        // Set initial status based on first approval step
        if (!approvalSteps.isEmpty()) {
            ApprovalStep firstStep = approvalSteps.get(0);
            form.setStatus("WITH_" + firstStep.getTaskId().replace("-task", "").toUpperCase());
            form.setCurrentApprover(firstStep.getAssignee());
        }

        formRepo.save(form);

        log.info("Form {} submitted with process instance: {} ({} approval steps)",
                form.getId(), instance.getProcessInstanceId(), approvalSteps.size());
        return instance.getProcessInstanceId();
    }

    @Transactional
    public void approveTask(String taskId, String approver, String comments, Boolean approved) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();

        Map<String, Object> variables = new HashMap<>();
        variables.put(approver + "Approved", approved);
        variables.put(approver + "Comments", comments);

        if (!approved) {
            variables.put("status", "REJECTED");
            variables.put("rejectionReason", comments);
            variables.put("rejectedBy", approver);
            taskService.complete(taskId, variables);

            FormData form = formRepo.findByProcessInstanceId(task.getProcessInstanceId()).orElse(null);
            if (form != null) {
                form.setStatus("REJECTED");
                form.setRejectionReason(comments);
                formRepo.save(form);
            }
            return;
        }

        // Complete current task
        taskService.complete(taskId, variables);

        // Dynamically update form status
        updateFormStatusFromBpmn(task.getProcessInstanceId());
    }

    private void updateFormStatusFromBpmn(String processInstanceId) {
        List<Task> activeTasks = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .active()
                .list();

        FormData form = formRepo.findByProcessInstanceId(processInstanceId).orElse(null);
        if (form == null) return;

        if (activeTasks.isEmpty()) {
            // All approvals completed
            form.setStatus("APPROVED");
            form.setCurrentApprover(null);
            form.setCurrentStepNumber(form.getTotalSteps());
        } else {
            // Get current approval step from BPMN
            ApprovalStep currentStep = bpmnParserService.getCurrentApprovalStep(processInstanceId);

            if (currentStep != null) {
                String status = "WITH_" + currentStep.getTaskId().replace("-task", "").toUpperCase();
                form.setStatus(status);
                form.setCurrentApprover(currentStep.getAssignee());
                form.setCurrentStepNumber(currentStep.getOrder());

                log.info("Form {} moved to step {}: {} (assigned to {})",
                        form.getId(), currentStep.getOrder(), currentStep.getTaskName(), currentStep.getAssignee());
            }
        }

        formRepo.save(form);
    }

    @Transactional
    public Map<String, Object> getApprovalProgress(String processInstanceId) {
        Map<String, Object> progress = new HashMap<>();

        FormData form = formRepo.findByProcessInstanceId(processInstanceId).orElse(null);
        if (form == null) return progress;

        progress.put("currentStep", form.getCurrentStepNumber());
        progress.put("totalSteps", form.getTotalSteps());
        progress.put("progress", bpmnParserService.getApprovalProgress(processInstanceId));
        progress.put("status", form.getStatus());

        // Parse and return approval steps with status
        try {
            List<ApprovalStep> steps = objectMapper.readValue(
                    form.getApprovalSteps(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, ApprovalStep.class)
            );
            progress.put("approvalSteps", steps);
        } catch (Exception e) {
            log.error("Failed to parse approval steps", e);
        }

        return progress;
    }

    // NEW: Submit with validation (accepts Map instead of JSON string)
    @Transactional
    public String submitWithValidation(Long formTypeId, Map<String, Object> formDataMap) throws JsonProcessingException {
        // Get form type with schema
        FormType formType = formTypeRepository.findById(formTypeId)
                .orElseThrow(() -> new RuntimeException("Form type not found: " + formTypeId));

        // Validate against schema
        validateFormDataAgainstSchema(formDataMap, formType.getSchemaJson());

        // Convert to JSON string
        String dataJson;
        try {
            dataJson = objectMapper.writeValueAsString(formDataMap);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert form data to JSON: " + e.getMessage());
        }

        // Submit using existing method
        return submit(formTypeId, dataJson);
    }

    private void validateFormDataAgainstSchema(Map<String, Object> formData, String schemaJson) {
        try {
            if (schemaJson == null || schemaJson.isEmpty()) {
                log.warn("No schema defined for form type, skipping validation");
                return;
            }

            JsonNode schema = objectMapper.readTree(schemaJson);

            // Check if schema has fields array
            JsonNode fields = schema.get("fields");
            if (fields == null || !fields.isArray()) {
                log.warn("Schema missing 'fields' array, skipping validation");
                return;
            }

            // Track validated fields
            Map<String, Boolean> validatedFields = new HashMap<>();

            // Validate each field
            for (JsonNode field : fields) {
                String fieldName = getFieldName(field);
                boolean required = isFieldRequired(field);
                String fieldType = getFieldType(field);
                String fieldLabel = getFieldLabel(field, fieldName);

                validatedFields.put(fieldName, true);

                // Check required fields
                if (required && !formData.containsKey(fieldName)) {
                    throw new RuntimeException("Required field missing: '" + fieldLabel + "' (" + fieldName + ")");
                }

                // Check if field is present in form data
                if (formData.containsKey(fieldName)) {
                    Object value = formData.get(fieldName);

                    // Check for empty values in required fields
                    if (required && (value == null || value.toString().trim().isEmpty())) {
                        throw new RuntimeException("Required field '" + fieldLabel + "' cannot be empty");
                    }

                    // Validate field type
                    if (value != null && !value.toString().isEmpty()) {
                        validateFieldType(fieldName, fieldLabel, value, fieldType);
                    }
                }
            }

            // Check for extra fields not in schema (optional - can be disabled)
            for (String fieldName : formData.keySet()) {
                if (!validatedFields.containsKey(fieldName)) {
                    log.warn("Extra field '{}' found in form data but not defined in schema", fieldName);
                }
            }

            log.info("Schema validation passed for {} fields", validatedFields.size());

        } catch (Exception e) {
            log.error("Schema validation failed: {}", e.getMessage());
            throw new RuntimeException("Schema validation failed: " + e.getMessage());
        }
    }

    private String getFieldName(JsonNode field) {
        JsonNode nameNode = field.get("name");
        if (nameNode == null) {
            throw new RuntimeException("Field missing 'name' property");
        }
        return nameNode.asText();
    }

    private boolean isFieldRequired(JsonNode field) {
        JsonNode requiredNode = field.get("required");
        return requiredNode != null && requiredNode.asBoolean();
    }

    private String getFieldType(JsonNode field) {
        JsonNode typeNode = field.get("type");
        if (typeNode == null) {
            throw new RuntimeException("Field '" + getFieldName(field) + "' missing 'type' property");
        }
        String type = typeNode.asText();
        if (!VALID_TYPES.contains(type)) {
            log.warn("Unknown field type '{}' for field '{}', using default validation", type, getFieldName(field));
        }
        return type;
    }

    private String getFieldLabel(JsonNode field, String defaultName) {
        JsonNode labelNode = field.get("label");
        return labelNode != null ? labelNode.asText() : defaultName;
    }

    private void validateFieldType(String fieldName, String fieldLabel, Object value, String expectedType) {
        switch (expectedType) {
            case "number":
                if (!(value instanceof Number)) {
                    try {
                        // Try to convert string to number
                        Double.parseDouble(value.toString());
                    } catch (NumberFormatException e) {
                        throw new RuntimeException("Field '" + fieldLabel + "' should be a number, got: " + value);
                    }
                }
                break;

            case "email":
                if (!value.toString().matches(EMAIL_REGEX)) {
                    throw new RuntimeException("Field '" + fieldLabel + "' should be a valid email address, got: " + value);
                }
                break;

            case "date":
                // Simple date validation (YYYY-MM-DD pattern)
                if (!value.toString().matches("^\\d{4}-\\d{2}-\\d{2}$")) {
                    throw new RuntimeException("Field '" + fieldLabel + "' should be a date in YYYY-MM-DD format, got: " + value);
                }
                break;

            case "text":
            case "textarea":
                if (!(value instanceof String)) {
                    throw new RuntimeException("Field '" + fieldLabel + "' should be text, got: " + value.getClass().getSimpleName());
                }
                break;

            case "checkbox":
                if (!(value instanceof Boolean)) {
                    throw new RuntimeException("Field '" + fieldLabel + "' should be true/false, got: " + value);
                }
                break;

            case "select":
            case "radio":
                // Check if value is in options (if options are provided)
                // This would require parsing options from schema - optional enhancement
                if (value == null || value.toString().isEmpty()) {
                    throw new RuntimeException("Field '" + fieldLabel + "' requires a selected value");
                }
                break;

            case "file":
                // File validation would be handled separately with MultipartFile
                // Just check if it's present
                if (value == null || value.toString().isEmpty()) {
                    throw new RuntimeException("Field '" + fieldLabel + "' requires a file upload");
                }
                break;

            default:
                log.debug("No specific validation for type: {}", expectedType);
        }
    }



    // Existing methods...
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Map<String, Object>> getTasksByAssignee(String assignee) {
        List<Task> tasks = taskService.createTaskQuery()
                .taskAssignee(assignee)
                .active()
                .orderByTaskCreateTime()
                .desc()
                .list();

        // Convert to DTO without accessing form key
        List<Map<String, Object>> taskList = new java.util.ArrayList<>();
        for (Task task : tasks) {
            Map<String, Object> taskInfo = new HashMap<>();
            taskInfo.put("id", task.getId());
            taskInfo.put("name", task.getName());
            taskInfo.put("assignee", task.getAssignee());
            taskInfo.put("createTime", task.getCreateTime());
            taskInfo.put("processInstanceId", task.getProcessInstanceId());
            taskInfo.put("taskDefinitionKey", task.getTaskDefinitionKey());
            taskInfo.put("description", task.getDescription());
            taskInfo.put("priority", task.getPriority());

            // Get form data for this task
            FormData form = formRepo.findByProcessInstanceId(task.getProcessInstanceId()).orElse(null);
            if (form != null) {
                taskInfo.put("formId", form.getId());
                taskInfo.put("formTypeId", form.getFormTypeId());
                taskInfo.put("status", form.getStatus());
            }

            taskList.add(taskInfo);
        }

        return taskList;
    }
    public Map<String, Object> getFormDetails(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        FormData form = formRepo.findByProcessInstanceId(task.getProcessInstanceId()).orElse(null);

        Map<String, Object> details = new HashMap<>();
        details.put("taskId", task.getId());
        details.put("taskName", task.getName());
        details.put("formId", form.getId());
        details.put("formTypeId", form.getFormTypeId());
        details.put("formData", form.getDataJson());
        details.put("status", form.getStatus());
        details.put("currentApprover", form.getCurrentApprover());
        details.put("currentStep", form.getCurrentStepNumber());
        details.put("totalSteps", form.getTotalSteps());
        details.put("approvalProgress", bpmnParserService.getApprovalProgress(task.getProcessInstanceId()));

        // Get all variables
        Map<String, Object> variables = runtimeService.getVariables(task.getProcessInstanceId());
        details.put("approvalHistory", extractApprovalHistory(variables));

        return details;
    }

    private Map<String, Object> extractApprovalHistory(Map<String, Object> variables) {
        Map<String, Object> history = new HashMap<>();

        // Extract approval history dynamically from variables
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String key = entry.getKey();
            if (key.endsWith("Approved") && !key.equals("approved")) {
                String approver = key.replace("Approved", "");
                Map<String, Object> approval = new HashMap<>();
                approval.put("approved", entry.getValue());
                approval.put("comments", variables.getOrDefault(approver + "Comments", ""));
                history.put(approver, approval);
            }
        }

        return history;
    }
}