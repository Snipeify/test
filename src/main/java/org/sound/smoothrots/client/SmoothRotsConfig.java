package org.sound.smoothrots.client;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = SmoothRotsClient.MOD_ID)
public class SmoothRotsConfig implements ConfigData {
    public boolean enabled = true;
    public RotationMode mode = RotationMode.STRICT_DOWN;

    @ConfigEntry.BoundedDiscrete(min = -90, max = 90)
    public int downPitch = 90;

    @ConfigEntry.BoundedDiscrete(min = 1, max = 180)
    public int smoothingPerTick = 20;

    public boolean strictLock = true;
    public boolean lockCamera = true;
    public boolean fallbackToDown = true;

    public static SmoothRotsConfig get() {
        return AutoConfig.getConfigHolder(SmoothRotsConfig.class).getConfig();
    }
}
