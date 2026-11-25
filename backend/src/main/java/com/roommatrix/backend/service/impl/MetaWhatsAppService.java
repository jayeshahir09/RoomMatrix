package com.roommatrix.backend.service.impl;

import com.roommatrix.backend.exception.WhatsAppDeliveryException;
import com.roommatrix.backend.service.WhatsAppService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Official Meta (Facebook) WhatsApp Cloud API implementation of WhatsAppService.
 *
 * Sends pre-approved Authentication / OTP templates via Meta's Graph API.
 * Endpoint: POST https://graph.facebook.com/{version}/{phoneNumberId}/messages
 */
@Service
@ConditionalOnProperty(name = "whatsapp.provider", havingValue = "meta")
public class MetaWhatsAppService implements WhatsAppService {

    private static final Logger log = LoggerFactory.getLogger(MetaWhatsAppService.class);

    private final String phoneNumberId;
    private final String accessToken;
    private final String templateName;
    private final String languageCode;
    private final boolean includeButton;
    private final RestClient restClient;

    public MetaWhatsAppService(
            @Value("${whatsapp.phone-number-id:}") String phoneNumberId,
            @Value("${whatsapp.access-token:}") String accessToken,
            @Value("${whatsapp.template-name:roommatrix_otp_verification}") String templateName,
            @Value("${whatsapp.api-version:v21.0}") String apiVersion,
            @Value("${whatsapp.language-code:en_US}") String languageCode,
            @Value("${whatsapp.include-button:false}") boolean includeButton,
            RestClient.Builder restClientBuilder) {
        this.phoneNumberId = phoneNumberId;
        this.accessToken = accessToken;
        this.templateName = templateName;
        this.languageCode = languageCode;
        this.includeButton = includeButton;
        this.restClient = restClientBuilder
                .baseUrl("https://graph.facebook.com/" + apiVersion)
                .build();
    }

    /**
     * Testing constructor allowing injection of a pre-configured RestClient.
     */
    public MetaWhatsAppService(
            String phoneNumberId,
            String accessToken,
            String templateName,
            String languageCode,
            boolean includeButton,
            RestClient restClient) {
        this.phoneNumberId = phoneNumberId;
        this.accessToken = accessToken;
        this.templateName = templateName;
        this.languageCode = languageCode;
        this.includeButton = includeButton;
        this.restClient = restClient;
    }

    @Override
    public void sendOtp(String mobileNumber, String otp) {
        validateCredentials();

        String recipientPhone = formatRecipientPhoneNumber(mobileNumber);
        String endpoint = "/" + phoneNumberId + "/messages";
        Map<String, Object> payload = buildTemplatePayload(recipientPhone, otp);

        try {
            log.info("Sending WhatsApp OTP via Meta Cloud API to recipient: +{}", recipientPhone);

            ResponseEntity<String> response = restClient.post()
                    .uri(endpoint)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toEntity(String.class);

            log.info("WhatsApp OTP sent successfully via Meta Cloud API. Status: {}", response.getStatusCode());
            log.debug("Meta API Response Body: {}", response.getBody());
        } catch (RestClientResponseException ex) {
            log.error("Meta WhatsApp Cloud API rejected request with status {} and body: {}",
                    ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new WhatsAppDeliveryException(
                    "Failed to deliver WhatsApp OTP via Meta Cloud API: " + ex.getResponseBodyAsString(), ex);
        } catch (Exception ex) {
            log.error("Network or unexpected error while sending WhatsApp OTP via Meta Cloud API", ex);
            throw new WhatsAppDeliveryException(
                    "Unexpected error delivering WhatsApp OTP: " + ex.getMessage(), ex);
        }
    }

    private void validateCredentials() {
        if (phoneNumberId == null || phoneNumberId.isBlank()) {
            throw new WhatsAppDeliveryException(
                    "Meta WhatsApp Cloud API Phone Number ID is missing. Please configure WHATSAPP_PHONE_NUMBER_ID.");
        }
        if (accessToken == null || accessToken.isBlank()) {
            throw new WhatsAppDeliveryException(
                    "Meta WhatsApp Cloud API Access Token is missing. Please configure WHATSAPP_ACCESS_TOKEN.");
        }
    }

    /**
     * Normalizes a mobile number to international format (without + symbol).
     * If 10 digits (e.g. 9876543210), prepends India country code (91 -> 919876543210).
     */
    public String formatRecipientPhoneNumber(String rawNumber) {
        if (rawNumber == null) {
            return "";
        }
        String digitsOnly = rawNumber.replaceAll("\\D", "");
        if (digitsOnly.length() == 10) {
            return "91" + digitsOnly;
        }
        return digitsOnly;
    }

    /**
     * Builds Meta WhatsApp Cloud API template message payload.
     */
    public Map<String, Object> buildTemplatePayload(String recipientPhone, String otp) {
        List<Map<String, Object>> components = new ArrayList<>();

        // Body component with OTP variable {{1}}
        components.add(Map.of(
                "type", "body",
                "parameters", List.of(
                        Map.of(
                                "type", "text",
                                "text", otp
                        )
                )
        ));

        // Optional copy-code button component
        if (includeButton) {
            components.add(Map.of(
                    "type", "button",
                    "sub_type", "url",
                    "index", "0",
                    "parameters", List.of(
                            Map.of(
                                    "type", "text",
                                    "text", otp
                            )
                    )
            ));
        }

        Map<String, Object> template = Map.of(
                "name", templateName,
                "language", Map.of("code", languageCode),
                "components", components
        );

        return Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", recipientPhone,
                "type", "template",
                "template", template
        );
    }
}
