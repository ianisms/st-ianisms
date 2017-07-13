/**
 *  Smart Come and Go SmartApp
 *
 *  MIT License
 *  
 *  Copyright (c) 2017 Ian N. Bennett
 *  
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *  
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *  
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

definition(
    name: "Smart Come and Go",
    namespace: "ianisms",
    author: "Ian N. Bennett",
    description: "On arrival: unlocks the door and after door opens, turns on lights and announces arrival",
    category: "Safety & Security",
    iconUrl: "https://lh6.ggpht.com/iLGaQaXCm23ye7jjtAZRIFMpTYAVaSRZ3F74OaOHKuIGB9edj-zvBAbFdGmaduO2cEU=w300",
    iconX2Url: "https://lh6.ggpht.com/iLGaQaXCm23ye7jjtAZRIFMpTYAVaSRZ3F74OaOHKuIGB9edj-zvBAbFdGmaduO2cEU=w300",
    iconX3Url: "https://lh6.ggpht.com/iLGaQaXCm23ye7jjtAZRIFMpTYAVaSRZ3F74OaOHKuIGB9edj-zvBAbFdGmaduO2cEU=w300",
    oauth: true
)

preferences {
    section("Basics") {
        input "enableApp", "boolean", title: "Enable App?", required: true, defaultValue: true
        input "presenceSensors", "capability.presenceSensor", title: "Which presence sensor(s)?", multiple: true
        input "presenceSensorNamePattern", "text", title: "Presense sensor name pattern", description: "Ex.: 's for Joe's iPhone will resolve to Joe", multiple: false, required: false, capitalization: "none"
        input "locks", "capability.lock", title: "Which Lock(s)?", multiple: true
        input "doorContacts", "capability.contactSensor", title: "Which Door Contact(s)?", multiple: true        
    }
        
    section("Motion Control") {
        input "enableMotionControl", "boolean", title: "Use Motion Sensor to Control Unlock, etc?", defaultValue: true
        input "motionSensors", "capability.motionSensor", title: "Which Motion Sensor(s)?", multiple: true
    }
        
    section("Light Control") {
        input "enableLightControl", "boolean", title: "Turn on light after arrival?", defaultValue: true
        input "switches", "capability.switch", title: "Which switch(es)?", multiple: true, required: false
    }

    section("Greetings and Notfications") {
        input "enableGreetings", "boolean", title: "Greet on Arrival?", defaultValue: true
        input "speechDevices", "capability.audioNotification", title: "Speech Device(s):", multiple: true, required: false
        input "enableNotifications", "boolean", title: "Send push notifications?", defaultValue: true
    }

    section(hideable: true, "Debugging") {
        input "logLevel", "enum", title: "Log Level:",  required: false, defaultValue: "Info", options: ["Info","Debug","Trace","Events"]
    }
}

def installed()
{
    init()
}

def updated()
{
    unsubscribe()
    init()
}

def init() {    
    if(enableApp == "true") {
        log("init: App is enabled...")

        state.presence = null
        state.newArrival = false
        state.isDark = false

        subscribe(app, appTouch)     
        subscribe(presenceSensors, "presence.present", presence)
        subscribe(doorContacts, "contact.open", contactOpen)
        subscribe(locks, "lock", lockHandler)   

        if(enableMotionControl == "true") {
            log("init: motion control enabled...")
			subscribe(motionSensors, "motion", motionActive)
        }

        if(enableLightControl == "true") {
            log("init: light control enabled...")
            subscribe(location, "sunrise", sunriseHandler)
            subscribe(location, "sunset", sunsetHandler)
        }
    
        if(enableGreetings == "true") {
            log("init: greetings after arrival enabled...")
        }
    } else {        
        log("init: App is disabled...")
    }
}

def appTouch(evt) {
    speak("state.presence is ${state.presence}, state.newArrival is ${state.newArrival}, and state.isDark is ${state.isDark}")
}

def presence(evt)
{ 
    state.presence = evt.displayName.toLowerCase()
    state.newArrival = true
    def endIndex = state.presence.length()
    if(presenceSensorNamePattern != null) {
        endIndex = state.presence.indexOf(presenceSensorNamePattern)
        state.presence = state.presence.substring(0, (endIndex > 0 ? endIndex : state.presence.length()))
    }

	if(enableMotionControl != "true") {
		welcomeHome()
	}
        
    log("presence: arrival of ${state.presence}")

}

def motionActive(evt) {
	if(evt.value == "active") {
        log("motionActive: motion after arrival")
        welcomeHome()
    }
}

def contactOpen(evt) {	    
    if(state.presence != null && state.newArrival == true) { 
        state.newArrival = false
        
        log("contactOpen: ${evt.displayName} open after new arrival of ${state.presence}")
        
        if (enableGreetings == "true" && speechDevices != null) {   
            speak("Welcome home, ${state.presence}")
        }

        state.presence = null
    }
}

def lockHandler(evt)
{   
	if (state.presence == null && state.newArrival == false && evt.value == "unlocked") {
        state.presence = "Unknown guest"
        state.newArrival = true
        log("lockUnlocked: ${evt.displayName}, manually unlocked")
        welcomeHome()
   }
}

def sunriseHandler(evt) {
    log("sunriseHandler")
    state.isDark = false
}

def sunsetHandler(evt) {
    log("sunsetHandler")
    state.isDark = true
}

private welcomeHome() {
	if(state.presence != null && state.newArrival == true) {
        log("welcomeHome: welcomeHome after arrival")

        sendNotificationEvent("Welcome home ${state.presence}") 
        
		sendLocationEvent(name: "alarmSystemStatus", value: "off")
        
        log("motionActive: disarmed alarm")

        def anyLocked = locks.count{it.currentLock == "unlocked"} != locks.size()

        if (anyLocked) {
            locks.unlock()
            if (enableNotifications == "true") {            
                sendPush("Unlocked locks on arrival of ${state.presence}")
            }
        }

        if (enableLightControl == "true" && state.isDark == true) {
            log("motionActive: turning on lights when dark")
            lightsOn()
        }
    }
}

private lightsOn() {
    if(switches) {
        switches.on()
        log("lightsOn: turned on ${switches}")
    }

    if(hueScene) {
        hueScene.on()
        log("lightsOn: turned on ${hueScene}")
    }
}

private speak(msg) {

    if(speechDevices != null) {
        log("speak: speaking ${msg} on ${speechDevices}")      
        speechDevices.playTextAndResume(msg, 100)
    }
}

private log(msg, level = logLevel) {
    
    switch(level) {
        case "Info":
            log.info(msg)  
            break;
        case "Debug":
            log.debug(msg)  
            break;
        case "Trace":
            log.trace(msg)  
            break;
        case "Events":
            log.trace(msg)  
            sendNotificationEvent(msg) 
            break;
    }
}