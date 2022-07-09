package com.movel.protetivovitima.events;

public class EventsLatLong {
    public Double latitude;
    public double longitude;

    public EventsLatLong(Double latitude, Double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public void setLocations(Double latitude, Double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public Double[] getLocations() {
        return new Double[] {latitude, longitude};
    }
}
