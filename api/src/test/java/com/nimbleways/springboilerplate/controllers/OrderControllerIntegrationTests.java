package com.nimbleways.springboilerplate.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.nimbleways.springboilerplate.dto.enums.ProductType;
import com.nimbleways.springboilerplate.entities.Order;
import com.nimbleways.springboilerplate.entities.Product;
import com.nimbleways.springboilerplate.repositories.OrderRepository;
import com.nimbleways.springboilerplate.repositories.ProductRepository;
import com.nimbleways.springboilerplate.services.implementations.NotificationService;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerIntegrationTests {

    private static final String URL = "/orders/{orderId}/processOrder";
    private static final LocalDate TODAY = LocalDate.now();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private ProductRepository productRepository;
    @MockBean
    private NotificationService notificationService;

    @Test
    void shouldReturnOrderId() throws Exception {
        Order order = saveOrderWith(normalProduct(30, 15));

        mockMvc.perform(post(URL, order.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(order.getId()));
    }

    @Test
    void shouldSaveNewStockOfEachProduct() throws Exception {
        Product normal = normalProduct(30, 15);
        Product seasonal = seasonalProduct(30, 15, TODAY.minusDays(10), TODAY.plusDays(50));
        Product expirable = expirableProduct(30, TODAY.plusDays(10));
        Order order = saveOrderWith(normal, seasonal, expirable);

        processOrder(order);

        assertThat(reload(normal).getAvailable()).isEqualTo(29);
        assertThat(reload(seasonal).getAvailable()).isEqualTo(29);
        assertThat(reload(expirable).getAvailable()).isEqualTo(29);
        then(notificationService).shouldHaveNoInteractions();
    }

    @Test
    void shouldNotifyDelayWhenProductIsOutOfStock() throws Exception {
        Order order = saveOrderWith(normalProduct(0, 10));

        processOrder(order);

        then(notificationService).should().sendDelayNotification(10, "USB Cable");
    }

    @Test
    void shouldNotifyOutOfStockWhenRestockArrivesAfterSeasonEnd() throws Exception {
        Product product = seasonalProduct(0, 30, TODAY.minusDays(10), TODAY.plusDays(10));
        Order order = saveOrderWith(product);

        processOrder(order);

        assertThat(reload(product).getAvailable()).isZero();
        then(notificationService).should().sendOutOfStockNotification("Watermelon");
    }

    @Test
    void shouldEmptyStockAndNotifyWhenProductIsExpired() throws Exception {
        LocalDate expiryDate = TODAY.minusDays(2);
        Product product = expirableProduct(6, expiryDate);
        Order order = saveOrderWith(product);

        processOrder(order);

        assertThat(reload(product).getAvailable()).isZero();
        then(notificationService).should().sendExpirationNotification("Milk", expiryDate);
    }

    @Test
    void shouldReturn404WhenOrderDoesNotExist() throws Exception {
        mockMvc.perform(post(URL, -1L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order -1 not found"));
    }

    @Test
    void shouldReturn400WhenOrderIdIsNotANumber() throws Exception {
        mockMvc.perform(post(URL, "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value 'abc' for parameter 'orderId'"));
    }

    @Test
    void shouldReturn405WhenCalledWithGet() throws Exception {
        mockMvc.perform(get(URL, 1L))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void shouldReturn500WhenSomethingUnexpectedHappens() throws Exception {
        // null stock makes the processing crash
        Order order = saveOrderWith(new Product(null, 15, null, ProductType.NORMAL, "Broken", null, null, null));

        mockMvc.perform(post(URL, order.getId()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    private void processOrder(Order order) throws Exception {
        mockMvc.perform(post(URL, order.getId())).andExpect(status().isOk());
    }

    private Order saveOrderWith(Product... products) {
        List<Product> savedProducts = productRepository.saveAll(List.of(products));
        return orderRepository.save(new Order(null, new HashSet<>(savedProducts)));
    }

    private Product reload(Product product) {
        return productRepository.findById(product.getId()).orElseThrow();
    }

    private static Product normalProduct(int available, int leadTime) {
        return new Product(null, leadTime, available, ProductType.NORMAL, "USB Cable", null, null, null);
    }

    private static Product seasonalProduct(int available, int leadTime, LocalDate seasonStart, LocalDate seasonEnd) {
        return new Product(null, leadTime, available, ProductType.SEASONAL, "Watermelon", null, seasonStart,
                seasonEnd);
    }

    private static Product expirableProduct(int available, LocalDate expiryDate) {
        return new Product(null, 15, available, ProductType.EXPIRABLE, "Milk", expiryDate, null, null);
    }
}
