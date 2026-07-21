package com.ecommerce.repository.spec;

import com.ecommerce.entity.Product;
import org.springframework.data.jpa.domain.Specification;

/**
 * Composable {@link Specification} predicates for the product catalog listing.
 * {@code activeOnly} lets non-admin callers only ever see {@code active=true}
 * products, while an admin caller can pass {@code false} to see the full catalog
 * including soft-deleted items.
 */
public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> hasCategory(String category) {
        return (root, query, cb) -> (category == null || category.isBlank())
                ? null
                : cb.equal(cb.lower(root.get("category")), category.toLowerCase());
    }

    public static Specification<Product> matchesSearch(String search) {
        return (root, query, cb) -> (search == null || search.isBlank())
                ? null
                : cb.like(cb.lower(root.get("name")), "%" + search.toLowerCase() + "%");
    }

    public static Specification<Product> isActive(boolean active) {
        return (root, query, cb) -> cb.equal(root.get("active"), active);
    }
}
