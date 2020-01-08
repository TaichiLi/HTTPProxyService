package httpproxyservice;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * @author Li Taiji
 * @date 2019-11-30
 */

public class HttpProxyServer {
	
	public static final String PROXY_HOST = "localhost";
	public final int PROXY_PORT; // The port of HTTP Proxy
	private static final Logger logger = Logger.getLogger("HTTPProxyServer"); // Log file
	private static final int POOL_SIZE = 4;
	private final String rootpath; // Proxy Server root path


	/**
	 * Initialize the Server
	 * @throws IOException
	 */
	public HttpProxyServer(String[] args) throws IOException {
		
		this.PROXY_PORT= Integer.parseInt(args[0]);
		this.rootpath = args[1];
		logger.info("The root path of proxy server " + this.rootpath);
		logger.info("Proxy Server Start");
		
	}

	/**
	 * Begin to service
	 */
	public void service() {	
		
		ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * POOL_SIZE);
		try (ServerSocket serverSocket = new ServerSocket(PROXY_PORT, POOL_SIZE)) { // try-with-resource

			logger.info("Accepting connections on port " + serverSocket.getLocalPort());
			Socket socket = null;
			while (true) {
				try {
					socket = serverSocket.accept();
					// waiting for getting the client
					logger.info("Connect to the client on " + socket.getInetAddress().getHostName());
					HttpProxyHandler httpHandler = new HttpProxyHandler(socket, this.rootpath, logger);
					pool.submit(httpHandler); // Start the thread
				} catch (IOException ex) {
					logger.log(Level.SEVERE, "Accept error", ex);
				} catch (RuntimeException ex) {
					logger.log(Level.SEVERE, "Unexpected error" + ex.getMessage(), ex);
				}
			}
		} catch (IOException ex) {
			logger.log(Level.SEVERE, "Can not start proxy server", ex);
		} catch (RuntimeException ex) {
			logger.log(Level.SEVERE, "Can not start proxy server" + ex.getMessage(), ex);
		}
	}

	/**
	 * 
	 * @param args
	 * @throws IOException
	 */
	public static void main(String args[]) throws IOException {
		if (args.length != 2) {
			logger.log(Level.SEVERE, "Missing port or root path!");
			logger.info("Please start proxy server with <port> <root path>!");
			return;
		}
		// Determine if the parameter is valid
		try {
			File root = new File(args[1]);
			if (!root.isDirectory()) {
				logger.log(Level.SEVERE, "The root path does not exist or is not a directory!");
				logger.info("Please start server with a valid root path!");
				return;
			}
		} catch (Exception ex) {
			logger.log(Level.SEVERE, "Can not resolve the root path", ex);
		}
		new HttpProxyServer(args).service();
	}
}
