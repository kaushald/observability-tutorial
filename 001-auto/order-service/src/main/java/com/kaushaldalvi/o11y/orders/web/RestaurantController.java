package com.kaushaldalvi.o11y.orders.web;

import com.kaushaldalvi.o11y.orders.dto.MenuItemResponse;
import com.kaushaldalvi.o11y.orders.dto.RestaurantResponse;
import com.kaushaldalvi.o11y.orders.repository.MenuItemRepository;
import com.kaushaldalvi.o11y.orders.repository.RestaurantRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class RestaurantController {

    private final RestaurantRepository restaurantRepository;
    private final MenuItemRepository menuItemRepository;

    public RestaurantController(RestaurantRepository restaurantRepository, MenuItemRepository menuItemRepository) {
        this.restaurantRepository = restaurantRepository;
        this.menuItemRepository = menuItemRepository;
    }

    @GetMapping("/api/restaurants")
    public List<RestaurantResponse> listRestaurants() {
        return restaurantRepository.findAll().stream()
                .map(r -> new RestaurantResponse(r.getId(), r.getName(), r.getCuisine()))
                .toList();
    }

    @GetMapping("/api/restaurants/{id}/menu")
    public ResponseEntity<List<MenuItemResponse>> menu(@PathVariable Long id) {
        if (restaurantRepository.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<MenuItemResponse> menu = menuItemRepository.findByRestaurantId(id).stream()
                .map(item -> new MenuItemResponse(item.getId(), item.getName(), item.getPriceCents()))
                .toList();
        return ResponseEntity.ok(menu);
    }
}
