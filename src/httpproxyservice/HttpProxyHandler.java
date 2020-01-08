package httpproxyservice;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.Files;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 
 * @author Li Taiji
 * @date 2019-11-30
 */
public class HttpProxyHandler implements Runnable {

	private static final String CRLF = "\r\n";
	private static final String ENCODING = "ISO-8859-1";
	private final Socket socket;
	private final String logHeader; // The header of log term
	private final String rootpath;
	private final String savepath;
	private StringBuilder request = null; // Save the request from the client
	private final BufferedInputStream inputStream;
	private final BufferedOutputStream outputStream;
	private String requestLine; // The first line of request
	private boolean keepAlive; // The "Connection" attribute
	private final Logger logger; // The log file
	private HttpClient httpClient;

	/**
	 * @param socket   The socket with client
	 * @param rootpath The root path of server
	 * @param logger   Log file
	 * @throws IOException 
	 */
	public HttpProxyHandler(Socket socket, String rootpath, Logger logger) throws IOException {
		this.socket = socket;
		this.rootpath = rootpath;
		this.savepath = rootpath + "\\saving";
		this.logger = logger;
		this.request = new StringBuilder();
		this.requestLine = "";
		this.keepAlive = true;
		this.inputStream = new BufferedInputStream(socket.getInputStream());
		this.outputStream = new BufferedOutputStream(socket.getOutputStream());
		this.logHeader = "Client on " + this.socket.getInetAddress().getHostAddress() + " <" + this.socket.getPort()
				+ ">: ";
		this.logger.info(this.logHeader + "Connect successfully!");
	}

	@Override
	/**
	 * Implement run thread
	 */
	public void run() {
		try {
			while (keepAlive) {
				receiveRequest(); // receive request
				this.logger.info('\n' + this.logHeader + request.toString());
				if (request.indexOf("GET") != -1) {
					doGetResponse();
				} else if (request.indexOf("PUT") != -1) {
					doPutResponse();
				} else { // Only respond to GET and PUT
					this.logger.log(Level.WARNING, this.logHeader + "Incorrert Request");
					String filePath = rootpath + "\\response\\400.html";
					File file = new File(filePath);
					sendHeader("HTTP/1.1 400 Bad Request", URLConnection.getFileNameMap().getContentTypeFor(filePath),
							file.length(), false);
					sendContent(filePath);
				}
			}

		} catch (IOException ex) {
			this.logger.log(Level.WARNING, this.logHeader + "Resolve Request Error", ex);
		}
	}

	/**
	 * handle the GET request
	 * 
	 * @throws IOException
	 */
	private void doGetResponse() throws IOException {
		String[] tokens = requestLine.split("\\s+");
		String filePath = null;
		File file = null;
		keepAlive = false;

		if (tokens.length != 3) {
			this.logger.log(Level.WARNING, this.logHeader + "Incorrect Request! Missing necessary parts");
			filePath = rootpath + "\\response\\400.html";
			file = new File(filePath);
			sendHeader("HTTP/1.1 400 Bad Request", URLConnection.getFileNameMap().getContentTypeFor(filePath),
					file.length(), keepAlive);
			sendContent(filePath);
		} else {

			String url = tokens[1];
			String version = tokens[2];

			tokens = request.toString().split("\\s+");
			for (int i = 0; i < tokens.length; i++) {
				if (tokens[i].equals("Connection:")) {
					// Get the "Connection"
					keepAlive = tokens[i + 1].equals("keep-alive");
					break;
				}
			}

			if (version.equals("HTTP/1.0") || version.equals("HTTP/1.1")) {
				if (url.endsWith("/")) {
					url = url + "index.html";
				}
				filePath = rootpath + url.replaceAll("/", "\\\\");
				file = new File(filePath);
				if (file.exists()) {
					sendHeader("HTTP/1.1 200 OK", URLConnection.getFileNameMap().getContentTypeFor(filePath),
							file.length(), keepAlive);
					sendContent(filePath);
				} else {
					requestServer(filePath);
				}
			} else {
				this.logger.log(Level.WARNING, this.logHeader + "HTTP version not accepted");
				filePath = rootpath + "\\response\\400.html";
				file = new File(filePath);
				sendHeader("HTTP/1.1 400 Bad Request", URLConnection.getFileNameMap().getContentTypeFor(filePath),
						file.length(), keepAlive);
				sendContent(filePath);
			}
		}
		if (!keepAlive) {
			// If Connection is not keep-alive, close the connection with client.
			inputStream.close();
			outputStream.close();
			socket.close();
		} else {
			request.setLength(0);
			// clear the request
		}
	}

