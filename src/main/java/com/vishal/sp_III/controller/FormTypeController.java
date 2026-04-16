package com.vishal.sp_III.controller;

import com.vishal.sp_III.entity.FormType;
import com.vishal.sp_III.service.FormTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/form-types")
@RequiredArgsConstructor
public class FormTypeController {

    private final FormTypeService formTypeService;

    // Create new form type
    @PostMapping
    public ResponseEntity<Map<String, Object>> createFormType( @RequestBody FormType formType) {
        FormType created = formTypeService.createFormType(formType);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Form type created successfully");
        response.put("formType", created);
        response.put("id", created.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Update form type
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateFormType(
            @PathVariable Long id,
            @RequestBody FormType formType) {

        FormType updated = formTypeService.updateFormType(id, formType);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Form type updated successfully");
        response.put("formType", updated);

        return ResponseEntity.ok(response);
    }

    // Get form type by ID
    @GetMapping("/{id}")
    public ResponseEntity<FormType> getFormType(@PathVariable Long id) {
        return ResponseEntity.ok(formTypeService.getFormTypeById(id));
    }

    // Get form type with workflow mapping
    @GetMapping("/{id}/with-workflow")
    public ResponseEntity<Map<String, Object>> getFormTypeWithWorkflow(@PathVariable Long id) {
        return ResponseEntity.ok(formTypeService.getFormTypeWithWorkflow(id));
    }

    // Get all form types
    @GetMapping
    public ResponseEntity<List<FormType>> getAllFormTypes() {
        return ResponseEntity.ok(formTypeService.getAllFormTypes());
    }

    // Get form types by department
    @GetMapping("/department/{departmentId}")
    public ResponseEntity<List<FormType>> getFormTypesByDepartment(@PathVariable Long departmentId) {
        return ResponseEntity.ok(formTypeService.getFormTypesByDepartment(departmentId));
    }

    // Delete form type
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteFormType(@PathVariable Long id) {
        formTypeService.deleteFormType(id);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Form type deleted successfully");
        return ResponseEntity.ok(response);
    }

    // Get sample schema
    @GetMapping("/sample-schema")
    public ResponseEntity<Map<String, String>> getSampleSchema() {
        Map<String, String> response = new HashMap<>();
        response.put("schema", formTypeService.getSampleSchema());
        return ResponseEntity.ok(response);
    }

    // Validate schema
    @PostMapping("/validate-schema")
    public ResponseEntity<Map<String, Object>> validateSchema(@RequestBody Map<String, String> request) {
        String schema = request.get("schema");

        try {
            formTypeService.validateSchema(schema); // Make validateSchema public or add this method
            Map<String, Object> response = new HashMap<>();
            response.put("valid", true);
            response.put("message", "Schema is valid");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("valid", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}