package de.secretsoft.vatsim_stats.detection;

public final class Haversine {

    private static final double EARTH_RADIUS_NM = 3440.065;

    private Haversine() {
    }

    public static double distanceNm( double lat1, double lon1, double lat2, double lon2 ) {
        double lat1Rad = Math.toRadians( lat1 );
        double lat2Rad = Math.toRadians( lat2 );
        double deltaLatRad = Math.toRadians( lat2 - lat1 );
        double deltaLonRad = Math.toRadians( lon2 - lon1 );

        double a = Math.sin( deltaLatRad / 2 ) * Math.sin( deltaLatRad / 2 )
            + Math.cos( lat1Rad ) * Math.cos( lat2Rad )
            * Math.sin( deltaLonRad / 2 ) * Math.sin( deltaLonRad / 2 );
        double c = 2 * Math.atan2( Math.sqrt( a ), Math.sqrt( 1 - a ) );

        return EARTH_RADIUS_NM * c;
    }
}
