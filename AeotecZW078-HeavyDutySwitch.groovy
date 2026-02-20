/**
 * Copyright (C) 2026 Z Sachen
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *  Aeotec ZW078 Heavy Duty Smart Switch — Hubitat C8 Pro Driver
 *
 *  Device: Aeotec ZW078 (Heavy Duty Smart Switch Gen5, 40A)
 *
 *  Capabilities:
 *    Switch          — on / off relay control
 *    PowerMeter      — instantaneous power (W)
 *    EnergyMeter     — accumulated energy (kWh)
 *    VoltageMeasurement — AC voltage (V)
 *    CurrentMeter    — AC current (A)
 *    Refresh         — poll all values on demand
 *    Configuration   — applies all Z-Wave parameters on save
 *
 *  Extra commands:
 *    resetEnergy()   — resets the kWh accumulation counter on-device
 *
 *  Z-Wave Command Classes:
 *    0x20 v1  BASIC
 *    0x25 v1  SWITCH_BINARY
 *    0x32 v3  METER  (W, kWh, V, A, Power Factor)
 *    0x56 v1  CRC_16_ENCAP
 *    0x59 v1  ASSOCIATION_GRP_INFO
 *    0x5A v1  DEVICE_RESET_LOCALLY
 *    0x5E v2  ZWAVEPLUS_INFO
 *    0x6C v1  SUPERVISION
 *    0x70 v1  CONFIGURATION
 *    0x71 v3  NOTIFICATION (overload protection events; older firmware sends as ALARM v2)
 *    0x72 v2  MANUFACTURER_SPECIFIC
 *    0x73 v1  POWERLEVEL
 *    0x85 v2  ASSOCIATION
 *    0x86 v2  VERSION
 *    0x8E v2  MULTI_CHANNEL_ASSOCIATION
 *    0x98 v1  SECURITY (S0)
 *
 *  Association Groups:
 *    Group 1 (Lifeline)      — hub receives all reports and notifications
 *    Group 2 (Switch State)  — BASIC_SET to associated nodes on relay change
 *    Group 3 (Meter)         — forwarded METER_REPORTs to associated nodes
 *
 *  Configuration Parameters (all confirmed for ZW078):
 *    3   (1 B) Overload protection (0=off, 1=on)
 *    20  (1 B) Power-restore state (0=last, 1=off, 2=on)
 *    80  (1 B) State-change notification type (0=none, 1=Basic Report, 2=Binary Report)
 *    90  (1 B) Enable threshold-based watt reports (0=off, 1=on)
 *    91  (2 B) Watt change threshold — absolute (watts)
 *    92  (1 B) Watt change threshold — percentage (%)
 *    101 (4 B) Timed report bitmask group 1 (kWh=1, W=4, V=16, A=32, PF=64)
 *    102 (4 B) Timed report bitmask group 2
 *    103 (4 B) Timed report bitmask group 3
 *    111 (4 B) Report interval group 1 (seconds)
 *    112 (4 B) Report interval group 2 (seconds)
 *    113 (4 B) Report interval group 3 (seconds)
 *    252 (1 B) Configuration lock (0=unlocked, 1=locked)
 *    255 (4 B) Factory reset (write 0x55555555 to reset params only)
 */

