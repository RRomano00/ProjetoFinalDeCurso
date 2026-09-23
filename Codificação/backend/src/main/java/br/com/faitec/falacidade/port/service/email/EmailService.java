package br.com.faitec.falacidade.port.service.email;

import br.com.faitec.falacidade.domain.UserModel;

public interface EmailService {
    /** Envia o código de recuperação de senha (o mesmo que a tela pede de volta). */
    void sendPasswordResetEmail(String toEmail, String code);
    void sendWelcomeEmail(String toEmail, String fullname);

    /**
     * Avisa a pessoa de que um administrador criou uma conta de Funcionário ou
     * Administrador para ela. O município vai no corpo porque é ele que define
     * quais ocorrências a conta enxerga.
     */
    void sendStaffWelcomeEmail(String toEmail, String fullname, UserModel.UserRole role, String city);
    /** Envia o código de verificação em duas etapas (MFA por e-mail). */
    void sendMfaCodeEmail(String toEmail, String code);
    void sendMfaDeactivationEmail(String toEmail, String code);
    /** Notifica a equipe (toEmail) sobre uma nova mensagem do formulário de contato. */
    void sendContactNotification(String toEmail, String senderName, String senderEmail,
                                 String subject, String message);

    /** Confirma o registro da ocorrência e agradece o comprometimento com a cidade. */
    void sendOccurrenceCreatedEmail(String toEmail, String fullname, String protocol, String title);

    /**
     * RF22: encaminha a ocorrência ao departamento responsável. O corpo leva
     * apenas os dados do problema — nenhum dado pessoal do autor — e as
     * fotografias vão anexadas. Diferente dos demais, este envio é síncrono:
     * o funcionário precisa saber se o encaminhamento saiu antes de a
     * ocorrência mudar de estado.
     */
    void sendOccurrenceForwardEmail(String toEmail, String departmentName,
                                    br.com.faitec.falacidade.domain.dto.occurrence.GetOccurrenceDto occurrence);

    /**
     * Avisa o titular de que a sua conta foi alterada por outra pessoa, com a
     * relação do que mudou e a identificação de quem mudou. A alteração feita
     * pelo próprio titular não passa por aqui — ele acabou de fazê-la.
     */
    void sendAccountChangedEmail(String toEmail, String fullname, java.util.List<String> changes,
                                 String changedByName, String changedByEmail);

    /** Notifica o autor sobre a mudança de status da ocorrência (com mensagem opcional do funcionário). */
    void sendStatusChangeEmail(String toEmail, String fullname, String protocol,
                               String newStatus, String message);
}
