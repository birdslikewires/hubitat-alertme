/*
 * 
 *  Salus SP600 Smart Plug Driver v1.01 (7th August 2020)
 *	
 */


metadata {

	definition (name: "Salus SP600 Smart Plug", namespace: "Salus", author: "Andrew Davison", importUrl: "https://raw.githubusercontent.com/birdslikewires/hubitat/master/salus_sp600.groovy") {

		capability "Actuator"
		capability "Initialize"
		capability "Outlet"
		capability "PowerMeter"
        capability "PresenceSensor"
		capability "Refresh"
		capability "Switch"

		command "simpleMeteringPowerRefresh"
		command "simpleMeteringPowerConfig"
		command "zigbeeOnOffConfig"
		command "zigbeeOnOffRefresh"

		attribute "powerWithUnit", "string"

		fingerprint profileId: "0104", inClusters: "0000, 0001, 0003, 0004, 0005, 0006, 0402, 0702, FC01", outClusters: "0019", manufacturer: "Computime", model: "SP600", deviceJoinName: "Salus SP600 Smart Plug"

	}

}


preferences {
		
	input name: "infoLogging",type: "bool",title: "Enable logging",defaultValue: true
	input name: "debugLogging",type: "bool",title: "Enable debug logging",defaultValue: true
	input name: "traceLogging",type: "bool",title: "Enable trace logging",defaultValue: true

}



def initialize() {
	
	log.warn "Initialize Called!"
    
	configure()
	
}


def configure() {
	// Runs after installed() whenever a device is paired or rejoined.

	state.presenceUpdated = 0

	device.updateSetting("infoLogging",[value:"true",type:"bool"])
	device.updateSetting("debugLogging",[value:"true",type:"bool"])
	device.updateSetting("traceLogging",[value:"true",type:"bool"])

	// Remove any scheduled events.
	unschedule()

	// Bunch of zero or null values.
	sendEvent(name: "power", value: 0, unit: "W", isStateChange: false)
	sendEvent(name: "powerWithUnit", value: "unknown", isStateChange: false)
	sendEvent(name: "presence", value: "present")
	sendEvent(name: "switch", value: "unknown")

    // Set the operating and reporting modes and turn off advanced logging.
	zigbee.onOffConfig() + simpleMeteringPowerConfig() + zigbee.onOffRefresh() + simpleMeteringPowerRefresh()


	// WHY DO THESE FUNCTIONS NOT RNU (OR COMPLETE) IF SOMETHING ELSE COMES AFTER EHRE?!?!?!


	//runIn(240,debugLogOff)
	//runIn(120,traceLogOff)

	// All done.
	//logging("${device} : Configured", "info")

}


def updated() {

	// Runs whenever preferences are saved.

	loggingStatus()
	refresh()

}

void loggingStatus() {

	log.info "${device} : Logging : ${infoLogging == true}"
	log.debug "${device} : Debug Logging : ${debugLogging == true}"
	log.trace "${device} : Trace Logging : ${traceLogging == true}"

}


void traceLogOff(){
	
	device.updateSetting("traceLogging",[value:"false",type:"bool"])
	log.trace "${device} : Trace Logging : Automatically Disabled"

}


void debugLogOff(){
	
	device.updateSetting("debugLogging",[value:"false",type:"bool"])
	log.debug "${device} : Debug Logging : Automatically Disabled"

}


void reportToDev(map) {

	String[] receivedData = map.data

	def receivedDataCount = ""
	if (receivedData != null) {
		receivedDataCount = "${receivedData.length} bits of "
	}

	logging("${device} : UNKNOWN DATA! Please report these messages to the developer.", "warn")
	logging("${device} : Received cluster: ${map.cluster}, clusterId: ${map.clusterId}, attrId: ${map.attrId}, command: ${map.command} with value: ${map.value} and ${receivedDataCount}data: ${receivedData}", "warn")
	logging("${device} : Splurge! ${map}", "warn")

}


def zigbeeOnOffRefresh() {

	logging("${device} : Relay State : Refreshing...", "info")
	zigbee.onOffRefresh()

}


def zigbeeOnOffConfig() {

	logging("${device} : on off configging ", "debug")
	zigbee.onOffConfig()
}

def simpleMeteringPowerRefresh() {

	logging("${device} : Power : Refreshing...", "info")
	zigbee.readAttribute(0x0702, 0x0400)

}

def simpleMeteringPowerConfig() {

	logging("${device} : Power Reporting : Configuring", "info")

	minReportTime=10
	maxReportTime=20
	reportableChange=0x01

	zigbee.configureReporting(0x0702, 0x0400, DataType.INT24, minReportTime, maxReportTime, reportableChange)

}


