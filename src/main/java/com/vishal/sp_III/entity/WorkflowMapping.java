package com.vishal.sp_III.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

@Entity
@Data
public class WorkflowMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private Long formTypeId;

    private String workflowKey; // e.g., "fed-dept-approval-flow"

    private Boolean active = true;

    private Integer approvalLevel;
}
