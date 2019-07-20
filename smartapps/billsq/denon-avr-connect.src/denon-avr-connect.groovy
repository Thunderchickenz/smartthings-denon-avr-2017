definition(
    name: "Denon AVR Connect",
    namespace: "billsq",
    author: "Qian Sheng",
    description: "Manage Denon AV Receivers.",

    category: "My Apps",
    iconUrl: "https://raw.githubusercontent.com/billsq/smartthings-denon-avr-2017/master/smartapps/denon.png",
    iconX2Url: "https://raw.githubusercontent.com/billsq/smartthings-denon-avr-2017/master/smartapps/denon@2x.png",
    iconX3Url: "https://raw.githubusercontent.com/billsq/smartthings-denon-avr-2017/master/smartapps/denon@3x.png"
)

preferences {
    page(name: "deviceDiscovery", title: "Device Setup", content: "deviceDiscovery")
}

def deviceDiscovery() {
    def options = [:]
    def devices = getVerifiedDevices()
    devices.each {
        def value = "${it.value.name} [Model: ${it.value.model}]"
        def key = it.value.mac
        options["${key}"] = value
    }

    ssdpSubscribe()
    ssdpDiscover()
    verifyDevices()

    return dynamicPage(name: "deviceDiscovery", title: "Discovering your Denon AV receivers...", nextPage: "", refreshInterval: 5, install: true, uninstall: true) {
        section {
            input "selectedDevices", "enum", required: false, title: "Select Devices (${options.size() ?: 0} found)", multiple: true, options: options
        }
    }
}

def installed() {
    log.debug "Installed with settings: ${settings}"

    initialize()
}

def updated() {
    log.debug "Updated with settings: ${settings}"

    unsubscribe()
    initialize()
}

def initialize() {
    unsubscribe()
    unschedule()

    ssdpSubscribe()

    if (selectedDevices) {
        addDevices()
    }

    runEvery5Minutes("ssdpDiscover")
}

void ssdpSubscribe() {
    subscribe(location, "ssdpTerm.urn:schemas-denon-com:device:AiosDevice:1", ssdpHandler)
}

void ssdpDiscover() {
    sendHubCommand(new physicalgraph.device.HubAction("lan discovery urn:schemas-denon-com:device:AiosDevice:1", physicalgraph.device.Protocol.LAN))
}

Map verifiedDevices() {
    def devices = getVerifiedDevices()
    def map = [:]
    devices.each {
        def value = "${it.value.name} [Model: ${it.value.model}]"
        def key = it.value.mac
        map["${key}"] = value
    }
    map
}

void verifyDevices() {
    def devices = getDevices().findAll { it?.value?.verified != true }
    devices.each {
        int port = convertHexToInt(it.value.deviceAddress)
        String ip = convertHexToIP(it.value.networkAddress)
        String host = "${ip}:${port}"

        log.debug("verifyDevices host=${host} path=${it.value.ssdpPath}")

        sendHubCommand(new physicalgraph.device.HubAction([
                path: it.value.ssdpPath,
                method: "GET",
                headers: [Host: host]
            ],
            host,
            [callback: deviceDescriptionHandler]
        ))
    }
}

def getVerifiedDevices() {
    getDevices().findAll{ it.value.verified == true }
}

def getDevices() {
    if (!state.devices) {
        state.devices = [:]
    }
    state.devices
}

