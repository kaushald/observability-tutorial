package com.kaushaldalvi.o11y.orders.repository;

import com.kaushaldalvi.o11y.orders.domain.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {
}
