package com.vishal.sp_III.dto;

import lombok.Data;

@Data
public class ValidationRule {
    private Double min;      // For numbers
    private Double max;      // For numbers
    private Integer minLength; // For text
    private Integer maxLength; // For text
    private String pattern;   // Regex pattern
}
