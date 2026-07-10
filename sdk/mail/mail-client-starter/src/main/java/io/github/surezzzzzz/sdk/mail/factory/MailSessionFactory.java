package io.github.surezzzzzz.sdk.mail.factory;

import io.github.surezzzzzz.sdk.mail.annotation.MailComponent;
import io.github.surezzzzzz.sdk.mail.configuration.MailProperties;
import io.github.surezzzzzz.sdk.mail.support.MailPropertiesHelper;

import javax.mail.Session;
import javax.mail.Store;

/**
 * Mail Session 工厂
 *
 * @author surezzzzzz
 */
@MailComponent
public class MailSessionFactory {

    private final MailProperties properties;
    private final Session session;

    public MailSessionFactory(MailProperties properties) {
        this.properties = properties;
        this.session = Session.getInstance(MailPropertiesHelper.toProperties(properties.getRead().getProperties()));
    }

    public Store createStore() throws Exception {
        Store store = session.getStore();
        store.connect(properties.getRead().getUsername(), properties.getRead().getPassword());
        return store;
    }
}
