package io.forgeloop.control.support;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpSupportRecoveryDeliveryTest {
    @Test void recoveryMessageContainsOnlyTheFragmentProofAndNoTicketContent(){
        var sender=mock(JavaMailSender.class);var beans=new StaticListableBeanFactory();beans.addBean("mailSender",sender);
        var delivery=new SmtpSupportRecoveryDelivery(beans.getBeanProvider(JavaMailSender.class),true,"smtp.example.com",
                "support@example.com","https://forgeloop.example.com/");
        assertTrue(delivery.available());delivery.sendRecovery("customer@example.com","a".repeat(64));
        var message=org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(message.capture());
        assertEquals("support@example.com",message.getValue().getFrom());
        assertArrayEquals(new String[]{"customer@example.com"},message.getValue().getTo());
        assertTrue(message.getValue().getText().contains("https://forgeloop.example.com/support#recover="+"a".repeat(64)));
        assertFalse(message.getValue().getText().contains("/api/support/tickets/"));
    }
    @Test void recoveryRequiresExplicitEnablementConfiguredSenderAndCanonicalSecureBaseUrl(){
        var sender=mock(JavaMailSender.class);var beans=new StaticListableBeanFactory();beans.addBean("mailSender",sender);
        assertFalse(new SmtpSupportRecoveryDelivery(beans.getBeanProvider(JavaMailSender.class),false,"smtp.example.com","support@example.com","https://forgeloop.example.com").available());
        assertFalse(new SmtpSupportRecoveryDelivery(beans.getBeanProvider(JavaMailSender.class),true,"","support@example.com","https://forgeloop.example.com").available());
        assertFalse(new SmtpSupportRecoveryDelivery(beans.getBeanProvider(JavaMailSender.class),true,"smtp.example.com","support@example.com","https://forgeloop.example.com/other").available());
        assertFalse(new SmtpSupportRecoveryDelivery(beans.getBeanProvider(JavaMailSender.class),true,"smtp.example.com","support@example.com","http://forgeloop.example.com").available());
    }
}
