// filepath: /backend/src/main/java/com/example/app/shared/email/EmailServiceImpl.java
package com.example.app.shared.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.io.UnsupportedEncodingException;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private static final String DEFAULT_BRAND_NAME = "Fulmintis";

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromAddress;

    // Nama produk yang tampil di subjek, isi email, dan nama pengirim. Default
    // ada di sini (bukan di application.properties) supaya rename produk tidak
    // butuh perubahan file konfigurasi. Satu tempat: ganti nilai ini kalau
    // nama produk berubah lagi.
    @Value("${app.brand.name:" + DEFAULT_BRAND_NAME + "}")
    private String brandName;

    @Override
    public void sendVerificationEmail(String toEmail, String verificationLink) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

            helper.setFrom(buildFromAddress());
            helper.setTo(toEmail);
            helper.setSubject("Verifikasi Akun " + brand() + " Kamu");
            helper.setText(buildHtmlBody(verificationLink), true);

            mailSender.send(message);
        } catch (MessagingException e) {
            // Sengaja tidak dilempar sebagai exception ke caller supaya proses
            // register tetap sukses meskipun pengiriman email gagal (misal SMTP
            // down). User tetap bisa minta kirim ulang link verifikasi nanti.
            log.error("Gagal mengirim email verifikasi ke {}: {}", toEmail, e.getMessage());
        }
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

            helper.setFrom(buildFromAddress());
            helper.setTo(toEmail);
            helper.setSubject("Reset Password Akun " + brand() + " Kamu");
            helper.setText(buildResetPasswordHtmlBody(resetLink), true);

            mailSender.send(message);
        } catch (MessagingException e) {
            log.error("Gagal mengirim email reset password ke {}: {}", toEmail, e.getMessage());
        }
    }

    /** Nama produk; kalau properti dikosongkan, kembali ke default (bukan email tanpa nama). */
    private String brand() {
        return (brandName == null || brandName.isBlank()) ? DEFAULT_BRAND_NAME : brandName.trim();
    }

    /**
     * Alamat pengirim dari {@code app.mail.from} (env MAIL_FROM). Boleh berformat
     * {@code Fulmintis <no-reply@domain.com>}; kalau yang diisi hanya alamat
     * telanjang, nama produk dipakai sebagai nama pengirim sehingga inbox
     * penerima menampilkan "Fulmintis", bukan alamat email mentah.
     */
    private InternetAddress buildFromAddress() throws AddressException {
        InternetAddress[] parsed = InternetAddress.parse(fromAddress);
        if (parsed.length != 1) {
            throw new AddressException("app.mail.from harus berisi tepat satu alamat", fromAddress);
        }

        InternetAddress from = parsed[0];
        if (from.getPersonal() == null || from.getPersonal().isBlank()) {
            try {
                from.setPersonal(brand(), "UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException("UTF-8 tidak didukung JVM ini.", e); // tidak mungkin terjadi
            }
        }
        return from;
    }

    // Header teks "Fulmintis" sengaja TEKS, bukan gambar: banyak klien email
    // (termasuk Gmail) memblokir logo SVG, dan gambar butuh URL publik absolut.
    // Nama dari konfigurasi di-escape sebelum masuk ke HTML.
    private String buildHtmlBody(String verificationLink) {
        String brand = HtmlUtils.htmlEscape(brand());
        return """
                <div style="font-family: sans-serif; max-width: 480px; margin: 0 auto;">
                    <p style="margin: 0 0 24px; font-size: 20px; font-weight: 700; letter-spacing: 0.5px; color:#111827;">%1$s</p>
                    <h2>Verifikasi Akun %1$s Kamu</h2>
                    <p>Terima kasih sudah mendaftar di %1$s. Klik tombol di bawah untuk verifikasi akun kamu:</p>
                    <p style="margin: 24px 0;">
                        <a href="%2$s"
                           style="background:#111827;color:#ffffff;padding:12px 24px;
                                  border-radius:8px;text-decoration:none;display:inline-block;">
                            Verifikasi Akun
                        </a>
                    </p>
                    <p>Atau salin link berikut ke browser kamu:</p>
                    <p style="word-break: break-all; color:#6b7280;">%2$s</p>
                    <p style="color:#9ca3af; font-size: 12px; margin-top:32px;">
                        Link ini berlaku selama 24 jam. Kalau kamu tidak merasa mendaftar di %1$s,
                        abaikan email ini.
                    </p>
                </div>
                """.formatted(brand, verificationLink);
    }

    private String buildResetPasswordHtmlBody(String resetLink) {
        String brand = HtmlUtils.htmlEscape(brand());
        return """
                <div style="font-family: sans-serif; max-width: 480px; margin: 0 auto;">
                    <p style="margin: 0 0 24px; font-size: 20px; font-weight: 700; letter-spacing: 0.5px; color:#111827;">%1$s</p>
                    <h2>Reset Password Akun %1$s Kamu</h2>
                    <p>Ada permintaan reset password untuk akun %1$s kamu. Klik tombol di bawah
                       untuk membuat password baru:</p>
                    <p style="margin: 24px 0;">
                        <a href="%2$s"
                           style="background:#111827;color:#ffffff;padding:12px 24px;
                                  border-radius:8px;text-decoration:none;display:inline-block;">
                            Reset Password
                        </a>
                    </p>
                    <p>Atau salin link berikut ke browser kamu:</p>
                    <p style="word-break: break-all; color:#6b7280;">%2$s</p>
                    <p style="color:#9ca3af; font-size: 12px; margin-top:32px;">
                        Link ini berlaku selama 60 menit. Kalau kamu tidak merasa
                        meminta reset password, abaikan email ini -- password kamu tidak
                        akan berubah.
                    </p>
                </div>
                """.formatted(brand, resetLink);
    }
}
