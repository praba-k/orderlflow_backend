package com.portfolio.oms.order;

import java.util.UUID;

public record OrderChanged(UUID orderId, UUID customerId, OrderStatus status) {}
