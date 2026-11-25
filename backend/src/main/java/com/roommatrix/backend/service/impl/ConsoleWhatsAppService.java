package com.roommatrix.backend.service.impl;

import com.roommatrix.backend.service.WhatsAppService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ConsoleWhatsAppService implements WhatsAppService {

    private static final Logger log = LoggerFactory.getLogger(ConsoleWhatsAppService.class);

    @Override
    public void sendOtp(String mobileNumber, String otp) {
        log.info("==========================================================");
        log.info("[WHATSAPP OTP] Sent to +91 {}: Your RoomMatrix verification OTP is: {}", mobileNumber, otp);
        log.info("[WHATSAPP OTP] This OTP is valid for 10 minutes.");
        log.info("==========================================================");
    }
}
