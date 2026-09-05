package com.nimbleways.springboilerplate.services;

import com.nimbleways.springboilerplate.entities.Product;

public interface ProductService {

    void notifyDelay(int leadTime, Product product);

    void handleSeasonalProduct(Product product);

    void handleExpiredProduct(Product product);
}
