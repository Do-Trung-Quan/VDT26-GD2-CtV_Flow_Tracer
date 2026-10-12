package com.example.flow.order;

import com.example.flow.common.Tables;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = Tables.ORDER)
public class Order {

    private Long id;
    private String status;

    public boolean isPending() {
        return "PENDING".equals(status);
    }
}
