
metadata {
    definition (name: "ComEd", namespace: "scottevtuch", author: "Scott Evtuch", importUrl: "https://raw.githubusercontent.com/scottevtuch/HubitatComEd/master/HubitatComEd.groovy") {
        capability "Refresh"

        attribute "CurrentPrice","number"
        attribute "CurrentState","enum",['Low','Normal','High','Extreme']
        attribute "LowThreshold","enum",['Untriggered','Triggered']
        attribute "HighThreshold","enum",['Untriggered','Triggered']
        attribute "ExtremeThreshold","enum",['Untriggered','Triggered']
    }
}

preferences {
    input name: "refreshRate", type: "number", title: "Refresh Period", description: "Minutes to wait between refreshes", defaultValue: 1
    input name: "lowPrice", type: "decimal", title: "Low Price", description: "A price below this value will trigger the 'Low' state", defaultValue: 4
    input name: "highPrice", type: "decimal", title: "High Price", description: "A price above this value will trigger the 'High' state", defaultValue: 8
    input name: "extremePrice", type: "decimal", title: "Extreme Price", description: "A price above this value will trigger the 'Extreme' state", defaultValue: 16
}

def parse5min(response) {
    try{
        def data = response.data
        def newPrice = data[0]['price'].toDouble()

        setPrice(newPrice)
    } catch (e) {
        log.error("Failed to extract 5min price data: ${e}")
    }
}

def setPrice(Number newPrice) {
    def oldPrice = state.fiveMinPrice

    try{
        if(oldPrice != newPrice) {
            state.fiveMinPrice = newPrice
            sendEvent(name: "CurrentPrice", value: newPrice, isStateChange: true)
            log.info("Price changed to ${newPrice}")
        }
    } catch (e) {
        log.error("Failed to set new price: ${e}")
    }
}

def setState() {
    price = state.fiveMinPrice
    try{
        if(price >= highPrice) {
            if(price >= extremePrice) {
                if (!state.currentState.extreme) {
                    state.currentState.extreme = true
                    sendEvent(name: "ExtremeThreshold", value: "Triggered", isStateChange: true)
                    sendEvent(name: "CurrentState", value: "Extreme", isStateChange: true)
                }
            } else {
                if (state.currentState.extreme) {
                    state.currentState.extreme = false
                    sendEvent(name: "ExtremeThreshold", value: "Untriggered", isStateChange: true)
                }
                if (state.currentState.state != "High") {
                    sendEvent(name: "CurrentState", value: "High", isStateChange: true)
                }
            }
            if (!state.currentState.high) {
                state.currentState.high = true
                sendEvent(name: "HighThreshold", value: "Triggered", isStateChange: true)
            }
            if (state.currentState.low) {
                state.currentState.low = false
                sendEvent(name: "LowThreshold", value: "Untriggered", isStateChange: true)
            }
        } else {
            if (state.currentState.extreme) {
                state.currentState.extreme = false
                sendEvent(name: "ExtremeThreshold", value: "Untriggered", isStateChange: true)
                    sendEvent(name: "CurrentState", value: "Normal", isStateChange: true)
            }
            if (state.currentState.high) {
                state.currentState.high = false
                sendEvent(name: "HighThreshold", value: "Untriggered", isStateChange: true)
                    sendEvent(name: "CurrentState", value: "Normal", isStateChange: true)
            }
            if (price < lowPrice) {
                if (!state.currentState.low) {
                    state.currentState.low = true
                    sendEvent(name: "LowThreshold", value: "Triggered", isStateChange: true)
                    sendEvent(name: "CurrentState", value: "Low", isStateChange: true)
                }
            } else {
                if (state.currentState.low) {
                    state.currentState.low = false
                    sendEvent(name: "LowThreshold", value: "Untriggered", isStateChange: true)
                    sendEvent(name: "CurrentState", value: "Normal", isStateChange: true)
                }
            }
        }
    } catch (e) {
        log.error("Failed to calculate state: ${e}")
    }
}

def refresh() {
    try{
        httpResponse = httpGet([uri:"https://hourlypricing.comed.com/api?type=5minutefeed",timeout:50],{parse5min(it)})
    } catch (e) {
        log.error("Failed to get 5min feed: ${e}")
    }
    setState()
}

def setSchedule() {
    schedule("0 */${refreshRate} * ? * *", refresh)
    log.info("ComEd will refresh every ${refreshRate} minutes")
}

def installed() {
    updated()
}

def updated() {
    state.currentState = [state: "Normal", low: false, high: false, extreme: false]
    sendEvent(name: "LowThreshold", value: "Untriggered", isStateChange: true)
    sendEvent(name: "HighThreshold", value: "Untriggered", isStateChange: true)
    sendEvent(name: "ExtremeThreshold", value: "Untriggered", isStateChange: true)
    state.fiveMinPrice = 0.0 as Number
    setSchedule()
    refresh()
}
