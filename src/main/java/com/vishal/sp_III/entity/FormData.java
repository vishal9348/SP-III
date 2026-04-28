package com.vishal.sp_III.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class FormData {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private Long formTypeId;

    @Lob
    private String dataJson;

    private String processInstanceId;

    private String status; // SUBMITTED, WITH_RMAKER, WITH_RCHECKER, WITH_COMAKER, WITH_COCHECKER, APPROVED, REJECTED

    private String currentApprover;

    private String rejectionReason;

    // New fields for dynamic approval tracking
    private Integer currentStepNumber;
    private Integer totalSteps;

    @Column(columnDefinition = "TEXT")
    private String approvalSteps; // Store approval steps as JSON

    private String currentTaskKey;
    private String currentTaskName;

    // New fields for multi-department support
    private Long parentFormDataId;      // For sub-forms, reference to master form
    private String departmentName;       // Department this sub-form belongs to
    private String departmentRole;       // maker, checker, cochecker for this department
    private String subFormKey;           // Unique key for this sub-form within master
}
