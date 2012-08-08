import java.util.LinkedList;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Client {
	private static final boolean LOG_RESPONSE_HEADER = true;
	static final String testDirectory = System.getProperty("user.dir") + File.separator;// + "testFiles" + File.separator;

	private static final String NEW_LINE = "\r\n";

	private static final int HTTP_PORT = 80;

	String url;
	private String saveDirectory = "";
	Socket serverSocket;
	private PrintWriter serverOut;

	private InputStream serverIn;

	private String hostname;

	private String getFile;
	public Client(String url, String savePath) {
		this(url, new File(savePath));
	}
	
	public Client(String url, File chosenFolder) {
		this.url = url;
		saveDirectory = chosenFolder.getAbsolutePath() + File.separator;
	}

	public void processWebPage() {
		processURL();
		sendHTTPRequest();
		String response = getHTMLResponse();
		String[] imageLinks = extractImageLinks(response);
		downloadImages(imageLinks);
	}
	
	private static boolean isHostname(String url) {
		if (url.startsWith("http://")|url.startsWith("//")) {
			return true;
		}
		if (url.startsWith("/")) {
			return false;
		}
		if (url.split("/").length > 1) {
			String head = url.split("/")[0];
			if (head.matches("(www.+)|($\\.+)")) {
				return true;
			}
		}
		return false;
	}
	
	public void processURL() {
		url = url.replaceAll("http://", "");
		if (url.startsWith("//")) {
			url = url.substring(2);
		}
		String[] urlTokenized = url.split("/");
		hostname = urlTokenized[0];
		getFile = "";
		if (urlTokenized.length > 1) {
			for (String s : urlTokenized) {
				if (!s.equals(hostname))
					getFile += "/" + s;
			}
		} else {
			getFile = "/";
		}
	}

	private void downloadImages(String[] imageLinks) {
		for (String image : imageLinks) {
			if(Client.isHostname(image)) {
				Client imageClient = new Client(image, saveDirectory);
				imageClient.processURL();
				imageClient.downloadImage(imageClient.getFile);
			} else {
				downloadImage(image);
			}
		}
	}

	private void downloadImage(String image) {
		sendHTTPRequest(getHeader(image));
		ByteArrayOutputStream response = getImageResponse();
		FileOutputStream writer;
		try {
			writer = new FileOutputStream(saveDirectory + getFilename(image));
			response.writeTo(writer);
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private String getFilename(String image) {
		return image.substring(image.lastIndexOf('/') + 1);
	}

	private String[] extractImageLinks(String response) {
		Pattern imageLinkPattern = Pattern.compile("\\<img.+src=\"([^\"]+)");//+\")");
		Matcher matchedLink = imageLinkPattern.matcher(response);
		Set<String> imageLinkSet = new HashSet<String>();
		while (matchedLink.find()) {
			String link = matchedLink.group(1);
			if (!"#".equals(link) && !link.toLowerCase().contains("https://")) {
				imageLinkSet.add(link);
			}
		}
		for (String s : imageLinkSet) {
			System.out.println("Image Link : " + s);
		}
		System.out.println("Total links : " + imageLinkSet.size());
		return (String[]) imageLinkSet.toArray(new String[imageLinkSet.size()]);
	}

	private boolean openSocket() {
		try {
			serverSocket = new Socket(hostname, HTTP_PORT);
			serverOut = new PrintWriter(serverSocket.getOutputStream());
			serverIn = serverSocket.getInputStream();
		} catch (UnknownHostException e) {
			e.printStackTrace();
			return false;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	private String hostPart() {
		return String.format("Host: %s%s", hostname, NEW_LINE);
	}

	private String getHeader() {
		return getHeader(getFile);
	}

	private String getHeader(String file) {
		if (file.charAt(0) != '/') {
			file = "/" + file;
		}
		String header = "GET " + file + " HTTP/1.0" + NEW_LINE + hostPart(); 

		String contentHeader = getContentTag(file);
		if ("".equals(contentHeader)) {
			return header;
		}
		return header + "Content-Type: " + contentHeader + NEW_LINE;
	}

	private static String getContentTag(String file) {
		Pattern imagePattern = Pattern.compile("(jpg)|(jpeg)|(png)|(gif)");
		Matcher m = imagePattern.matcher(file);
		if (m.find()) return "image/" + m.group();
		if (file.matches("svg")) return "image/svg+xml";
		return "";
	}

	private void sendHTTPRequest() {
		sendHTTPRequest(getHeader());
	}

	private void sendHTTPRequest(String request) {
		request += NEW_LINE;
		System.out.println("Connecting to host:" + hostname);
		while(!openSocket());
		System.out.println(request);
		serverOut.write(request);
		serverOut.flush();
	}

	private String getHTMLResponse() {
		StringBuilder response = new StringBuilder();
		String line = "";
		BufferedReader serverInputReader = new BufferedReader(new InputStreamReader(serverIn));
		String header = "";
		try {
			while((line = serverInputReader.readLine()) != null && !line.trim().equals("")) {
				//Reading and removing header
				header += line + "\n";
			}

			while ((line = serverInputReader.readLine())  != null) {
				response.append(line + "\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				serverInputReader.close(); 
				closeSockets();
			}
			catch (IOException ignored){}
		}
		if (LOG_RESPONSE_HEADER) {
			System.out.println(header);
		}		return response.toString();
	}
	private void closeSockets() throws IOException {
		serverIn.close();
		serverOut.close();
		serverSocket.close();
	}

	private void removeHeader() throws IOException {
		Queue<Integer> headerQueue = new LinkedList<Integer>();
		LinkedList<Integer> terminationArray = new LinkedList<Integer>();
		terminationArray.add((int) '\r');
		terminationArray.add((int) '\n');
		terminationArray.add((int) '\r');
		terminationArray.add((int) '\n');
		
		for (int i = 1; i <= 4; i++) {
			headerQueue.add(serverIn.read());
		}
		
		while (!headerQueue.equals(terminationArray)) {
			headerQueue.poll();
			headerQueue.add(serverIn.read());
		}			
	}
	
	private ByteArrayOutputStream getImageResponse() {
		ByteArrayOutputStream response = new ByteArrayOutputStream();
		int byteRead;
		try {
			removeHeader();
			while ((byteRead = serverIn.read())  != -1) {
				response.write(byteRead);
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				closeSockets();
			}
			catch (IOException ignored){}
		}

		return response;
	}
}
