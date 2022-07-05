
metadata {
    definition (name: "ComEd", namespace: "scottevtuch", author: "Scott Evtuch", importUrl: "https://raw.githubusercontent.com/scottevtuch/HubitatComEd/master/HubitatComEd.groovy") {
        capability "Refresh"

        command "updatePredictions"

        attribute "CurrentPrice","number"
        attribute "SpotPrice","number"
        attribute "CurrentState","enum",['Low','Normal','High','Extreme']
        attribute "LowThreshold","enum",['Untriggered','Predicted','Triggered']
        attribute "HighThreshold","enum",['Untriggered','Predicted','Triggered']
        attribute "ExtremeThreshold","enum",['Untriggered','Predicted','Triggered']
    }
}

preferences {
    input name: "refreshRate", type: "number", title: "Refresh Period", description: "Minutes to wait between refreshes", defaultValue: 3
    input name: "lowPrice", type: "decimal", title: "Low Price", description: "A price below this value will trigger the 'Low' state", defaultValue: 5
    input name: "highPrice", type: "decimal", title: "High Price", description: "A price above this value will trigger the 'High' state", defaultValue: 10
    input name: "extremePrice", type: "decimal", title: "Extreme Price", description: "A price above this value will trigger the 'Extreme' state", defaultValue: 20
    input name: "predictionMinutes", type: "number", title: "Prediction Minutes", description: "How many minutes in advance to trigger 'Predicted' state", defaultValue: 120
    input name: "predictionDiffCents", type: "decimal", title: "Prediction Minimum Difference", description: "How many cents the current price must be below a prediction to trigger 'Predicted' state", defaultValue: 3
    input name: "hysteresisCents", type: "decimal", title: "Hysteresis Cents", description: "How many extra cents a price must recover to untrigger states", defaultValue: 1
}

def parse5min(prev, current) {
    try{
        def prev_data = prev.data
        def current_data = current.data

        // Pad with previous hour prices if there are less than 2
        if (current_data.size < 2) {
            prices = prev_data + current_data
        } else {
            prices = current_data
        }

        size = prices.size

        state.currentPrices = []

        sendEvent(name: "SpotPrice", value: prices[0].price, isStateChange: true)

        Double total = 0

        for(Integer i=0;i<size&&i<12;i++) {
            total += prices[i]['price'].toDouble()
            state.currentPrices += prices[i]['price'].toDouble()
        }

        hourNumber = getFormatTime("H",60) as Integer
        prediction = state.predictions[hourNumber][0]

        // Pad the prices with the prediction if there are less than 6
        if (size < 6) {
            total += ( 6 - size ) * prediction
            size = 6
        }

        newPrice = ( total / size ).round(1)

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
        }
    } catch (e) {
        log.error("Failed to set new price: ${e}")
    }
}

