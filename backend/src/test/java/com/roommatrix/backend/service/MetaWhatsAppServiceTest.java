package com.roommatrix.backend.service;

import com.roommatrix.backend.exception.WhatsAppDeliveryException;
import com.roommatrix.backend.service.impl.MetaWhatsAppService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class MetaWhatsAppServiceTest {

    private MockRestServiceServer mockServer;
    private MetaWhatsAppService metaWhatsAppService;
    private RestClient restClient;

    private static final String PHONE_NUMBER_ID = "104928129381029";
    private static final String ACCESS_TOKEN = "EAABtesttoken123456789";
    private static final String TEMPLATE_NAME = "roommatrix_otp_verification";

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://graph.facebook.com/v21.0");

        mockServer = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();

        metaWhatsAppService = new MetaWhatsAppService(
                PHONE_NUMBER_ID,
                ACCESS_TOKEN,
                TEMPLATE_NAME,
                "en_US",
                false,
                restClient
        );
    }

    // ─── PHONE NUMBER NORMALIZATION TESTS ─────────────────────────────────────

    @Test
    @DisplayName("Should normalize 10-digit Indian number by prepending country code 91")
    void formatRecipientPhoneNumber_10Digits_Prepends91() {
        String formatted = metaWhatsAppService.formatRecipientPhoneNumber("9876543210");
        assertThat(formatted).isEqualTo("919876543210");
    }

    @Test
    @DisplayName("Should strip spaces, hyphens, and plus signs from phone number")
    void formatRecipientPhoneNumber_WithFormatting_StripsNonDigits() {
        String formatted = metaWhatsAppService.formatRecipientPhoneNumber("+91 98765-43210");
        assertThat(formatted).isEqualTo("919876543210");
    }

    // ─── TEMPLATE PAYLOAD TESTS ───────────────────────────────────────────────

    @Test
    @DisplayName("Should build template payload with body OTP parameter")
    void buildTemplatePayload_StandardBody() {
        Map<String, Object> payload = metaWhatsAppService.buildTemplatePayload("919876543210", "582910");

        assertThat(payload.get("messaging_product")).isEqualTo("whatsapp");
        assertThat(payload.get("to")).isEqualTo("919876543210");
        assertThat(payload.get("type")).isEqualTo("template");

        @SuppressWarnings("unchecked")
        Map<String, Object> template = (Map<String, Object>) payload.get("template");
        assertThat(template.get("name")).isEqualTo(TEMPLATE_NAME);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> components = (List<Map<String, Object>>) template.get("components");
        assertThat(components).hasSize(1);
        assertThat(components.get(0).get("type")).isEqualTo("body");
    }

    @Test
    @DisplayName("Should include button component when includeButton is true")
    void buildTemplatePayload_WithButton() {
        MetaWhatsAppService serviceWithButton = new MetaWhatsAppService(
                PHONE_NUMBER_ID, ACCESS_TOKEN, TEMPLATE_NAME, "en_US", true, restClient
        );

        Map<String, Object> payload = serviceWithButton.buildTemplatePayload("919876543210", "582910");

        @SuppressWarnings("unchecked")
        Map<String, Object> template = (Map<String, Object>) payload.get("template");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> components = (List<Map<String, Object>>) template.get("components");

        assertThat(components).hasSize(2);
        assertThat(components.get(1).get("type")).isEqualTo("button");
    }

    // ─── CREDENTIAL VALIDATION TESTS ──────────────────────────────────────────

    @Test
    @DisplayName("Should throw WhatsAppDeliveryException if PHONE_NUMBER_ID is missing")
    void sendOtp_MissingPhoneNumberId_ThrowsException() {
        MetaWhatsAppService invalidService = new MetaWhatsAppService(
                "", ACCESS_TOKEN, TEMPLATE_NAME, "en_US", false, restClient
        );

        assertThatThrownBy(() -> invalidService.sendOtp("9876543210", "123456"))
                .isInstanceOf(WhatsAppDeliveryException.class)
                .hasMessageContaining("Phone Number ID is missing");
    }

    @Test
    @DisplayName("Should throw WhatsAppDeliveryException if ACCESS_TOKEN is missing")
    void sendOtp_MissingAccessToken_ThrowsException() {
        MetaWhatsAppService invalidService = new MetaWhatsAppService(
                PHONE_NUMBER_ID, null, TEMPLATE_NAME, "en_US", false, restClient
        );

        assertThatThrownBy(() -> invalidService.sendOtp("9876543210", "123456"))
                .isInstanceOf(WhatsAppDeliveryException.class)
                .hasMessageContaining("Access Token is missing");
    }

    // ─── API DISPATCH & HTTP MOCK TESTS ───────────────────────────────────────

    @Test
    @DisplayName("Should successfully dispatch OTP to Meta Graph API endpoint with Bearer auth")
    void sendOtp_Success() {
        String expectedUri = "https://graph.facebook.com/v21.0/" + PHONE_NUMBER_ID + "/messages";

        mockServer.expect(requestTo(expectedUri))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(
                        """
                        {
                          "messaging_product": "whatsapp",
                          "contacts": [{"input": "919876543210", "wa_id": "919876543210"}],
                          "messages": [{"id": "wamid.HBgMOTE5ODc2NTQzMjEwFQIAERgSMTA0OTI4MTI5MzgxMDI5AA=="}]
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        // Act
        metaWhatsAppService.sendOtp("9876543210", "582910");

        // Assert
        mockServer.verify();
    }

    @Test
    @DisplayName("Should throw WhatsAppDeliveryException when Meta API returns an error")
    void sendOtp_MetaApiError_ThrowsWhatsAppDeliveryException() {
        String expectedUri = "https://graph.facebook.com/v21.0/" + PHONE_NUMBER_ID + "/messages";

        mockServer.expect(requestTo(expectedUri))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withBadRequest().body(
                        """
                        {
                          "error": {
                            "message": "(#132001) Template name does not exist in the translated language",
                            "type": "OAuthException",
                            "code": 132001
                          }
                        }
                        """
                ));

        // Act & Assert
        assertThatThrownBy(() -> metaWhatsAppService.sendOtp("9876543210", "582910"))
                .isInstanceOf(WhatsAppDeliveryException.class)
                .hasMessageContaining("Failed to deliver WhatsApp OTP via Meta Cloud API")
                .hasMessageContaining("Template name does not exist");

        mockServer.verify();
    }
}
