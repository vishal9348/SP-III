package com.vishal.sp_III.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vishal.sp_III.dto.DynamicField;
import com.vishal.sp_III.entity.FormType;
import com.vishal.sp_III.entity.WorkflowMapping;
import com.vishal.sp_III.repository.FormTypeRepository;
import com.vishal.sp_III.repository.WorkflowMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicFormService {

    private final FormTypeRepository formTypeRepository;
    private final WorkflowMappingRepository workflowMappingRepository;
    private final ObjectMapper objectMapper;
    private final BpmnParserService bpmnParserService;

    // Create form type with dynamic fields
    @Transactional
    public FormType createDynamicForm(String formName, Long departmentId,String workflowKey, List<DynamicField> fields) {
        // Build schema JSON dynamically
        String schemaJson = buildSchema(formName, fields);

        // Validate schema
        validateSchema(schemaJson);

        // Save form type
        FormType formType = new FormType();
        formType.setName(formName);
        formType.setDepartmentId(departmentId);
        formType.setSchemaJson(schemaJson);

        FormType saved = formTypeRepository.save(formType);

        // Create workflow mapping
        WorkflowMapping mapping = new WorkflowMapping();
        mapping.setFormTypeId(saved.getId());
//        mapping.setWorkflowKey("fed-dept-approval-flow");
        mapping.setWorkflowKey(workflowKey);
        mapping.setActive(true);
//        mapping.setApprovalLevel(4);
        workflowMappingRepository.save(mapping);

        log.info("Created dynamic form '{}' with {} fields", formName, fields.size());
        return saved;
    }

    public List<FormType> getAllCreatedForms(){
        List<FormType> all = formTypeRepository.findAll();
        return all;
    }

    public FormType getCreatedFormByID(Long formId) throws Exception {
        Optional<FormType> byId = formTypeRepository.findById(formId);
        if (byId.isPresent()){
            return byId.get();
        }
        throw new Exception("Form not found");
    }

    // Add fields to existing form
    @Transactional
    public FormType addFieldsToForm(Long formTypeId, List<DynamicField> newFields) {
        FormType formType = formTypeRepository.findById(formTypeId)
                .orElseThrow(() -> new RuntimeException("Form type not found"));

        // Parse existing schema
        JsonNode rootNode;
        try {
            rootNode = objectMapper.readTree(formType.getSchemaJson());
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse existing schema");
        }

        // Get existing fields array
        ArrayNode fieldsArray;
        if (rootNode.has("fields")) {
            fieldsArray = (ArrayNode) rootNode.get("fields");
        } else {
            fieldsArray = objectMapper.createArrayNode();
            ((ObjectNode) rootNode).set("fields", fieldsArray);
        }

        // Add new fields
        for (DynamicField field : newFields) {
            ObjectNode fieldNode = objectMapper.createObjectNode();
            fieldNode.put("name", field.getName());
            fieldNode.put("label", field.getLabel());
            fieldNode.put("type", field.getType());
            fieldNode.put("required", field.isRequired());

            // Add options for select/radio
            if (field.getOptions() != null && !field.getOptions().isEmpty()) {
                ArrayNode optionsArray = objectMapper.createArrayNode();
                for (String option : field.getOptions()) {
                    optionsArray.add(option);
                }
                fieldNode.set("options", optionsArray);
            }

            // Add placeholder if present
            if (field.getPlaceholder() != null) {
                fieldNode.put("placeholder", field.getPlaceholder());
            }

            // Add validation rules
            if (field.getValidation() != null) {
                ObjectNode validationNode = objectMapper.createObjectNode();
                if (field.getValidation().getMin() != null) {
                    validationNode.put("min", field.getValidation().getMin());
                }
                if (field.getValidation().getMax() != null) {
                    validationNode.put("max", field.getValidation().getMax());
                }
                if (field.getValidation().getPattern() != null) {
                    validationNode.put("pattern", field.getValidation().getPattern());
                }
                fieldNode.set("validation", validationNode);
            }

            fieldsArray.add(fieldNode);
        }

        // Update schema
        String updatedSchema = rootNode.toString();
        validateSchema(updatedSchema);
        formType.setSchemaJson(updatedSchema);

        return formTypeRepository.save(formType);
    }

    // Remove fields from form
    @Transactional
    public FormType removeFieldsFromForm(Long formTypeId, List<String> fieldNames) {
        FormType formType = formTypeRepository.findById(formTypeId)
                .orElseThrow(() -> new RuntimeException("Form type not found"));

        try {
            JsonNode rootNode = objectMapper.readTree(formType.getSchemaJson());
            ArrayNode fieldsArray = (ArrayNode) rootNode.get("fields");

            // Remove specified fields
            Iterator<JsonNode> iterator = fieldsArray.iterator();
            while (iterator.hasNext()) {
                JsonNode field = iterator.next();
                String fieldName = field.get("name").asText();
                if (fieldNames.contains(fieldName)) {
                    iterator.remove();
                }
            }

            String updatedSchema = rootNode.toString();
            validateSchema(updatedSchema);
            formType.setSchemaJson(updatedSchema);

            return formTypeRepository.save(formType);

        } catch (Exception e) {
            throw new RuntimeException("Failed to remove fields: " + e.getMessage());
        }
    }

    // Update existing field
    @Transactional
    public FormType updateField(Long formTypeId, String fieldName, DynamicField updatedField) {
        FormType formType = formTypeRepository.findById(formTypeId)
                .orElseThrow(() -> new RuntimeException("Form type not found"));

        try {
            JsonNode rootNode = objectMapper.readTree(formType.getSchemaJson());
            ArrayNode fieldsArray = (ArrayNode) rootNode.get("fields");

            // Find and update field
            for (JsonNode field : fieldsArray) {
                if (field.get("name").asText().equals(fieldName)) {
                    ((ObjectNode) field).put("label", updatedField.getLabel());
                    ((ObjectNode) field).put("type", updatedField.getType());
                    ((ObjectNode) field).put("required", updatedField.isRequired());

                    if (updatedField.getPlaceholder() != null) {
                        ((ObjectNode) field).put("placeholder", updatedField.getPlaceholder());
                    }

                    if (updatedField.getOptions() != null && !updatedField.getOptions().isEmpty()) {
                        ArrayNode optionsArray = objectMapper.createArrayNode();
                        for (String option : updatedField.getOptions()) {
                            optionsArray.add(option);
                        }
                        ((ObjectNode) field).set("options", optionsArray);
                    }
                    break;
                }
            }

            String updatedSchema = rootNode.toString();
            validateSchema(updatedSchema);
            formType.setSchemaJson(updatedSchema);

            return formTypeRepository.save(formType);

        } catch (Exception e) {
            throw new RuntimeException("Failed to update field: " + e.getMessage());
        }
    }

    private String buildSchema(String formName, List<DynamicField> fields) {
        try {
            ObjectNode rootNode = objectMapper.createObjectNode();
            rootNode.put("title", formName);
            rootNode.put("version", "1.0");
            rootNode.put("createdAt", new Date().toString());

            ArrayNode fieldsArray = objectMapper.createArrayNode();

            for (DynamicField field : fields) {
                ObjectNode fieldNode = objectMapper.createObjectNode();
                fieldNode.put("name", field.getName());
                fieldNode.put("label", field.getLabel());
                fieldNode.put("type", field.getType());
                fieldNode.put("required", field.isRequired());

                if (field.getPlaceholder() != null) {
                    fieldNode.put("placeholder", field.getPlaceholder());
                }

                if (field.getDefaultValue() != null) {
                    fieldNode.put("defaultValue", field.getDefaultValue());
                }

                if (field.getOptions() != null && !field.getOptions().isEmpty()) {
                    ArrayNode optionsArray = objectMapper.createArrayNode();
                    for (String option : field.getOptions()) {
                        optionsArray.add(option);
                    }
                    fieldNode.set("options", optionsArray);
                }

                if (field.getValidation() != null) {
                    ObjectNode validationNode = objectMapper.createObjectNode();
                    if (field.getValidation().getMin() != null) {
                        validationNode.put("min", field.getValidation().getMin());
                    }
                    if (field.getValidation().getMax() != null) {
                        validationNode.put("max", field.getValidation().getMax());
                    }
                    if (field.getValidation().getPattern() != null) {
                        validationNode.put("pattern", field.getValidation().getPattern());
                    }
                    if (field.getValidation().getMinLength() != null) {
                        validationNode.put("minLength", field.getValidation().getMinLength());
                    }
                    if (field.getValidation().getMaxLength() != null) {
                        validationNode.put("maxLength", field.getValidation().getMaxLength());
                    }
                    fieldNode.set("validation", validationNode);
                }

                fieldsArray.add(fieldNode);
            }

            rootNode.set("fields", fieldsArray);
            return objectMapper.writeValueAsString(rootNode);

        } catch (Exception e) {
            throw new RuntimeException("Failed to build schema: " + e.getMessage());
        }
    }

    private void validateSchema(String schemaJson) {
        try {
            JsonNode schema = objectMapper.readTree(schemaJson);

            if (!schema.has("title")) {
                throw new RuntimeException("Schema must have a title");
            }

            if (!schema.has("fields") || !schema.get("fields").isArray()) {
                throw new RuntimeException("Schema must have a fields array");
            }

            JsonNode fields = schema.get("fields");
            Set<String> fieldNames = new HashSet<>();

            for (JsonNode field : fields) {
                String name = field.get("name").asText();
                if (fieldNames.contains(name)) {
                    throw new RuntimeException("Duplicate field name: " + name);
                }
                fieldNames.add(name);

                if (!field.has("label")) {
                    throw new RuntimeException("Field '" + name + "' missing label");
                }

                if (!field.has("type")) {
                    throw new RuntimeException("Field '" + name + "' missing type");
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Invalid schema: " + e.getMessage());
        }
    }
}