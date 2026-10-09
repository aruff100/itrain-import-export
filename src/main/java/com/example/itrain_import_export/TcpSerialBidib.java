package com.example.itrain_import_export;

import org.bidib.jbidibc.core.BidibInterface;
import org.bidib.jbidibc.messages.MessageReceiver;
import org.bidib.jbidibc.messages.base.AbstractBaseBidib;
import org.bidib.jbidibc.messages.helpers.Context;
import org.bidib.jbidibc.serial.AbstractSerialBidib;

import java.util.List;

/**
 * BiDiB-Schnittstelle "seriell ueber TCP" (siehe {@link TcpSerialBidibConnector}
 * fuer das Warum) - das Gegenstueck zu jbidibcs {@code JSerialCommSerialBidib},
 * nur mit einem Socket statt eines COM-Ports. Der Portname ist "Adresse:Port".
 * Kein Pairing (das gibt es nur bei netBiDiB); der Verbindungsaufbau laeuft
 * wie am COM-Port ueber MSG_SYS_GET_MAGIC.
 */
final class TcpSerialBidib extends AbstractSerialBidib {

    private TcpSerialBidibConnector connector;

    private TcpSerialBidib() {
    }

    static BidibInterface createInstance(Context context) {
        TcpSerialBidib instance = new TcpSerialBidib();
        instance.initialize(context);
        return instance;
    }

    @Override
    public void initialize(Context context) {
        super.initialize(context);
        connector = new TcpSerialBidibConnector();
        MessageReceiver receiver = getMessageReceiver();
        connector.setMessageReceiver(receiver);
        connector.setLineStatusListener(this::fireCtsChanged);
        initializeConnector(connector);
    }

    @Override
    public List<String> getPortIdentifiers() {
        return List.of();
    }

    @Override
    protected void internalOpen(String portName, Context context) throws Exception {
        connector.internalOpen(portName, context);
    }

    @Override
    public void close() {
        if (connector.close()) {
            super.close();
            cleanupAfterClose(getMessageReceiver());
        }
    }

    @Override
    public boolean isOpened() {
        return connector.isOpened();
    }

    @Override
    protected boolean isImplAvaiable() {
        return connector.isImplAvailable();
    }

    @Override
    public void send(byte[] data) {
        connector.send(data);
    }

    @Override
    protected AbstractBaseBidib<MessageReceiver> getConnector() {
        return connector;
    }
}
