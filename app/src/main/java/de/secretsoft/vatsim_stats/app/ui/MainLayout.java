package de.secretsoft.vatsim_stats.app.ui;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.RouterLayout;

public class MainLayout extends AppLayout implements RouterLayout {

    public MainLayout() {
        addToNavbar( new HorizontalLayout( new DrawerToggle(), new H1( "vatsim-stats — Verifikation" ) ) );

        SideNav nav = new SideNav();
        nav.addItem( new SideNavItem( "Pilot Sessions", PilotSessionsView.class ) );
        nav.addItem( new SideNavItem( "ATC Sessions", AtcSessionsView.class ) );
        addToDrawer( nav );
    }
}
