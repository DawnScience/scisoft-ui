package uk.ac.diamond.scisoft.analysis.powder.indexer.rcp;

import org.eclipse.dawnsci.analysis.api.io.ILoaderService;
import org.eclipse.dawnsci.analysis.api.persistence.IPersistenceService;
import org.osgi.service.event.EventAdmin;

public class PowderServiceManager {

	private static ILoaderService lservice;
	private static EventAdmin eventAdmin;
	private static IPersistenceService pservice;

	public void setLoaderService(ILoaderService s) {
		lservice = s;
	}
	
	public static ILoaderService getLoaderService() {
		return lservice;
	}
	
	public static EventAdmin getEventAdmin() {
		return eventAdmin;
	}

	public void setEventAdmin(EventAdmin eAdmin) {
		eventAdmin = eAdmin;
	}
	
	public void setPersistenceService(IPersistenceService s) {
		pservice = s;
	}
	
	public static IPersistenceService getPersistenceService() {
		return pservice;
	}
	
}