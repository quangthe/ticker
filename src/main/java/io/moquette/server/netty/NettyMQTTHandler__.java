/*
 * Copyright (c) 2012-2017 The original author or authorsgetRockQuestions()
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package io.moquette.server.netty;

import static io.moquette.parser.proto.messages.AbstractMessage.CONNECT;
import static io.moquette.parser.proto.messages.AbstractMessage.DISCONNECT;
import static io.moquette.parser.proto.messages.AbstractMessage.PINGREQ;
import static io.moquette.parser.proto.messages.AbstractMessage.PUBACK;
import static io.moquette.parser.proto.messages.AbstractMessage.PUBCOMP;
import static io.moquette.parser.proto.messages.AbstractMessage.PUBLISH;
import static io.moquette.parser.proto.messages.AbstractMessage.PUBREC;
import static io.moquette.parser.proto.messages.AbstractMessage.PUBREL;
import static io.moquette.parser.proto.messages.AbstractMessage.SUBSCRIBE;
import static io.moquette.parser.proto.messages.AbstractMessage.UNSUBSCRIBE;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.moquette.parser.proto.Utils;
import io.moquette.parser.proto.messages.AbstractMessage;
import io.moquette.parser.proto.messages.ConnectMessage;
import io.moquette.parser.proto.messages.PingRespMessage;
import io.moquette.parser.proto.messages.PubAckMessage;
import io.moquette.parser.proto.messages.PubCompMessage;
import io.moquette.parser.proto.messages.PubRecMessage;
import io.moquette.parser.proto.messages.PubRelMessage;
import io.moquette.parser.proto.messages.PublishMessage;
import io.moquette.parser.proto.messages.SubAckMessage;
import io.moquette.parser.proto.messages.SubscribeMessage;
import io.moquette.parser.proto.messages.UnsubAckMessage;
import io.moquette.parser.proto.messages.UnsubscribeMessage;
import io.moquette.spi.impl.ProtocolProcessor__;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.CorruptedFrameException;

/**
 *
 * @author andrea
 */
@Sharable
public class NettyMQTTHandler__ extends ChannelInboundHandlerAdapter {
    
    private static final Logger LOG = LoggerFactory.getLogger(NettyMQTTHandler__.class);
    private final ProtocolProcessor__ m_processor;

    public NettyMQTTHandler__(ProtocolProcessor__ processor) {
        m_processor = processor;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object message) {
        AbstractMessage msg = (AbstractMessage) message;
        LOG.info("Received a message of type {}", Utils.msgType2String(msg.getMessageType()));
        try {
            switch (msg.getMessageType()) {
                case CONNECT:
                    m_processor.processConnect(ctx.channel(), (ConnectMessage) msg);
                    break;
                case SUBSCRIBE:
                	//MODLOG: reject subscription requests
                    //m_processor.processSubscribe(ctx.channel(), (SubscribeMessage) msg);
                    SubAckMessage reject = ProtocolProcessor__.prepareRejectSubscriptionResponse((SubscribeMessage) msg);
                    ctx.channel().writeAndFlush(reject);
                    break;
                case UNSUBSCRIBE:
                	//MODLOG: there is no real unsubscribe action here
                    //m_processor.processUnsubscribe(ctx.channel(), (UnsubscribeMessage) msg);
                	UnsubAckMessage dummy = new UnsubAckMessage();
                	dummy.setMessageID(((UnsubscribeMessage) msg).getMessageID());
                	ctx.channel().writeAndFlush(dummy);
                    break;
                case PUBLISH:
                    m_processor.processPublish(ctx.channel(), (PublishMessage) msg);
                    break;
                case PUBREC:
                    m_processor.processPubRec(ctx.channel(), (PubRecMessage) msg);
                    break;
                case PUBCOMP:
                    m_processor.processPubComp(ctx.channel(), (PubCompMessage) msg);
                    break;
                case PUBREL:
                    m_processor.processPubRel(ctx.channel(), (PubRelMessage) msg);
                    break;
                case DISCONNECT:
                    m_processor.processDisconnect(ctx.channel());
                    break;
                case PUBACK:
                    m_processor.processPubAck(ctx.channel(), (PubAckMessage) msg);
                    break;
                case PINGREQ:
                    PingRespMessage pingResp = new PingRespMessage();
                    ctx.writeAndFlush(pingResp);
                    break;
            }
        } catch (Exception ex) {
            LOG.error("Bad error in processing the message", ex);
            ctx.fireExceptionCaught(ex);
        }
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        String clientID = NettyUtils.clientID(ctx.channel());
        if (clientID != null && !clientID.isEmpty()) {
            m_processor.processConnectionLost(clientID, ctx.channel());
        }
        ctx.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cause instanceof CorruptedFrameException) {
            //something goes bad with decoding
            LOG.warn("Error decoding a packet, probably a bad formatted packet, message: " + cause.getMessage());
        } else if (cause instanceof IOException && "Connection reset by peer".equals(cause.getMessage())) {
            LOG.warn("Network connection closed abruptly");
        } else {
            LOG.error("Ugly error on networking", cause);
        }
        ctx.close();
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        if (ctx.channel().isWritable()) {
            m_processor.notifyChannelWritable(ctx.channel());
        }
        ctx.fireChannelWritabilityChanged();
    }

}
