UPDATE selve_vedtaksperiode_generasjon SET tilstand='KlarTilBehandling' WHERE tilstand='Åpen' AND utbetaling_id IS NOT NULL;