/**
 *  Smart Sprinkler
 *
 *  Author: brian@bevey.org
 *  Date: 6/14/16
 *
 *  Turn off a sprinkler switch if there's rain coming.
 */

definition(
  name: "Smart Sprinkler",
  namespace: "imbrianj",
  author: "brian@bevey.org",
  description: "Turn off a switch if the forecast has rain.",
  category: "Green Living",
  iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
  iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience%402x.png"
)

preferences {
  section("Zip code?") {
    input "zipcode", "text", title: "Zipcode?", required: false
  }

  section("Things to turn off?") {
    input "sprinkler", "capability.switch", multiple: true
  }
}

def installed() {
  init()
}

def updated() {
  unsubscribe()
  unschedule()
  init()
}

def init() {
  state.lastCheck = ["time": 0, "result": false]
  schedule("0 0,30 * * * ?", scheduleCheck) // Check at top and half-past of every hour
  subscribe(sprinkler, "switch.on", scheduleCheck)
}

def scheduleCheck(evt) {
  def sprinklerOn = sprinkler.findAll { it?.latestValue("switch") == "on" }
  def expireWeather = (now() - (30 * 60 * 1000))
  // Only need to poll if we haven't checked in a while - and if something is on.
  if((now() - (30 * 60 * 1000) > state.lastCheck["time"]) && sprinklerOn) {
    log.info("Something's on - let's check the weather.")
    state.weatherForecast = getWeatherFeature("forecast", zipcode)
  }

  if(((now() - (30 * 60 * 1000) <= state.lastCheck["time"]) && state.lastCheck["result"]) && sprinklerOn) {
    def weather = isStormy(state.weatherForecast)

    if(weather) {
      log.info("Rainy weather - shut everything down.")
      sprinkler?.off()
    }
  } else {
    log.info("Everything looks off, no reason to check weather.")
  }
}

private isStormy(json) {
  def types    = ["rain", "snow", "showers", "sprinkles", "precipitation"]
  def forecast = json?.forecast?.txt_forecast?.forecastday?.first()
  def result   = false

  if(forecast) {
    def text = forecast?.fcttext?.toLowerCase()

    log.debug(text)

    if(text) {
      for (int i = 0; i < types.size() && !result; i++) {
        if(text.contains(types[i])) {
          result = types[i]
        }
      }
    } else {
      log.warn("Got forecast, couldn't parse.")
    }
  } else {
    log.warn("Did not get a forecast: ${json}")
  }

  state.lastCheck = ["time": now(), "result": result]

  return result
}
