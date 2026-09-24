package dev.metro.launcher.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

private val Context.weatherStore by preferencesDataStore(name = "metro_weather")

data class City(
    val name: String,
    val country: String,
    val lat: Double,
    val lon: Double,
)

data class WeatherNow(
    val temp: Double,
    val feelsLike: Double,
    val humidity: Int,
    val pressure: Int,
    val wind: Double,
    val code: Int,
)

sealed interface WeatherState {
    data object Loading : WeatherState
    data class Data(
        val city: City,
        val now: WeatherNow,
        val hourly: List<HourPoint> = emptyList(),
        val daily: List<DayPoint> = emptyList(),
    ) : WeatherState
    data class Error(val message: String) : WeatherState
}

/** Почасовой прогноз: unix-время + температура + код. */
data class HourPoint(
    val epochSec: Long,
    val temp: Double,
    val code: Int,
)

/** Дневной прогноз: дата ISO + мин/макс + код. */
data class DayPoint(
    val date: String,
    val tMin: Double,
    val tMax: Double,
    val code: Int,
)

/** WMO weather_code -> русское описание (упрощенный WeatherCodes.js из шелла). */
fun weatherText(code: Int): String = when (code) {
    0 -> "Ясно"
    1 -> "Преимущественно ясно"
    2 -> "Переменная облачность"
    3 -> "Пасмурно"
    45, 48 -> "Туман"
    51, 53, 55 -> "Морось"
    56, 57 -> "Ледяная морось"
    61 -> "Небольшой дождь"
    63 -> "Дождь"
    65 -> "Сильный дождь"
    66, 67 -> "Ледяной дождь"
    71 -> "Небольшой снег"
    73 -> "Снег"
    75 -> "Сильный снег"
    77 -> "Снежная крупа"
    80, 81, 82 -> "Ливень"
    85, 86 -> "Снегопад"
    95 -> "Гроза"
    96, 99 -> "Гроза с градом"
    else -> "—"
}

/**
 * Open-Meteo (тот же провайдер что Weather.qml в шелле): без ключей.
 * Город по умолчанию — Москва, меняется через поиск (geocoding API).
 */
class WeatherRepository(private val context: Context) {
    private val cityKey = stringPreferencesKey("city_json")

    val city: Flow<City> = context.weatherStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }.map { prefs ->
        prefs[cityKey]?.let(::parseCity) ?: City("Москва", "Россия", 55.75, 37.61)
    }

    suspend fun refresh(): WeatherState {
        val c = city.first()
        return try {
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=${c.lat}&longitude=${c.lon}" +
                "&current=temperature_2m,relative_humidity_2m,apparent_temperature," +
                "weather_code,pressure_msl,wind_speed_10m" +
                "&hourly=temperature_2m,weather_code" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                "&forecast_days=7&timezone=UTC"
            val root = JSONObject(get(url))
            val cur = root.getJSONObject("current")
            WeatherState.Data(
                city = c,
                now = WeatherNow(
                    temp = cur.getDouble("temperature_2m"),
                    feelsLike = cur.getDouble("apparent_temperature"),
                    humidity = cur.getInt("relative_humidity_2m"),
                    pressure = cur.getInt("pressure_msl"),
                    wind = cur.getDouble("wind_speed_10m"),
                    code = cur.getInt("weather_code"),
                ),
                hourly = parseHourly(root),
                daily = parseDaily(root),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            WeatherState.Error("Не удалось обновить погоду")
        }
    }

    /** До восьми последних доступных часов, заканчивающихся текущим. */
    private fun parseHourly(root: JSONObject): List<HourPoint> {
        return try {
            val h = root.getJSONObject("hourly")
            val times = h.getJSONArray("time")
            val temps = h.getJSONArray("temperature_2m")
            val codes = h.getJSONArray("weather_code")
            val count = minOf(times.length(), temps.length(), codes.length())
            val points = mutableListOf<HourPoint>()
            for (i in 0 until count) {
                val epochSec = isoToEpoch(times.getString(i)) ?: continue
                points.add(
                    HourPoint(
                        epochSec = epochSec,
                        temp = temps.getDouble(i),
                        code = codes.getInt(i),
                    ),
                )
            }
            val nowSec = System.currentTimeMillis() / 1000
            val endIndex = points.indexOfLast { it.epochSec <= nowSec }
            if (endIndex < 0) {
                points.take(8)
            } else {
                points.subList(maxOf(0, endIndex - 7), endIndex + 1)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun isoToEpoch(iso: String): Long? {
        return try {
            runCatching { OffsetDateTime.parse(iso).toEpochSecond() }.getOrNull()
                ?: LocalDateTime.parse(iso).toEpochSecond(ZoneOffset.UTC)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseDaily(root: JSONObject): List<DayPoint> {
        return try {
            val d = root.getJSONObject("daily")
            val times = d.getJSONArray("time")
            val codes = d.getJSONArray("weather_code")
            val maxs = d.getJSONArray("temperature_2m_max")
            val mins = d.getJSONArray("temperature_2m_min")
            List(times.length()) { i ->
                DayPoint(
                    date = times.getString(i),
                    tMin = mins.getDouble(i),
                    tMax = maxs.getDouble(i),
                    code = codes.getInt(i),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun search(query: String): List<City> = withContext(Dispatchers.IO) {
        if (query.trim().length < 2) return@withContext emptyList()
        try {
            val q = URLEncoder.encode(query.trim(), "UTF-8")
            val url = "https://geocoding-api.open-meteo.com/v1/search" +
                "?name=$q&count=5&language=ru&format=json"
            val root = JSONObject(get(url))
            val arr = root.optJSONArray("results") ?: return@withContext emptyList()
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                City(
                    name = o.optString("name"),
                    country = o.optString("country", ""),
                    lat = o.getDouble("latitude"),
                    lon = o.getDouble("longitude"),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun setCity(c: City) {
        context.weatherStore.edit { prefs ->
            prefs[cityKey] = JSONObject()
                .put("name", c.name).put("country", c.country)
                .put("lat", c.lat).put("lon", c.lon).toString()
        }
    }

    private fun parseCity(json: String): City? = try {
        val o = JSONObject(json)
        City(o.getString("name"), o.optString("country", ""), o.getDouble("lat"), o.getDouble("lon"))
    } catch (_: Exception) {
        null
    }

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("User-Agent", "metro-launcher/0.1")
        }
        try {
            val responseCode = conn.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorPreview = conn.errorStream?.bufferedReader()?.use { reader ->
                    val buffer = CharArray(200)
                    val count = reader.read(buffer)
                    if (count > 0) {
                        String(buffer, 0, count).replace(Regex("\\s+"), " ").trim().take(200)
                    } else {
                        ""
                    }
                }.orEmpty()
                val diagnostic = if (errorPreview.isBlank()) "" else ": $errorPreview"
                throw IOException("HTTP $responseCode$diagnostic")
            }
            conn.inputStream.bufferedReader().use { reader -> reader.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
