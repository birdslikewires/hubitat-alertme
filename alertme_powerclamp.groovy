/*
 * 
 *  AlertMe Power Clamp Driver v1.00 (13th July 2020)
 *	
 */


metadata {

	definition (name: "AlertMe Power Clamp", namespace: "AlertMe", author: "Andrew Davison") {

		capability "Battery"
		capability "Initialize"
        capability "Power Meter"
        capability "Temperature Measurement"

        attribute "batteryWithUnit", "string"
        attribute "batteryVoltage", "string"
        attribute "batteryVoltageWithUnit", "string"
        attribute "deviceType", "string"
        attribute "powerWithUnit", "string"
        attribute "temperatureWithUnit", "string"

		fingerprint profileId: "C216", inClusters: "00F0,00F3,00EF", outClusters: "", manufacturer: "AlertMe.com", model: "Power Clamp", deviceJoinName: "AlertMe Power Clamp"
        
	}

}


preferences {
	
	//input name: "powerOffset", type: "int", title: "Power Offset", description: "Offset in Watts (default: 0)", defaultValue: "0"
	input name: "vMinSetting",type: "decimal",title: "Battery Minimum Voltage",description: "Low battery voltage (default: 2.5)",defaultValue: "2.5",range: "2.1..2.8"
    input name: "vMaxSetting",type: "decimal",title: "Battery Maximum Voltage",description: "Full battery voltage (default: 3.0)",defaultValue: "3.0",range: "2.9..3.4"
	input name: "infoLogging",type: "bool",title: "Enable logging",defaultValue: true
	input name: "debugLogging",type: "bool",title: "Enable debug logging",defaultValue: false
	
}

void initialize() {
	
	configure()
	updated()

    sendEvent(name:"deviceType",value:"Unconfirmed",isStateChange: false)
    sendEvent(name:"battery",value:0,unit: "%", isStateChange: false)
    sendEvent(name:"batteryWithUnit",value:"Unknown",isStateChange: false)
	sendEvent(name:"batteryVoltage", value: 0, unit: "V", isStateChange: false)
	sendEvent(name:"batteryVoltageWithUnit", value: "Unknown", isStateChange: false)
	sendEvent(name:"power", value: 0, unit: "W", isStateChange: false)
	sendEvent(name:"powerWithUnit", value: "Unknown", isStateChange: false)
	sendEvent(name:"temperature", value: 0, unit: "C", isStateChange: false)
	sendEvent(name:"temperatureWithUnit", value: "Unknown", unit: "C", isStateChange: false)

	logging("Initialised!",100)
	
}

void debugLogOff(){

	device.updateSetting("debugLogging",[value:"false",type:"bool"])
	logging("Debug logging disabled.",101)

}

void updated(){

	unschedule()

	logging("Info logging is: ${infoLogging == true}",101)
	logging("Debug logging is: ${debugLogging == true}",101)
	
	if (debugLogging) runIn(1800,debugLogOff)

}

def parse(String description) {
	    
    def descriptionMap = zigbee.parseDescriptionAsMap(description)
    
	if (descriptionMap) {
	
        logging("splurge: ${descriptionMap}",1)
		outputValues(descriptionMap)

	} else {
		
		logging("PARSE FAILED: $description",101)

	}	

}

def refresh() {

    def fixedDescription = "catchall: C216 00F0 02 02 0040 00 B893 01 00 0000 FB 01 1BBC5F621C870B1401B4FF0000"
    logging("Attempting to parse fixed data where battery = 2.951 and temperature = 17.25",1)
    parse(fixedDescription)

}

def configure() {

	logging("There's really nothing to be configured on this device.",1)

}

def randomRefreshAttempt() {

    def fixedDescription = "C216 00F0 02 02 0040 00 B893 01 00 0000 FB 01 1BB31D571C870B1401B4FF0000"
    
    parse(fixedDescription)
    
	logging("Attempting to parse fixed data...",1)

}

