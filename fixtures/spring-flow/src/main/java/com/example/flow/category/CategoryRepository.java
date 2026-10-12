package com.example.flow.category;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository Spring Data: một method suy ra từ tên, một method có @Query, còn lại kế thừa (save, findById…). */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsByCode(String code);

    @Query("select c from Category c where lower(c.title) like lower(concat('%', :keyword, '%'))")
    List<Category> search(@Param("keyword") String keyword);
}
