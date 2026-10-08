// filepath: /backend/src/test/java/com/example/app/shared/email/EmailServiceAdminInvitationTest.java
package com.example.app.shared.email;

import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailServiceAdminInvitationTest {

    private MimeMessage sent;
    private EmailServiceImpl emailService;

    @BeforeEach
    void setUp() {
        JavaMailSenderImpl fakeSender = new JavaMailSenderImpl() {
            @Override
            public void send(MimeMessage mimeMessage) {
                sent = mimeMessage;
            }
        };
        emailService = new EmailServiceImpl(fakeSender);
        ReflectionTestUtils.setField(emailService, "fromAddress", "no-reply@example.com");
        ReflectionTestUtils.setField(emailService, "brandName", "Fulmintis");
    }

    @Test
    void invitationEmailHasLinkInviterAndBrand() throws Exception {
        emailService.sendAdminInvitationEmail(
                "sari@example.com", "Sari", "http://localhost:5173/admin/accept-invitation?token=abc", "budi@example.com");

        assertEquals("sari@example.com", sent.getRecipients(Message.RecipientType.TO)[0].toString());
        assertEquals("Undangan Admin Fulmintis", sent.getSubject());
        String html = (String) sent.getContent();
        assertTrue(html.contains("http://localhost:5173/admin/accept-invitation?token=abc"));
        assertTrue(html.contains("budi@example.com"));
        assertTrue(html.contains("Sari"));
    }

    @Test
    void nameAndInviterAreHtmlEscaped() throws Exception {
        emailService.sendAdminInvitationEmail(
                "x@example.com", "<script>alert(1)</script>", "http://x.test/a?token=1", "\"><img src=x>");

        String html = (String) sent.getContent();
        assertFalse(html.contains("<script>"));
        assertFalse(html.contains("<img src=x>"));
        assertTrue(html.contains("&lt;script&gt;"));
    }
}
