// filepath: /backend/src/test/java/com/example/app/shared/email/EmailServiceImplTest.java
package com.example.app.shared.email;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailServiceImplTest {

    private MimeMessage sent;
    private EmailServiceImpl emailService;

    @BeforeEach
    void setUp() {
        // Pengirim palsu: menangkap pesan, tidak membuka koneksi SMTP.
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

    private String sentHtml() throws Exception {
        return (String) sent.getContent();
    }

    @Test
    void verificationEmailMentionsBrandInSubjectAndBody() throws Exception {
        emailService.sendVerificationEmail("user@example.com", "https://x.test/verify?token=abc");

        assertEquals("Verifikasi Akun Fulmintis Kamu", sent.getSubject());
        String html = sentHtml();
        assertTrue(html.contains("Verifikasi Akun Fulmintis Kamu"));
        assertTrue(html.contains("mendaftar di Fulmintis"));
        assertTrue(html.contains("https://x.test/verify?token=abc"));
        assertFalse(html.contains("%1$s") || html.contains("%2$s"));
    }

    @Test
    void resetEmailMentionsBrandInSubjectAndBody() throws Exception {
        emailService.sendPasswordResetEmail("user@example.com", "https://x.test/reset?token=abc");

        assertEquals("Reset Password Akun Fulmintis Kamu", sent.getSubject());
        String html = sentHtml();
        assertTrue(html.contains("akun Fulmintis kamu"));
        assertTrue(html.contains("https://x.test/reset?token=abc"));
    }

    @Test
    void bareFromAddressGetsBrandAsSenderName() throws Exception {
        emailService.sendVerificationEmail("user@example.com", "https://x.test/v");

        InternetAddress from = (InternetAddress) sent.getFrom()[0];
        assertEquals("Fulmintis", from.getPersonal());
        assertEquals("no-reply@example.com", from.getAddress());
    }

    @Test
    void explicitSenderNameInFromAddressIsKept() throws Exception {
        ReflectionTestUtils.setField(emailService, "fromAddress", "Tim Fulmintis <no-reply@example.com>");

        emailService.sendVerificationEmail("user@example.com", "https://x.test/v");

        InternetAddress from = (InternetAddress) sent.getFrom()[0];
        assertEquals("Tim Fulmintis", from.getPersonal());
    }

    @Test
    void blankBrandFallsBackToDefaultAndBrandIsHtmlEscaped() throws Exception {
        ReflectionTestUtils.setField(emailService, "brandName", "  ");
        emailService.sendVerificationEmail("user@example.com", "https://x.test/v");
        assertEquals("Verifikasi Akun Fulmintis Kamu", sent.getSubject());

        ReflectionTestUtils.setField(emailService, "brandName", "A<b>&Co");
        emailService.sendVerificationEmail("user@example.com", "https://x.test/v");
        assertFalse(sentHtml().contains("<b>"));
        assertTrue(sentHtml().contains("A&lt;b&gt;&amp;Co"));
    }
}
