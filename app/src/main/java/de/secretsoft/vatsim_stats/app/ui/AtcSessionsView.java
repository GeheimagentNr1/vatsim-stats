package de.secretsoft.vatsim_stats.app.ui;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import de.secretsoft.vatsim_stats.ingestion.domain.AtcSession;
import de.secretsoft.vatsim_stats.ingestion.domain.AtcSessionRepository;

@Route( value = "atc-sessions", layout = MainLayout.class )
public class AtcSessionsView extends VerticalLayout {

    public AtcSessionsView( AtcSessionRepository atcSessionRepository ) {
        Grid<AtcSession> grid = new Grid<>( AtcSession.class, false );
        grid.addColumn( AtcSession::getCallsign ).setHeader( "Callsign" );
        grid.addColumn( AtcSession::getCid ).setHeader( "CID" );
        grid.addColumn( AtcSession::getFacility ).setHeader( "Facility" );
        grid.addColumn( AtcSession::getStartedAt ).setHeader( "Started" );
        grid.addColumn( AtcSession::getEndedAt ).setHeader( "Ended" );
        grid.setItems( atcSessionRepository.findTop200ByOrderByStartedAtDesc() );
        grid.setSizeFull();

        setSizeFull();
        add( grid );
    }
}