	/**
	 * handle the PUT request
	 */
	private void doPutResponse() {
		try {
			String[] tokens = request.toString().split("\\s+");
			int fileLength = 0;
			String contentType = null;
			keepAlive = false;
			for (int i = 0; i < tokens.length; i++) {
				// resolve the request
				if (tokens[i].equals("Content-Length:")) {
					fileLength = Integer.parseInt(tokens[i + 1]);
				} else if (tokens[i].equals("Content-Type:")) {
					contentType = tokens[i + 1];
				} else if (tokens[i].equals("Connection:")) {
					keepAlive = tokens[i + 1].equals("keep-alive");
				}
			}

			sendHeader("HTTP/1.1 200 OK", contentType, fileLength, keepAlive);

			String path = savepath + tokens[1].replaceAll("/", "\\\\");
			File file = new File(path);
			FileOutputStream fos = new FileOutputStream(file);

			byte[] buffer = new byte[fileLength];
			int revBytes = 0;
			int bytes;
			while ((bytes = inputStream.read(buffer, revBytes, fileLength - revBytes)) > 0) {
				revBytes += bytes;

			}
			fos.write(buffer);
			fos.flush();
			fos.close();
			if (!keepAlive) {
				inputStream.close();
				outputStream.close();
				socket.close();
			} else {
				request.setLength(0);
			}
		} catch (IOException ex) {
			this.logger.log(Level.SEVERE, this.logHeader + "Can not receive file", ex);
		}
	}

	/**
	 * resolve request
	 * 
	 * @throws IOException
	 */
	private void receiveRequest() throws IOException {
		boolean finishFirst = false;
		boolean finishAll = false;
		boolean finishLast = false;
		int c = 0;
		while (!finishAll && (c = inputStream.read()) != -1) {
			switch (c) {
			case '\r':
				break;
			case '\n':
				if (!finishFirst) {
					requestLine = new String(request.toString().getBytes(), ENCODING);
					// Get the first line of request
					finishFirst = true;
				}
				if (!finishLast) {
					request.append("\r\n");
					finishLast = true;
				} else if (finishLast) {
					finishAll = true;
				}
				break;
			default:
				request.append((char) c);
				finishLast = false;
				break;
			}
		}
	}
	
	/**
	 * write the content of file, and return the URI of file.
	 * 
	 * @param fileContent
	 * @param path
	 * @return URI
	 * @throws IOException
	 */
	private URI writeFile(String fileContent, String path) throws IOException {
		File file;
		int index = path.lastIndexOf("/");
		if (index != -1) {
			String fileName = path.substring(index + 1); // Get the name of file
			String dir = path.substring(0, index); // Get the directory
			file = new File(rootpath, dir);		
			if (!file.exists()) {
				file.mkdirs(); // If the directory does not exist, it will be created.
			}
			File newFile = new File(file.getAbsolutePath(), fileName);
			FileOutputStream outfile = new FileOutputStream(newFile);
			outfile.write(fileContent.getBytes(ENCODING));
			outfile.flush();
			outfile.close();
		} else {
			file = new File(rootpath, path);
			FileOutputStream outfile = new FileOutputStream(file);
			outfile.write(fileContent.getBytes(ENCODING));
			outfile.flush();
			outfile.close();
		}
		return file.toURI();
	}
	
	/**
	 * @throws IOException
	 */
	private void requestServer(String fileName) throws IOException {
		httpClient = new HttpClient();
		httpClient.connect(HttpServer.SERVER_HOST, HttpServer.DEFAULT_PORT);
		httpClient.processGetRequest(requestLine, keepAlive);
		byte[] buffer = httpClient.getHeader().getBytes(ENCODING);
		logger.info("Header: \r\n" + httpClient.getHeader());
		outputStream.write(buffer, 0, buffer.length);
		outputStream.flush();
		byte[] file = httpClient.getContent().getBytes(ENCODING);
		writeFile(httpClient.getContent(), fileName);
		outputStream.write(file, 0, file.length);
		outputStream.flush();
		if (!keepAlive) {
			httpClient.close();
		}
		
	}

	/**
	 * send the response to client
	 * 
	 * @param responseCode
	 * @param contentType
	 * @param length
	 * @param keepAlive
	 */
	private void sendHeader(String responseCode, String contentType, long length, boolean keepAlive) {
		try {
			StringBuilder response = new StringBuilder();
			response.append(responseCode + CRLF);
			response.append("Date: " + new Date().toString() + CRLF);
			response.append("Server: MyHttpServer/1.0" + CRLF);
			response.append("Content-Length: " + length + CRLF);
			response.append("Content-type: " + contentType + CRLF);
			if (keepAlive) {
				response.append("Connection: keep-alive" + CRLF + CRLF);
			} else {
				response.append("Connection: close" + CRLF + CRLF);
			}

			byte[] buffer = response.toString().getBytes(ENCODING);
			outputStream.write(buffer, 0, buffer.length);
			outputStream.flush();
		} catch (UnsupportedEncodingException ex) {
			this.logger.log(Level.SEVERE, "Unsupported Encoding", ex);
		} catch (IOException ex) {
			this.logger.log(Level.SEVERE, "Send Header Error", ex);
		}

	}

	/**
	 * send the file to client
	 * 
	 * @param filePath
	 */
	private void sendContent(String filePath) {
		try {
			File file = new File(filePath);
			byte[] sendData = Files.readAllBytes(file.toPath());
			outputStream.write(sendData);
			outputStream.flush();
		} catch (IOException ex) {
			this.logger.log(Level.SEVERE, "Can not send file", ex);
		}
	}
}
