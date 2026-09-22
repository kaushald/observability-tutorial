package com.kaushaldalvi.o11y.orders;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Restaurant {

    @Id
    private Long id;

    private String name;

    private String cuisine;

    protected Restaurant() {
        // for JPA
    }

    public Restaurant(Long id, String name, String cuisine) {
        this.id = id;
        this.name = name;
        this.cuisine = cuisine;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCuisine() {
        return cuisine;
    }
}
