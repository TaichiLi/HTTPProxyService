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
 * 
 * HTTP Server
 * @author Li Taiji
 * @date 2019-11-30
 *
 */
public class HttpServer {

	public static final String SERVER_HOST = "localhost";
	public static final int DEFAULT_PORT = 18088;
	private static final Logger logger = Logger.getLogger("HTTPServer"); // Log file
	private static final int POOL_SIZE = 4; // Thread pool capacity
	private final String rootpath; // Server root path

	/**
	 * @param args command line argument
	 */
	public HttpServer(String[] args) {

		this.rootpath = args[0];
		logger.info("The root path of server " + this.rootpath);
		logger.info("Server Start");

	}

	public static void main(String[] args) throws IOException {
		if (args.length != 1) {
			logger.log(Level.SEVERE, "Missing root path!");
			logger.info("Please start server with <root path>!");
			return;
		}
		// Determine if the parameter is valid
		try {
			File root = new File(args[0]);
			if (!root.isDirectory()) {
				logger.log(Level.SEVERE, "The root path does not exist or is not a directory!");
				logger.info("Please start server with a valid root path!");
				return;
			}
		} catch (Exception ex) {
			logger.log(Level.SEVERE, "Can not resolve the root path", ex);
		}
		new HttpServer(args).service(); // Start the server
	}

	/**
	 * handle the connection with client
	 * 
	 * @throws IOException If an error occurred in the connection
	 */
	public void service() throws IOException {

		ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * POOL_SIZE);
		//ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(POOL_SIZE, POOL_SIZE * 10, 10000, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
		try (ServerSocket serverSocket = new ServerSocket(DEFAULT_PORT, POOL_SIZE)) { // try-with-resource

			logger.info("Accepting connections on port " + serverSocket.getLocalPort());
			Socket socket = null;
			while (true) {
				try {
					socket = serverSocket.accept();
					// waiting for getting the client
					logger.info("Connect to the client on " + socket.getInetAddress().getHostName());
					HttpHandler httpHandler = new HttpHandler(socket, this.rootpath, logger);
					pool.submit(httpHandler); // Start the thread
				} catch (IOException ex) {
					logger.log(Level.SEVERE, "Accept error", ex);
				} catch (RuntimeException ex) {
					logger.log(Level.SEVERE, "Unexpected error" + ex.getMessage(), ex);
				}
			}
		} catch (IOException ex) {
			logger.log(Level.SEVERE, "Can not start server", ex);
		} catch (RuntimeException ex) {
			logger.log(Level.SEVERE, "Can not start server" + ex.getMessage(), ex);
		}

	}
}

