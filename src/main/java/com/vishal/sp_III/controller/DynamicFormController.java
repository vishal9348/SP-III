package com.vishal.sp_III.controller;

import com.vishal.sp_III.dto.DynamicField;
import com.vishal.sp_III.dto.ValidationRule;
import com.vishal.sp_III.entity.FormType;
import com.vishal.sp_III.service.DynamicFormService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dynamic-forms")
@RequiredArgsConstructor
public class DynamicFormController {

    private final DynamicFormService dynamicFormService;

    // Create custom form with any number of fields
    @PostMapping("/create-custom")
    public ResponseEntity<Map<String, Object>> createCustomForm(
            @RequestParam String formName,
            @RequestParam Long departmentId,
            @RequestParam String workflowKey,
            @RequestBody List<DynamicField> fields) {

        FormType formType = dynamicFormService.createDynamicForm(formName, departmentId, workflowKey, fields);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Custom form created successfully");
        response.put("formType", formType);
        response.put("fieldCount", fields.size());

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<FormType>> findAllForms(){
        return new ResponseEntity<>(dynamicFormService.getAllCreatedForms(), HttpStatus.OK);
    }

    @GetMapping("/{formId}")
    public ResponseEntity<FormType> findFormById(@PathVariable Long formId) throws Exception {
        return new ResponseEntity<>(dynamicFormService.getCreatedFormByID(formId), HttpStatus.OK);
    }

    // Add fields to existing form
    @PostMapping("/{formTypeId}/add-fields")
    public ResponseEntity<Map<String, Object>> addFields(
            @PathVariable Long formTypeId,
            @RequestBody List<DynamicField> fields) {

        FormType updated = dynamicFormService.addFieldsToForm(formTypeId, fields);

        Map<String, Object> response = new HashMap<>();
        response.put("message", fields.size() + " fields added successfully");
        response.put("formType", updated);

        return ResponseEntity.ok(response);
    }

    // Remove fields from form
    @DeleteMapping("/{formTypeId}/remove-fields")
    public ResponseEntity<Map<String, Object>> removeFields(
            @PathVariable Long formTypeId,
            @RequestBody List<String> fieldNames) {

        FormType updated = dynamicFormService.removeFieldsFromForm(formTypeId, fieldNames);

        Map<String, Object> response = new HashMap<>();
        response.put("message", fieldNames.size() + " fields removed successfully");
        response.put("formType", updated);

        return ResponseEntity.ok(response);
    }

    // Update specific field
    @PutMapping("/{formTypeId}/fields/{fieldName}")
    public ResponseEntity<Map<String, Object>> updateField(
            @PathVariable Long formTypeId,
            @PathVariable String fieldName,
            @RequestBody DynamicField updatedField) {

        FormType updated = dynamicFormService.updateField(formTypeId, fieldName, updatedField);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Field updated successfully");
        response.put("formType", updated);

        return ResponseEntity.ok(response);
    }

    private DynamicField createField(String name, String label, String type,
                                     boolean required, String placeholder,
                                     List<String> options) {
        DynamicField field = new DynamicField();
        field.setName(name);
        field.setLabel(label);
        field.setType(type);
        field.setRequired(required);
        field.setPlaceholder(placeholder);
        field.setOptions(options);
        return field;
    }
}
