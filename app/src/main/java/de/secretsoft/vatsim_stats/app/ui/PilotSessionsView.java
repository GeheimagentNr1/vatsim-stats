package de.secretsoft.vatsim_stats.app.ui;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotAirportEvent;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotAirportEventRepository;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotSession;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotSessionRepository;

@Route( value = "", layout = MainLayout.class )
public class PilotSessionsView extends VerticalLayout {

    public PilotSessionsView( PilotSessionRepository pilotSessionRepository,
                               PilotAirportEventRepository pilotAirportEventRepository ) {
        Grid<PilotSession> sessionGrid = new Grid<>( PilotSession.class, false );
        sessionGrid.addColumn( PilotSession::getCallsign ).setHeader( "Callsign" );
        sessionGrid.addColumn( PilotSession::getCid ).setHeader( "CID" );
        sessionGrid.addColumn( PilotSession::getSequenceNumber ).setHeader( "Leg" );
        sessionGrid.addColumn( PilotSession::getPlannedDeparture ).setHeader( "Planned Dep" );
        sessionGrid.addColumn( PilotSession::getPlannedDestination ).setHeader( "Planned Dest" );
        sessionGrid.addColumn( PilotSession::getAircraftShort ).setHeader( "Aircraft" );
        sessionGrid.addColumn( PilotSession::getStatus ).setHeader( "Status" );
        sessionGrid.addColumn( PilotSession::getStartedAt ).setHeader( "Started" );
        sessionGrid.addColumn( PilotSession::getEndedAt ).setHeader( "Ended" );
        sessionGrid.setItems( pilotSessionRepository.findTop200ByOrderByStartedAtDesc() );
        sessionGrid.setHeightFull();

        Grid<PilotAirportEvent> eventGrid = new Grid<>( PilotAirportEvent.class, false );
        eventGrid.addColumn( PilotAirportEvent::getAirportIcao ).setHeader( "Airport" );
        eventGrid.addColumn( PilotAirportEvent::getEventType ).setHeader( "Event" );
        eventGrid.addColumn( PilotAirportEvent::getOccurredAt ).setHeader( "Occurred" );
        eventGrid.setHeightFull();

        sessionGrid.asSingleSelect().addValueChangeListener( change -> {
            PilotSession selected = change.getValue();
            eventGrid.setItems( selected == null
                ? java.util.List.of()
                : pilotAirportEventRepository.findByPilotSessionOrderByOccurredAt( selected ) );
        } );

        setSizeFull();
        add( sessionGrid, eventGrid );
        setFlexGrow( 1, sessionGrid );
        setFlexGrow( 1, eventGrid );
    }
}
