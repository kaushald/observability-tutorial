package com.kaushaldalvi.o11y.orders.repository;

import com.kaushaldalvi.o11y.orders.domain.CustomerOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {
}
