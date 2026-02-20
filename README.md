# Aeotec ZW078 Heavy Duty Smart Switch — Hubitat Driver

A Hubitat Elevation driver for the **Aeotec ZW078 Heavy Duty Smart Switch (40A)** that adds full energy monitoring on top of basic on/off control, replacing the built-in Generic Z-Wave Relay driver.

---

## Why this driver exists

The ZW078 ships with a Z-Wave Meter command class (V3) that reports instantaneous power (W), accumulated energy (kWh), AC voltage (V), and AC current (A). The Hubitat **Generic Z-Wave Relay** driver ignores all of that — it only handles on/off switching.

This driver:
- Exposes all four meter readings as standard Hubitat attributes
- Configures the device's on-board auto-reporting so readings arrive on schedule without polling
- Enables threshold-based reporting so a power change (e.g. an appliance turning on) is reflected immediately
- Handles overload protection tripping and reflects the relay state correctly
- Sends all Z-Wave configuration commands with the correct byte widths (a common source of silent failures in community drivers for this device)

---

## Features

| Feature | Details |
|---|---|
| On / Off | Direct relay control via Switch capability |
| Power | Instantaneous draw in Watts (PowerMeter) |
| Energy | Accumulated consumption in kWh (EnergyMeter), lifetime or since last reset |
| Voltage | AC RMS voltage in Volts (VoltageMeasurement) |
| Current | AC RMS current in Amps (CurrentMeter) |
| Power factor | Dimensionless 0–1, reported when device includes it |
| Session high / low | `powerHigh` and `powerLow` track the Watt peak and valley since last hub restart |
| Reset energy | `resetEnergy()` command zeroes the kWh counter on the device itself |
| Overload protection | Configurable; driver catches the overcurrent notification, sets switch to off, and marks `overloadStatus = tripped` |
| Auto-reporting | Choose which readings and how often the device pushes them unprompted |
| Threshold reporting | Reports a Watt reading whenever power changes by a set absolute value or percentage |
| Power-restore state | Configure what the relay does after a power outage |
| Secure inclusion | Uses `zwaveSecureEncap()` automatically when paired with S0 security |
| Debug log timeout | Debug logging auto-disables after 30 minutes |

---

## Confirmed compatible hardware

| Model | Z-Wave region | Max load | Notes |
|---|---|---|---|
| ZW078-A | US (908.42 MHz) | 40A resistive / 10HP motor | Primary target |
| ZW078-C | EU (868.42 MHz) | 40A resistive / 10HP motor | Same firmware, different frequency |

The driver fingerprints both the US (`prod: "0003"`) and EU (`prod: "0103"`) product type IDs with `model: "004E"` (ManufacturerID `0x0086` — Aeotec / Aeon Labs).

> **Note:** Very early firmware revisions (v1.x) may not support voltage and current reporting (Meter V3 scales 4 and 5). Power (W) and energy (kWh) work on all known firmware. If voltage and current stay at zero after a Refresh, your unit likely has old firmware.

---

## Installation

### Option A — Manual (current method)

1. Open `AeotecZW078-HeavyDutySwitch.groovy` from this repo
2. Copy the entire file contents
3. In Hubitat, go to **Drivers Code → + New Driver**
4. Paste and click **Save**

### Assign the driver to your device

1. In Hubitat, go to **Devices** and open your ZW078
2. Change **Type** to `Aeotec ZW078 Heavy Duty Smart Switch`
3. Click **Save Device**
4. Click **Configure** — this pushes all Z-Wave parameters to the device
5. Click **Refresh** — pulls the current switch state and all meter readings

---

## Preferences

| Setting | Default | Description |
|---|---|---|
| Debug logging | On | Auto-disables after 30 minutes |
| Info text logging | On | Logs readings and state changes to the hub log |
| Auto-report: which values | kWh + W + V + A | Which meter readings the device pushes on a schedule |
| Auto-report interval | 300 s | How often timed reports are sent (seconds) |
| Threshold reporting | On | Also report whenever Watts change by the thresholds below |
| Watt threshold — absolute | 25 W | Trigger a report if power changes by this many watts |
| Watt threshold — percentage | 5 % | Trigger a report if power changes by this percentage |
| Overload protection | On | Relay shuts off automatically if current exceeds ~39.5 A for >5 seconds |
| Relay state after outage | Restore last state | What the relay does when power returns after an outage |
| Switch-state notification | Switch Binary Report | How the device tells the hub when the relay changes state |

### Auto-report value options

| Option | Param 101 value | What gets reported |
|---|---|---|
| None | 0 | Nothing (use Refresh to poll) |
| kWh only | 1 | Accumulated energy |
| Watts only | 4 | Instantaneous power |
| kWh + Watts | 5 | Energy + power |
| kWh + Watts + Voltage + Amps *(default)* | 53 | All four primary readings |
| All + Power Factor | 117 | All four readings plus power factor |

> The bitmask is `kWh=1, W=4, V=16, A=32, PF=64`. Parameters 101, 102, 103, 111, 112, and 113 must be sent as **4-byte** values and parameter 91 as **2 bytes** — the device silently ignores incorrect sizes, which is the most common failure mode in other community drivers for this device.

---

## Attributes

| Attribute | Type | Unit | Description |
|---|---|---|---|
| `switch` | string | on / off | Relay state |
| `power` | number | W | Instantaneous power draw |
| `energy` | number | kWh | Accumulated energy (since device manufacture or last `resetEnergy()`) |
| `voltage` | number | V | AC RMS voltage |
| `amperage` | number | A | AC RMS current |
| `powerFactor` | number | — | Power factor (0.00 – 1.00) |
| `powerHigh` | number | W | Session peak power (resets on hub restart) |
| `powerLow` | number | W | Session minimum power (resets on hub restart) |
| `overloadStatus` | string | normal / tripped | Set to `tripped` when overload protection fires |

---

## License

GPL-3.0 — see [LICENSE](LICENSE)

This driver is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, version 3.

© 2026 Z Sachen
