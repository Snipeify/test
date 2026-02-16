package org.sound.smoothrots.client;

import me.shedaniel.autoconfig.serializer.Toml4jConfigSerializer;

public class SmoothRotsConfigSerializer extends Toml4jConfigSerializer<SmoothRotsConfig> {
    public SmoothRotsConfigSerializer(me.shedaniel.autoconfig.ConfigDefinition configDefinition, Class<SmoothRotsConfig> configClass) {
        super(configDefinition, configClass);
    }
}
