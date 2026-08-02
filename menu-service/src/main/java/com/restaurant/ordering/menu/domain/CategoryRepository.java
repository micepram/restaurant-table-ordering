package com.restaurant.ordering.menu.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * The full menu is assembled with three fetch queries — categories with items, items with
 * modifier groups, groups with modifiers — rather than one.
 *
 * <p>Fetching the whole chain in a single statement joins two {@code List} collections at
 * once, which Hibernate rejects with MultipleBagFetchException. Three queries each touch a
 * single collection, and the results merge in the shared persistence context, so the graph
 * still comes out fully populated with no N+1.
 */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    @Query("select distinct c from Category c left join fetch c.items order by c.sortOrder")
    List<Category> findAllWithItems();
}
