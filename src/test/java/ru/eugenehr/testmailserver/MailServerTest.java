/**
 * Copyright 2018 (c) Eugene Khrustalev
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ru.eugenehr.testmailserver;

import java.io.File;
import java.util.Properties;
import javax.mail.Authenticator;
import javax.mail.BodyPart;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests.
 *
 * @author <a href="mailto:eugene@efo.ru">Eugene Khrustalev</a>
 */
public class MailServerTest {

    private static final String LOREM_IPSUM = "Lorem ipsum dolor sit amet, consectetur adipiscing elit,\n"
        + "sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.\n"
        + "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris\n"
        + "nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in\n"
        + "reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla\n"
        + "pariatur. Excepteur sint occaecat cupidatat non proident, sunt in\n"
        + "culpa qui officia deserunt mollit anim id est laborum.";

    private static final String LOREM_IPSUM_RU_UTF8 = "Что такое Lorem Ipsum?\n"
        + "Lorem Ipsum - это текст-\"рыба\", часто используемый в печати и вэб-дизайне.\n"
        + "Lorem Ipsum является стандартной \"рыбой\" для текстов на латинице с начала XVI века.\n"
        + "В то время некий безымянный печатник создал большую коллекцию размеров и форм шрифтов,\n"
        + "используя Lorem Ipsum для распечатки образцов. Lorem Ipsum не только успешно пережил\n"
        + "без заметных изменений пять веков, но и перешагнул в электронный дизайн.\n"
        + "Его популяризации в новое время послужили публикация листов Letraset с образцами\n"
        + "Lorem Ipsum в 60-х годах и, в более недавнее время, программы электронной вёрстки\n"
        + "типа Aldus PageMaker, в шаблонах которых используется Lorem Ipsum.";

    private static Properties props;

    @BeforeClass
    public static void setUp() throws Exception {
        Mailboxes mailboxes = new Mailboxes(new File(System.getProperty("java.io.tmpdir")));
        MailServer.INSTANCE = new MailServer(mailboxes);
        MailServer.INSTANCE.setRedirectToSender(true);
        MailServer.getInstance().startSMTP(2500);
        MailServer.getInstance().startPOP3(1100);

        props = new Properties();
        props.setProperty("mail.debug", "true");
        props.setProperty("mail.send.protocol", "smtp");
        props.setProperty("mail.smtp.host", "127.0.0.1");
        props.setProperty("mail.smtp.port", "2500");
        props.setProperty("mail.smtp.auth", "false");
        props.setProperty("mail.store.protocol", "pop3");
        props.setProperty("mail.pop3.host", "127.0.0.1");
        props.setProperty("mail.pop3.port", "1100");
        props.setProperty("mail.pop3.auth", "true");
    }

    @AfterClass
    public static void tearDown() throws Exception {
        MailServer.getInstance().stopSMTP();
        MailServer.getInstance().stopPOP3();
    }

    @Test
    public void javamailTest() throws Exception {
        // Sending mail
        final Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication("john.doe@example.com", "Pa$$word");
            }
        });
        final MimeMessage message = new MimeMessage(session);
        message.setSubject("Test message");
        message.setFrom(new InternetAddress("john.doe@example.com"));
        message.addRecipient(Message.RecipientType.TO, new InternetAddress("jane.doe@example.com", "Jane Doe"));
        message.addRecipient(Message.RecipientType.CC, new InternetAddress("john.doe@example.com"));
        message.addRecipient(Message.RecipientType.BCC, new InternetAddress("jeff.doe@example.com"));

        final Multipart multipart = new MimeMultipart();
        BodyPart bodyPart = new MimeBodyPart();
        bodyPart.setContent("Test message", "text/plain");
        multipart.addBodyPart(bodyPart);

        bodyPart = new MimeBodyPart();
        bodyPart.setContent(LOREM_IPSUM, "text/plain");
        bodyPart.setDisposition("attachment");
        bodyPart.setFileName("lorem-ipsum.txt");
        multipart.addBodyPart(bodyPart);

        bodyPart = new MimeBodyPart();
        bodyPart.setContent(LOREM_IPSUM_RU_UTF8, "text/plain; charset=utf-8");
        bodyPart.setDisposition("attachment");
        bodyPart.setFileName("lorem-ipsum.ru.txt");
        multipart.addBodyPart(bodyPart);

        message.setContent(multipart);

        Transport.send(message);

        // Receiving mail
        final Store store = session.getStore();
        store.connect();
        final Folder inbox = store.getFolder("INBOX");
        inbox.open(Folder.READ_WRITE);
        final Message[] messages = inbox.getMessages();
        assertNotNull(messages);
        assertTrue(messages.length >= 1);

        Message testMessage = null;
        for (Message mail : messages) {
            if (mail.isSet(Flags.Flag.DELETED)) {
                continue;
            }
            mail.setFlag(Flags.Flag.DELETED, true);
            if (testMessage == null && "Test message".equals(mail.getSubject())) {
                testMessage = mail;
            }
        }
        assertNotNull(testMessage);
        final Object content = testMessage.getContent();
        assertTrue(content instanceof Multipart);
        bodyPart = ((Multipart) content).getBodyPart(0);
        assertTrue(bodyPart.getContentType().startsWith("text/plain"));
        assertEquals(bodyPart.getContent(), "Test message");
        inbox.close(true);
    }
}