package com.restaurant.ordering.menu.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ModifierGroupRepository extends JpaRepository<ModifierGroup, Long> {

    /** Third of the three menu fetch queries; see {@link CategoryRepository}. */
    @Query("select distinct g from ModifierGroup g left join fetch g.modifiers")
    List<ModifierGroup> findAllWithModifiers();
}