metadata {
    definition(
        name:      "Aeotec ZW078 Heavy Duty Smart Switch",
        namespace: "aeotec",
        author:    "Custom",
        importUrl: ""
    ) {
        capability "Switch"
        capability "PowerMeter"           // power (W)
        capability "EnergyMeter"          // energy (kWh)
        capability "VoltageMeasurement"   // voltage (V)
        capability "CurrentMeter"         // amperage (A)
        capability "Refresh"
        capability "Actuator"
        capability "Sensor"
        capability "Configuration"

        command "resetEnergy", [[name: "Reset kWh accumulation counter on the device"]]

        attribute "powerFactor",   "number"   // dimensionless 0.00–1.00
        attribute "powerHigh",     "number"   // session high-water mark (W)
        attribute "powerLow",      "number"   // session low-water mark (W)
        attribute "overloadStatus","string"   // "normal" or "tripped"

        // US product IDs
        fingerprint mfr: "0086", prod: "0003", model: "004E",
                    inClusters: "0x25,0x32,0x56,0x70,0x71,0x72,0x85,0x86",
                    deviceJoinName: "Aeotec ZW078 Heavy Duty Switch"
        // EU product IDs
        fingerprint mfr: "0086", prod: "0103", model: "004E",
                    inClusters: "0x25,0x32,0x56,0x70,0x71,0x72,0x85,0x86",
                    deviceJoinName: "Aeotec ZW078 Heavy Duty Switch (EU)"
    }

    preferences {
        // ── Logging ──────────────────────────────────────────────────────────
        input name: "logEnable",
              type: "bool",
              title: "Enable debug logging (auto-off after 30 min)",
              defaultValue: true

        input name: "txtEnable",
              type: "bool",
              title: "Enable description text logging",
              defaultValue: true

        // ── Auto-reporting (Group 1) ──────────────────────────────────────────
        // Bitmask for Param 101: kWh=1, W=4, V=16, A=32, PF=64
        // Common useful values: 5=kWh+W, 53=kWh+W+V+A, 117=kWh+W+V+A+PF
        input name: "reportGroup1Mask",
              type: "enum",
              title: "Auto-report: which values to send on a schedule",
              description: "Device sends these readings automatically at the interval below",
              options: [
                  "0":   "None (disable timed reports)",
                  "1":   "Energy (kWh) only",
                  "4":   "Power (W) only",
                  "5":   "Energy (kWh) + Power (W)",
                  "16":  "Voltage (V) only",
                  "32":  "Current (A) only",
                  "21":  "Energy (kWh) + Power (W) + Voltage (V)",
                  "37":  "Energy (kWh) + Power (W) + Current (A)",
                  "53":  "Energy (kWh) + Power (W) + Voltage (V) + Current (A)  ← recommended",
                  "117": "Energy (kWh) + Power (W) + Voltage (V) + Current (A) + Power Factor"
              ],
              defaultValue: "53"

        input name: "reportInterval1",
              type: "number",
              title: "Auto-report interval (seconds, 0 = disable)",
              description: "How often the device sends the readings above",
              range: "0..2147483647",
              defaultValue: 300

        // ── Threshold-based watt reporting ──────────────────────────────────
        input name: "thresholdEnable",
              type: "bool",
              title: "Enable threshold-based Watt reporting",
              description: "Sends a report whenever power changes by the thresholds below (either condition triggers)",
              defaultValue: true

        input name: "thresholdWatts",
              type: "number",
              title: "  Watt change threshold — absolute (1–32000 W)",
              range: "1..32000",
              defaultValue: 25

        input name: "thresholdPercent",
              type: "number",
              title: "  Watt change threshold — percentage (1–100 %)",
              range: "1..100",
              defaultValue: 5

        // ── Overload protection ───────────────────────────────────────────────
        input name: "overloadProtection",
              type: "bool",
              title: "Enable current overload protection",
              description: "Relay turns OFF automatically if current exceeds ~39.5A for more than 5 seconds",
              defaultValue: true

        // ── Power-restore behavior ───────────────────────────────────────────
        input name: "powerRestoreState",
              type: "enum",
              title: "Relay state after power outage",
              options: [
                  "0": "Restore last state before outage",
                  "1": "Always OFF",
                  "2": "Always ON"
              ],
              defaultValue: "0"

        // ── Notification type ────────────────────────────────────────────────
        input name: "notificationType",
              type: "enum",
              title: "Switch-state notification sent to hub",
              description: "How the device tells the hub the relay changed state",
              options: [
                  "0": "Nothing (hub must poll)",
                  "1": "Basic Report",
                  "2": "Switch Binary Report  ← recommended"
              ],
              defaultValue: "2"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Lifecycle
// ─────────────────────────────────────────────────────────────────────────────

def installed() {
    logDebug "installed()"
    sendEvent(name: "overloadStatus", value: "normal")
    runIn(3, "configure")
}

def updated() {
    logDebug "updated()"
    if (logEnable) runIn(1800, "logsOff")
    runIn(2, "configure")
}

def configure() {
    logDebug "configure()"
    List<String> cmds = []

    // ── Ensure hub is in Association Group 1 (Lifeline) ──────────────────────
    // Without this the device sends no unsolicited reports to the hub.
    cmds << secureCmd(zwave.associationV2.associationSet(groupingIdentifier: 1, nodeId: [zwaveHubNodeId]))
    cmds << "delay 300"

    // ── Param 3: Overload protection ─────────────────────────────────────────
    cmds << secureCmd(cfgSet(3, 1, overloadProtection ? 1 : 0))

    // ── Param 20: Power-restore state ────────────────────────────────────────
    cmds << secureCmd(cfgSet(20, 1, safeInt(powerRestoreState, 0)))

    // ── Param 80: State-change notification type ─────────────────────────────
    // 0 = none, 1 = Basic Report, 2 = Switch Binary Report (preferred)
    cmds << secureCmd(cfgSet(80, 1, safeInt(notificationType, 2)))

    // ── Param 90: Enable threshold-based watt reports ────────────────────────
    cmds << secureCmd(cfgSet(90, 1, thresholdEnable ? 1 : 0))

    // ── Param 91: Absolute watt threshold (MUST be 2 bytes) ──────────────────
    cmds << secureCmd(cfgSet(91, 2, safeInt(thresholdWatts, 25)))

    // ── Param 92: Percentage watt threshold ──────────────────────────────────
    cmds << secureCmd(cfgSet(92, 1, safeInt(thresholdPercent, 5)))

    // ── Params 101–103: Timed report bitmasks (MUST be 4 bytes each) ─────────
    // Bitmask: kWh=1, W=4, V=16, A=32, PF=64
    int mask1 = safeInt(reportGroup1Mask, 53)
    cmds << secureCmd(cfgSet(101, 4, mask1))
    cmds << secureCmd(cfgSet(102, 4, 0))   // disable group 2 timed reports
    cmds << secureCmd(cfgSet(103, 4, 0))   // disable group 3 timed reports

    // ── Params 111–113: Report intervals (MUST be 4 bytes each) ──────────────
    int interval1 = safeInt(reportInterval1, 300)
    cmds << secureCmd(cfgSet(111, 4, interval1))
    cmds << secureCmd(cfgSet(112, 4, 3600)) // group 2 interval (unused, keep default)
    cmds << secureCmd(cfgSet(113, 4, 3600)) // group 3 interval (unused, keep default)

    // ── Initial poll ─────────────────────────────────────────────────────────
    cmds << "delay 1500"
    cmds += refreshCmds()

    sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZWAVE))
}

