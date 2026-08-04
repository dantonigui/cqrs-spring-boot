package com.project.cqrs.shared.kafka;

public final class KafkaEventAliases {

    private KafkaEventAliases() {
    }

    // User
    public static final String USER_CREATED = "userCreated";
    public static final String USER_UPDATED = "userUpdated";
    public static final String USER_LOGOUT = "userLogout";

    // Category
    public static final String CATEGORY_CREATED = "categoryCreate";
    public static final String CATEGORY_UPDATED = "categoryUpdate";
    public static final String CATEGORY_DELETED = "categoryDelete";

    // Product
    public static final String PRODUCT_CREATED = "productCreate";
    public static final String PRODUCT_UPDATED = "productUpdate";
    public static final String PRODUCT_DELETED = "productDelete";

    // Order
    public static final String ORDER_CREATED = "orderCreated";
    public static final String ORDER_UPDATED = "orderUpdated";
    public static final String ORDER_DELETED = "orderDeleted";

    // Payment
    public static final String PAYMENT_APPROVED = "paymentApproved";
}