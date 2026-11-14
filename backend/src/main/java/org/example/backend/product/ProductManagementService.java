package org.example.backend.product;

import org.example.backend.shop.CurrentShop;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Owner-only product catalog management. Validation is imperative here
 * rather than bean validation on the request DTOs, matching
 * StaffManagementService's style — this app has no @Valid/@ControllerAdvice
 * convention anywhere yet.
 */
@Service
public class ProductManagementService {

    private final ProductRepository productRepository;
    private final CurrentShop currentShop;
    private final ProductCostService productCostService;

    public ProductManagementService(ProductRepository productRepository, CurrentShop currentShop,
                                    ProductCostService productCostService) {
        this.productRepository = productRepository;
        this.currentShop = currentShop;
        this.productCostService = productCostService;
    }

    @Transactional
    public Product createProduct(String name, ProductUnit unit, BigDecimal price, BigDecimal alertLevel,
                                  BigDecimal cartonWeight) {
        return createProduct(name, unit, price, alertLevel, cartonWeight, null);
    }

    @Transactional
    public Product createProduct(String name, ProductUnit unit, BigDecimal price, BigDecimal alertLevel,
                                  BigDecimal cartonWeight, BigDecimal costPrice) {
        validate(name, unit, price, alertLevel, cartonWeight);
        if (productRepository.existsByShopAndNameIgnoreCase(currentShop.get(), name)) {
            throw new IllegalArgumentException("A product named \"" + name + "\" already exists");
        }
        Product product = new Product(currentShop.get(), name, unit, price, alertLevel);
        product.setCartonWeight(cartonWeight);
        Product saved = productRepository.save(product);
        // A new product has no sales yet, so the first-cost fill finds nothing.
        productCostService.applyCost(saved, costPrice);
        return saved;
    }

    @Transactional
    public Product updateProduct(Long id, String name, ProductUnit unit, BigDecimal price, BigDecimal alertLevel,
                                  BigDecimal cartonWeight) {
        return updateProduct(id, name, unit, price, alertLevel, cartonWeight, null);
    }

    /** A null {@code costPrice} leaves the cost unchanged; a cost can be changed but never cleared. */
    @Transactional
    public Product updateProduct(Long id, String name, ProductUnit unit, BigDecimal price, BigDecimal alertLevel,
                                  BigDecimal cartonWeight, BigDecimal costPrice) {
        validate(name, unit, price, alertLevel, cartonWeight);
        Product product = getProduct(id);
        if (productRepository.existsByShopAndNameIgnoreCaseAndIdNot(currentShop.get(), name, id)) {
            throw new IllegalArgumentException("A product named \"" + name + "\" already exists");
        }
        product.setName(name);
        product.setUnit(unit);
        product.setPrice(price);
        product.setAlertLevel(alertLevel);
        product.setCartonWeight(cartonWeight);
        productCostService.applyCost(product, costPrice);
        return product;
    }

    @Transactional
    public void deactivate(Long id) {
        getProduct(id).setActive(false);
    }

    public List<Product> listProducts() {
        return productRepository.findByShopOrderByName(currentShop.get());
    }

    private Product getProduct(Long id) {
        return productRepository.findByIdAndShop(id, currentShop.get())
                .orElseThrow(() -> new IllegalArgumentException("No product " + id));
    }

    private void validate(String name, ProductUnit unit, BigDecimal price, BigDecimal alertLevel,
                           BigDecimal cartonWeight) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Product name is required");
        }
        if (unit == null) {
            throw new IllegalArgumentException("Product unit is required");
        }
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("Price must be greater than zero");
        }
        if (alertLevel == null || alertLevel.signum() < 0) {
            throw new IllegalArgumentException("Alert level cannot be negative");
        }
        if (cartonWeight != null && cartonWeight.signum() <= 0) {
            throw new IllegalArgumentException("Carton weight must be greater than zero when given");
        }
    }
}
