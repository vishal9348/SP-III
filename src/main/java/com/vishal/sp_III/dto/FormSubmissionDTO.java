package com.vishal.sp_III.dto;


import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

@Data
public class FormSubmissionDTO {
    private Long formTypeId;
    private Map<String, Object> data;

    private String submittedBy;
    private String department;
}