def outputValues(map) {

	String[] receivedData = map.data

	Map alertMeDevices = [
	    '1B': 'Power Clamp',
	    '1C': 'Switch',
	    '1D': 'Key Fob'
	]

	if (map.clusterId == "00EF") {

		// Cluster 00EF deals with power usage information.

		if (receivedData.length == 2) {

			// If we receive two 8-bit values, we know it's the instant reading. 

			def powerValueHex = "undefined"
			def int powerValue = 0

			powerValueHex = receivedData[0..1].reverse().join()
			logging("${device} : power byte flipped : ${powerValueHex}",1)

			powerValue = zigbee.convertHexToInt(powerValueHex)
			logging("${device} : power sensor reports : ${powerValue}",1)

			//if (powerOffset != null) powerValue = powerValue + powerOffset
			logging("${device} : Power Usage : ${powerValue} W",100)

			sendEvent(name: "power", value: powerValue, unit: "W", isStateChange: true)
			sendEvent(name: "powerWithUnit", value: "${powerValue} W", isStateChange: true)

		} else if (receivedData.length == 9) {

			// Nine 8-bit values and we've received the power usage summary. Not seen anyone work this one out yet.

			logging("Power usage summary data received, but we don't know how to decipher this yet.",1)
			logging("Received clusterId ${map.clusterId} with ${receivedData.length} values: ${receivedData}",1)

		} else {

			logging("Unknown power usage information! Please report this to the developer.",101)
			logging("Received clusterId ${map.clusterId} with ${receivedData.length} values: ${receivedData}",101)

		}

	} else if (map.clusterId == "00F0") {

		// Cluster 00F0 deals with device status, including battery and temperature data.

		// Look up the real device type from the AlertMe device map.
        def deviceValue = receivedData[0]
        sendEvent(name:"deviceType",value:alertMeDevices[deviceValue],isStateChange: false)

        // Report the battery voltage and calculated percentage.
		def batteryValue = "undefined"
		batteryValue = receivedData[5..6].reverse().join()
		logging("${device} : batteryValue byte flipped : ${batteryValue}",1)
		batteryValue = zigbee.convertHexToInt(batteryValue) / 1000
		parseAndSendBatteryStatus(batteryValue)
		sendEvent(name:"batteryVoltage", value: batteryValue, unit: "V", isStateChange: false)
		sendEvent(name:"batteryVoltageWithUnit", value: "${batteryValue} V", isStateChange: false)

		def temperatureValue = "undefined"
		temperatureValue = receivedData[7..8].reverse().join()
		logging("${device} : temperatureValue byte flipped : ${temperatureValue}",1)
        temperatureValue = zigbee.convertHexToInt(temperatureValue) / 16
		logging("${device} : Temperature : ${temperatureValue} C",100)

		sendEvent(name:"temperature", value: temperatureValue, unit: "C", isStateChange: false)
		sendEvent(name:"temperatureWithUnit", value: "${temperatureValue} °C", unit: "C", isStateChange: false)

	} else if (map.clusterId == "00F3") {

		logging("I think this is the tamper button status. We don't know what to do with this yet.",100)
		logging("Received clusterId ${map.clusterId} with ${receivedData.length} values: ${receivedData}",100)

	} else {

		logging("Unknown cluster received! Please report this to the developer.",101)
		logging("Received clusterId ${map.clusterId} with ${receivedData.length} values: ${receivedData}",101)

	}

	return null

}

void parseAndSendBatteryStatus(BigDecimal vCurrent) {

    BigDecimal bat = 0
    BigDecimal vMin = vMinSetting == null ? 2.5 : vMinSetting
    BigDecimal vMax = vMaxSetting == null ? 3.0 : vMaxSetting    

    if(vMax - vMin > 0) {
        bat = ((vCurrent - vMin) / (vMax - vMin)) * 100.0
    } else {
        bat = 100
    }
    bat = bat.setScale(0, BigDecimal.ROUND_HALF_UP)
    bat = bat > 100 ? 100 : bat
    
    vCurrent = vCurrent.setScale(3, BigDecimal.ROUND_HALF_UP)

    logging("${device} : Battery : $bat% ($vCurrent V)", 100)
    sendEvent(name:"battery",value:bat,unit: "%", isStateChange: false)
    sendEvent(name:"batteryWithUnit",value:"${bat} %",isStateChange: false)

}

private boolean logging(message, level) {

    boolean didLog = false
     
    Integer logLevelLocal = 0

    if (infoLogging == null || infoLogging == true) {
        logLevelLocal = 100
    }

    if (debugLogging == true) {
        logLevelLocal = 1
    }
     
    if (logLevelLocal != 0){

        switch (logLevelLocal) {
	        case 1:  
	            if (level >= 1 && level < 99) {
	                log.debug "$message"
	                didLog = true
	            } else if (level == 100) {
	                log.info "$message"
	                didLog = true
	            } else if (level > 100) {
					log.warn "$message"
	                didLog = true
	            }
	        break
	        case 100:  
	            if (level == 100) {
	                log.info "$message"
	                didLog = true
	            } else if (level > 100) {
					log.warn "$message"
	                didLog = true
	            }
	        break
        }

    }

    return didLog

}