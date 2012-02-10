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
import org.apache.jackrabbit.rmi.server.ServerAdapterFactory;

public class RmiPublicationServlet extends GenericServlet {

	private static final long serialVersionUID = -3007803818617523652L;
	
	private static final String REPOSITORY_JNDI_NAME_PARAMETER = "repository-jndi-name";
	private static final String RMI_SERVICE_NAME_PARAMETER = "rmi-service-name";
	private static final String RMI_REGISTRY_PORT_PARAMETER = "rmi-registry-port";
	private static final String REPOSITORY_PORT_PARAMETER = "repository-port";

	private static final String DEFAULT_JNDI_NAME = "jcr/repository";
	private static final String DEFAULT_RMI_SERVICE_NAME = "JackrabbitRMI";
	private static final int DEFAULT_RMI_REGISTRY_PORT_NUMBER = 1099;
	private static final int DEFAULT_REPOSITORY_PORT_NUMBER = 1100;

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
		int rmiPortNumber = getPortNumber(config, RMI_REGISTRY_PORT_PARAMETER, DEFAULT_RMI_REGISTRY_PORT_NUMBER);
		int repositoryPortNumber = getPortNumber(config, REPOSITORY_PORT_PARAMETER, DEFAULT_REPOSITORY_PORT_NUMBER);
		
		log(String.format("Starting servlet on '%s' for repository '%s'", 
				rmiServiceName, repositoryName));
		
		try {
			InitialContext context = new InitialContext();
			Context environment = (Context) context.lookup("java:comp/env");

			Repository repository = (Repository) environment
					.lookup(repositoryName);

			ServerAdapterFactory factory = new ServerAdapterFactory();
			factory.setPortNumber(repositoryPortNumber);
			
			remoteRepository = factory.getRemoteRepository(repository);
			
			log("Remote repository instance created");
			
			rmiRegistry = createOrGetRegistry(rmiPortNumber);
			if (rmiRegistry == null)
				throw new IllegalStateException("Failed to get RMI registry, see log for details");
			
			log("RMI registry created");
			
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

	@Override
	public void destroy() {
		try {
			if (rmiRegistry != null) {
				rmiRegistry.unbind(rmiServiceName);
				UnicastRemoteObject.unexportObject(remoteRepository, true);
				UnicastRemoteObject.unexportObject(rmiRegistry, true);
				log("RMI registry closed");
			}
		}
		catch (Exception ex) {
			this.log(String.format("Failed to unbind service '%s'", rmiServiceName), ex);
		}
		
		super.destroy();
	}

	private Registry createOrGetRegistry(int rmiPortNumber) {
		Registry result = null;
		try {
			result = LocateRegistry.createRegistry(rmiPortNumber);
		}
		catch (RemoteException exception) {
			log(String.format("Failed to create RMI registry on port %d", 
					rmiPortNumber), exception);
			
			try {
				result = LocateRegistry.getRegistry(rmiPortNumber);
			}
			catch (RemoteException ex) {
				log(String.format("Failed to get reference to remote RMI registry on port %d", 
						rmiPortNumber));
			}
		}
		
		return result;
	}

	private int getPortNumber(ServletConfig config, String parameterName, int defaultValue) {
		return Integer.parseInt(getContextParameter(config, parameterName, 
				Integer.toString(defaultValue)));
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
