package com.ticketing.service;

import com.ticketing.dto.MlPredictionResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

@Component
public class MlClassificationClient {
    private final RestClient restClient;

    public MlClassificationClient(
            RestClient.Builder builder,
            @Value("${ml.service-url:http://localhost:8000}") String serviceUrl,
            @Value("${ml.connect-timeout-ms:1000}") int connectTimeoutMs,
            @Value("${ml.read-timeout-ms:3000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.restClient = builder.baseUrl(serviceUrl).requestFactory(factory).build();
    }

    public MlPredictionResponse predict(String text) {
        try {
            MlPredictionResponse response = restClient.post()
                    .uri("/predict")
                    .body(new PredictionRequest(text))
                    .retrieve()
                    .body(MlPredictionResponse.class);
            if (response == null || response.category() == null || response.category().isBlank()) {
                throw new MlServiceException("ML service returned an empty prediction");
            }
            return response;
        } catch (RestClientException exception) {
            throw new MlServiceException("ML service request failed", exception);
        }
    }

    private record PredictionRequest(String text) {
    }

    public static class MlServiceException extends RuntimeException {
        public MlServiceException(String message) {
            super(message);
        }

        public MlServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
