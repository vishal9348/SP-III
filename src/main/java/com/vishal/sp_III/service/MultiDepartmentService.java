package com.vishal.sp_III.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vishal.sp_III.dto.SubFormConfig;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultiDepartmentService {

    private final FormRepository formRepository;
    private final FormTypeRepository formTypeRepository;
    private final WorkflowMappingRepository workflowMappingRepository;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final ObjectMapper objectMapper;

    // Create master form type with sub-form configuration
    @Transactional
    public FormType createMasterFormType(String masterFormName, Long departmentId,
                                         String masterSchema, List<SubFormConfig> subFormConfigs) {
        // Create master form type
        FormType masterFormType = new FormType();
        masterFormType.setName(masterFormName);
        masterFormType.setDepartmentId(departmentId);
        masterFormType.setSchemaJson(masterSchema);
        masterFormType.setIsMasterForm(true);
        masterFormType.setAssociatedWorkflowKey("multi-department-approval-flow");
        masterFormType.setApprovalLevel(4);

        FormType savedMaster = formTypeRepository.save(masterFormType);

        // Create sub-form types for each department
        for (SubFormConfig config : subFormConfigs) {
            FormType subFormType = new FormType();
            subFormType.setName(masterFormName + " - " + config.getDepartmentName());
            subFormType.setDepartmentId(config.getDepartmentId());
            subFormType.setSchemaJson(config.getSubFormSchema());
            subFormType.setIsMasterForm(false);
            subFormType.setParentFormTypeId(savedMaster.getId());
            subFormType.setAssociatedWorkflowKey("department-approval-flow");
            subFormType.setApprovalLevel(3); // maker, checker, cochecker

            formTypeRepository.save(subFormType);

            // Create workflow mapping for sub-form
            WorkflowMapping mapping = new WorkflowMapping();
            mapping.setFormTypeId(subFormType.getId());
            mapping.setWorkflowKey("department-approval-flow");
            mapping.setActive(true);
            mapping.setApprovalLevel(3);
            workflowMappingRepository.save(mapping);
        }

        // Create workflow mapping for master form
        WorkflowMapping masterMapping = new WorkflowMapping();
        masterMapping.setFormTypeId(savedMaster.getId());
        masterMapping.setWorkflowKey("multi-department-approval-flow");
        masterMapping.setActive(true);
        masterMapping.setApprovalLevel(4);
        workflowMappingRepository.save(masterMapping);

        log.info("Created master form type '{}' with {} sub-form types", masterFormName, subFormConfigs.size());
        return savedMaster;
    }

    // Submit multi-department form
    @Transactional
    public Map<String, Object> submitMultiDepartmentForm(Long masterFormTypeId,
                                                         Map<String, Object> masterData,
                                                         List<Map<String, Object>> subFormsData) throws JsonProcessingException {

        // Validate master form type exists
        FormType masterFormType = formTypeRepository.findById(masterFormTypeId)
                .orElseThrow(() -> new RuntimeException("Master form type not found: " + masterFormTypeId));

        if (!masterFormType.getIsMasterForm()) {
            throw new RuntimeException("Form type is not a master form: " + masterFormTypeId);
        }

        // 1. Save master form data
        FormData masterForm = new FormData();
        masterForm.setFormTypeId(masterFormTypeId);
        masterForm.setDataJson(objectMapper.writeValueAsString(masterData));
        masterForm.setStatus("IN_PROGRESS");
        FormData savedMaster = formRepository.save(masterForm);

        // 2. Save all sub-forms and start their workflows
        List<Map<String, Object>> subFormResults = new ArrayList<>();
        Map<String, String> subFormProcessIds = new HashMap<>();

        for (Map<String, Object> subFormData : subFormsData) {
            String departmentName = (String) subFormData.get("departmentName");
            Long departmentId = Long.valueOf(subFormData.get("departmentId").toString());

            // Get sub-form type
            Optional<FormType> subFormTypeOpt = formTypeRepository.findByParentFormTypeIdAndDepartmentId(
                    masterFormTypeId, departmentId);

            if (!subFormTypeOpt.isPresent()) {
                log.warn("No sub-form type found for department {} in master form {}", departmentId, masterFormTypeId);
                continue;
            }

            FormType subFormType = subFormTypeOpt.get();

            // Save sub-form data
            FormData subForm = new FormData();
            subForm.setFormTypeId(subFormType.getId());
            subForm.setDataJson(objectMapper.writeValueAsString(subFormData));
            subForm.setParentFormDataId(savedMaster.getId());
            subForm.setDepartmentName(departmentName);
            subForm.setStatus("PENDING");
            FormData savedSubForm = formRepository.save(subForm);

            // Start sub-form workflow with department-specific assignees
            Map<String, Object> variables = new HashMap<>();
            variables.put("formId", savedSubForm.getId());
            variables.put("formTypeId", subFormType.getId());
            variables.put("formData", subForm.getDataJson());
            variables.put("departmentName", departmentName);
            variables.put("departmentMaker", subFormData.get("departmentMaker"));
            variables.put("departmentChecker", subFormData.get("departmentChecker"));
            variables.put("departmentCoChecker", subFormData.get("departmentCoChecker"));

            // Start workflow for sub-form
            WorkflowMapping mapping = workflowMappingRepository.findByFormTypeIdAndActiveTrue(subFormType.getId());
            if (mapping == null) {
                throw new RuntimeException("No workflow mapping found for sub-form type: " + subFormType.getId());
            }

            String subProcessId = runtimeService.startProcessInstanceByKey(
                    mapping.getWorkflowKey(), variables).getProcessInstanceId();

            savedSubForm.setProcessInstanceId(subProcessId);
            savedSubForm.setStatus("WITH_MAKER");
            savedSubForm.setCurrentApprover(subFormData.get("departmentMaker").toString());
            formRepository.save(savedSubForm);

            subFormProcessIds.put(departmentName, subProcessId);
            subFormResults.add(Map.of(
                    "departmentName", departmentName,
                    "subFormId", savedSubForm.getId(),
                    "processInstanceId", subProcessId,
                    "status", "STARTED"
            ));
        }

        // 3. Start master workflow
        Map<String, Object> masterVariables = new HashMap<>();
        masterVariables.put("masterFormId", savedMaster.getId());
        masterVariables.put("formData", savedMaster.getDataJson());
        masterVariables.put("subForms", subFormsData);
        masterVariables.put("subFormProcessIds", subFormProcessIds);
        masterVariables.put("subFormsCount", subFormsData.size());
        masterVariables.put("completedCount", 0);
        masterVariables.put("rejected", false);

        WorkflowMapping masterMapping = workflowMappingRepository.findByFormTypeIdAndActiveTrue(masterFormTypeId);
        if (masterMapping == null) {
            throw new RuntimeException("No workflow mapping found for master form type: " + masterFormTypeId);
        }

        String masterProcessId = runtimeService.startProcessInstanceByKey(
                masterMapping.getWorkflowKey(), masterVariables).getProcessInstanceId();

        savedMaster.setProcessInstanceId(masterProcessId);
        formRepository.save(savedMaster);

        log.info("Master form {} submitted with {} sub-forms", savedMaster.getId(), subFormResults.size());

        return Map.of(
                "masterFormId", savedMaster.getId(),
                "masterProcessInstanceId", masterProcessId,
                "subForms", subFormResults
        );
    }

    // Get tasks for a specific department
    public List<Map<String, Object>> getDepartmentTasks(String departmentName, String role) {
        String assignee = departmentName.toLowerCase() + "_" + role;
        List<org.camunda.bpm.engine.task.Task> tasks = taskService.createTaskQuery()
                .taskAssignee(assignee)
                .active()
                .list();

        List<Map<String, Object>> result = new ArrayList<>();
        for (org.camunda.bpm.engine.task.Task task : tasks) {
            Map<String, Object> variables = runtimeService.getVariables(task.getProcessInstanceId());
            Long formId = (Long) variables.get("formId");

            if (formId != null) {
                Optional<FormData> subFormOpt = formRepository.findById(formId);
                if (subFormOpt.isPresent()) {
                    FormData subForm = subFormOpt.get();

                    Map<String, Object> taskInfo = new HashMap<>();
                    taskInfo.put("taskId", task.getId());
                    taskInfo.put("taskName", task.getName());
                    taskInfo.put("assignee", task.getAssignee());
                    taskInfo.put("subFormId", subForm.getId());
                    taskInfo.put("departmentName", subForm.getDepartmentName());
                    taskInfo.put("subFormData", subForm.getDataJson());
                    taskInfo.put("status", subForm.getStatus());

                    if (subForm.getParentFormDataId() != null) {
                        Optional<FormData> masterFormOpt = formRepository.findById(subForm.getParentFormDataId());
                        masterFormOpt.ifPresent(masterForm -> {
                            taskInfo.put("masterFormId", masterForm.getId());
                            try {
                                Map<String, Object> masterData = objectMapper.readValue(masterForm.getDataJson(), Map.class);
                                taskInfo.put("masterFormTitle", masterData.getOrDefault("requestTitle", "No Title"));
                            } catch (Exception e) {
                                taskInfo.put("masterFormTitle", "Unknown");
                            }
                        });
                    }

                    result.add(taskInfo);
                }
            }
        }
        return result;
    }

    // Get master form progress
    public Map<String, Object> getMasterFormProgress(Long masterFormId) {
        Optional<FormData> masterFormOpt = formRepository.findById(masterFormId);
        if (!masterFormOpt.isPresent()) {
            return Collections.singletonMap("error", "Master form not found: " + masterFormId);
        }

        FormData masterForm = masterFormOpt.get();
        List<FormData> subForms = formRepository.findByParentFormDataId(masterFormId);

        Map<String, Object> progress = new HashMap<>();
        progress.put("masterFormId", masterFormId);
        progress.put("status", masterForm.getStatus());
        progress.put("totalSubForms", subForms.size());

        long approvedCount = subForms.stream()
                .filter(sf -> "APPROVED".equals(sf.getStatus()))
                .count();
        long pendingCount = subForms.stream()
                .filter(sf -> sf.getStatus() != null && sf.getStatus().startsWith("WITH_"))
                .count();
        long rejectedCount = subForms.stream()
                .filter(sf -> "REJECTED".equals(sf.getStatus()))
                .count();

        progress.put("approvedCount", approvedCount);
        progress.put("pendingCount", pendingCount);
        progress.put("rejectedCount", rejectedCount);
        progress.put("completionPercentage", subForms.isEmpty() ? 0 : (approvedCount * 100 / subForms.size()));

        List<Map<String, Object>> subFormDetails = new ArrayList<>();
        for (FormData subForm : subForms) {
            Map<String, Object> subDetail = new HashMap<>();
            subDetail.put("subFormId", subForm.getId());
            subDetail.put("departmentName", subForm.getDepartmentName());
            subDetail.put("status", subForm.getStatus());
            subDetail.put("currentApprover", subForm.getCurrentApprover());

            // Parse sub-form data to get summary
            try {
                Map<String, Object> subData = objectMapper.readValue(subForm.getDataJson(), Map.class);
                subDetail.put("requestDetails", subData.getOrDefault("requestDetails", ""));
                subDetail.put("amount", subData.getOrDefault("amount", 0));
            } catch (Exception e) {
                subDetail.put("requestDetails", "Unknown");
            }

            subFormDetails.add(subDetail);
        }
        progress.put("subForms", subFormDetails);

        // Get master form data
        try {
            Map<String, Object> masterData = objectMapper.readValue(masterForm.getDataJson(), Map.class);
            progress.put("masterData", masterData);
        } catch (Exception e) {
            progress.put("masterData", Collections.emptyMap());
        }

        return progress;
    }
}