def setState() {
    hourNumber = getFormatTime("H",( predictionMinutes + 60 )) as Integer
    prediction = state.predictions[hourNumber][0]

    price = state.fiveMinPrice
    try{
        if(price >= highPrice) {
            if(price >= extremePrice) {
                if (state.currentState.extreme <= 0) {
                    // Trigger Extreme status
                    state.currentState.state = "Extreme"
                    sendEvent(name: "CurrentState", value: "Extreme", isStateChange: true)
                    state.currentState.extreme = 1
                    sendEvent(name: "ExtremeThreshold", value: "Triggered", isStateChange: true)
                }
            } else {
                if (state.currentState.state != "High") {
                    // Set High state
                    state.currentState.state = "High"
                    sendEvent(name: "CurrentState", value: "High", isStateChange: true)
                }
                if (state.currentState.extreme > 0 && price < (extremePrice - hysteresisCents) ) {
                    // Untrigger Extreme status
                    state.currentState.extreme = -1
                    sendEvent(name: "ExtremeThreshold", value: "Untriggered", isStateChange: true)
                }
            }
            if (state.currentState.high <= 0) {
                // Trigger High status
                state.currentState.high = 1
                sendEvent(name: "HighThreshold", value: "Triggered", isStateChange: true)
            }
            if (state.currentState.low > 0 && price > (lowPrice + hysteresisCents)) {
                // Untrigger Low status
                state.currentState.low = -1
                sendEvent(name: "LowThreshold", value: "Untriggered", isStateChange: true)
            }
        } else {
            if (state.currentState.extreme > 0) {
                // Untrigger Extreme status
                state.currentState.extreme = -1
                sendEvent(name: "ExtremeThreshold", value: "Untriggered", isStateChange: true)
            } else if (price <= (prediction - predictionDiffCents) && prediction >= extremePrice && state.currentState.extreme < 0) {
                // Predict Extreme prices
                state.currentState.extreme = 0
                sendEvent(name: "ExtremeThreshold", value: "Predicted", isStateChange: true)
            } else if (prediction < (extremePrice - hysteresisCents) && state.currentState.extreme == 0) {
                // Unpredict Extreme prices
                state.currentState.extreme = -1
                sendEvent(name: "ExtremeThreshold", value: "Untriggered", isStateChange: true)
            }
            if (state.currentState.high > 0 && price < (highPrice - hysteresisCents)) {
                // Untrigger High status
                state.currentState.high = -1
                sendEvent(name: "HighThreshold", value: "Untriggered", isStateChange: true)
            } else if (price <= (prediction - predictionDiffCents) && prediction >= highPrice && state.currentState.high < 0) {
                // Predict High prices
                state.currentState.high = 0
                sendEvent(name: "HighThreshold", value: "Predicted", isStateChange: true)
            } else if (prediction < (highPrice - hysteresisCents) && state.currentState.high == 0) {
                // Unpredict High prices
                state.currentState.high = -1
                sendEvent(name: "HighThreshold", value: "Untriggered", isStateChange: true)
            }
            if (price < lowPrice) {
                if (state.currentState.low < 0) {
                    // Trigger Low status
                    state.currentState.state = "Low"
                    sendEvent(name: "CurrentState", value: "Low", isStateChange: true)
                    state.currentState.low = 1
                    sendEvent(name: "LowThreshold", value: "Triggered", isStateChange: true)
                }
            } else if (price > (lowPrice + hysteresisCents)) {
                if (state.currentState.state != "Normal" && price < (highPrice - hysteresisCents)) {
                    // Resume Normal status
                    state.currentState.state = "Normal"
                    sendEvent(name: "CurrentState", value: "Normal", isStateChange: true)
                }
                if (state.currentState.low > 0) {
                    // Untrigger Low status
                    state.currentState.low = -1
                    sendEvent(name: "LowThreshold", value: "Untriggered", isStateChange: true)
                }
            }
        }
    } catch (e) {
        log.error("Failed to calculate state: ${e}")
    }
}

def refresh() {
    prev_begin = getFormatTime("yyyyMMddHH41",-60)
    current_begin = getFormatTime("yyyyMMddHH01",0)
    end = getFormatTime("yyyyMMddHH01",60)
    try{
        httpGet([uri:"https://hourlypricing.comed.com/api?type=5minutefeed&datestart=${prev_begin}&dateend=${current_begin}",timeout:50,ignoreSSLIssues:true],{prev = it})
        httpGet([uri:"https://hourlypricing.comed.com/api?type=5minutefeed&datestart=${current_begin}&dateend=${end}",timeout:50,ignoreSSLIssues:true],{current = it})
        parse5min(prev, current)
    } catch (e) {
        log.error("Failed to get 5min feed: ${e}")
    }
    setState()
}

def updatePredictions() {
    date = getFormatTime("yyyyMMdd",60)

    try{
        httpResponse = httpGet([uri:"https://hourlypricing.comed.com/rrtp/ServletFeed?type=daynexttoday&date=${date}",timeout:50,ignoreSSLIssues:true],{parsePrediction(it)})
    } catch (e) {
        log.error("Failed to get prediction feed: ${e}")
    }
}

def parsePrediction(response) {
    hourNumber = getFormatTime("H",60) as Integer

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
}

def installed() {
    sendEvent(name: "CurrentState", value: "Normal", isStateChange: true)
    sendEvent(name: "LowThreshold", value: "Untriggered", isStateChange: true)
    sendEvent(name: "HighThreshold", value: "Untriggered", isStateChange: true)
    sendEvent(name: "ExtremeThreshold", value: "Untriggered", isStateChange: true)
    sendEvent(name: "SpotPrice", value: -1, isStateChange: true)
    sendEvent(name: "CurrentPrice", value: -1, isStateChange: true)
    updated()
}

def updated() {
    state.currentState = [state: "Normal", low: -1, high: -1, extreme: -1]
    state.fiveMinPrice = 0.0 as Number
    setSchedule()
    updatePredictions()
    refresh()
}

def private getFormatTime(format, addMinutes=0) {
    now = new Date()
    tz = TimeZone.getTimeZone("America/Chicago")
    Long minute = 60*1000
    future = new Date(now.getTime() + minute * addMinutes)
    return future.format(format)
}
