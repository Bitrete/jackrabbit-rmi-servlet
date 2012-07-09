package ru.bitrete.content.repository.publication.rmi;

import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import javax.jcr.Repository;
import javax.servlet.GenericServlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.apache.jackrabbit.rmi.remote.RemoteRepository;
import org.apache.jackrabbit.rmi.server.ServerAdapterFactory;

public class RmiPublicationServlet extends GenericServlet {

	private static final long serialVersionUID = -3007803818617523652L;
	
	private static final String REPOSITORY_WEBAPP_NAME_PARAMETER = "repository-servlet-name";
	private static final String RMI_SERVICE_NAME_PARAMETER = "rmi-service-name";
	private static final String RMI_REGISTRY_PORT_PARAMETER = "rmi-registry-port";
	private static final String REPOSITORY_PORT_PARAMETER = "repository-port";

	private static final String DEFAULT_WEBAPP_NAME = "/RepositoryService";
	private static final String DEFAULT_RMI_SERVICE_NAME = "jackrabbit.repository";
	private static final int DEFAULT_RMI_REGISTRY_PORT_NUMBER = 1099;
	private static final int DEFAULT_REPOSITORY_PORT_NUMBER = 1101;

	private RemoteRepository remoteRepository;
	private String rmiServiceName;

	private Registry rmiRegistry;
	private boolean localRegistryInUse;

	@Override
	public void service(ServletRequest request, ServletResponse response)
			throws ServletException, IOException {
	}

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);

		rmiServiceName = getRmiServiceName(config);
		String webappName = getRepositoryWebappName(config);
		int rmiPortNumber = getPortNumber(config, RMI_REGISTRY_PORT_PARAMETER, 
				DEFAULT_RMI_REGISTRY_PORT_NUMBER);
		int repositoryPortNumber = getPortNumber(config, REPOSITORY_PORT_PARAMETER, 
				DEFAULT_REPOSITORY_PORT_NUMBER);
		
		log(String.format("Starting servlet on '%s' for Jackrabbit Webapp '%s'", 
				rmiServiceName, webappName));
		
		try {
			ServletContext jackrabbitWebappContext = 
					getServletContext().getContext(webappName);
			if (jackrabbitWebappContext == null) {
				String message = 
						String.format("Unable to get context of '%s' web application", webappName);
				log(message);
				
				throw new IllegalStateException(message);
			}
			
			String repositoryClassName = Repository.class.getName();
			Repository repository = 
					(Repository) jackrabbitWebappContext.getAttribute(repositoryClassName);
			if (repository == null) {
				String message = 
						String.format("Failed to obtain instance of '%s'", repositoryClassName);
				log(message);
				
				throw new IllegalStateException(message);
			}

			ServerAdapterFactory factory = new ServerAdapterFactory();
			factory.setPortNumber(repositoryPortNumber);
			
			remoteRepository = factory.getRemoteRepository(repository);
			if (remoteRepository == null) {
				String message = "Failed to obtain remote repository";
				log(message);
				
				throw new IllegalStateException(message);
			}
			log("Remote repository instance created");
			
			rmiRegistry = createOrGetRegistry(rmiPortNumber);
			
			log("RMI registry obtained. Binding...");
			
			rmiRegistry.rebind(rmiServiceName, remoteRepository);
			log(String.format("Remote repository registered on '%s'", rmiServiceName));
		}
		catch (RemoteException ex) {
			log("Failed to create remote repository", ex);
		}
	}

	@Override
	public void destroy() {
		log("Finalizing RMI Publisher servlet");
		try {
			if (rmiRegistry != null) {
				rmiRegistry.unbind(rmiServiceName);
				
				log("Removing remote repository from RMI infrastructure");
				UnicastRemoteObject.unexportObject(remoteRepository, true);
				
				remoteRepository = null;
				System.gc();
				
				log("Remote repository unbinded and removed");
				if (localRegistryInUse) {
					if (rmiRegistry.list().length == 0) {
						UnicastRemoteObject.unexportObject(rmiRegistry, true);
						log("Local RMI registry was closed");
					}
					else {
						log("RMI registry still holds objects, leave it alone");
					}
				}
			}
		}
		catch (Exception ex) {
			log(String.format("Failed to unbind service '%s'", rmiServiceName), ex);
		}
		
		super.destroy();
	}

	private Registry createOrGetRegistry(int rmiPortNumber) {
		Registry result = null;
		
		try {
			result = LocateRegistry.createRegistry(rmiPortNumber);
			localRegistryInUse = true;
		} 
		catch (RemoteException ex) {
			log(String.format(
					"Failed to create RMI registry on port %d; Trying to get existing RMI registry",
					rmiPortNumber));
			try {
				result = LocateRegistry.getRegistry(rmiPortNumber);
			} 
			catch (RemoteException exception) {
				log(String.format("Failed to get RMI registry on port %d", 
						rmiPortNumber), exception);
				
				throw new IllegalStateException("Failed to get RMI registry, see log for details");
			}
		}
		
		return result;
	}

	private int getPortNumber(ServletConfig config, String parameterName, int defaultValue) {
		return Integer.parseInt(getContextParameter(config, parameterName, 
				Integer.toString(defaultValue)));
	}

	private String getRepositoryWebappName(ServletConfig config) {
		return getContextParameter(config, REPOSITORY_WEBAPP_NAME_PARAMETER, DEFAULT_WEBAPP_NAME);
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
