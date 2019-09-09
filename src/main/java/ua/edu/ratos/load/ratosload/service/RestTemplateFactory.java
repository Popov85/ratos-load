package ua.edu.ratos.load.ratosload.service;

import org.springframework.boot.web.client.ClientHttpRequestFactorySupplier;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Component
public class RestTemplateFactory {

    public RestTemplate getRestTemplateStart(String login, String password) {
        return new RestTemplateBuilder()
                .requestFactory(new ClientHttpRequestFactorySupplier())
                .basicAuthentication(login, password)
                .setConnectTimeout(Duration.ofMinutes(15))
                .setReadTimeout(Duration.ofMinutes(15))
                .build();
    }
}
