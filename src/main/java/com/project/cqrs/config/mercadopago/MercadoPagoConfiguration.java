package com.project.cqrs.config.mercadopago;

import com.mercadopago.MercadoPagoConfig;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MercadoPagoConfiguration {

    @Value("${mp.access-token}")
    private String accessToken;

    @Value("${mp.public-key}")
    private String publicKey;

    @PostConstruct
    public void init() {
        MercadoPagoConfig.setAccessToken(accessToken);
    }

    public String getPublicKey() {
        return publicKey;
    }
}
