package com.smartelectronicsupermarket.ai.summary;

import java.util.Optional;
import java.util.UUID;

public interface SpecSummaryDataAccess {

    /** Product + brand + category + raw JSONB specifications; empty if the product id does not exist. */
    Optional<SpecProduct> findProduct(UUID productId);
}
