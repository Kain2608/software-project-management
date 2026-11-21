package com.smartelectronicsupermarket.reporting.data;

import java.math.BigDecimal;

public record StoreSummaryRecord(BigDecimal grossPaidRevenue,long paidOrderCount,long availableDeviceCount,
                                 long receivedMovementCount,long soldMovementCount,long returnedMovementCount) {}
