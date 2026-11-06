package com.api.supermarket.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.api.supermarket.exception.BadRequestException;

class PaginationValidatorTest {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
        "categoryId",
        "categoryName",
        "isActive",
        "createAt"
    );

    @Test
    void negativePageShouldThrowBadRequest() {
        assertThrows(
            BadRequestException.class,
            () -> PaginationValidator.validate(
                -1, 10, "categoryId", "asc", ALLOWED_SORT_FIELDS
            )
        );
    }

    @Test
    void zeroSizeShouldThrowBadRequest() {
        assertThrows(
            BadRequestException.class,
            () -> PaginationValidator.validate(
                0, 0, "categoryId", "asc", ALLOWED_SORT_FIELDS
            )
        );
    }

    @Test
    void sizeGreaterThanOneHundredShouldThrowBadRequest() {
        assertThrows(
            BadRequestException.class,
            () -> PaginationValidator.validate(
                0, 101, "categoryId", "asc", ALLOWED_SORT_FIELDS
            )
        );
    }

    @Test
    void invalidSortDirectionShouldThrowBadRequest() {
        assertThrows(
            BadRequestException.class,
            () -> PaginationValidator.validate(
                0, 10, "categoryId", "abc", ALLOWED_SORT_FIELDS
            )
        );
    }

    @Test
    void unknownSortFieldShouldThrowBadRequest() {
        assertThrows(
            BadRequestException.class,
            () -> PaginationValidator.validate(
                0, 10, "unknownField", "asc", ALLOWED_SORT_FIELDS
            )
        );
    }

    @Test
    void validPaginationShouldNotThrowException() {
        assertDoesNotThrow(
            () -> PaginationValidator.validate(
                0, 100, "categoryName", "desc", ALLOWED_SORT_FIELDS
            )
        );
    }
}
