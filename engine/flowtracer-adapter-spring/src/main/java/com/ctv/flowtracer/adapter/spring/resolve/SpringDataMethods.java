package com.ctv.flowtracer.adapter.spring.resolve;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Các method Spring Data mà repository KẾ THỪA (không khai báo trong mã nguồn): chữ ký chuẩn, thao tác dữ liệu. Method có
 * nhiều kiểu tham số khả dĩ ({@code findAll(Pageable | Sort | Specification | Example)}) ghi tham số là {@code …} thay vì
 * đoán một kiểu.
 */
final class SpringDataMethods {

    /**
     * @param paramTypes kiểu tham số dùng trong node key (vd {@code S}, {@code ID})
     * @param paramNames tên tham số dùng làm nhãn mũi tên
     * @param verb câu lệnh SQL tương ứng
     * @param operation {@code READ}, {@code WRITE} hoặc {@code DELETE}
     * @param returns kiểu trả về, {@code {E}} là tên entity; null nếu có nhiều khả năng nên không đoán
     */
    record Crud(List<String> paramTypes, List<String> paramNames, String verb, String operation, String returns) {

        /** Kiểu trả về với {@code {E}} thay bằng tên entity ({@code entity} null thì dùng {@code T}). */
        String returnType(String entity) {
            return returns == null ? null : returns.replace("{E}", entity == null ? "T" : entity);
        }
    }

    private static final String SELECT = "SELECT";
    private static final String COUNT = "SELECT COUNT(*)";
    private static final String SAVE = "INSERT/UPDATE";
    private static final String ANY = "?";
    private static final String ANY_NAME = "…";

    /** Khóa {@code tên/số đối số}. */
    private static final Map<String, Crud> TABLE = Map.ofEntries(
            entry("save", 1, List.of("S"), List.of("entity"), SAVE, "WRITE", "{E}"),
            entry("saveAll", 1, List.of("Iterable<S>"), List.of("entities"), SAVE, "WRITE", "List<{E}>"),
            entry("saveAndFlush", 1, List.of("S"), List.of("entity"), SAVE, "WRITE", "{E}"),
            entry("saveAllAndFlush", 1, List.of("Iterable<S>"), List.of("entities"), SAVE, "WRITE", "List<{E}>"),
            entry("findById", 1, List.of("ID"), List.of("id"), SELECT, "READ", "Optional<{E}>"),
            entry("existsById", 1, List.of("ID"), List.of("id"), SELECT, "READ", "boolean"),
            entry("getReferenceById", 1, List.of("ID"), List.of("id"), SELECT, "READ", "{E}"),
            entry("getById", 1, List.of("ID"), List.of("id"), SELECT, "READ", "{E}"),
            entry("findAllById", 1, List.of("Iterable<ID>"), List.of("ids"), SELECT, "READ", "List<{E}>"),
            entry("findAll", 0, List.of(), List.of(), SELECT, "READ", "List<{E}>"),
            entry("findAll", 1, List.of(ANY), List.of(ANY_NAME), SELECT, "READ", null),
            entry("findAll", 2, List.of(ANY, ANY), List.of(ANY_NAME, ANY_NAME), SELECT, "READ", null),
            entry("findOne", 1, List.of(ANY), List.of(ANY_NAME), SELECT, "READ", "Optional<{E}>"),
            entry("exists", 1, List.of(ANY), List.of(ANY_NAME), SELECT, "READ", "boolean"),
            entry("count", 0, List.of(), List.of(), COUNT, "READ", "long"),
            entry("count", 1, List.of(ANY), List.of(ANY_NAME), COUNT, "READ", "long"),
            entry("delete", 1, List.of("T"), List.of("entity"), "DELETE", "DELETE", "void"),
            entry("deleteById", 1, List.of("ID"), List.of("id"), "DELETE", "DELETE", "void"),
            entry("deleteAll", 0, List.of(), List.of(), "DELETE", "DELETE", "void"),
            entry("deleteAll", 1, List.of("Iterable<? extends T>"), List.of("entities"), "DELETE", "DELETE", "void"),
            entry("deleteAllById", 1, List.of("Iterable<? extends ID>"), List.of("ids"), "DELETE", "DELETE", "void"),
            entry("deleteInBatch", 1, List.of("Iterable<T>"), List.of("entities"), "DELETE", "DELETE", "void"),
            entry("deleteAllInBatch", 0, List.of(), List.of(), "DELETE", "DELETE", "void"),
            entry("deleteAllInBatch", 1, List.of("Iterable<T>"), List.of("entities"), "DELETE", "DELETE", "void"),
            entry("deleteAllByIdInBatch", 1, List.of("Iterable<ID>"), List.of("ids"), "DELETE", "DELETE", "void"));

    private SpringDataMethods() {}

    static Optional<Crud> find(String name, int argCount) {
        return Optional.ofNullable(TABLE.get(name + "/" + argCount));
    }

    private static Map.Entry<String, Crud> entry(
            String name, int argCount, List<String> types, List<String> names, String verb, String operation, String returns) {
        return Map.entry(name + "/" + argCount, new Crud(types, names, verb, operation, returns));
    }
}
