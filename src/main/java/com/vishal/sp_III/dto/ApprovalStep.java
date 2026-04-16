package com.vishal.sp_III.dto;

import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class ApprovalStep {
    private int order;
    private String taskId;
    private String taskName;
    private String assignee;
    private List<String> candidateGroups;
    private String status; // PENDING, APPROVED, REJECTED
    private String comments;
    private String approvedBy;
    private Date approvedAt;
}