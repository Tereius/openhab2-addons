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

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.smarthome.core.audio.AudioFormat;
import org.eclipse.smarthome.core.audio.AudioStream;
import org.eclipse.smarthome.core.thing.ThingTypeUID;

/**
 * The {@link KefLS50WirelessBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Björn Stresing - Initial contribution
 */
@NonNullByDefault
public class KefLS50WirelessBindingConstants {

    private static final String BINDING_ID = "kefls50wireless";

    public static final Set<AudioFormat> SUPPORTED_AUDIO_FORMATS = Collections
            .unmodifiableSet(Stream.of(AudioFormat.MP3, AudioFormat.WAV).collect(Collectors.toSet()));
    public static final Set<Class<? extends AudioStream>> SUPPORTED_AUDIO_STREAMS = Collections
            .singleton(AudioStream.class);

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_SAMPLE = new ThingTypeUID(BINDING_ID, "loudspeaker");

    // List of all Channel ids
    public static final String CHANNEL_VOLUME = "volume";
    public static final String CHANNEL_MUTE = "mute";
    public static final String CHANNEL_INPUT = "input";

    // List of thing properties
    public static final String PROPERTY_HOST = "host";
    public static final String PROPERTY_SERIAL_NUMBER = "serial";
    public static final String PROPERTY_UDN = "udn";
}
