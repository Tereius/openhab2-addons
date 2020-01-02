/**
 * Copyright (c) 2014,2019 by the respective copyright holders.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.kefls50wireless.internal;

import static org.openhab.binding.kefls50wireless.internal.KefLS50WirelessBindingConstants.*;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.audio.AudioFormat;
import org.eclipse.smarthome.core.audio.AudioHTTPServer;
import org.eclipse.smarthome.core.audio.AudioSink;
import org.eclipse.smarthome.core.audio.AudioStream;
import org.eclipse.smarthome.core.audio.UnsupportedAudioFormatException;
import org.eclipse.smarthome.core.audio.UnsupportedAudioStreamException;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.eclipse.smarthome.core.types.UnDefType;
import org.eclipse.smarthome.io.transport.upnp.UpnpIOParticipant;
import org.eclipse.smarthome.io.transport.upnp.UpnpIOService;
import org.openhab.binding.kefls50wireless.internal.KefLS50Client.Input;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link KefLS50WirelessHandler} is responsible for handling commands,
 * which are sent to one of the channels.
 *
 * @author Björn Stresing - Initial contribution
 */
@NonNullByDefault
public class KefLS50WirelessHandler extends BaseThingHandler implements AudioSink, UpnpIOParticipant {

    private final Logger logger = LoggerFactory.getLogger(KefLS50WirelessHandler.class);

    @Nullable
    private AudioHTTPServer audioServer = null;

    @Nullable
    private KefLS50Client client = null;
    @Nullable
    private String callbackUrl = null;

    @Nullable
    private KefLS50WirelessConfiguration config;

    @Nullable
    private UpnpIOService upnpIOService;

    public KefLS50WirelessHandler(Thing thing, AudioHTTPServer audioHTTPServer, UpnpIOService upnpIOService,
            String callbackUrl) {
        super(thing);
        this.audioServer = audioHTTPServer;
        this.callbackUrl = callbackUrl;
        this.upnpIOService = upnpIOService;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (CHANNEL_VOLUME.equals(channelUID.getId())) {
            try {
                if (command instanceof RefreshType) {
                    updateState(channelUID, new PercentType((int) client.getVol()));
                    updateStatus(ThingStatus.ONLINE);
                } else if (command instanceof PercentType) {
                    client.setVol(((PercentType) command).floatValue());
                    updateStatus(ThingStatus.ONLINE);
                }
            } catch (IOException e) {
                updateState(channelUID, UnDefType.NULL);
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
            }
        } else if (CHANNEL_MUTE.equals(channelUID.getId())) {
            try {
                if (command instanceof RefreshType) {
                    updateState(channelUID, client.isMuted() ? OnOffType.ON : OnOffType.OFF);
                    updateStatus(ThingStatus.ONLINE);
                } else if (command instanceof OnOffType) {
                    client.setMuted(command.equals(OnOffType.ON));
                    updateStatus(ThingStatus.ONLINE);
                }
            } catch (IOException e) {
                updateState(channelUID, UnDefType.NULL);
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
            }
        } else if (CHANNEL_INPUT.equals(channelUID.getId())) {
            try {
                if (command instanceof RefreshType) {
                    Input input = client.getInput();
                    updateState(channelUID, new StringType(input.toString()));
                    updateStatus(ThingStatus.ONLINE);
                } else if (command instanceof StringType) {
                    client.setInput(Input.valueOf(command.toString()));
                    updateState(channelUID, new StringType(command.toString()));
                    updateStatus(ThingStatus.ONLINE);
                }
            } catch (IOException e) {
                updateState(channelUID, UnDefType.NULL);
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
            }
        } else {
            logger.warn("Can't handle unknown channel: \"" + channelUID.getId() + "\"");
        }
    }

    @Override
    public void initialize() {
        // logger.debug("Start initializing!");
        config = getConfigAs(KefLS50WirelessConfiguration.class);

        client = new KefLS50Client(config.host);

        // TODO: Initialize the handler.
        // The framework requires you to return from this method quickly. Also, before
        // leaving this method a thing
        // status from one of ONLINE, OFFLINE or UNKNOWN must be set. This might already
        // be the real thing status in
        // case you can decide it directly.
        // In case you can not decide the thing status directly (e.g. for long running
        // connection handshake using WAN
        // access or similar) you should set status UNKNOWN here and then decide the
        // real status asynchronously in the
        // background.

        // set the thing status to UNKNOWN temporarily and let the background task
        // decide for the real status.
        // the framework is then able to reuse the resources from the thing handler
        // initialization.
        // we set this upfront to reliably check status updates in unit tests.
        updateStatus(ThingStatus.UNKNOWN);

        // Example for background initialization:
        scheduler.execute(() -> {
            boolean thingReachable = client.isAvailable(); // <background task with long running initialization here>
            // when done do:
            if (thingReachable) {
                updateStatus(ThingStatus.ONLINE);
            } else {
                updateStatus(ThingStatus.OFFLINE);
            }

            upnpIOService.addSubscription(this, "urn:schemas-upnp-org:service:AVTransport:1", 99999);
        });

        // logger.debug("Finished initializing!");

        // Note: When initialization can NOT be done set the status with more details
        // for further
        // analysis. See also class ThingStatusDetail for all available status details.
        // Add a description to give user information to understand why thing does not
        // work as expected. E.g.
        // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
        // "Can not access device as username and/or password are invalid");
    }

    @Override
    public String getId() {

        return thing.getUID().toString();
    }

    @Override
    public @Nullable String getLabel(@Nullable Locale locale) {

        return thing.getLabel();
    }

    @Override
    public void process(@Nullable AudioStream audioStream)
            throws UnsupportedAudioFormatException, UnsupportedAudioStreamException {

        if (audioStream != null) {
            try {
                client.setInput(Input.NETWORK);
                client.play(audioStream, audioServer, callbackUrl);
                client.getMediaInfo();
            } catch (IOException e) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
            }
        }
    }

    @Override
    public Set<AudioFormat> getSupportedFormats() {

        return KefLS50WirelessBindingConstants.SUPPORTED_AUDIO_FORMATS;
    }

    @Override
    public Set<Class<? extends AudioStream>> getSupportedStreams() {

        return KefLS50WirelessBindingConstants.SUPPORTED_AUDIO_STREAMS;
    }

    @Override
    public PercentType getVolume() throws IOException {

        return new PercentType((int) (client.getVol() + .5));
    }

    @Override
    public void setVolume(PercentType volume) throws IOException {

        client.setVol(volume.floatValue());
    }

    @Override
    public String getUDN() {

        return thing.getProperties().get(PROPERTY_UDN);
    }

    @Override
    public void onValueReceived(@Nullable String variable, @Nullable String value, @Nullable String service) {

        logger.warn("test");
    }

    @Override
    public void onServiceSubscribed(@Nullable String service, boolean succeeded) {
        logger.warn("test");
    }

    @Override
    public void onStatusChanged(boolean status) {
        logger.warn("test");
    }
}
