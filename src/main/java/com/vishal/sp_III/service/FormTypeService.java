package com.vishal.sp_III.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vishal.sp_III.entity.FormType;
import com.vishal.sp_III.entity.WorkflowMapping;
import com.vishal.sp_III.repository.FormTypeRepository;
import com.vishal.sp_III.repository.WorkflowMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FormTypeService {

    private final FormTypeRepository formTypeRepository;
    private final WorkflowMappingRepository workflowMappingRepository;
    private final ObjectMapper objectMapper;

    // Create new form type
    @Transactional
    public FormType createFormType(FormType formType) {
        // Validate schema JSON
        if (formType.getSchemaJson() != null && !formType.getSchemaJson().isEmpty()) {
            validateSchema(formType.getSchemaJson());
        }

        // Check if name already exists
        if (formTypeRepository.existsByName(formType.getName())) {
            throw new RuntimeException("Form type with name '" + formType.getName() + "' already exists");
        }

        FormType saved = formTypeRepository.save(formType);
        log.info("Created form type: {} with ID: {}", saved.getName(), saved.getId());
        return saved;
    }

    // Update form type
    @Transactional
    public FormType updateFormType(Long id, FormType formTypeDetails) {
        FormType existing = formTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Form type not found with id: " + id));

        if (formTypeDetails.getName() != null && !formTypeDetails.getName().equals(existing.getName())) {
            if (formTypeRepository.existsByName(formTypeDetails.getName())) {
                throw new RuntimeException("Form type with name '" + formTypeDetails.getName() + "' already exists");
            }
            existing.setName(formTypeDetails.getName());
        }

        if (formTypeDetails.getDepartmentId() != null) {
            existing.setDepartmentId(formTypeDetails.getDepartmentId());
        }

        if (formTypeDetails.getSchemaJson() != null && !formTypeDetails.getSchemaJson().isEmpty()) {
            validateSchema(formTypeDetails.getSchemaJson());
            existing.setSchemaJson(formTypeDetails.getSchemaJson());
        }

        FormType updated = formTypeRepository.save(existing);
        log.info("Updated form type: {}", updated.getName());
        return updated;
    }

    // Get form type by ID
    public FormType getFormTypeById(Long id) {
        return formTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Form type not found with id: " + id));
    }

    // Get all form types
    public List<FormType> getAllFormTypes() {
        return formTypeRepository.findAll();
    }

    // Get form types by department
    public List<FormType> getFormTypesByDepartment(Long departmentId) {
        return formTypeRepository.findByDepartmentId(departmentId);
    }

    // Delete form type
    @Transactional
    public void deleteFormType(Long id) {
        FormType formType = getFormTypeById(id);

        // Check if there are any workflow mappings using this form type
        WorkflowMapping mapping = workflowMappingRepository.findByFormTypeIdAndActiveTrue(id);
        if (mapping != null) {
            throw new RuntimeException("Cannot delete form type as it has an active workflow mapping. Deactivate the workflow mapping first.");
        }

        formTypeRepository.delete(formType);
        log.info("Deleted form type: {}", formType.getName());
    }

    // Validate JSON schema
    public void validateSchema(String schemaJson) {
        try {
            JsonNode schema = objectMapper.readTree(schemaJson);

            // Basic validation - check if it has required fields
            if (!schema.has("fields") && !schema.has("type")) {
                log.warn("Schema may be missing 'fields' or 'type' property");
            }

            log.info("Schema validation passed");
        } catch (Exception e) {
            throw new RuntimeException("Invalid JSON schema: " + e.getMessage());
        }
    }

    // Get form schema with workflow mapping info
    public Map<String, Object> getFormTypeWithWorkflow(Long id) {
        FormType formType = getFormTypeById(id);
        WorkflowMapping mapping = workflowMappingRepository.findByFormTypeIdAndActiveTrue(id);

        Map<String, Object> response = new java.util.HashMap<>();
        response.put("formType", formType);
        response.put("hasWorkflowMapping", mapping != null);

        if (mapping != null) {
            response.put("workflowKey", mapping.getWorkflowKey());
            response.put("approvalLevel", mapping.getApprovalLevel());
        }

        return response;
    }

    // Create sample form schema
    public String getSampleSchema() {
        return """
        {
            "title": "fed dept Form",
            "type": "object",
            "fields": [
                {
                    "name": "fullName",
                    "label": "Full Name",
                    "type": "text",
                    "required": true
                },
                {
                    "name": "email",
                    "label": "Email Address",
                    "type": "email",
                    "required": true
                },
                {
                    "name": "department",
                    "label": "Department",
                    "type": "select",
                    "options": ["IT", "HR", "Finance", "Operations"],
                    "required": true
                },
                {
                    "name": "amount",
                    "label": "Request Amount",
                    "type": "number",
                    "required": true
                },
                {
                    "name": "description",
                    "label": "Description",
                    "type": "textarea",
                    "required": true
                },
                {
                    "name": "attachments",
                    "label": "Supporting Documents",
                    "type": "file",
                    "required": false
                }
            ]
        }
        """;
    }
}
