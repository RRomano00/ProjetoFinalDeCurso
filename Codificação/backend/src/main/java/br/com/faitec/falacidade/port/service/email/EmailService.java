package br.com.faitec.falacidade.port.service.email;

import br.com.faitec.falacidade.domain.UserModel;

public interface EmailService {
    void sendPasswordResetEmail(String toEmail, String code);
    void sendWelcomeEmail(String toEmail, String fullname);

    void sendStaffWelcomeEmail(String toEmail, String fullname, UserModel.UserRole role, String city);
    void sendMfaCodeEmail(String toEmail, String code);
    void sendMfaDeactivationEmail(String toEmail, String code);
    void sendContactNotification(String toEmail, String senderName, String senderEmail,
                                 String subject, String message);

    void sendOccurrenceCreatedEmail(String toEmail, String fullname, String protocol, String title);

    void sendOccurrenceForwardEmail(String toEmail, String departmentName,
                                    br.com.faitec.falacidade.domain.dto.occurrence.GetOccurrenceDto occurrence);

    void sendAccountChangedEmail(String toEmail, String fullname, java.util.List<String> changes,
                                 String changedByName, String changedByEmail);

    void sendStatusChangeEmail(String toEmail, String fullname, String protocol,
                               String newStatus, String message);
}
