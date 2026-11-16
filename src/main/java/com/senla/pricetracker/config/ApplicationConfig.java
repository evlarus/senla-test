package com.senla.pricetracker.config;

import com.senla.pricetracker.service.Sleeper;
import com.senla.pricetracker.service.TrendCalculator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Clock;

@Configuration
public class ApplicationConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public Sleeper sleeper() {
        return Thread::sleep;
    }

    @Bean
    public TrendCalculator trendCalculator(TrendProperties trendProperties) {
        return new TrendCalculator(trendProperties.flatThreshold());
    }

    @Bean
    public RestClient binanceRestClient(RestClient.Builder builder, BinanceProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        return builder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
