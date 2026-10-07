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
            new NoiseFilter("*#get*()", "Getter"),
            new NoiseFilter("*#set*(*)", "Setter"),
            new NoiseFilter("java.util.Optional#*", "Optional API"),
            new NoiseFilter("java.util.stream.*", "Stream API"));

    private SpringNoiseFilters() {}
}
