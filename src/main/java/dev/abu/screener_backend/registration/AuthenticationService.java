package dev.abu.screener_backend.registration;

import dev.abu.screener_backend.appuser.AppUser;
import dev.abu.screener_backend.appuser.AppUserRepository;
import dev.abu.screener_backend.registration.token.ConfirmationToken;
import dev.abu.screener_backend.registration.token.ConfirmationTokenRepository;
import dev.abu.screener_backend.registration.token.ConfirmationTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static dev.abu.screener_backend.utils.RequestUtilities.*;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    @Value("${server.base-url}")
    private String serverBaseUrl;

    private final ConfirmationTokenRepository confirmationTokenRepository;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailValidator emailValidator;
    private final AuthenticationManager authenticationManager;
    private final ConfirmationTokenService confirmationTokenService;
    private final EmailService emailService;

    public ResponseEntity<?> register(RegisterRequest request) {
        boolean isValidEmail = emailValidator.test(request.getEmail());
        if (!isValidEmail) throw new IllegalStateException(EMAIL_NOT_VALID);

        var user = request.getAppUser();
        boolean userExists = appUserRepository.findByEmail( user.getEmail() ).isPresent();
        if (userExists) throw new IllegalStateException(EMAIL_TAKEN);

        user.setPassword(passwordEncoder.encode(request.getPassword()));
        appUserRepository.save(user);

        return sendConfirmationEmail(user);
    }

    public ResponseEntity<?> authenticate(AuthenticationRequest request) {
        var user = appUserRepository.findByEmail( request.getEmail() ).orElseThrow(
                () -> new UsernameNotFoundException(EMAIL_NOT_VALID)
        );

        if (!user.isEnabled()) {
            LocalDateTime expirationDate =
                    confirmationTokenRepository.getLatestUnconfirmedExpirationDate(request.getEmail());
            if (expirationDate.isBefore(LocalDateTime.now())) {
                return sendConfirmationEmail(user);
            }
        }

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        var jwtToken = jwtService.generateToken( user );
        var authResponse = AuthenticationResponse.builder().token( jwtToken ).build();
        return new ResponseEntity<>(authResponse.create(), HttpStatus.OK);
    }

    public ResponseEntity<?> sendConfirmationEmail(AppUser user) {
        String token = UUID.randomUUID().toString();
        ConfirmationToken confirmationToken = new ConfirmationToken(
                token,
                LocalDateTime.now(),
                LocalDateTime.now().plusMinutes(30),
                user
        );
        confirmationTokenService.saveConfirmationToken(confirmationToken);

        String email = user.getEmail();
        String link = serverBaseUrl + "/api/v1/auth/confirm?confirmationToken=" + token + "&email=" + email;
        emailService.send(email, buildEmail(user.getFirstname(), link));

        var registrationResponse = RegistrationResponse.builder()
                .confirmationToken(token)
                .message(EMAIL_SENT_SUCCESSFULLY)
                .build().create();
        return new ResponseEntity<>(registrationResponse, HttpStatus.OK);
    }

    @Transactional
    public ResponseEntity<String> confirmToken(String token, String email) {
        ConfirmationToken confirmationToken = confirmationTokenService.getToken(token)
                .orElseThrow(() -> new IllegalStateException(TOKEN_NOT_FOUND));

        if (confirmationToken.getConfirmedAt() != null) {
            return new ResponseEntity<>(alreadyConfirmedHtmlPage(email), HttpStatus.ACCEPTED);
        }

        LocalDateTime expiredAt = confirmationToken.getExpiresAt();
        if (expiredAt.isBefore(LocalDateTime.now())) {
            return new ResponseEntity<>(expiredLinkHtmlPage(), HttpStatus.BAD_REQUEST);
        }

        confirmationTokenService.setConfirmedAt(token);
        appUserRepository.enableAppUser(confirmationToken.getAppUser().getEmail());
        return new ResponseEntity<>(emailConfirmedHtmlPage(email), HttpStatus.OK);
    }

    private String buildEmail(String name, String link) {
        return "<div style=\"font-family:Helvetica,Arial,sans-serif;font-size:16px;margin:0;color:#0b0c0c\">\n" +
                "\n" +
                "<span style=\"display:none;font-size:1px;color:#fff;max-height:0\"></span>\n" +
                "\n" +
                "  <table role=\"presentation\" width=\"100%\" style=\"border-collapse:collapse;min-width:100%;width:100%!important\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">\n" +
                "    <tbody><tr>\n" +
                "      <td width=\"100%\" height=\"53\" bgcolor=\"#0b0c0c\">\n" +
                "        \n" +
                "        <table role=\"presentation\" width=\"100%\" style=\"border-collapse:collapse;max-width:580px\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" align=\"center\">\n" +
                "          <tbody><tr>\n" +
                "            <td width=\"70\" bgcolor=\"#0b0c0c\" valign=\"middle\">\n" +
                "                <table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"border-collapse:collapse\">\n" +
                "                  <tbody><tr>\n" +
                "                    <td style=\"padding-left:10px\">\n" +
                "                  \n" +
                "                    </td>\n" +
                "                    <td style=\"font-size:28px;line-height:1.315789474;Margin-top:4px;padding-left:10px\">\n" +
                "                      <span style=\"font-family:Helvetica,Arial,sans-serif;font-weight:700;color:#ffffff;text-decoration:none;vertical-align:top;display:inline-block\">Активируйте свой аккаунт</span>\n" +
                "                    </td>\n" +
                "                  </tr>\n" +
                "                </tbody></table>\n" +
                "              </a>\n" +
                "            </td>\n" +
                "          </tr>\n" +
                "        </tbody></table>\n" +
                "        \n" +
                "      </td>\n" +
                "    </tr>\n" +
                "  </tbody></table>\n" +
                "  <table role=\"presentation\" class=\"m_-6186904992287805515content\" align=\"center\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"border-collapse:collapse;max-width:580px;width:100%!important\" width=\"100%\">\n" +
                "    <tbody><tr>\n" +
                "      <td width=\"10\" height=\"10\" valign=\"middle\"></td>\n" +
                "      <td>\n" +
                "        \n" +
                "                <table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"border-collapse:collapse\">\n" +
                "                  <tbody><tr>\n" +
                "                    <td bgcolor=\"#1D70B8\" width=\"100%\" height=\"10\"></td>\n" +
                "                  </tr>\n" +
                "                </tbody></table>\n" +
                "        \n" +
                "      </td>\n" +
                "      <td width=\"10\" valign=\"middle\" height=\"10\"></td>\n" +
                "    </tr>\n" +
                "  </tbody></table>\n" +
                "\n" +
                "\n" +
                "\n" +
                "  <table role=\"presentation\" class=\"m_-6186904992287805515content\" align=\"center\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"border-collapse:collapse;max-width:580px;width:100%!important\" width=\"100%\">\n" +
                "    <tbody><tr>\n" +
                "      <td height=\"30\"><br></td>\n" +
                "    </tr>\n" +
                "    <tr>\n" +
                "      <td width=\"10\" valign=\"middle\"><br></td>\n" +
                "      <td style=\"font-family:Helvetica,Arial,sans-serif;font-size:19px;line-height:1.315789474;max-width:560px\">\n" +
                "        \n" +
                "            <p style=\"Margin:0 0 20px 0;font-size:19px;line-height:25px;color:#0b0c0c\">Добро пожаловать, " + name + ",</p><p style=\"Margin:0 0 20px 0;font-size:19px;line-height:25px;color:#0b0c0c\"> Спасибо что выбрали Clerk Screener! Для активации вашего аккаунта, пожалуйста перейдите по ссылке ниже: </p><blockquote style=\"Margin:0 0 20px 0;border-left:10px solid #b1b4b6;padding:15px 0 0.1px 15px;font-size:19px;line-height:25px\"><p style=\"Margin:0 0 20px 0;font-size:19px;line-height:25px;color:#0b0c0c\"> <a href=\"" + link + "\">Активировать Сейчас</a> </p></blockquote>\n Ссылка активна в течение 30 минут. <p>Удачи в трейдинге!</p>" +
                "        \n" +
                "      </td>\n" +
                "      <td width=\"10\" valign=\"middle\"><br></td>\n" +
                "    </tr>\n" +
                "    <tr>\n" +
                "      <td height=\"30\"><br></td>\n" +
                "    </tr>\n" +
                "  </tbody></table><div class=\"yj6qo\"></div><div class=\"adL\">\n" +
                "\n" +
                "</div></div>";
    }

    private String emailConfirmedHtmlPage(String email) {
        return """
                <!DOCTYPE html>
                <html lang="en" dir="ltr">
                  <head>
                    <meta charset="utf-8">
                    <title>Аккаунт активирован</title>
                    <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:opsz,wght,FILL,GRAD@24,400,0,0&icon_names=mark_email_read" />
                  </head>
                  <style>
                
                    *,
                    *::after,
                    *::before {
                      margin: 0;
                      box-sizing: border-box;
                    }
                
                    body {
                      margin: 0;
                      background-color: #232528;
                      color: white;
                      font-family: "Ubuntu", sans-serif;
                      font-weight: 100;
                      font-style: normal;
                      text-align: center;
                      font-size: 1vw;
                      line-height: 1.8vw;
                    }
                
                    .email-verified-layout {
                      width: 100%;
                      min-height: 100vh;
                      padding-top: 5%;
                      display: flex;
                      justify-content: center;
                    }
                
                    .email-verified-container {
                      background-color: #2F2F37;
                      border-top-left-radius: 15px;
                      border-top-right-radius: 15px;
                      box-shadow: 10px 10px 20px rgba(0, 0, 0, 0.3), -10px -10px 20px rgba(0, 0, 0, 0.3);
                      padding: 40px 100px;
                      max-width: 50%;
                
                      display: flex;
                      flex-direction: column;
                      justify-content: space-between;
                      align-items: center;
                    }
                
                    .main {
                      display: flex;
                      flex-direction: column;
                      justify-content: start;
                      align-items: center;
                    }
                
                    .footer {
                      font-size: .9vw;
                      line-height: 1.5vw;
                    }
                
                    h2 {
                      text-align: center;
                      font-size: calc(1.7vw);
                      padding: 5% 0;
                    }
                
                    .email-icon {
                      font-size: 8vw;
                      color: #72b472;
                    }
                
                    a.button {
                      background-color: white;
                      border: 1px solid white;
                      border-radius: 5px;
                      display: block;
                      margin: 5% 0;
                      padding: 1.5%;
                      font-weight: bold;
                      text-decoration: none;
                      color: black;
                    }
                
                    a.button:hover {
                      background-color: inherit;
                      color: white;
                      border: 1px solid white;
                    }
                
                    a {
                      color: #8888ff;
                    }
                
                  </style>
                  <body>
                    <div class="email-verified-layout">
                      <div class="email-verified-container">
                        <div class="main">
                          <span class="material-symbols-outlined email-icon">
                              mark_email_read
                          </span>
                          <h2>Ваш аккаунт подтвержден</h2>
                          <p> Вы ввели <b>""" + email + """
                            </b> в качестве своего электронного адреса.
                            Теперь вы можете перейти по ссылке ниже, чтобы открыть скринер: </p>
                          <a href="http://185.39.31.76" class='button'>
                            Перейти к скринеру
                          </a>
                        </div>
                        <div class="footer">
                          <p>Или скопируйте и вставте эту ссылку в ваш браузер:</p>
                          <a href="http://185.39.31.76">http://185.39.31.76</a>
                        </div>
                      </div>
                    </div>
                  </body>
                </html>
                """;
    }

    private String expiredLinkHtmlPage() {
        return """
                <!DOCTYPE html>
                <html lang="en" dir="ltr">
                  <head>
                    <meta charset="utf-8">
                    <title>Ошибка</title>
                    <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:opsz,wght,FILL,GRAD@24,400,0,0&icon_names=error" />
                  </head>
                  <style>
                
                    *,
                    *::after,
                    *::before {
                      margin: 0;
                      box-sizing: border-box;
                    }
                
                    body {
                      margin: 0;
                      background-color: #232528;
                      color: white;
                      font-family: "Ubuntu", sans-serif;
                      font-weight: 100;
                      font-style: normal;
                      text-align: center;
                      font-size: 1vw;
                      line-height: 1.8vw;
                    }
                
                    .email-verified-layout {
                      width: 100%;
                      min-height: 100vh;
                      padding-top: 5%;
                      display: flex;
                      justify-content: center;
                    }
                
                    .email-verified-container {
                      background-color: #2F2F37;
                      border-top-left-radius: 15px;
                      border-top-right-radius: 15px;
                      box-shadow: 10px 10px 20px rgba(0, 0, 0, 0.3), -10px -10px 20px rgba(0, 0, 0, 0.3);
                      padding: 40px 100px;
                      max-width: 50%;
                
                      display: flex;
                      flex-direction: column;
                      justify-content: space-between;
                      align-items: center;
                    }
                
                    .main {
                      display: flex;
                      flex-direction: column;
                      justify-content: start;
                      align-items: center;
                    }
                
                    .footer {
                      font-size: .9vw;
                      line-height: 1.5vw;
                    }
                
                    h2 {
                      text-align: center;
                      font-size: calc(1.7vw);
                      padding: 5% 0;
                    }
                
                    .error-icon {
                      font-size: 8vw;
                      color: #ff5858;
                    }
                
                    a.button {
                      background-color: white;
                      border: 1px solid white;
                      border-radius: 5px;
                      display: block;
                      margin: 5% 0;
                      padding: 1.5%;
                      font-weight: bold;
                      text-decoration: none;
                      color: black;
                    }
                
                    a.button:hover {
                      background-color: inherit;
                      color: white;
                      border: 1px solid white;
                    }
                
                    a {
                      color: #8888ff;
                    }
                
                  </style>
                  <body>
                    <div class="email-verified-layout">
                      <div class="email-verified-container">
                        <div class="main">
                          <span class="material-symbols-outlined error-icon">
                              error
                          </span>
                          <h2>Срок для активации истек</h2>
                          <p>
                            К сожалению ссылка для активации вашего аккаунта уже истекла! <br>
                            <b style="color: #ff5858"> Пожалуйста совершите вход в приложение снова и получите новое письмо с обновленной ссылкой:</b>
                          </p>
                          <a href="http://185.39.31.76" class='button'>
                            Получить новое письмо
                          </a>
                        </div>
                        <div class="footer">
                          <p>Или скопируйте и вставте эту ссылку в ваш браузер:</p>
                          <a href="http://185.39.31.76">http://185.39.31.76</a>
                        </div>
                      </div>
                    </div>
                  </body>
                </html>
                """;
    }

    private String alreadyConfirmedHtmlPage(String email) {
        return """
                <!DOCTYPE html>
                <html lang="en" dir="ltr">
                  <head>
                    <meta charset="utf-8">
                    <title>Вы уже активировали аккаунт</title>
                    <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:opsz,wght,FILL,GRAD@24,400,0,0&icon_names=mark_email_read" />
                  </head>
                  <style>
                
                    *,
                    *::after,
                    *::before {
                      margin: 0;
                      box-sizing: border-box;
                    }
                
                    body {
                      margin: 0;
                      background-color: #232528;
                      color: white;
                      font-family: "Ubuntu", sans-serif;
                      font-weight: 100;
                      font-style: normal;
                      text-align: center;
                      font-size: 1vw;
                      line-height: 1.8vw;
                    }
                
                    .email-verified-layout {
                      width: 100%;
                      min-height: 100vh;
                      padding-top: 5%;
                      display: flex;
                      justify-content: center;
                    }
                
                    .email-verified-container {
                      background-color: #2F2F37;
                      border-top-left-radius: 15px;
                      border-top-right-radius: 15px;
                      box-shadow: 10px 10px 20px rgba(0, 0, 0, 0.3), -10px -10px 20px rgba(0, 0, 0, 0.3);
                      padding: 40px 100px;
                      max-width: 50%;
                
                      display: flex;
                      flex-direction: column;
                      justify-content: space-between;
                      align-items: center;
                    }
                
                    .main {
                      display: flex;
                      flex-direction: column;
                      justify-content: start;
                      align-items: center;
                    }
                
                    .footer {
                      font-size: .9vw;
                      line-height: 1.5vw;
                    }
                
                    h2 {
                      text-align: center;
                      font-size: calc(1.7vw);
                      padding: 5% 0;
                    }
                
                    .email-icon {
                      font-size: 8vw;
                      color: #72b472;
                    }
                
                    a.button {
                      background-color: white;
                      border: 1px solid white;
                      border-radius: 5px;
                      display: block;
                      margin: 5% 0;
                      padding: 1.5%;
                      font-weight: bold;
                      text-decoration: none;
                      color: black;
                    }
                
                    a.button:hover {
                      background-color: inherit;
                      color: white;
                      border: 1px solid white;
                    }
                
                    a {
                      color: #8888ff;
                    }
                
                  </style>
                  <body>
                    <div class="email-verified-layout">
                      <div class="email-verified-container">
                        <div class="main">
                          <span class="material-symbols-outlined email-icon">
                              mark_email_read
                          </span>
                          <h2>Ваш аккаунт уже активирован</h2>
                          <p>
                            Вы ввели <b>""" + email + """
                            </b> в качестве своего электронного адреса.
                            Теперь вы можете перейти по ссылке ниже чтобы открыть скринер:
                          </p>
                          <a href="http://185.39.31.76" class='button'>
                            Перейти к скринеру
                          </a>
                        </div>
                        <div class="footer">
                          <p>Или скопируйте и вставте эту ссылку в ваш браузер:</p>
                          <a href="http://185.39.31.76">http://185.39.31.76</a>
                        </div>
                      </div>
                    </div>
                  </body>
                </html>

                """;
    }
}