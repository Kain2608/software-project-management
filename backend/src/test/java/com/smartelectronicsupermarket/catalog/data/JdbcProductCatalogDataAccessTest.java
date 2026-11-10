package com.smartelectronicsupermarket.catalog.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcProductCatalogDataAccessTest {

    private static final String CREATE_PRODUCT_SQL = """
            SELECT sasms.create_product(?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?)
            """;

    private static final String UPDATE_PRODUCT_SQL = """
            SELECT sasms.update_product(?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?)
            """;

    private JdbcTemplate jdbcTemplate;
    private JdbcProductCatalogDataAccess dataAccess;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        dataAccess = new JdbcProductCatalogDataAccess(jdbcTemplate);
    }

    @Test
    void createProductBindsContractParametersAndReturnsProductId() {
        UUID actorId = UUID.randomUUID();
        UUID brandId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        String specificationsJson = "{\"resolution\":\"4K\"}";
        BigDecimal price = new BigDecimal("12990000.00");

        when(jdbcTemplate.queryForObject(
                CREATE_PRODUCT_SQL,
                UUID.class,
                actorId,
                "TV-S1-001",
                brandId,
                categoryId,
                "Smart TV",
                specificationsJson,
                price,
                24)).thenReturn(productId);

        UUID result = dataAccess.createProduct(
                actorId,
                "TV-S1-001",
                brandId,
                categoryId,
                "Smart TV",
                specificationsJson,
                price,
                24);

        assertEquals(productId, result);
        verify(jdbcTemplate).queryForObject(
                CREATE_PRODUCT_SQL,
                UUID.class,
                actorId,
                "TV-S1-001",
                brandId,
                categoryId,
                "Smart TV",
                specificationsJson,
                price,
                24);
    }

    @Test
    void updateProductPreservesExpectedRevisionAndReturnsNewRevision() {
        UUID actorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        String specificationsJson = "{\"screen_inches\":55}";
        BigDecimal price = new BigDecimal("11990000.00");

        when(jdbcTemplate.queryForObject(
                UPDATE_PRODUCT_SQL,
                Integer.class,
                actorId,
                productId,
                7,
                "Smart TV revised",
                specificationsJson,
                price,
                36,
                false)).thenReturn(8);

        int result = dataAccess.updateProduct(
                actorId,
                productId,
                7,
                "Smart TV revised",
                specificationsJson,
                price,
                36,
                false);

        assertEquals(8, result);
        verify(jdbcTemplate).queryForObject(
                UPDATE_PRODUCT_SQL,
                Integer.class,
                actorId,
                productId,
                7,
                "Smart TV revised",
                specificationsJson,
                price,
                36,
                false);
    }

    @Test
    void createProductPropagatesDatabaseFailureWithoutRetry() {
        UUID actorId = UUID.randomUUID();
        UUID brandId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        String specificationsJson = "{}";
        BigDecimal price = BigDecimal.ONE;
        DataIntegrityViolationException failure =
                new DataIntegrityViolationException("duplicate SKU");

        when(jdbcTemplate.queryForObject(
                CREATE_PRODUCT_SQL,
                UUID.class,
                actorId,
                "DUPLICATE-SKU",
                brandId,
                categoryId,
                "Duplicate",
                specificationsJson,
                price,
                12)).thenThrow(failure);

        DataIntegrityViolationException thrown = assertThrows(
                DataIntegrityViolationException.class,
                () -> dataAccess.createProduct(
                        actorId,
                        "DUPLICATE-SKU",
                        brandId,
                        categoryId,
                        "Duplicate",
                        specificationsJson,
                        price,
                        12));

        assertSame(failure, thrown);
        verify(jdbcTemplate, times(1)).queryForObject(
                CREATE_PRODUCT_SQL,
                UUID.class,
                actorId,
                "DUPLICATE-SKU",
                brandId,
                categoryId,
                "Duplicate",
                specificationsJson,
                price,
                12);
    }

    @Test
    void updateProductPropagatesRevisionConflictWithoutRetry() {
        UUID actorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        String specificationsJson = "{}";
        BigDecimal price = BigDecimal.TEN;
        ConcurrencyFailureException failure =
                new ConcurrencyFailureException("SQLSTATE 40001");

        when(jdbcTemplate.queryForObject(
                UPDATE_PRODUCT_SQL,
                Integer.class,
                actorId,
                productId,
                3,
                "Stale update",
                specificationsJson,
                price,
                12,
                true)).thenThrow(failure);

        ConcurrencyFailureException thrown = assertThrows(
                ConcurrencyFailureException.class,
                () -> dataAccess.updateProduct(
                        actorId,
                        productId,
                        3,
                        "Stale update",
                        specificationsJson,
                        price,
                        12,
                        true));

        assertSame(failure, thrown);
        verify(jdbcTemplate, times(1)).queryForObject(
                UPDATE_PRODUCT_SQL,
                Integer.class,
                actorId,
                productId,
                3,
                "Stale update",
                specificationsJson,
                price,
                12,
                true);
    }
}
