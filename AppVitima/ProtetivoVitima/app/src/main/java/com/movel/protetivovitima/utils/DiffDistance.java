package com.movel.protetivovitima.utils;

import com.movel.protetivovitima.events.ExternalLatLong;
import com.movel.protetivovitima.events.InternalLatLong;

public class DiffDistance {
        public static double difference(InternalLatLong InternalLocation, ExternalLatLong ExternalLocation)
        {
            double lat1 = InternalLocation.latitude;
            double lon1 = InternalLocation.longitude;

            double lat2 = ExternalLocation.latitude;
            double lon2 = ExternalLocation.longitude;

            // distance between latitudes and longitudes
            double dLat = Math.toRadians(lat2 - lat1);
            double dLon = Math.toRadians(lon2 - lon1);

            // convert to radians
            lat1 = Math.toRadians(lat1);
            lat2 = Math.toRadians(lat2);

            // apply formulae
            double a = Math.pow(Math.sin(dLat / 2), 2) +
                    Math.pow(Math.sin(dLon / 2), 2) *
                            Math.cos(lat1) *
                            Math.cos(lat2);
            double rad = 6371;
            double c = 2 * Math.asin(Math.sqrt(a));
            return rad * c; // em Kilometros
        }
}
