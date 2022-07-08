package com.example.myweatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import com.example.myweatherapp.POJO.ModelClass
import com.example.myweatherapp.Utilities.ApiUtilities
import com.example.myweatherapp.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.squareup.picasso.Picasso
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.*


class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var _binding: ActivityMainBinding

    val logTag = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        _binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        supportActionBar?.hide()
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        _binding.mainContainer.isVisible = false

        getCurrentLocation()

        _binding.etToolbar.setOnEditorActionListener { textView, actionId, keyEvent ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                Log.d(logTag,"Just got called in edit text")
                getCityWeather(_binding.etToolbar.text.toString())
                val view = this.currentFocus
                if (view != null) {
                    val imm: InputMethodManager =
                        getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(view.windowToken, 0)
                    _binding.etToolbar.clearFocus()
                }
                true
            } else false
        }
    }

    private fun getCityWeather(city: String) {
        Log.d(logTag,"Just got called")
        _binding.loader.isVisible = true
        ApiUtilities.getApiInterface()?.getCityWeatherData(city, API_KEY)?.enqueue(callback)
    }

    private fun fetchCurrentLocationWeather(latitude: String, longitude: String) {
        _binding.apply {
            mainContainer.isVisible = false
            loader.isVisible = true
        }

        ApiUtilities.getApiInterface()?.getCurrentWeatherData(latitude, longitude, API_KEY)
            ?.enqueue(callback)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("SimpleDateFormat", "SetTextI18n")
    private fun setDataOnViews(body: ModelClass?) {
        body!!
        _binding.apply {
            errorText.isVisible = false
            loader.isVisible = true

            val sdf = SimpleDateFormat("dd/mm/yyyy hh:mm")
            val currentDate = sdf.format(Date())
            tvDateAndTime.text = currentDate

            val tempMax = kelvinToCelsius(body.main.temp_max).toString() + "°C"
            val tempMin = kelvinToCelsius(body.main.temp_min).toString() + "°C"
            val temp = kelvinToCelsius(body.main.temp).toString() + "°C"
            tvTemperature.text = temp

            tvWeatherType.text = body.weather.first().main

            val sunrise = timeStampToLocalDate(body.sys.sunrise.toLong())
            val sunset = timeStampToLocalDate(body.sys.sunset.toLong())
            tvSunset.text = sunset
            tvSunrise.text = sunrise

            tvPressure.text = body.main.pressure.toString()
            tvHumidity.text = body.main.humidity.toString() + " %"
            tvWind.text = body.wind.speed.toString() + " m/s"
            tvCity.text = body.name

            val icon: String = body.weather.first().icon
            val iconUrl = "http://openweathermap.org/img/wn/$icon@2x.png"
            Picasso.get().load(iconUrl).into(ivIcon, object: com.squareup.picasso.Callback {
                override fun onSuccess() {
                    //set animations here
                    Log.d(logTag,"Icon successfully loaded")
                }
                override fun onError(e: java.lang.Exception?) {
                    //do on Error
                    Log.d(logTag,"Icon failed: $e")
                }
            })

            loader.isVisible = false
            mainContainer.isVisible = true
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun timeStampToLocalDate(timeStamp: Long): String {
        return timeStamp.let {
            Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault()).toLocalTime()
        }.toString()
    }


    private fun kelvinToCelsius(temp: Double): Double {
        return temp.minus(273).toBigDecimal().setScale(1, RoundingMode.UP).toDouble()
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    private fun checkPermissions(): Boolean {
        if (
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) return true
        return false
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            ),
            PERMISSION_REQUEST_ACCESS_LOCATION
        )
    }

    companion object {
        private const val PERMISSION_REQUEST_ACCESS_LOCATION = 100
        const val API_KEY = "7096de7da77a602fbf307df1e2331be6"
    }

    private val callback = object : Callback<ModelClass> {
        @SuppressLint("SetTextI18n")
        @RequiresApi(Build.VERSION_CODES.O)
        override fun onResponse(call: Call<ModelClass>, response: Response<ModelClass>) {
            if (response.isSuccessful) setDataOnViews(response.body())
            else _binding.errorText.apply {
                text = response.toString()
                isVisible = true
            }
        }

        override fun onFailure(call: Call<ModelClass>, t: Throwable) {
            _binding.apply {
                loader.isVisible = false
                errorText.text = "Failed to call API"
                errorText.isVisible = true
            }
        }
    }

    private fun getCurrentLocation() {
        if (checkPermissions()) {
            if (isLocationEnabled()) {

                //final latitude and longitude code here 3
                if (ActivityCompat.checkSelfPermission(
                        this,
                        android.Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                        this,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissions()
                    return
                }

                fusedLocationProviderClient.lastLocation.addOnCompleteListener(this) { task ->
                    val location: Location? = task.result
                    if (location == null) Toast.makeText(
                        this,
                        "No Location Data Received",
                        Toast.LENGTH_SHORT
                    ).show()
                    else {
                        Toast.makeText(this, "Location successfully received", Toast.LENGTH_SHORT)
                            .show()
                        // fetch weather here
                        fetchCurrentLocationWeather(
                            location.latitude.toString(),
                            location.longitude.toString()
                        )
                    }
                }
            } else { //setting open here
                Toast.makeText(this, "Turn on location", Toast.LENGTH_SHORT).show()
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(intent)
            }
        } else {
            requestPermissions()
        }
    }
}