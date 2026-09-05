package com.nimbleways.springboilerplate.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nimbleways.springboilerplate.contollers.OrderController;
import com.nimbleways.springboilerplate.dto.product.ProcessOrderResponse;
import com.nimbleways.springboilerplate.services.OrderService;
import com.nimbleways.springboilerplate.utils.Annotations.UnitTest;

@UnitTest
@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderService orderService;
    @InjectMocks
    private OrderController orderController;

    @Test
    void shouldReturnServiceResponse() {
        ProcessOrderResponse expected = new ProcessOrderResponse(1L);
        given(orderService.processOrder(1L)).willReturn(expected);

        ProcessOrderResponse response = orderController.processOrder(1L);

        assertThat(response).isEqualTo(expected);
    }
}
