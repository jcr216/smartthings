preferences {
	input("lacrosseUser", "lacrosseUser", title: "Username", description: "LaCrosse website username")
    input("lacrossePassword", "lacrossePassword", title: "Password", description: "LaCrosse website password")
    
    // login through the web interface and use chome dev tools to look for requests to a URL like
    // https://www.lacrossealerts.com/v1/observations/?id=XXXX.  take the value of XXXX and put it here.
    input("lacrosseId", "lacrosseId", title: "LaCrosse ID", description: "ID from LaCrosse website")
}

metadata {
    definition (name: "LaCrosse Temperature Sensor", namespace: "jcr216", author: "John Ryder") {
        capability "Polling"
        capability "Temperature Measurement"
        capability "Relative Humidity Measurement"
        capability "Signal Strength"
        capability "Refresh"
        capability "Battery"

        attribute "lastUpdated", "string"
    }

    tiles {
        valueTile("temperature", "device.temperature", width: 2, height: 2) {
            state("temperature", label:'${currentValue}°',
                    backgroundColors:[
                            [value: 31, color: "#153591"],
                            [value: 44, color: "#1e9cbb"],
                            [value: 59, color: "#90d2a7"],
                            [value: 74, color: "#44b621"],
                            [value: 84, color: "#f1d801"],
                            [value: 95, color: "#d04e00"],
                            [value: 96, color: "#bc2323"]
                    ]
            )
        }
        
        valueTile("humidity", "device.humidity", inactiveLabel: false, decoration: "flat") {
			state "default", label: 'Humidity\n${currentValue}%', unit: ""
		}
        
        valueTile("lastUpdated", "device.lastUpdated", inactiveLabel: false, decoration: "flat") {
        	state("default", label:'${currentValue}', unit: "", decoration: "flat")
		}
        
        valueTile("battery", "device.battery", inactiveLabel: false, decoration: "flat") {
			state "default", label: '${currentValue}', unit: ""
		}
        
        valueTile("lqi", "device.lqi", inactiveLabel: false, decoration: "flat") {
			state "default", label: 'Link\n${currentValue}', unit: ""
		}
        

        main(["temperature"])
        details(["temperature", "lastUpdated", "humidity", "battery", "lqi"])
    }
    
    simulator {
        for (int i = 20; i <= 110; i += 10) {
        	status "Temperature ${i}°": "temperature:${i}"
        }
        status "Invalid message" : "foobar:100.0"
	}

}
// parse events into attributes
def parse(String description) {
    log.debug "Parsing '${description}'"
}

def installed() {
	log.debug "Installed with settings: ${settings}"
}
def updated() {
	log.debug "Updated with settings: ${settings}"
}


def asDateString(Long timestamp) {
    def date = new java.util.Date(timestamp)
	def dateDf = new java.text.SimpleDateFormat("d/M/y") 
    def timeDf = new java.text.SimpleDateFormat("h:m a")
    
    if (location.timeZone) {
        dateDf.setTimeZone(location.timeZone)
        timeDf.setTimeZone(location.timeZone)
    }
    else {
        dateDf.setTimeZone(TimeZone.getTimeZone("America/New_York"))
        timeDf.setTimeZone(TimeZone.getTimeZone("America/New_York"))
    }
    
    return timeDf.format(date) + "\n" + dateDf.format(date)
}

def poll() {
    log.debug "Polling LaCrosse for sensor " + preferences.lacrosseId
    
    def loginParams = [
    	uri: "https://www.lacrossealerts.com/login",
        requestContentType: "application/x-www-form-urlencoded",
        body: [username: preferences.lacrosseUser, password:  preferences.lacrossePassword]
    ]
    
    try
    {
    	// login
    	def cookie
    	httpPost(loginParams) {resp ->
        	log.debug "Login status ['status: ' $resp.status, 'contentType: ' $resp.contentType]"
            
            // several Set-Cookie headers are returned.  each Set-Cookie header has its own value
            // for the session ID.  it appears that only the last session ID in the last Set-Cookie
            // response header is valid.
            resp.headers.each {
            	if (it.name == "Set-Cookie") {
                	cookie = it.value
                }
 			}
        }
        
        log.debug "Got login cookie: $cookie"
        
        // getting the actual values
        
        def pollParams = [
    		uri: "https://www.lacrossealerts.com/v1/observations/?id=" + preferences.lacrosseId,
        	contentType: "application/json",
            headers: [ "Cookie": cookie]
		]
    
       	httpGet(pollParams) { resp ->
        	
            log.debug "Received JSON response: ${resp.data}"
            
            def jsonUnits = resp.data.response.units
            def jsonObs = resp.data.response.obs[0]
            def jsonValues = jsonObs.values
            
            // temp 2
            def temp2 = jsonValues.temp2
            def temp2Units = jsonUnits.temp2
            
            // relative humidity
            def rh = jsonValues.rh
            def rhUnits = jsonUnits.rh
            
            // link quality
            def lqi = jsonValues.linkquality
            def lqiUnits = jsonUnits.linkquality
            
            // timestamp
            def timestamp = jsonObs.timeStamp * 1000L
			def dateString = asDateString(timestamp)
            
            // battery
            def battery;
            if (jsonValues.lowbatt == 0)
            	battery = "Battery Ok"
            else
            	battery = "Battery Low"
            
            // send some events
            log.debug "Sending events"
            sendEvent(name: "lastUpdated", value: dateString, unit: "")
            sendEvent(name: "temperature", value: temp2, unit: temp2Units)
            sendEvent(name: "humidity", value: rh, unit: rhUnits)
            sendEvent(name: "lqi", value: lqi, unit: lqiUnits)
            sendEvent(name: "battery", value: battery, unit: "")
            
            log.debug "Done!"
        }

    }
    catch(Exception e)
    {
    	log.debug "exception: " + e
    }
}

def refresh() {
    log.debug "Executing poll() from refresh()"
    poll()
}
