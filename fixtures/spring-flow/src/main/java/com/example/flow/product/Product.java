package com.example.flow.product;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "product")
public class Product {

    private Long id;
    private String title;
}
