/*
 * ImportHelper.kt
 * Implements the ImportHelper object
 * A ImportHelper provides methods for integrating station files from URLRadio v3
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-22 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package com.jamal2367.urlradio.helpers

import android.content.Context
import androidx.core.net.toUri
import com.jamal2367.urlradio.Keys
import com.jamal2367.urlradio.core.Collection
import com.jamal2367.urlradio.core.Station
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import java.io.File
import java.util.*


/*
 * ImportHelper object
 */
object ImportHelper {


    /* */
    fun removeDefaultStationImageUris(context: Context) {
        val collection: Collection = FileHelper.readCollection(context)
        collection.stations.forEach { station ->
            if (station.image == Keys.LOCATION_DEFAULT_STATION_IMAGE) {
                station.image = String()
            }
            if (station.smallImage == Keys.LOCATION_DEFAULT_STATION_IMAGE) {
                station.smallImage = String()
            }
        }
        CollectionHelper.saveCollection(context, collection, async = false)
    }


    /* Converts older station of type .m3u  */
    fun convertOldStations(context: Context): Any {
        val oldStations: ArrayList<Station> = arrayListOf()
        val oldCollectionFolder: File? =
            context.getExternalFilesDir(Keys.URLRADIO_LEGACY_FOLDER_COLLECTION)
        if (oldCollectionFolder != null && shouldStartImport(oldCollectionFolder)) {
            CoroutineScope(IO).launch {
                var success = false
                // start import
                oldCollectionFolder.listFiles()?.forEach { file ->
                    // look for station files from URLRadio v3
                    if (file.name.endsWith(Keys.URLRADIO_LEGACY_STATION_FILE_EXTENSION)) {
                        // read stream uri and name
                        val station: Station = FileHelper.readStationPlaylist(file.inputStream())
                        station.nameManuallySet = true
                        // detect stream content
                        station.streamContent =
                            NetworkHelper.detectContentType(station.getStreamUri()).type
                        // try to also import station image
                        val sourceImageUri: String = getLegacyStationImageFileUri(context, station)
                        if (sourceImageUri.isNotEmpty()) {
                            // create and add image and small image + get main color
                            station.image = FileHelper.saveStationImage(
                                context,
                                station.uuid,
                                sourceImageUri.toUri(),
                                Keys.SIZE_STATION_IMAGE_CARD,
                                Keys.STATION_IMAGE_FILE
                            ).toString()
                            station.smallImage = FileHelper.saveStationImage(
                                context,
                                station.uuid,
                                sourceImageUri.toUri(),
                                Keys.SIZE_STATION_IMAGE_MAXIMUM,
                                Keys.STATION_IMAGE_FILE
                            ).toString()
                            station.imageColor = ImageHelper.getMainColor(context, sourceImageUri.toUri())
                            station.imageManuallySet = true
                        }
                        // improvise a name if empty
                        if (station.name.isEmpty()) {
                            station.name = file.name.substring(0, file.name.lastIndexOf("."))
                            station.nameManuallySet = false
                        }
                        station.modificationDate = GregorianCalendar.getInstance().time
                        // add station
                        oldStations.add(station)
                        success = true
                    }
                }
                // check for success (= at least one station was found)
                if (success) {
                    // delete files from URLRadio v3
                    oldCollectionFolder.deleteRecursively()
                    // sort and save collection
                    val newCollection: Collection =
                        CollectionHelper.sortCollection(Collection(stations = oldStations))
                    CollectionHelper.saveCollection(context, newCollection)
                }
            }
            // import has been started
            return String()

        } else {
            // import has NOT been started
            return String()
        }
    }


    /* Checks if conditions for a station import are met */
    private fun shouldStartImport(oldCollectionFolder: File): Boolean {
        return oldCollectionFolder.exists() &&
                oldCollectionFolder.isDirectory &&
                oldCollectionFolder.listFiles()?.isNotEmpty()!! &&
                !FileHelper.checkForCollectionFile(oldCollectionFolder)
    }


    /* Gets Uri for station images created by older URLRadio versions */
    private fun getLegacyStationImageFileUri(context: Context, station: Station): String {
        val collectionFolder: File? =
            context.getExternalFilesDir(Keys.URLRADIO_LEGACY_FOLDER_COLLECTION)
        return if (collectionFolder != null && collectionFolder.exists() && collectionFolder.isDirectory) {
            val stationNameCleaned: String = station.name.replace(Regex("[:/]"), "_")
            val legacyStationImage = File("$collectionFolder/$stationNameCleaned.png")
            if (legacyStationImage.exists()) {
                legacyStationImage.toString()
            } else {
                Keys.LOCATION_DEFAULT_STATION_IMAGE
            }
        } else {
            Keys.LOCATION_DEFAULT_STATION_IMAGE
        }
    }


}
