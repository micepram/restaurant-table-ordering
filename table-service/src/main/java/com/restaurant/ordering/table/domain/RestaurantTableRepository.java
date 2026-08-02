package com.restaurant.ordering.table.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RestaurantTableRepository extends JpaRepository<RestaurantTable, Long> {

    Optional<RestaurantTable> findByQrCode(String qrCode);

    Optional<RestaurantTable> findByCode(String code);

    List<RestaurantTable> findAllByOrderByCodeAsc();
}
