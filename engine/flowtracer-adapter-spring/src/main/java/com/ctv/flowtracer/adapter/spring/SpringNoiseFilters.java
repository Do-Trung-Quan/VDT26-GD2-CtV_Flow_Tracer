package com.ctv.flowtracer.adapter.spring;

import com.ctv.flowtracer.ir.NoiseFilter;
import java.util.List;

/** Bộ lọc nhiễu mặc định cho dự án Spring. */
public final class SpringNoiseFilters {

    public static final List<NoiseFilter> DEFAULTS = List.of(
            new NoiseFilter("org.slf4j.*", "Ghi log"),
            new NoiseFilter("*.Logger#*", "Ghi log"),
            new NoiseFilter("org.modelmapper.*", "Chuyển đổi đối tượng"),
            new NoiseFilter("*Mapper#*", "Chuyển đổi đối tượng (MapStruct)"),
            new NoiseFilter("*Utils#*", "Hàm tiện ích"),
            new NoiseFilter("*Parser#*", "Phân tích / chuyển đổi dữ liệu"),
            new NoiseFilter("*Converter#*", "Chuyển đổi đối tượng"),
            new NoiseFilter("*Assembler#*", "Chuyển đổi đối tượng"),
            new NoiseFilter("*Formatter#*", "Định dạng dữ liệu"),
            new NoiseFilter("*Helper#*", "Hàm tiện ích"),
            new NoiseFilter("*Util#*", "Hàm tiện ích"),
            new NoiseFilter("*Validator#*", "Kiểm tra dữ liệu đầu vào"),
            new NoiseFilter("*Response#*", "Đóng gói kết quả trả về"),
            new NoiseFilter("*Request#*", "Đối tượng dữ liệu đầu vào"),
            new NoiseFilter("*Dto#*", "Đối tượng chuyển dữ liệu"),
            new NoiseFilter("*DTO#*", "Đối tượng chuyển dữ liệu"),
            new NoiseFilter("java.util.Optional#*", "Optional API"),
            new NoiseFilter("java.util.stream.*", "Stream API"));

    private SpringNoiseFilters() {}
}
