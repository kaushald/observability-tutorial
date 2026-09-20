package com.kaushaldalvi.o11y.orders;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    // Lesson 006: the fix for the N+1 the traces showed. The entity graph makes Hibernate
    // fetch each item's tags in the same query, instead of one extra SELECT per item.
    @EntityGraph(attributePaths = "tags")
    List<MenuItem> findByRestaurantId(Long restaurantId);
}
