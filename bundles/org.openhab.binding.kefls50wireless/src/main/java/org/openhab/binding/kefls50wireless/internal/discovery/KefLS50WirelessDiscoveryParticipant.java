package org.openhab.binding.kefls50wireless.internal.discovery;

import static org.openhab.binding.kefls50wireless.internal.KefLS50WirelessBindingConstants.THING_TYPE_SAMPLE;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.config.discovery.upnp.UpnpDiscoveryParticipant;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.jupnp.model.meta.DeviceDetails;
import org.jupnp.model.meta.ModelDetails;
import org.jupnp.model.meta.RemoteDevice;
import org.osgi.service.component.annotations.Component;

@NonNullByDefault
@Component(service = UpnpDiscoveryParticipant.class, immediate = true)
public class KefLS50WirelessDiscoveryParticipant implements UpnpDiscoveryParticipant {

	private static final Set<ThingTypeUID> DISCOVERABLE_THING_TYPES_UIDS = Collections.singleton(THING_TYPE_SAMPLE);
	private static final String HOST = "Host";
	private static final String PROPERTY_SERIAL_NUMBER = "Serial";

	@Override
	public Set<ThingTypeUID> getSupportedThingTypeUIDs() {
		return DISCOVERABLE_THING_TYPES_UIDS;
	}

	@Override
	public @Nullable DiscoveryResult createResult(RemoteDevice device) {
        ThingUID uid = getThingUID(device);
        if (uid != null) {
            Map<String, Object> properties = new HashMap<>();
            properties.put(HOST, device.getDetails().getFriendlyName());
            properties.put(PROPERTY_SERIAL_NUMBER, device.getDetails().getSerialNumber());

            DiscoveryResult result = DiscoveryResultBuilder.create(uid).withProperties(properties)
                    .withLabel(device.getDetails().getFriendlyName()).withRepresentationProperty(PROPERTY_SERIAL_NUMBER)
                    .build();
            return result;
        } else {
            return null;
        }
	}

	@Override
	public @Nullable ThingUID getThingUID(RemoteDevice device) {
        DeviceDetails details = device.getDetails();
        if (details != null) {
            ModelDetails modelDetails = details.getModelDetails();
            if (modelDetails != null) {
                String modelDescriptoin = modelDetails.getModelDescription();
                if (modelDescriptoin != null) {
                    if (modelDescriptoin.equals("LS50 Wireless")) {
                        return new ThingUID(THING_TYPE_SAMPLE, details.getSerialNumber());
                    }
                }
            }
        }
        return null;
	}
}
