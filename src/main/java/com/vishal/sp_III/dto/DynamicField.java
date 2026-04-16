package com.vishal.sp_III.dto;

import lombok.Data;
import java.util.List;

@Data
public class DynamicField {
    private String name;
    private String label;
    private String type; // text, number, email, date, select, radio, checkbox, textarea, file
    private boolean required = false;
    private String placeholder;
    private String defaultValue;
    private List<String> options; // For select/radio
    private ValidationRule validation;
}
