package com.restaurant.ordering.menu.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    /** Second of the three menu fetch queries; see {@link CategoryRepository}. */
    @Query("select distinct i from MenuItem i left join fetch i.modifierGroups")
    List<MenuItem> findAllWithGroups();

    Optional<MenuItem> findByNameIgnoreCase(String name);
}
