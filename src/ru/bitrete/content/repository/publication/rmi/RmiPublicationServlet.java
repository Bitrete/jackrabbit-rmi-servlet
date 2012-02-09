package ru.bitrete.content.repository.publication.rmi;

import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import javax.jcr.Repository;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.GenericServlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.apache.jackrabbit.rmi.remote.RemoteRepository;
import org.apache.jackrabbit.rmi.server.RemoteAdapterFactory;
import org.apache.jackrabbit.rmi.server.ServerAdapterFactory;

public class RmiPublicationServlet extends GenericServlet {

	private static final long serialVersionUID = -3007803818617523652L;
	
	private static final String REPOSITORY_JNDI_NAME_PARAMETER = "repository-jndi-name";
	private static final String RMI_SERVICE_NAME_PARAMETER = "rmi-service-name";
	private static final String RMI_PORT_PARAMETER = "rmi-port";
	
	private static final String DEFAULT_JNDI_NAME = "jcr/repository";
	private static final String DEFAULT_RMI_SERVICE_NAME = "JackrabbitRMI";
	private static final int DEFAULT_RMI_PORT_NUMBER = 1099;

	private RemoteRepository remoteRepository;
	private String rmiServiceName;

	private Registry rmiRegistry;
	
	@Override
	public void service(ServletRequest request, ServletResponse response)
			throws ServletException, IOException {
	}

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);

		rmiServiceName = getRmiServiceName(config);
		String repositoryName = getRepositoryJndiName(config);
		String portNumberValue = getRmiPortNumber(config);
		
		log(String.format("Starting servlet on '%s' for repository '%s'", 
				rmiServiceName, repositoryName));
		
		try {
			int rmiPortNumber = Integer.parseInt(portNumberValue);

			InitialContext context = new InitialContext();
			Context environment = (Context) context.lookup("java:comp/env");

			Repository repository = (Repository) environment
					.lookup(repositoryName);

			RemoteAdapterFactory factory = new ServerAdapterFactory();
			remoteRepository = factory.getRemoteRepository(repository);
			
			log("Remote repository instance created");
			
			rmiRegistry = getOrCreateRegistry(rmiPortNumber);
			
			log("RMI registry created");
			
			if (rmiRegistry != null)
				rmiRegistry.rebind(rmiServiceName, remoteRepository);
			
			log(String.format("Remote repository registered on '%s'", rmiServiceName));
		}
		catch (NamingException ex) {
			log("Naming context access failure", ex);
		}
		catch (RemoteException ex) {
			log("Failed to create remote repository", ex);
		}
	}

	private Registry getOrCreateRegistry(int rmiPortNumber) {
		Registry result = null;
		try {
			result = LocateRegistry.createRegistry(rmiPortNumber);
		}
		catch (RemoteException exception) {
			log(String.format("Failed to create RMI registry on port %d", 
					rmiPortNumber), exception);
		}
		
		return result;
	}

	private String getRmiPortNumber(ServletConfig config) {
		return getContextParameter(config, RMI_PORT_PARAMETER, 
				Integer.toString(DEFAULT_RMI_PORT_NUMBER)); 
	}

	@Override
	public void destroy() {
		try {
			if (rmiRegistry != null) {
				rmiRegistry.unbind(rmiServiceName);
				UnicastRemoteObject.unexportObject(rmiRegistry, true);
				log("RMI registry closed");
			}
		}
		catch (Exception ex) {
			this.log(String.format("Failed to unbind service '%s'", rmiServiceName), ex);
		}
		
		super.destroy();
	}

	private String getRepositoryJndiName(ServletConfig config) {
		return getContextParameter(config, REPOSITORY_JNDI_NAME_PARAMETER, DEFAULT_JNDI_NAME);
	}
	
	private String getRmiServiceName(ServletConfig config) {
		return getContextParameter(config, RMI_SERVICE_NAME_PARAMETER, DEFAULT_RMI_SERVICE_NAME);
	}
	
	private String getContextParameter(ServletConfig config, 
			String parameterName, String defaultValue) {
		String configuredValue = config.getInitParameter(parameterName);
		if (configuredValue != null && configuredValue.length() > 0)
			return configuredValue;
			
		log(String.format("Parameter '%s' was not set. Using default value '%s'.", 
				parameterName, defaultValue));
		
		return defaultValue;
	}
}
