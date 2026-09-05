package com.nimbleways.springboilerplate.services.implementations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nimbleways.springboilerplate.dto.enums.ProductType;
import com.nimbleways.springboilerplate.dto.product.ProcessOrderResponse;
import com.nimbleways.springboilerplate.entities.Order;
import com.nimbleways.springboilerplate.entities.Product;
import com.nimbleways.springboilerplate.exceptions.OrderNotFoundException;
import com.nimbleways.springboilerplate.repositories.OrderRepository;
import com.nimbleways.springboilerplate.repositories.ProductRepository;
import com.nimbleways.springboilerplate.services.ProductService;
import com.nimbleways.springboilerplate.utils.Annotations.UnitTest;

@UnitTest
@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    private static final Long ORDER_ID = 1L;
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductService productService;

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
        orderService = new OrderServiceImpl(orderRepository, productRepository, productService, clock);
    }

    @Test
    void shouldThrowWhenOrderDoesNotExist() {
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.processOrder(ORDER_ID))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessage("Order 1 not found");
    }

    @Test
    void shouldReturnOrderId() {
        givenOrderWith();

        ProcessOrderResponse response = orderService.processOrder(ORDER_ID);

        assertThat(response.id()).isEqualTo(ORDER_ID);
    }

    @Test
    void shouldDecrementStockWhenNormalProductIsAvailable() {
        Product product = normalProduct(30, 15);
        givenOrderWith(product);

        orderService.processOrder(ORDER_ID);

        assertThat(product.getAvailable()).isEqualTo(29);
        then(productRepository).should().save(product);
    }

    @Test
    void shouldNotifyDelayWhenNormalProductIsOutOfStock() {
        Product product = normalProduct(0, 10);
        givenOrderWith(product);

        orderService.processOrder(ORDER_ID);

        then(productService).should().notifyDelay(10, product);
        then(productRepository).shouldHaveNoInteractions();
    }

    @Test
    void shouldDoNothingWhenNormalProductIsOutOfStockWithoutLeadTime() {
        Product product = normalProduct(0, 0);
        givenOrderWith(product);

        orderService.processOrder(ORDER_ID);

        then(productRepository).shouldHaveNoInteractions();
        then(productService).shouldHaveNoInteractions();
    }

    @Test
    void shouldDecrementStockWhenSeasonalProductIsInSeason() {
        Product product = seasonalProduct(30, TODAY.minusDays(10), TODAY.plusDays(50));
        givenOrderWith(product);

        orderService.processOrder(ORDER_ID);

        assertThat(product.getAvailable()).isEqualTo(29);
        then(productRepository).should().save(product);
    }

    @ParameterizedTest
    @CsvSource({
            "0, -10, 50",   // out of stock
            "30, 30, 90",   // season not started
            "30, -90, -30", // season over
            "30, 0, 50",    // season starts today
            "30, -50, 0"    // season ends today
    })
    void shouldDelegateSeasonalProductThatCannotBeSold(int available, int daysUntilStart, int daysUntilEnd) {
        Product product = seasonalProduct(available, TODAY.plusDays(daysUntilStart), TODAY.plusDays(daysUntilEnd));
        givenOrderWith(product);

        orderService.processOrder(ORDER_ID);

        then(productService).should().handleSeasonalProduct(product);
        then(productRepository).shouldHaveNoInteractions();
    }

    @Test
    void shouldDecrementStockWhenExpirableProductIsNotExpired() {
        Product product = expirableProduct(30, TODAY.plusDays(10));
        givenOrderWith(product);

        orderService.processOrder(ORDER_ID);

        assertThat(product.getAvailable()).isEqualTo(29);
        then(productRepository).should().save(product);
    }

    @ParameterizedTest
    @CsvSource({
            "30, -2", // expired
            "30, 0",  // expires today
            "0, 10"   // out of stock
    })
    void shouldDelegateExpirableProductThatCannotBeSold(int available, int daysUntilExpiry) {
        Product product = expirableProduct(available, TODAY.plusDays(daysUntilExpiry));
        givenOrderWith(product);

        orderService.processOrder(ORDER_ID);

        then(productService).should().handleExpiredProduct(product);
        then(productRepository).shouldHaveNoInteractions();
    }

    private void givenOrderWith(Product... products) {
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(new Order(ORDER_ID, Set.of(products))));
    }

    private static Product normalProduct(int available, int leadTime) {
        return new Product(null, leadTime, available, ProductType.NORMAL, "USB Cable", null, null, null);
    }

    private static Product seasonalProduct(int available, LocalDate seasonStart, LocalDate seasonEnd) {
        return new Product(null, 15, available, ProductType.SEASONAL, "Watermelon", null, seasonStart, seasonEnd);
    }

    private static Product expirableProduct(int available, LocalDate expiryDate) {
        return new Product(null, 15, available, ProductType.EXPIRABLE, "Milk", expiryDate, null, null);
    }
}
