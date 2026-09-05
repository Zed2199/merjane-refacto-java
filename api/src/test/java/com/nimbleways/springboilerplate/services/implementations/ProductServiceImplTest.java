package com.nimbleways.springboilerplate.services.implementations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nimbleways.springboilerplate.dto.enums.ProductType;
import com.nimbleways.springboilerplate.entities.Product;
import com.nimbleways.springboilerplate.repositories.ProductRepository;
import com.nimbleways.springboilerplate.utils.Annotations.UnitTest;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);

    @Mock
    private ProductRepository productRepository;
    @Mock
    private NotificationService notificationService;

    private ProductServiceImpl productService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
        productService = new ProductServiceImpl(productRepository, notificationService, clock);
    }

    @Test
    void shouldUpdateLeadTimeAndNotifyDelay() {
        Product product = new Product(null, 5, 0, ProductType.NORMAL, "USB Cable", null, null, null);

        productService.notifyDelay(10, product);

        assertThat(product.getLeadTime()).isEqualTo(10);
        then(productRepository).should().save(product);
        then(notificationService).should().sendDelayNotification(10, "USB Cable");
    }

    @Test
    void shouldMarkOutOfStockWhenRestockArrivesAfterSeasonEnd() {
        Product product = seasonalProduct(5, 30, TODAY.minusDays(10), TODAY.plusDays(20));

        productService.handleSeasonalProduct(product);

        assertThat(product.getAvailable()).isZero();
        then(productRepository).should().save(product);
        then(notificationService).should().sendOutOfStockNotification("Watermelon");
    }

    @Test
    void shouldNotifyOutOfStockButKeepStockWhenSeasonHasNotStarted() {
        Product product = seasonalProduct(30, 15, TODAY.plusDays(30), TODAY.plusDays(90));

        productService.handleSeasonalProduct(product);

        assertThat(product.getAvailable()).isEqualTo(30);
        then(productRepository).should().save(product);
        then(notificationService).should().sendOutOfStockNotification("Watermelon");
    }

    @ParameterizedTest
    @ValueSource(ints = { 5, 20 }) // 20 = restock lands on the last day of the season
    void shouldNotifyDelayWhenRestockArrivesBeforeSeasonEnd(int leadTime) {
        Product product = seasonalProduct(0, leadTime, TODAY.minusDays(10), TODAY.plusDays(20));

        productService.handleSeasonalProduct(product);

        then(productRepository).should().save(product);
        then(notificationService).should().sendDelayNotification(leadTime, "Watermelon");
    }

    @Test
    void shouldDecrementStockWhenProductIsNotExpired() {
        Product product = expirableProduct(6, TODAY.plusDays(10));

        productService.handleExpiredProduct(product);

        assertThat(product.getAvailable()).isEqualTo(5);
        then(productRepository).should().save(product);
        then(notificationService).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @CsvSource({
            "6, -2", // expired
            "6, 0",  // expires today
            "0, 10"  // out of stock
    })
    void shouldEmptyStockAndNotifyExpiration(int available, int daysUntilExpiry) {
        LocalDate expiryDate = TODAY.plusDays(daysUntilExpiry);
        Product product = expirableProduct(available, expiryDate);

        productService.handleExpiredProduct(product);

        assertThat(product.getAvailable()).isZero();
        then(productRepository).should().save(product);
        then(notificationService).should().sendExpirationNotification("Milk", expiryDate);
    }

    private static Product seasonalProduct(int available, int leadTime, LocalDate seasonStart, LocalDate seasonEnd) {
        return new Product(null, leadTime, available, ProductType.SEASONAL, "Watermelon", null, seasonStart,
                seasonEnd);
    }

    private static Product expirableProduct(int available, LocalDate expiryDate) {
        return new Product(null, 15, available, ProductType.EXPIRABLE, "Milk", expiryDate, null, null);
    }
}
