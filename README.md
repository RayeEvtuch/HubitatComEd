
# Hubitat ComEd

## Summary

This is an **unofficial** [Hubitat device driver](https://docs.hubitat.com/index.php?title=Device_Definition) for the [ComEd Hourly Pricing API](https://hourlypricing.comed.com/hp-api/). It is not sponsored, authorized, or endorsed by ComEd. This driver is subject to breaking changes in their API that may occur in the future.

## Usage

### Install/Setup

* In your Hubitat, navigate to "Drivers code" under the "Developer" heading
* Click "New Driver" in the upper-right hand corner
* Click "Import" in the upper-right hand corner
* Enter the following URL
```
https://raw.githubusercontent.com/ScottEvtuch/HubitatComEd/master/HubitatComEd.groovy
```
* Click "Import"
* Click "Save"

* In your Hubitat, navigate to "Devices"
* Click "Add Device" in the upper-right hand corner
* Select "Virtual" under "Add device manually"
* Enter a device name (it can be anything)
* Select "ComEd" under the "Type" dropdown
* Click "Save Device"

### Customization

* Prices
  * The defaults given should suit most use-cases but you may wish to change the prices associated with Low/High/Extreme status to your unique needs.
* Prediction
  * Increasing "Hysteresis Cents" will result in changes between statuses less often.
  * Increasing "Prediction Minutes" will put threshold in "Predicted" status earlier
  * Increasing "Prediction Minimum Difference" will require more price savings to trigger predictions

### Usage in Rule Machine

Here are some example use cases for utilizing the device driver in Rule Machine automations. All of them use the "Custom Attribute" capability for Trigger Events.

I highly recommend you use the thresholds and states for any automations. If you do decide to use price directly, you should use **CurrentPrice** for any automations since it more intelligently and smoothly predicts the actual price you will pay for usage in a given hour. Using **SpotPrice** will result in dramatic changes in the price and run your automations unnecessarily.

Also keep in mind there are no protections for direct transitions from Low to High without triggering a "Normal" state inbetween. Plan your automations carefully. I provide "CurrentState" and "Threshold" variables for flexibility in programming automations.

#### Trigger AC ahead of high prices

You may wish to pre-cool your house if prices are likely to become high in the near future

```
Trigger Event:
  ComEd reports HighThreshold Predicted
Actions:
  setCoolingSetpoint(68) on Thermostat
```

#### React to high prices

You may wish to shut off high-amperage devices and raise the temperature on your thermostat when prices are high

```
Trigger Event:
  ComEd reports HighThreshold Triggered
Actions:
  Off: EVSE Car Charger
  Off: Bitcoin Miner
  setCoolingSetpoint(78) on Thermostat
```

#### React to low prices

You may wish to opportunistically run high-amperage devices when prices are low

```
Trigger Event:
  ComEd reports LowThreshold Triggered
Actions:
  On: EVSE Car Charger
  On: Bitcoin Miner
```

#### Return to normal

When prices return to normal you will want to return devices to their regular state

```
Trigger Event:
  ComEd reports CurrentState Normal
Actions:
  On: EVSE Car Charger
  Off: Bitcoin Miner
  resumeProgram() on Thermostat
```

## Methodology

ComEd charges customers for **the entire hour** based on the average of all the spot prices for that hour. For this reason we need to agressively predict prices at the beginning of an hour because we do not know what the rest of the prices will be.

To predict prices I use a mixture of:

* All prices published so far for current hour
* The most recent prices from the previous hour (until we have two spot prices for the current hour)
* ComEd's published prediction for the hour (until we have six spot prices for the current hour)

In my experience I have found this provides a decent balance of semi-accurate predictions and smooth price changes throughout the day. Unfortunately there are definitely edge-cases where it is impossible to predict prices will dramatically change near the end of an hour.