def off() {

	zigbee.off()

}

def on() {

	zigbee.on()

}

def refresh() {
	
	logging("${device} : Refreshing...", "info")
	simpleMeteringPowerRefresh() + zigbee.onOffRefresh()

}


def checkPresence() {

	// Check how long ago the last presence report was received.
	// These devices report uptime every 60 seconds. If no reports are seen after 240 seconds, we know something is wrong.

	long timeNow = new Date().time / 1000

	if (state.presenceUpdated > 0) {
		if (timeNow - state.presenceUpdated > 240) {
			sendEvent(name: "presence", value: "not present")
			logging("${device} : No recent presence reports.", "warn")
			logging("${device} : checkPresence() : ${timeNow} - ${state.presenceUpdated} > 240", "trace")
		} else {
			sendEvent(name: "presence", value: "present")
			logging("${device} : Recent presence report received.", "debug")
			logging("${device} : checkPresence() : ${timeNow} - ${state.presenceUpdated} < 240", "trace")
		}
	} else {
		logging("${device} : checkPresence() : Waiting for first presence report.", "debug")
	}

}


def parse(String description) {

	// Primary parse routine.

	logging("${device} : Parse!", "debug")
	logging("${device} : Description : $description", "debug")

	def descriptionMap = zigbee.parseDescriptionAsMap(description)

	if (descriptionMap) {
	
		processMap(descriptionMap)

	} else {
		
		reportToDev(descriptionMap)

	}	

}


void processMap(map) {

	logging("${device} : processMap() : ${map}", "trace")

	if (map.cluster == "0006" || map.clusterId == "0006") {

		// Relay configuration and response handling.

		if (map.command == "00") {

			logging("${device} : hmm. don't know what this is right now. : ${map}", "trace")

		} else if (map.command == "01" || map.command == "0A") {

			// Relay States

			// 01 - Prompted Refresh
			// 0A - Automated Refresh

			if (map.value == "01") {

				sendEvent(name: "switch", value: "on")
				logging("${device} : Switch : On", "info")

			} else {

				sendEvent(name: "switch", value: "off")
				logging("${device} : Switch : Off", "info")

			}

		} else if (map.command == "07") {

			// Relay Configuration

			logging("${device} : Relay Configuration : Successful", "info")


		} else if (map.command == "0B") {

			// Relay State Confirmations

			String[] receivedData = map.data
			def String powerStateHex = receivedData[0]

			if (powerStateHex == "01") {

				sendEvent(name: "switch", value: "on")
				logging("${device} : Switch Confirmed : On", "info")

			} else {

				sendEvent(name: "switch", value: "off")
				logging("${device} : Switch Confirmed : Off", "info")

			}

		} else {

			reportToDev(map)

		}

	} else if (map.cluster == "0702" || map.clusterId == "0702") {

		// Power configuration and response handling.

		if (map.command == "07") {

			// Power Configuration

			logging("${device} : Power Reporting Configuration : Successful", "info")

		} else if (map.command == "01" || map.command == "0A") {

			// Power Usage

			// 01 - Prompted Refresh
			// 0A - Automated Refresh

			def int powerValue = zigbee.convertHexToInt(map.value)
			sendEvent(name: "power", value: powerValue, unit: "W", isStateChange: false)
			sendEvent(name: "powerWithUnit", value: "${powerValue} W", isStateChange: false)
			logging("${device} : power hex value : ${map.value}", "trace")
			logging("${device} : power sensor reports : ${powerValue}", "debug")

			if (map.command == "01") {
				// If this has been requested by the user, return the value in the log.
				logging("${device} : Power : ${powerValue} W", "info")

			}

			if (powerValue > 0) {
				sendEvent(name: "switch", value: "on", isStateChange: false)		// In case we've initialised and never toggle the relay.
			}

		} else {

			reportToDev(map)

		}

	} else if (map.cluster == "8021" || map.clusterId == "8021") {

		logging("${device} : skipping discovery message : ${map}", "trace")

	} else {

		reportToDev(map)

	}
	
}


def logging(String message, String level) {

	if (level == "error") {
		log.error "$message"
		didLog = true
	}

	if (level == "warn") {
		log.warn "$message"
		didLog = true
	}

	if (traceLogging && level == "trace") {
		log.trace "$message"
		didLog = true
	}

	if (debugLogging && level == "debug") {
		log.debug "$message"
		didLog = true
	}

	if (infoLogging && level == "info") {
		log.info "$message"
		didLog = true
	}

}