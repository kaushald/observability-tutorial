package com.kaushaldalvi.o11y.orders.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
public class MenuItem {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id")
    private Restaurant restaurant;

    private String name;

    private int priceCents;

    protected MenuItem() {
        // for JPA
    }

    public MenuItem(Long id, Restaurant restaurant, String name, int priceCents) {
        this.id = id;
        this.restaurant = restaurant;
        this.name = name;
        this.priceCents = priceCents;
    }

    public Long getId() {
        return id;
    }

    public Restaurant getRestaurant() {
        return restaurant;
    }

    public String getName() {
        return name;
    }

    public int getPriceCents() {
        return priceCents;
    }
}
