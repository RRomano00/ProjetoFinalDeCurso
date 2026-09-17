package br.com.faitec.falacidade.implementation.service.email;

import br.com.faitec.falacidade.domain.UserModel;
import br.com.faitec.falacidade.port.service.email.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Envio de e-mails transacionais do sistema.
 * Todos usam o mesmo layout: marca no topo, conteúdo no cartão branco e
 * rodapé escuro com os contatos e o endereço.
 */
@Service
public class EmailServiceImpl implements EmailService {

    private static final String BRAND_COLOR = "#1F4E79";
    private static final String ADDRESS     = "Santa Rita do Sapucaí - MG";

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.contact-recipient:fala.cidade.faitec@gmail.com}")
    private String contactEmail;

    public EmailServiceImpl(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /** Código de recuperação de senha (válido por 30 min). */
    @Override
    @Async("emailExecutor")
    public void sendPasswordResetEmail(String toEmail, String code) {
        String text =
            "Olá!\n\n" +
            "Recebemos uma solicitação para redefinir sua senha.\n\n" +
            "Seu código de recuperação é: " + code + "\n\n" +
            "Informe esse código na tela \"Esqueci minha senha\" do aplicativo. " +
            "Ele é válido por 30 minutos.\n\n" +
            "Se você não solicitou a redefinição, ignore este e-mail.";

        String content =
            "<h2 style='margin:0 0 16px; font-size:20px; color:#111;'>Recuperação de senha</h2>" +
            "<p style='font-size:15px; color:#333;'>Olá!</p>" +
            "<p style='font-size:15px; color:#333;'>Use o código abaixo na tela " +
            "<strong>Esqueci minha senha</strong> para criar uma nova senha:</p>" +
            "<div style='font-size:30px; font-weight:bold; letter-spacing:6px; color:" + BRAND_COLOR + ";" +
            "            background:#eef4fb; border-radius:8px; padding:16px; margin:20px 0; text-align:center;'>" +
                 code +
            "</div>" +
            "<p style='font-size:13px; color:#777;'>Válido por 30 minutos.</p>" +
            "<p style='font-size:12px; color:#aaa;'>Se você não solicitou a redefinição, ignore este e-mail. " +
            "Sua senha permanece a mesma.</p>";

        send(toEmail, null, "Fala, Cidade! – Código de Recuperação de Senha", withFooter(text), layout(content),
             "Falha ao enviar e-mail de recuperação de senha");
    }

    /** Código de verificação do login em duas etapas (válido por 10 min). */
    @Override
    @Async("emailExecutor")
    public void sendMfaCodeEmail(String toEmail, String code) {
        String text =
            "Seu código de verificação é: " + code + "\n\n" +
            "Ele é válido por 10 minutos. Se você não tentou fazer login, ignore este e-mail.";

        String content =
            "<h2 style='margin:0 0 16px; font-size:20px; color:#111;'>Verificação em duas etapas</h2>" +
            "<p style='font-size:15px; color:#333;'>Use o código abaixo para concluir seu login:</p>" +
            "<div style='font-size:32px; font-weight:bold; letter-spacing:8px; color:" + BRAND_COLOR + ";" +
            "            background:#eef4fb; border-radius:8px; padding:16px; margin:20px 0; text-align:center;'>" +
                 code +
            "</div>" +
            "<p style='font-size:13px; color:#777;'>Válido por 10 minutos. " +
            "Se você não tentou fazer login, ignore este e-mail.</p>";

        send(toEmail, null, "Fala, Cidade! – Código de verificação", withFooter(text), layout(content),
             "Falha ao enviar código de verificação por e-mail");
    }

    /** Código para confirmar a desativação do MFA (com alerta de segurança). */
    @Override
    @Async("emailExecutor")
    public void sendMfaDeactivationEmail(String toEmail, String code) {
        String text =
            "Seu código para desativar a verificação em duas etapas é: " + code + "\n\n" +
            "Ele é válido por 10 minutos. ATENÇÃO: Se você não solicitou esta desativação, " +
            "sua conta pode estar em risco. Altere sua senha imediatamente.";

        String content =
            "<h2 style='margin:0 0 16px; font-size:20px; color:#111;'>Desativação de segurança</h2>" +
            "<p style='font-size:15px; color:#333;'>Use o código abaixo para <strong>desativar</strong> " +
            "a verificação em duas etapas da sua conta:</p>" +
            "<div style='font-size:32px; font-weight:bold; letter-spacing:8px; color:#D9534F;" +
            "            background:#fdf0ef; border-radius:8px; padding:16px; margin:20px 0; text-align:center;'>" +
                 code +
            "</div>" +
            "<p style='font-size:13px; color:#777;'>Válido por 10 minutos.</p>" +
            "<p style='font-size:13px; color:#D9534F; font-weight:bold; border-top:1px solid #eee; padding-top:14px;'>" +
            "Atenção: se você não solicitou esta ação, sua conta pode estar comprometida. Altere sua senha imediatamente.</p>";

        send(toEmail, null, "Fala, Cidade! – Desativação de segurança (MFA)", withFooter(text), layout(content),
             "Falha ao enviar código de desativação do MFA por e-mail");
    }

    /** Notifica a equipe sobre mensagem do formulário de contato (Reply-To = cidadão). */
    @Override
    @Async("emailExecutor")
    public void sendContactNotification(String toEmail, String senderName, String senderEmail,
                                        String subject, String message) {
        String safeSubject = (subject == null || subject.isBlank()) ? "Sem assunto" : subject;

        String text =
            "Nova mensagem pelo formulário de contato.\n\n" +
            "Nome: " + senderName + "\n" +
            "E-mail: " + senderEmail + "\n" +
            "Assunto: " + safeSubject + "\n\n" +
            "Mensagem:\n" + message;

        String content =
            "<h2 style='margin:0 0 16px; font-size:20px; color:#111;'>Nova mensagem de contato</h2>" +
            "<p style='font-size:14px; color:#333; margin:4px 0;'><strong>Nome:</strong> " + senderName + "</p>" +
            "<p style='font-size:14px; color:#333; margin:4px 0;'><strong>E-mail:</strong> " + senderEmail + "</p>" +
            "<p style='font-size:14px; color:#333; margin:4px 0;'><strong>Assunto:</strong> " + safeSubject + "</p>" +
            "<hr style='border:none; border-top:1px solid #eee; margin:18px 0;'>" +
            "<p style='font-size:14px; color:#333; white-space:pre-wrap;'>" + message + "</p>";

        send(toEmail, senderEmail, "Fala, Cidade! – Contato: " + safeSubject, withFooter(text), layout(content),
             "Falha ao enviar notificação de contato por e-mail");
    }

    /** E-mail de boas-vindas após o cadastro. */
    @Override
    @Async("emailExecutor")
    public void sendWelcomeEmail(String toEmail, String fullname) {
        String text =
            "Olá, " + fullname + "!\n\n" +
            "Seu cadastro foi realizado com sucesso.\n" +
            "Agora você pode registrar ocorrências e acompanhar o andamento das suas solicitações.";

        String content =
            "<h2 style='margin:0 0 16px; font-size:20px; color:#111;'>Bem-vindo(a)! 🎉</h2>" +
            "<p style='font-size:15px; color:#333;'>Olá, <strong>" + fullname + "</strong>!</p>" +
            "<p style='font-size:15px; color:#333;'>Seu cadastro foi realizado com sucesso. " +
            "Agora você faz parte da comunidade que está transformando Santa Rita do Sapucaí.</p>" +
            "<p style='font-size:15px; color:#333;'>Com o Fala, Cidade! você pode:</p>" +
            "<ul style='font-size:14px; color:#444; line-height:1.8;'>" +
            "  <li>📍 Registrar ocorrências na cidade com foto e localização</li>" +
            "  <li>🔔 Acompanhar o status das suas ocorrências</li>" +
            "  <li>🤝 Apoiar ocorrências de outros cidadãos</li>" +
            "</ul>";

        send(toEmail, null, "Bem-vindo(a) ao Fala, Cidade!", withFooter(text), layout(content),
             "Falha ao enviar e-mail de boas-vindas");
    }

    /** Conta de Funcionário/Administrador criada por um administrador. */
    @Override
    @Async("emailExecutor")
    public void sendStaffWelcomeEmail(String toEmail, String fullname, UserModel.UserRole role, String city) {
        String name = (fullname == null || fullname.isBlank()) ? "colega" : fullname;
        boolean isAdmin = role == UserModel.UserRole.ADMINISTRATOR;
        String roleLabel = isAdmin ? "Administrador" : "Funcionário";
        String cityLabel = (city == null || city.isBlank()) ? "não informado" : city;

        String permissions = isAdmin
            ? "Painel de gestão com os indicadores de demanda\n" +
              "Todas as ocorrências do sistema\n" +
              "Cadastro de funcionários e administradores\n" +
              "Relatórios de desempenho por categoria"
            : "Ocorrências registradas no seu município\n" +
              "Atualização do status das ocorrências\n" +
              "Histórico e dados dos autores das ocorrências";

        String permissionsHtml = isAdmin
            ? "  <li>📊 Painel de gestão com os indicadores de demanda</li>" +
              "  <li>🗂️ Todas as ocorrências do sistema</li>" +
              "  <li>👥 Cadastro de funcionários e administradores</li>" +
              "  <li>📈 Relatórios de desempenho por categoria</li>"
            : "  <li>🗂️ Ocorrências registradas no seu município</li>" +
              "  <li>🔄 Atualização do status das ocorrências</li>" +
              "  <li>🔍 Histórico e dados dos autores das ocorrências</li>";

        String text =
            "Olá, " + name + "!\n\n" +
            "Um administrador criou uma conta de " + roleLabel + " para você no Fala, Cidade!.\n\n" +
            "Município vinculado: " + cityLabel + "\n" +
            "E-mail de acesso: " + toEmail + "\n\n" +
            "Com esta conta você tem acesso a:\n" + permissions + "\n\n" +
            "Importante: a senha inicial foi definida pelo administrador. " +
            "Troque-a no primeiro acesso.\n" +
            "No primeiro login o sistema pede a configuração obrigatória da " +
            "verificação em duas etapas (2FA).";

        String content =
            "<h2 style='margin:0 0 16px; font-size:20px; color:#111;'>Sua conta de " + roleLabel + " foi criada</h2>" +
            "<p style='font-size:15px; color:#333;'>Olá, <strong>" + name + "</strong>!</p>" +
            "<p style='font-size:15px; color:#333;'>Um administrador criou uma conta de " +
            "<strong>" + roleLabel + "</strong> para você no Fala, Cidade!.</p>" +

            "<div style='background:#eef4fb; border-radius:8px; padding:14px 16px; margin:20px 0;'>" +
            "  <p style='font-size:13px; color:#777; margin:0 0 6px;'>Município vinculado</p>" +
            "  <p style='font-size:17px; font-weight:bold; color:" + BRAND_COLOR + "; margin:0 0 12px;'>" +
                 cityLabel + "</p>" +
            "  <p style='font-size:13px; color:#777; margin:0 0 6px;'>E-mail de acesso</p>" +
            "  <p style='font-size:15px; color:#333; margin:0;'>" + toEmail + "</p>" +
            "</div>" +

            (isAdmin ? "" :
            "<p style='font-size:13px; color:#777; margin:-8px 0 18px;'>" +
            "Você enxerga as ocorrências deste município. Se ele estiver incorreto, " +
            "peça a um administrador para corrigir o cadastro.</p>") +

            "<p style='font-size:15px; color:#333;'>Com esta conta você tem acesso a:</p>" +
            "<ul style='font-size:14px; color:#444; line-height:1.8;'>" + permissionsHtml + "</ul>" +

            "<div style='border-top:1px solid #eee; margin-top:22px; padding-top:16px;'>" +
            "  <p style='font-size:14px; color:#333; margin:0 0 8px;'><strong>Antes do primeiro acesso</strong></p>" +
            "  <p style='font-size:14px; color:#555; margin:0 0 6px;'>" +
            "    A senha inicial foi definida pelo administrador — troque-a no primeiro acesso.</p>" +
            "  <p style='font-size:14px; color:#555; margin:0;'>" +
            "    O sistema pedirá a configuração obrigatória da verificação em duas etapas (2FA).</p>" +
            "</div>";

        send(toEmail, null, "Fala, Cidade! – Sua conta de " + roleLabel + " foi criada",
             withFooter(text), layout(content), "Falha ao enviar e-mail de boas-vindas da equipe");
    }

    /** Confirma o registro da ocorrência e agradece o comprometimento com a cidade. */
    @Override
    @Async("emailExecutor")
    public void sendOccurrenceCreatedEmail(String toEmail, String fullname, String protocol, String title) {
        String name = (fullname == null || fullname.isBlank()) ? "cidadão" : fullname;
        String safeTitle = (title == null || title.isBlank()) ? "" : " – " + title;

        String text =
            "Olá, " + name + "!\n\n" +
            "Sua ocorrência" + safeTitle + " foi registrada com sucesso.\n" +
            "Protocolo: " + protocol + "\n\n" +
            "Você pode acompanhar o andamento pelo aplicativo a qualquer momento.\n\n" +
            "Obrigado pelo seu comprometimento com a cidade! Registros como o seu " +
            "ajudam a administração pública a agir mais rápido.";

        String content =
            "<h2 style='margin:0 0 16px; font-size:20px; color:#111;'>Ocorrência registrada com sucesso ✅</h2>" +
            "<p style='font-size:15px; color:#333;'>Olá, <strong>" + name + "</strong>!</p>" +
            "<p style='font-size:15px; color:#333;'>Sua ocorrência" + safeTitle + " foi registrada. " +
            "Guarde o protocolo para acompanhar o andamento:</p>" +
            "<div style='font-family:monospace; font-size:22px; font-weight:bold; color:" + BRAND_COLOR + ";" +
            "            background:#eef4fb; border-radius:8px; padding:14px; margin:20px 0; text-align:center;'>" +
                 protocol +
            "</div>" +
            "<p style='font-size:15px; color:#333;'><strong>Obrigado pelo seu comprometimento com a cidade!</strong> " +
            "Registros como o seu ajudam a administração pública a agir mais rápido.</p>";

        send(toEmail, null, "Fala, Cidade! – Ocorrência registrada (" + protocol + ")",
             withFooter(text), layout(content), "Falha ao enviar e-mail de ocorrência registrada");
    }

    /** Notifica o autor sobre a mudança de status (mensagem do funcionário é opcional). */
    @Override
    @Async("emailExecutor")
    public void sendStatusChangeEmail(String toEmail, String fullname, String protocol,
                                      String newStatus, String message) {
        String name = (fullname == null || fullname.isBlank()) ? "cidadão" : fullname;
        String statusLabel = switch (newStatus) {
            case "EM_ANDAMENTO" -> "Em Andamento";
            case "ATENDIDA"     -> "Atendida";
            case "INDEFERIDA"   -> "Indeferida";
            default             -> newStatus;
        };
        String statusColor = switch (newStatus) {
            case "EM_ANDAMENTO" -> "#d97706";
            case "ATENDIDA"     -> "#16a34a";
            case "INDEFERIDA"   -> "#6b7280";
            default             -> BRAND_COLOR;
        };
        boolean hasMessage = message != null && !message.isBlank();

        String text =
            "Olá, " + name + "!\n\n" +
            "O status da sua ocorrência (protocolo " + protocol + ") foi atualizado para: " + statusLabel + ".\n" +
            (hasMessage ? "\nMensagem da equipe:\n" + message + "\n" : "") +
            "\nVocê pode ver os detalhes pelo aplicativo a qualquer momento.";

        String content =
            "<h2 style='margin:0 0 16px; font-size:20px; color:#111;'>Sua ocorrência foi atualizada</h2>" +
            "<p style='font-size:15px; color:#333;'>Olá, <strong>" + name + "</strong>!</p>" +
            "<p style='font-size:15px; color:#333;'>O status da ocorrência de protocolo " +
            "<strong style='font-family:monospace;'>" + protocol + "</strong> mudou para:</p>" +
            "<div style='text-align:center; margin:20px 0;'>" +
            "  <span style='display:inline-block; background:" + statusColor + "22; color:" + statusColor + ";" +
            "        padding:10px 24px; border-radius:999px; font-size:16px; font-weight:bold;'>" + statusLabel + "</span>" +
            "</div>" +
            (hasMessage
                ? "<div style='background:#f9fafb; border-left:4px solid " + BRAND_COLOR + "; border-radius:6px;" +
                  "     padding:14px 16px; margin:18px 0;'>" +
                  "  <p style='font-size:13px; color:#777; margin:0 0 6px;'>Mensagem da equipe:</p>" +
                  "  <p style='font-size:15px; color:#333; margin:0; white-space:pre-wrap;'>" + message + "</p>" +
                  "</div>"
                : "") +
            "<p style='font-size:14px; color:#555;'>Você pode ver os detalhes pelo aplicativo a qualquer momento.</p>";

        send(toEmail, null, "Fala, Cidade! – Ocorrência " + protocol + ": " + statusLabel,
             withFooter(text), layout(content), "Falha ao enviar e-mail de mudança de status");
    }

    // ── Layout padrão ─────────────────────────────────────────────────────────

    /** Envolve o conteúdo no layout padrão: marca no topo, cartão branco e rodapé escuro. */
    private String layout(String contentHtml) {
        return
            "<!DOCTYPE html><html lang='pt-BR'><head><meta charset='UTF-8'></head>" +
            "<body style='margin:0; padding:0; background:#f4f4f4; font-family:Arial, sans-serif;'>" +
            "<div style='max-width:560px; margin:0 auto; padding:24px 16px;'>" +

            // Marca do sistema no topo
            "  <div style='padding:8px 4px 16px;'>" +
            "    <span style='font-size:24px; font-weight:800; color:" + BRAND_COLOR + ";'>Fala, Cidade!</span>" +
            "  </div>" +

            // Cartão branco com o conteúdo
            "  <div style='background:#fff; border-radius:10px; padding:32px 28px;" +
            "       box-shadow:0 2px 8px rgba(0,0,0,0.08);'>" +
                 contentHtml +
            "  </div>" +

            // Rodapé escuro com contatos e endereço
            "  <div style='background:#1a1a1a; border-radius:10px; padding:24px 28px; margin-top:16px;'>" +
            "    <p style='color:#fff; font-size:15px; font-weight:bold; margin:0 0 12px;'>" +
            "      Abraços,<br>Equipe Fala, Cidade!</p>" +
            "    <p style='color:#9ca3af; font-size:12px; margin:0 0 4px;'>" +
            "      Dúvidas? Fale com a gente: " +
            "      <a href='mailto:" + contactEmail + "' style='color:#9ca3af;'>" + contactEmail + "</a></p>" +
            "    <p style='color:#9ca3af; font-size:12px; margin:0 0 12px;'>" + ADDRESS + "</p>" +
            "    <p style='color:#6b7280; font-size:11px; margin:0;'>" +
            "      Por favor, não responda este e-mail — trata-se de uma mensagem automática.</p>" +
            "  </div>" +

            "</div></body></html>";
    }

    /** Rodapé da versão texto puro (fallback). */
    private String withFooter(String text) {
        return text + "\n\n—\nEquipe Fala, Cidade!\n" +
               "Contato: " + contactEmail + "\n" + ADDRESS + "\n" +
               "Mensagem automática — não responda este e-mail.";
    }

    /**
     * Monta e envia um e-mail multipart UTF-8 (texto puro como fallback + HTML).
     * replyTo é opcional; failureMessage vira a mensagem da exceção em caso de erro.
     */
    private void send(String to, String replyTo, String subject,
                      String text, String html, String failureMessage) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            if (replyTo != null && !replyTo.isBlank()) helper.setReplyTo(replyTo);
            helper.setSubject(subject);
            helper.setText(text, html);
            mailSender.send(msg);
        } catch (MessagingException e) {
            throw new RuntimeException(failureMessage, e);
        }
    }
}
