package com.restaurant.ordering.order.domain;

import java.util.List;
import java.util.Optional;

import com.restaurant.ordering.events.OrderStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {

    List<CustomerOrder> findByTableIdOrderByPlacedAtDesc(Long tableId);

    List<CustomerOrder> findByStatusInOrderByPlacedAtAsc(List<OrderStatus> statuses);

    /**
     * Loads an order with its lines and modifiers in one go.
     *
     * <p>Two collections would be a MultipleBagFetchException, so only the lines are
     * fetch-joined here; the modifiers are small and load per line.
     */
    @Query("select distinct o from CustomerOrder o left join fetch o.lines where o.id = :id")
    Optional<CustomerOrder> findByIdWithLines(Long id);
}
