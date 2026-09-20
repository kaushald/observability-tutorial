package com.kaushaldalvi.o11y.orders;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Set;

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

        // Restaurant 1: items 1-8
        menuItemRepository.save(new MenuItem(1L, bellaPizza, "Margherita Pizza", 1200, Set.of("vegetarian", "popular")));
        menuItemRepository.save(new MenuItem(2L, bellaPizza, "Pepperoni Pizza", 1400, Set.of("popular")));
        menuItemRepository.save(new MenuItem(3L, bellaPizza, "Garlic Bread", 500, Set.of("vegetarian")));
        menuItemRepository.save(new MenuItem(4L, bellaPizza, "Tiramisu", 650, Set.of("vegetarian", "new")));
        menuItemRepository.save(new MenuItem(5L, bellaPizza, "Quattro Formaggi Pizza", 1500, Set.of("vegetarian", "new")));
        menuItemRepository.save(new MenuItem(6L, bellaPizza, "Diavola Pizza", 1450, Set.of("spicy", "popular")));
        menuItemRepository.save(new MenuItem(7L, bellaPizza, "Bruschetta", 550, Set.of("vegetarian", "gluten-free")));
        menuItemRepository.save(new MenuItem(8L, bellaPizza, "Caprese Salad", 600, Set.of("vegetarian", "gluten-free", "new")));

        // Restaurant 2: items 9-16
        menuItemRepository.save(new MenuItem(9L, slowNoodles, "Pad Thai", 1100, Set.of("popular")));
        menuItemRepository.save(new MenuItem(10L, slowNoodles, "Drunken Noodles", 1150, Set.of("spicy")));
        menuItemRepository.save(new MenuItem(11L, slowNoodles, "Spring Rolls", 500, Set.of("vegetarian")));
        menuItemRepository.save(new MenuItem(12L, slowNoodles, "Thai Iced Tea", 400, Set.of("vegetarian", "gluten-free")));
        menuItemRepository.save(new MenuItem(13L, slowNoodles, "Green Curry", 1200, Set.of("spicy", "gluten-free")));
        menuItemRepository.save(new MenuItem(14L, slowNoodles, "Pho", 1150, Set.of("gluten-free", "popular")));
        menuItemRepository.save(new MenuItem(15L, slowNoodles, "Mango Sticky Rice", 650, Set.of("vegetarian", "new", "gluten-free")));
        menuItemRepository.save(new MenuItem(16L, slowNoodles, "Edamame", 450, Set.of("vegetarian", "gluten-free")));

        // Restaurant 3: items 17-24
        menuItemRepository.save(new MenuItem(17L, tacoCorner, "Street Tacos (3pc)", 900, Set.of("popular")));
        menuItemRepository.save(new MenuItem(18L, tacoCorner, "Burrito Bowl", 1050, Set.of("gluten-free")));
        menuItemRepository.save(new MenuItem(19L, tacoCorner, "Chips & Guac", 550, Set.of("vegetarian", "popular")));
        menuItemRepository.save(new MenuItem(20L, tacoCorner, "Horchata", 350, Set.of("vegetarian", "gluten-free")));
        menuItemRepository.save(new MenuItem(21L, tacoCorner, "Quesadilla", 750, Set.of("vegetarian", "new")));
        menuItemRepository.save(new MenuItem(22L, tacoCorner, "Nachos Supreme", 950, Set.of("spicy", "popular")));
        menuItemRepository.save(new MenuItem(23L, tacoCorner, "Elote", 500, Set.of("vegetarian", "gluten-free", "spicy")));
        menuItemRepository.save(new MenuItem(24L, tacoCorner, "Churros", 500, Set.of("vegetarian", "new")));
    }
}