def addDevices() {
    def devices = getDevices()

	// DEBUG
    log.debug "selectedDevices total ${selectedDevices}"
	selectedDevices.each {
    	//log.debug "selectedDevices it ${it.value}"
    
    }
    //log.debug "devices sum"
    log.debug "devices total ${devices}"
    //log.debug "devices.length ${devices.size()}"
    //devices.each {
    //	log.debug "device it ${it}"
    	//log.debug "device it ${it.value}"
    	//log.debug "device it ${it.value.mac}"
    //}
    
    def selDevices = []
    devices.each {
    	log.debug "device mac: ${it.value.mac}"
        if (it.value.mac == selectedDevices) {
        	selDevices.add(it.value)
        }
    }
    log.debug "selDevices ${selDevices}"
    selDevices.each {
    	log.debug "cur selDevice it ${it}"
    	//log.debug "cur selDevice it.value ${it.value}"
    	def selectedDevice = it
      	log.debug "selectedDevice, getting child devices"
      	//log.debug "selectedDevice2 it ${it.value}"
      	//log.debug "selectedDevice3 it ${selectedDevice.value}"
		def children = getChildDevices()
        log.debug "childDevices ${children}"
        def d = getChildDevices()?.find {
			it.deviceNetworkId == selectedDevice.mac
		}
        log.debug "about to test"
        log.debug "about to test1"
        log.debug "about to test2"
        
        if (!d) {
            //log.debug "Creating Denon AV receiver with dev: ${selectedDevice}"
            //log.debug "Creating Denon AV receiver with val: ${selectedDevice.value}"
            //log.debug "Creating Denon AV receiver with dni: ${selectedDevice.value.mac}"
            addChildDevice("billsq", "Denon AV Receiver", selectedDevice.mac, selectedDevice?.hub, [
                "label": selectedDevice.name,
                "data": [
                    "mac": selectedDevice.mac,
                    "ip": selectedDevice.networkAddress,
                    "port": selectedDevice.deviceAddress,
                    "apiPort": selectedDevice.apiPort,
                    "RenderingControlControlPath": selectedDevice.RenderingControlControlPath,
                    "RenderingControlEventPath": selectedDevice.RenderingControlEventPath,
                    "AVTransportControlPath": selectedDevice.AVTransportControlPath,
                    "AVTransportEventPath": selectedDevice.AVTransportEventPath
                ]
            ])
        }
    }
    // END DEBUG
}

def ssdpHandler(evt) {
    def description = evt.description
    def hub = evt?.hubId

    def parsedEvent = parseLanMessage(description)
    parsedEvent << ["hub": hub]

    log.debug "parsedEvent ${parsedEvent}"

    def devices = getDevices()
    String ssdpUSN = parsedEvent.ssdpUSN.toString()
    if (devices."${ssdpUSN}") {
        def d = devices."${ssdpUSN}"
        if (d.networkAddress != parsedEvent.networkAddress || d.deviceAddress != parsedEvent.deviceAddress) {
            d.networkAddress = parsedEvent.networkAddress
            d.deviceAddress = parsedEvent.deviceAddress
            def child = getChildDevice(parsedEvent.mac)
            if (child) {
                child.sync(parsedEvent.networkAddress, parsedEvent.deviceAddress)
            }
        }
    } else {
        devices << ["${ssdpUSN}": parsedEvent]
    }
}

void deviceDescriptionHandler(physicalgraph.device.HubResponse hubResponse) {
    def body = hubResponse.xml
    def devices = getDevices()
    def device = devices.find { it?.key?.contains(body?.device?.UDN?.text()) }
    if (device) {
        def verified = 0
        device.value << [name: body?.device?.friendlyName?.text(), model:body?.device?.modelName?.text(), serialNumber:body?.device?.serialNum?.text(), apiPort:body?.device?.X_WebAPIPort?.text()]

        if (body?.device?.deviceList?.device?.size()) {
            def devs = body?.device?.deviceList?.device

            devs.each { dev ->
                if (dev.deviceType?.text().contains("MediaRenderer")) {
                    if (dev.serviceList?.service?.size()) {
                        def services = dev.serviceList?.service

                        services.each {
                            if (it.serviceType?.text().contains("RenderingControl")) {
                                def control = it.controlURL?.text()
                                def event = it.eventSubURL?.text()

                                if (!control.startsWith("/")) {
                                    control = "/" + control
                                }

                                if (!event.startsWith("/")) {
                                    event = "/" + event
                                }

                                log.info "Got RenderingControl control=${control} event=${event} for device ${device.value["name"]}"
                                device.value << [RenderingControlControlPath: control, RenderingControlEventPath: event]
                                verified += 1
                            } else if (it.serviceType?.text().contains("AVTransport")) {
                                def control = it.controlURL?.text()
                                def event = it.eventSubURL?.text()

                                if (!control.startsWith("/")) {
                                    control = "/" + control
                                }

                                if (!event.startsWith("/")) {
                                    event = "/" + event
                                }

                                log.info "Got AVTransport control=${control} event=${event} for device ${device.value["name"]}"
                                device.value << [AVTransportControlPath: control, AVTransportEventPath: event]
                                verified += 1
                            }
                        }
                    }
                }
            }
        }

        if (verified == 2) {
            device.value << [verified: true]
        }

        log.debug "deviceDescriptionHandler device.value=${device.value}"
    }
}

private Integer convertHexToInt(hex) {
    Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
    [convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}
