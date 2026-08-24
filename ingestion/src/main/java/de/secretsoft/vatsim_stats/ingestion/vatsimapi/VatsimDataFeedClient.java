package de.secretsoft.vatsim_stats.ingestion.vatsimapi;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Component
public class VatsimDataFeedClient {

    private static final String FEED_URL = "https://data.vatsim.net/v3/vatsim-data.json";

    private final RestClient restClient;

    public VatsimDataFeedClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout( (int) Duration.ofSeconds( 10 ).toMillis() );
        requestFactory.setReadTimeout( (int) Duration.ofSeconds( 10 ).toMillis() );
        this.restClient = RestClient.builder().requestFactory( requestFactory ).build();
    }

    public VatsimDataFeed fetchCurrent() {
        try {
            VatsimDataFeed feed = restClient.get().uri( FEED_URL ).retrieve().body( VatsimDataFeed.class );
            if( feed == null ) {
                throw new VatsimFeedException( "VATSIM feed returned an empty body", null );
            }
            return feed;
        } catch( Exception e ) {
            throw new VatsimFeedException( "Failed to fetch/parse VATSIM data feed", e );
        }
    }
}
