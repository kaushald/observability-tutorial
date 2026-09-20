package com.kaushaldalvi.o11y.orders;

import com.kaushaldalvi.o11y.orders.domain.MenuItem;
import com.kaushaldalvi.o11y.orders.domain.Restaurant;
import com.kaushaldalvi.o11y.orders.repository.MenuItemRepository;
import com.kaushaldalvi.o11y.orders.repository.RestaurantRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class SeedDataLoader implements CommandLineRunner {

    private final RestaurantRepository restaurantRepository;
    private final MenuItemRepository menuItemRepository;

    public SeedDataLoader(RestaurantRepository restaurantRepository, MenuItemRepository menuItemRepository) {
        this.restaurantRepository = restaurantRepository;
        this.menuItemRepository = menuItemRepository;
    }

    @Override
    public void run(String... args) {
        if (restaurantRepository.count() > 0) {
            return;
        }

        Restaurant bellaPizza = restaurantRepository.save(new Restaurant(1L, "Bella Pizza", "Italian"));
        Restaurant slowNoodles = restaurantRepository.save(new Restaurant(2L, "Slow Noodles", "Asian"));
        Restaurant tacoCorner = restaurantRepository.save(new Restaurant(3L, "Taco Corner", "Mexican"));

        menuItemRepository.save(new MenuItem(1L, bellaPizza, "Margherita Pizza", 1200));
        menuItemRepository.save(new MenuItem(2L, bellaPizza, "Pepperoni Pizza", 1400));
        menuItemRepository.save(new MenuItem(3L, bellaPizza, "Garlic Bread", 500));
        menuItemRepository.save(new MenuItem(4L, bellaPizza, "Tiramisu", 650));

        menuItemRepository.save(new MenuItem(5L, slowNoodles, "Pad Thai", 1100));
        menuItemRepository.save(new MenuItem(6L, slowNoodles, "Drunken Noodles", 1150));
        menuItemRepository.save(new MenuItem(7L, slowNoodles, "Spring Rolls", 500));
        menuItemRepository.save(new MenuItem(8L, slowNoodles, "Thai Iced Tea", 400));

        menuItemRepository.save(new MenuItem(9L, tacoCorner, "Street Tacos (3pc)", 900));
        menuItemRepository.save(new MenuItem(10L, tacoCorner, "Burrito Bowl", 1050));
        menuItemRepository.save(new MenuItem(11L, tacoCorner, "Chips & Guac", 550));
        menuItemRepository.save(new MenuItem(12L, tacoCorner, "Horchata", 350));
    }
}
