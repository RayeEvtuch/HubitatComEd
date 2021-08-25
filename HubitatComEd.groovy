
metadata {
    definition (name: "ComEd", namespace: "scottevtuch", author: "Scott Evtuch", importUrl: "https://raw.githubusercontent.com/scottevtuch/HubitatComEd/master/HubitatComEd.groovy") {
        capability "Refresh"

        command "updatePredictions"

        attribute "CurrentPrice","number"
        attribute "CurrentState","enum",['Low','Normal','High','Extreme']
        attribute "LowThreshold","enum",['Untriggered','Predicted','Triggered']
        attribute "HighThreshold","enum",['Untriggered','Predicted','Triggered']
        attribute "ExtremeThreshold","enum",['Untriggered','Predicted','Triggered']
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
        size = data.size
        log.info("Found ${size} 5min prices")

        Double total = 0

        for(Integer i=0;i<size&&i<12;i++) {
            log.info("Adding ${data[i]['price']} to average")
            total += data[i]['price'].toDouble()
        }

        log.info("Real average is ${ ( total / size ).round(1) }")

        hourNumber = getFormatTime("H",1) as Integer
        prediction = state.predictions[hourNumber][0]

        log.info("Current hour prediction is ${prediction}")

        if (size < 12) {
            log.info("Adding prediction times ${12 - size}")
            total += ( 12 - size ) * prediction
        }

        newPrice = ( total / 12 ).round(1)

        log.info("Best estimate price is ${newPrice}")

        setPrice(newPrice)
    } catch (e) {
        log.error("Failed to extract 5min price data: ${e}")
    }
}

def setPrice(Number newPrice) {
    oldPrice = state.fiveMinPrice

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
    hourNumber = getFormatTime("H",2) as Integer
    prediction = state.predictions[hourNumber][0]

    log.info("Next hour prediction is ${prediction}")

    price = state.fiveMinPrice
    try{
        if(price >= highPrice) {
            if(price >= extremePrice) {
                if (state.currentState.extreme <= 0) {
                    // Trigger Extreme status
                    state.currentState.extreme = 1
                    sendEvent(name: "ExtremeThreshold", value: "Triggered", isStateChange: true)
                    state.currentState.state = "Extreme"
                    sendEvent(name: "CurrentState", value: "Extreme", isStateChange: true)
                }
            } else {
                if (state.currentState.extreme > 0) {
                    // Untrigger Extreme status
                    state.currentState.extreme = -1
                    sendEvent(name: "ExtremeThreshold", value: "Untriggered", isStateChange: true)
                }
                if (state.currentState.state != "High") {
                    // Set High state
                    state.currentState.state = "High"
                    sendEvent(name: "CurrentState", value: "High", isStateChange: true)
                }
            }
            if (state.currentState.high < 0) {
                // Trigger High status
                state.currentState.high = 1
                sendEvent(name: "HighThreshold", value: "Triggered", isStateChange: true)
            }
            if (state.currentState.low > 0) {
                // Untrigger Low status
                state.currentState.low = -1
                sendEvent(name: "LowThreshold", value: "Untriggered", isStateChange: true)
            }
        } else {
            if (state.currentState.extreme > 0) {
                // Untrigger Extreme status
                state.currentState.extreme = -1
                sendEvent(name: "ExtremeThreshold", value: "Untriggered", isStateChange: true)
            } else if (prediction >= extremePrice && state.currentState.extreme < 0) {
                // Predict Extreme prices
                state.currentState.extreme = 0
                sendEvent(name: "ExtremeThreshold", value: "Predicted", isStateChange: true)
            } else if (prediction < extremePrice && state.currentState.extreme >= 0) {
                // Unpredict Extreme prices
                state.currentState.extreme = -1
                sendEvent(name: "ExtremeThreshold", value: "Untriggered", isStateChange: true)
            }
            if (state.currentState.high > 0) {
                // Untrigger High status
                state.currentState.high = -1
                sendEvent(name: "HighThreshold", value: "Untriggered", isStateChange: true)
            } else if (prediction >= highPrice && state.currentState.high < 0) {
                // Predict High prices
                state.currentState.high = 0
                sendEvent(name: "HighThreshold", value: "Predicted", isStateChange: true)
            } else if (prediction < highPrice && state.currentState.high >= 0) {
                // Unpredict High prices
                state.currentState.high = -1
                sendEvent(name: "HighThreshold", value: "Untriggered", isStateChange: true)
            }
            if (price < lowPrice) {
                if (state.currentState.low < 0) {
                    // Trigger Low status
                    state.currentState.low = 1
                    sendEvent(name: "LowThreshold", value: "Triggered", isStateChange: true)
                    state.currentState.state = "Low"
                    sendEvent(name: "CurrentState", value: "Low", isStateChange: true)
                }
            } else {
                if (state.currentState.low > 0) {
                    // Untrigger Low status
                    state.currentState.low = -1
                    sendEvent(name: "LowThreshold", value: "Untriggered", isStateChange: true)
                }
                if (state.currentState.state != "Normal") {
                    // Resume Normal status
                    state.currentState.state = "Normal"
                    sendEvent(name: "CurrentState", value: "Normal", isStateChange: true)
                }
            }
        }
    } catch (e) {
        log.error("Failed to calculate state: ${e}")
    }
}

def refresh() {
    begin = getFormatTime("yyyyMMddHH30",-1)
    end = getFormatTime("yyyyMMddHH00",1)
    try{
        httpResponse = httpGet([uri:"https://hourlypricing.comed.com/api?type=5minutefeed&datestart=${begin}&dateend=${end}",timeout:50],{parse5min(it)})
    } catch (e) {
        log.error("Failed to get 5min feed: ${e}")
    }
    setState()
}

def updatePredictions() {
    date = getFormatTime("yyyyMMdd",1)

    try{
        httpResponse = httpGet([uri:"https://hourlypricing.comed.com/rrtp/ServletFeed?type=daynexttoday&date=${date}",timeout:50],{parsePrediction(it)})
    } catch (e) {
        log.error("Failed to get prediction feed: ${e}")
    }
}

def parsePrediction(response) {
    hourNumber = getFormatTime("H",1) as Integer

    try{
        def json = response.data[0].text().replaceAll(/Date.UTC\([\d,]+\), /,'')

        def data = parseJson(json)

        state.predictions = data
    } catch (e) {
        log.error("Failed to parse prediction: ${e}")
    }
}

def setSchedule() {
    schedule("0 1 0,23 ? * *", updatePredictions)
    schedule("0 */${refreshRate} * ? * *", refresh)
    log.info("ComEd will refresh every ${refreshRate} minutes")
}

def installed() {
    updated()
}

def updated() {
    state.currentState = [state: "Normal", low: -1, high: -1, extreme: -1]
    sendEvent(name: "LowThreshold", value: "Untriggered", isStateChange: true)
    sendEvent(name: "HighThreshold", value: "Untriggered", isStateChange: true)
    sendEvent(name: "ExtremeThreshold", value: "Untriggered", isStateChange: true)
    state.fiveMinPrice = 0.0 as Number
    setSchedule()
    refresh()
}

def private getFormatTime(format, addHours=0) {
    now = new Date()
    tz = TimeZone.getTimeZone("America/Chicago")
    Long hour = 3600*1000
    future = new Date(now.getTime() + hour * addHours)
    return future.format(format)
}
