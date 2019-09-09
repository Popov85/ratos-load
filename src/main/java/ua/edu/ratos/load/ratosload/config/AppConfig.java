package ua.edu.ratos.load.ratosload.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;


@Configuration
public class AppConfig {

    /**
     * https://stackoverflow.com/questions/32832006/spring-resttemplate-readtimeout-property-not-working-properly-strange-issue
     * @return
     */
    @Bean
    public ClientHttpRequestFactory clientHttpRequestFactory() {
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
        requestFactory.setReadTimeout(900 * 1000);
        requestFactory.setConnectionRequestTimeout(900*1000);
        requestFactory.setConnectTimeout(900*1000);
        return requestFactory;
    }

    @Bean
    @Qualifier("next")
    public RestTemplate restTemplate() {
        RestTemplate result = new RestTemplate(clientHttpRequestFactory());
        return result;
    }

}
