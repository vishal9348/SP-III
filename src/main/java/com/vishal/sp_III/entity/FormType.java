package com.vishal.sp_III.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FormType {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    private String name;
    private Long departmentId;

    @Lob
    private String schemaJson;

    private Boolean isMasterForm = false;  // true for master form that contains sub-forms
    private Long parentFormTypeId;         // For sub-forms, reference to master form
    private String associatedWorkflowKey;  // Workflow key for this form type
    private Integer approvalLevel;          // Number of approval levels
}
