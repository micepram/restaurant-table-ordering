package com.restaurant.ordering.kitchen.domain;

import java.util.List;

import com.restaurant.ordering.events.OrderStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface KitchenTicketRepository extends JpaRepository<KitchenTicket, Long> {

    /**
     * The board: everything the kitchen still owns, strictly oldest first.
     *
     * <p>This ordering is the whole point of the fan-in. Orders arrive from many tables on
     * several partitions, and the queue the cooks work is a single global sequence by the
     * time each order was placed — not by the order events happened to be consumed in.
     */
    @Query("select distinct t from KitchenTicket t left join fetch t.lines "
            + "where t.status in :statuses order by t.placedAt asc")
    List<KitchenTicket> findBoard(List<OrderStatus> statuses);

    @Query("select distinct t from KitchenTicket t left join fetch t.lines where t.orderId = :orderId")
    KitchenTicket findWithLines(Long orderId);
}