// ─────────────────────────────────────────────────────────────────────────────
// Switch commands
// ─────────────────────────────────────────────────────────────────────────────

def on() {
    logDebug "on()"
    sendHubCommand(new hubitat.device.HubAction(
        secureCmd(zwave.switchBinaryV1.switchBinarySet(switchValue: 0xFF)),
        hubitat.device.Protocol.ZWAVE
    ))
}

def off() {
    logDebug "off()"
    sendHubCommand(new hubitat.device.HubAction(
        secureCmd(zwave.switchBinaryV1.switchBinarySet(switchValue: 0x00)),
        hubitat.device.Protocol.ZWAVE
    ))
}

def refresh() {
    logDebug "refresh()"
    sendHubCommand(new hubitat.device.HubMultiAction(refreshCmds(), hubitat.device.Protocol.ZWAVE))
}

def resetEnergy() {
    logDebug "resetEnergy() — sending METER_RESET to device"
    if (txtEnable) log.info "${device.displayName}: resetting energy accumulation counter"
    sendHubCommand(new hubitat.device.HubAction(
        secureCmd(zwave.meterV3.meterReset()),
        hubitat.device.Protocol.ZWAVE
    ))
    // Poll energy immediately after reset; device may also send unsolicited report
    runIn(2, "refresh")
}

// ─────────────────────────────────────────────────────────────────────────────
// Z-Wave message parsing
// ─────────────────────────────────────────────────────────────────────────────

def parse(String description) {
    if (description == "updated") return []

    def cmd = zwave.parse(description, [
        0x20: 1,   // BASIC
        0x25: 1,   // SWITCH_BINARY
        0x32: 3,   // METER v3
        0x56: 1,   // CRC_16_ENCAP
        0x6C: 1,   // SUPERVISION
        0x70: 1,   // CONFIGURATION
        0x71: 3,   // NOTIFICATION (v3) / ALARM — overload protection reports arrive here
        0x72: 2,   // MANUFACTURER_SPECIFIC
        0x85: 2,   // ASSOCIATION
        0x86: 2,   // VERSION
        0x98: 1,   // SECURITY
        0x8E: 2    // MULTI_CHANNEL_ASSOCIATION
    ])

    if (cmd) {
        return zwaveEvent(cmd)
    }
    logDebug "parse: could not decode — ${description}"
    return []
}

