package com.ridesniper.app

import android.app.Application
import com.ridesniper.app.data.database.RideDatabase
import com.ridesniper.app.data.repository.RideRepository
import com.ridesniper.app.settings.SettingsDataStore
import com.ridesniper.app.util.DestinationRiskStore

class RideSniperApp : Application() {

    lateinit var settingsDataStore: SettingsDataStore
        private set
    lateinit var rideRepository: RideRepository
        private set
    lateinit var destinationRiskStore: DestinationRiskStore
        private set

    override fun onCreate() {
        super.onCreate()
        settingsDataStore = SettingsDataStore(this)
        rideRepository = RideRepository(RideDatabase.getInstance(this).rideDao())
        destinationRiskStore = DestinationRiskStore(this)
    }
}
