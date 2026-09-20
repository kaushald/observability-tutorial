package com.kaushaldalvi.o11y.orders;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.util.LinkedHashSet;
import java.util.Set;

@Entity
public class MenuItem {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id")
    private Restaurant restaurant;

    private String name;

    private int priceCents;

    // Deliberate N+1: tags are a lazy element collection with no join fetch, entity
    // graph or batch size configured, so reading tags item-by-item issues one extra
    // SELECT per item. Lesson 006 is about finding and fixing this from traces.
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "menu_item_tag", joinColumns = @JoinColumn(name = "menu_item_id"))
    @Column(name = "tag")
    private Set<String> tags = new LinkedHashSet<>();

    protected MenuItem() {
        // for JPA
    }

    public MenuItem(Long id, Restaurant restaurant, String name, int priceCents, Set<String> tags) {
        this.id = id;
        this.restaurant = restaurant;
        this.name = name;
        this.priceCents = priceCents;
        if (tags != null) {
            this.tags = new LinkedHashSet<>(tags);
        }
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

    public Set<String> getTags() {
        return tags;
    }
}