// ── Switch Binary Report ──────────────────────────────────────────────────────
def zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
    logDebug "SwitchBinaryReport: value=${cmd.value}"
    def sw = cmd.value ? "on" : "off"
    if (txtEnable) log.info "${device.displayName} switch is ${sw}"
    sendEvent(name: "switch", value: sw, descriptionText: "${device.displayName} switch is ${sw}")
    return []
}

// ── Basic Report (sent when Param 80 = 1) ────────────────────────────────────
def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
    logDebug "BasicReport: value=${cmd.value}"
    def sw = cmd.value ? "on" : "off"
    if (txtEnable) log.info "${device.displayName} switch is ${sw}"
    sendEvent(name: "switch", value: sw, descriptionText: "${device.displayName} switch is ${sw}")
    return []
}

// ── Meter Report ─────────────────────────────────────────────────────────────
def zwaveEvent(hubitat.zwave.commands.meterv3.MeterReport cmd) {
    logDebug "MeterReport: scale=${cmd.scale} value=${cmd.scaledMeterValue}"
    def val = cmd.scaledMeterValue

    switch (cmd.scale) {
        case 0:   // kWh — accumulated energy
            if (txtEnable) log.info "${device.displayName} energy: ${val} kWh"
            sendEvent(name: "energy", value: val, unit: "kWh",
                      descriptionText: "${device.displayName} energy ${val} kWh")
            break

        case 2:   // Watts — instantaneous power
            if (txtEnable) log.info "${device.displayName} power: ${val} W"
            sendEvent(name: "power", value: val, unit: "W",
                      descriptionText: "${device.displayName} power ${val} W")
            trackPowerHighLow(val)
            break

        case 4:   // Volts — AC RMS voltage
            if (txtEnable) log.info "${device.displayName} voltage: ${val} V"
            sendEvent(name: "voltage", value: val, unit: "V",
                      descriptionText: "${device.displayName} voltage ${val} V")
            break

        case 5:   // Amps — AC RMS current
            if (txtEnable) log.info "${device.displayName} current: ${val} A"
            sendEvent(name: "amperage", value: val, unit: "A",
                      descriptionText: "${device.displayName} current ${val} A")
            break

        case 6:   // Power factor — dimensionless 0–1
            if (txtEnable) log.info "${device.displayName} power factor: ${val}"
            sendEvent(name: "powerFactor", value: val,
                      descriptionText: "${device.displayName} power factor ${val}")
            break

        default:
            logDebug "MeterReport: unhandled scale ${cmd.scale} value=${val}"
    }
    return []
}

// ── Overload / Power Management Alarm ────────────────────────────────────────
// Sent by device when Param 3 = 1 and current exceeds ~39.5 A for >5 s
def zwaveEvent(hubitat.zwave.commands.notificationv3.NotificationReport cmd) {
    logDebug "NotificationReport type=${cmd.notificationType} event=${cmd.event}"
    if (cmd.notificationType == 8 && cmd.event == 6) {
        log.warn "${device.displayName}: OVERLOAD PROTECTION TRIPPED — relay turned off by device"
        sendEvent(name: "switch",         value: "off",     descriptionText: "${device.displayName} overload trip")
        sendEvent(name: "overloadStatus", value: "tripped", descriptionText: "${device.displayName} overcurrent detected")
    } else {
        logDebug "NotificationReport: unhandled type=${cmd.notificationType} event=${cmd.event}"
    }
    return []
}

// ── Alarm (older firmware may use this instead of Notification) ───────────────
def zwaveEvent(hubitat.zwave.commands.alarmv2.AlarmReport cmd) {
    logDebug "AlarmReport type=${cmd.zwaveAlarmType} event=${cmd.zwaveAlarmEvent}"
    if (cmd.zwaveAlarmType == 8 && cmd.zwaveAlarmEvent == 6) {
        log.warn "${device.displayName}: OVERLOAD PROTECTION TRIPPED (Alarm CC) — relay off"
        sendEvent(name: "switch",         value: "off",     descriptionText: "${device.displayName} overload trip")
        sendEvent(name: "overloadStatus", value: "tripped", descriptionText: "${device.displayName} overcurrent detected")
    }
    return []
}


