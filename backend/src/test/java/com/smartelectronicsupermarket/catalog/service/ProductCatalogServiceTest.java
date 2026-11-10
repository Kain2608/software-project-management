package com.smartelectronicsupermarket.catalog.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;

import com.smartelectronicsupermarket.catalog.data.ProductCatalogDataAccess;

import tools.jackson.databind.json.JsonMapper;

class ProductCatalogServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID BRAND_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final String SKU = "TV-S1-001";
    private static final String NAME = "Smart TV";
    private static final String SPECIFICATIONS = "{\"resolution\":\"4K\"}";
    private static final BigDecimal PRICE = new BigDecimal("12990000.00");

    private ProductCatalogDataAccess dataAccess;
    private ProductCatalogService service;

    @BeforeEach
    void setUp() {
        dataAccess = mock(ProductCatalogDataAccess.class);
        service = new ProductCatalogService(dataAccess, JsonMapper.builder().build());
    }

    @Test
    void createProductDelegatesOnceAndReturnsProductId() {
        when(dataAccess.createProduct(
                ACTOR_ID, SKU, BRAND_ID, CATEGORY_ID, NAME, SPECIFICATIONS, PRICE, 24))
                .thenReturn(PRODUCT_ID);

        UUID result = service.createProduct(
                ACTOR_ID, SKU, BRAND_ID, CATEGORY_ID, NAME, SPECIFICATIONS, PRICE, 24);

        assertEquals(PRODUCT_ID, result);
        verify(dataAccess, times(1)).createProduct(
                ACTOR_ID, SKU, BRAND_ID, CATEGORY_ID, NAME, SPECIFICATIONS, PRICE, 24);
    }

    @Test
    void updateProductPreservesExpectedRevisionAndReturnsNewRevision() {
        when(dataAccess.updateProduct(
                ACTOR_ID, PRODUCT_ID, 7, NAME, SPECIFICATIONS, PRICE, 36, false))
                .thenReturn(8);

        int result = service.updateProduct(
                ACTOR_ID, PRODUCT_ID, 7, NAME, SPECIFICATIONS, PRICE, 36, false);

        assertEquals(8, result);
        verify(dataAccess, times(1)).updateProduct(
                ACTOR_ID, PRODUCT_ID, 7, NAME, SPECIFICATIONS, PRICE, 36, false);
    }

    @Test
    void missingCreateInputsAreRejectedWithoutCallingDataAccess() {
        assertThrows(IllegalArgumentException.class, () -> service.createProduct(
                null, SKU, BRAND_ID, CATEGORY_ID, NAME, SPECIFICATIONS, PRICE, 24));
        assertThrows(IllegalArgumentException.class, () -> service.createProduct(
                ACTOR_ID, " ", BRAND_ID, CATEGORY_ID, NAME, SPECIFICATIONS, PRICE, 24));
        assertThrows(IllegalArgumentException.class, () -> service.createProduct(
                ACTOR_ID, SKU, null, CATEGORY_ID, NAME, SPECIFICATIONS, PRICE, 24));
        assertThrows(IllegalArgumentException.class, () -> service.createProduct(
                ACTOR_ID, SKU, BRAND_ID, null, NAME, SPECIFICATIONS, PRICE, 24));
        assertThrows(IllegalArgumentException.class, () -> service.createProduct(
                ACTOR_ID, SKU, BRAND_ID, CATEGORY_ID, " ", SPECIFICATIONS, PRICE, 24));
        assertThrows(IllegalArgumentException.class, () -> service.createProduct(
                ACTOR_ID, SKU, BRAND_ID, CATEGORY_ID, NAME, SPECIFICATIONS, null, 24));

        verifyNoInteractions(dataAccess);
    }

    @Test
    void missingOrInvalidUpdateInputsAreRejectedWithoutCallingDataAccess() {
        assertThrows(IllegalArgumentException.class, () -> service.updateProduct(
                null, PRODUCT_ID, 0, NAME, SPECIFICATIONS, PRICE, 24, true));
        assertThrows(IllegalArgumentException.class, () -> service.updateProduct(
                ACTOR_ID, null, 0, NAME, SPECIFICATIONS, PRICE, 24, true));
        assertThrows(IllegalArgumentException.class, () -> service.updateProduct(
                ACTOR_ID, PRODUCT_ID, -1, NAME, SPECIFICATIONS, PRICE, 24, true));
        assertThrows(IllegalArgumentException.class, () -> service.updateProduct(
                ACTOR_ID, PRODUCT_ID, 0, " ", SPECIFICATIONS, PRICE, 24, true));
        assertThrows(IllegalArgumentException.class, () -> service.updateProduct(
                ACTOR_ID, PRODUCT_ID, 0, NAME, SPECIFICATIONS, null, 24, true));

        verifyNoInteractions(dataAccess);
    }

    @Test
    void nameOverDatabaseLimitIsRejectedWithoutCallingDataAccess() {
        String longName = "a".repeat(201);

        assertThrows(IllegalArgumentException.class, () -> service.createProduct(
                ACTOR_ID, SKU, BRAND_ID, CATEGORY_ID, longName, SPECIFICATIONS, PRICE, 24));

        verifyNoInteractions(dataAccess);
    }

    @Test
    void nameWithSqlTrimmedLengthAtDatabaseLimitIsAccepted() {
        String paddedName = "  " + "a".repeat(200) + "  ";
        when(dataAccess.createProduct(
                ACTOR_ID, SKU, BRAND_ID, CATEGORY_ID,
                paddedName, SPECIFICATIONS, PRICE, 24))
                .thenReturn(PRODUCT_ID);

        UUID result = service.createProduct(
                ACTOR_ID, SKU, BRAND_ID, CATEGORY_ID,
                paddedName, SPECIFICATIONS, PRICE, 24);

        assertEquals(PRODUCT_ID, result);
        verify(dataAccess).createProduct(
                ACTOR_ID, SKU, BRAND_ID, CATEGORY_ID,
                paddedName, SPECIFICATIONS, PRICE, 24);
    }

    @Test
    void priceAtBothDatabaseBoundariesIsAcceptedWithoutRounding() {
        BigDecimal minimumPrice = BigDecimal.ZERO;
        BigDecimal maximumPrice = new BigDecimal("999999999999.99");
        when(dataAccess.createProduct(
                ACTOR_ID, SKU, BRAND_ID, CATEGORY_ID,
                NAME, SPECIFICATIONS, minimumPrice, 24))
                .thenReturn(PRODUCT_ID);
        when(dataAccess.updateProduct(
                ACTOR_ID, PRODUCT_ID, 0,
                NAME, SPECIFICATIONS, maximumPrice, 24, false))
                .thenReturn(1);

        UUID createdProductId = service.createProduct(
                ACTOR_ID, SKU, BRAND_ID, CATEGORY_ID,
                NAME, SPECIFICATIONS, minimumPrice, 24);
        int revision = service.updateProduct(
                ACTOR_ID, PRODUCT_ID, 0,
                NAME, SPECIFICATIONS, maximumPrice, 24, false);

        assertEquals(PRODUCT_ID, createdProductId);
        assertEquals(1, revision);
        verify(dataAccess).createProduct(
                ACTOR_ID, SKU, BRAND_ID, CATEGORY_ID,
                NAME, SPECIFICATIONS, minimumPrice, 24);
        verify(dataAccess).updateProduct(
                ACTOR_ID, PRODUCT_ID, 0,
                NAME, SPECIFICATIONS, maximumPrice, 24, false);
    }

    @Test
    void priceOutsideDatabaseRangeIsRejectedWithoutCallingDataAccess() {
        assertThrows(IllegalArgumentException.class, () -> service.createProduct(
                ACTOR_ID, SKU, BRAND_ID, CATEGORY_ID, NAME, SPECIFICATIONS,
                new BigDecimal("-0.01"), 24));
        assertThrows(IllegalArgumentException.class, () -> service.updateProduct(
                ACTOR_ID, PRODUCT_ID, 0, NAME, SPECIFICATIONS,
                new BigDecimal("1000000000000.00"), 24, true));

        verifyNoInteractions(dataAccess);
    }

    @Test
    void warrantyOutsideDatabaseRangeIsRejectedWithoutCallingDataAccess() {
        assertThrows(IllegalArgumentException.class, () -> service.createProduct(
                ACTOR_ID, SKU, BRAND_ID, CATEGORY_ID, NAME, SPECIFICATIONS, PRICE, -1));
        assertThrows(IllegalArgumentException.class, () -> service.updateProduct(
                ACTOR_ID, PRODUCT_ID, 0, NAME, SPECIFICATIONS, PRICE, 121, true));

        verifyNoInteractions(dataAccess);
    }

    @Test
    void malformedOrNonObjectSpecificationsAreRejectedWithoutCallingDataAccess() {
        assertThrows(IllegalArgumentException.class, () -> service.createProduct(
                ACTOR_ID, SKU, BRAND_ID, CATEGORY_ID, NAME, "{invalid", PRICE, 24));
        assertThrows(IllegalArgumentException.class, () -> service.updateProduct(
                ACTOR_ID, PRODUCT_ID, 0, NAME, "[]", PRICE, 24, true));

        verifyNoInteractions(dataAccess);
    }

    @Test
    void emptyJsonObjectIsAcceptedAndPassedUnchanged() {
        String emptySpecifications = "{}";
        when(dataAccess.createProduct(
                ACTOR_ID, SKU, BRAND_ID, CATEGORY_ID,
                NAME, emptySpecifications, PRICE, 24))
                .thenReturn(PRODUCT_ID);

        UUID result = service.createProduct(
                ACTOR_ID, SKU, BRAND_ID, CATEGORY_ID,
                NAME, emptySpecifications, PRICE, 24);

        assertEquals(PRODUCT_ID, result);
        verify(dataAccess).createProduct(
                ACTOR_ID, SKU, BRAND_ID, CATEGORY_ID,
                NAME, emptySpecifications, PRICE, 24);
    }

    @Test
    void createAllowsNullSpecificationsAndPreservesDatabaseDefaultContract() {
        when(dataAccess.createProduct(
                ACTOR_ID, SKU, BRAND_ID, CATEGORY_ID, NAME, null, PRICE, 24))
                .thenReturn(PRODUCT_ID);

        UUID result = service.createProduct(
                ACTOR_ID, SKU, BRAND_ID, CATEGORY_ID, NAME, null, PRICE, 24);

        assertEquals(PRODUCT_ID, result);
        verify(dataAccess).createProduct(
                ACTOR_ID, SKU, BRAND_ID, CATEGORY_ID, NAME, null, PRICE, 24);
    }

    @Test
    void updateRejectsNullSpecificationsWithoutCallingDataAccess() {
        assertThrows(IllegalArgumentException.class, () -> service.updateProduct(
                ACTOR_ID, PRODUCT_ID, 0, NAME, null, PRICE, 24, true));

        verifyNoInteractions(dataAccess);
    }

    @Test
    void dataAccessExceptionIsPropagated() {
        DataIntegrityViolationException failure =
                new DataIntegrityViolationException("catalog constraint violation");
        when(dataAccess.createProduct(
                ACTOR_ID, SKU, BRAND_ID, CATEGORY_ID, NAME, SPECIFICATIONS, PRICE, 24))
                .thenThrow(failure);

        DataIntegrityViolationException thrown = assertThrows(
                DataIntegrityViolationException.class,
                () -> service.createProduct(
                        ACTOR_ID, SKU, BRAND_ID, CATEGORY_ID,
                        NAME, SPECIFICATIONS, PRICE, 24));

        assertSame(failure, thrown);
        verify(dataAccess, times(1)).createProduct(
                ACTOR_ID, SKU, BRAND_ID, CATEGORY_ID, NAME, SPECIFICATIONS, PRICE, 24);
    }

    @Test
    void revisionConflictIsPropagatedWithoutRetry() {
        ConcurrencyFailureException failure =
                new ConcurrencyFailureException("SQLSTATE 40001");
        when(dataAccess.updateProduct(
                ACTOR_ID, PRODUCT_ID, 3, NAME, SPECIFICATIONS, PRICE, 24, true))
                .thenThrow(failure);

        ConcurrencyFailureException thrown = assertThrows(
                ConcurrencyFailureException.class,
                () -> service.updateProduct(
                        ACTOR_ID, PRODUCT_ID, 3,
                        NAME, SPECIFICATIONS, PRICE, 24, true));

        assertSame(failure, thrown);
        verify(dataAccess, times(1)).updateProduct(
                ACTOR_ID, PRODUCT_ID, 3, NAME, SPECIFICATIONS, PRICE, 24, true);
    }
}
