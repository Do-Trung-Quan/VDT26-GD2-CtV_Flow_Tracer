package com.example.flow.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CategoryRequest {

    /** Mã danh mục, không trùng với danh mục khác. */
    @NotBlank
    @Size(max = 50)
    private String code;

    @NotBlank
    @Size(max = 255)
    private String title;

    @Size(max = 1000)
    private String brief;

    private CategoryStatus status;

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public String getBrief() {
        return brief;
    }

    public CategoryStatus getStatus() {
        return status;
    }
}