// ── CRC-16 Encapsulation ──────────────────────────────────────────────────────
def zwaveEvent(hubitat.zwave.commands.crc16encapv1.Crc16Encap cmd) {
    logDebug "Crc16Encap commandClass=${cmd.commandClass} command=${cmd.command}"
    def inner = zwave.getCommand(cmd.commandClass, cmd.command, cmd.data)
    if (inner) return zwaveEvent(inner)
    logDebug "Crc16Encap: could not decode inner command"
    return []
}

// ── Supervision (Z-Wave Plus supervised delivery) ─────────────────────────────
def zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet cmd) {
    logDebug "SupervisionGet sessionID=${cmd.sessionID}"
    sendHubCommand(new hubitat.device.HubAction(
        secureCmd(zwave.supervisionV1.supervisionReport(
            sessionID:      cmd.sessionID,
            reservedByte:   0,
            moreStatusUpdates: false,
            status:         0xFF
        )),
        hubitat.device.Protocol.ZWAVE
    ))
    def inner = zwave.parse(cmd.encapsulatedCommand, [0x25: 1, 0x32: 3])
    if (inner) return zwaveEvent(inner)
    return []
}

// ── Configuration Report (echo after configure()) ────────────────────────────
def zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
    logDebug "ConfigurationReport param=${cmd.parameterNumber} value=${cmd.scaledConfigurationValue}"
    return []
}

// ── Catch-all ─────────────────────────────────────────────────────────────────
def zwaveEvent(hubitat.zwave.Command cmd) {
    logDebug "Unhandled Z-Wave command: ${cmd}"
    return []
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

/** Wraps a Z-Wave command in security encapsulation when the device is securely
 *  included (S0). Falls back to plain formatting when not secure. */
private String secureCmd(hubitat.zwave.Command cmd) {
    if (getDataValue("zwaveSecurePairingComplete") == "true") {
        return zwaveSecureEncap(cmd)
    }
    return cmd.format()
}

private String secureCmd(String rawCmd) {
    return rawCmd  // already formatted
}

/** Convenience: build a CONFIGURATION_SET command object. */
private hubitat.zwave.Command cfgSet(Integer param, Integer size, Integer value) {
    return zwave.configurationV1.configurationSet(
        parameterNumber:          param,
        size:                     size,
        scaledConfigurationValue: value
    )
}

/** Poll all meter scales plus switch state. */
private List<String> refreshCmds() {
    return [
        secureCmd(zwave.switchBinaryV1.switchBinaryGet()),
        "delay 300",
        secureCmd(zwave.meterV3.meterGet(scale: 2)),   // Watts
        "delay 300",
        secureCmd(zwave.meterV3.meterGet(scale: 0)),   // kWh
        "delay 300",
        secureCmd(zwave.meterV3.meterGet(scale: 4)),   // Volts
        "delay 300",
        secureCmd(zwave.meterV3.meterGet(scale: 5)),   // Amps
    ]
}

/** Track session power high/low watermarks. */
private void trackPowerHighLow(BigDecimal watts) {
    def high = (device.currentValue("powerHigh") ?: watts) as BigDecimal
    def low  = (device.currentValue("powerLow")  ?: watts) as BigDecimal

    if (watts > high) {
        sendEvent(name: "powerHigh", value: watts, unit: "W",
                  descriptionText: "${device.displayName} power high watermark ${watts} W")
    }
    if (watts < low) {
        sendEvent(name: "powerLow", value: watts, unit: "W",
                  descriptionText: "${device.displayName} power low watermark ${watts} W")
    }
}

private int safeInt(def val, int fallback) {
    try { return (val as String).toInteger() } catch (e) { return fallback }
}

private void logsOff() {
    log.warn "${device.displayName}: debug logging disabled (30 min timeout)"
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}

private void logDebug(String msg) {
    if (logEnable) log.debug "${device.displayName}: ${msg}"
}
