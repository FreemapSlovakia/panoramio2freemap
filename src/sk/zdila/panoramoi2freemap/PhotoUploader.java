package sk.zdila.panoramoi2freemap;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;

class PhotoUploader {

	private static final String BOUNDARY = "FreeMapTaNeviemCiNeHejIbaZeByNe";


	static void upload(final String appKey, final Photo photo) throws UnknownHostException, IOException, UnsupportedEncodingException, MalformedURLException,
			FileNotFoundException, InterruptedException {
		System.out.println("=====================================> " + photo.url);

		final Socket socket = new Socket("dev.freemap.sk", 80);
		final OutputStream os = socket.getOutputStream();

		final Writer w = new OutputStreamWriter(os, "UTF-8");

		// we can't use HttpURLConnection because it doesn't support HTTP/1.0 required by Freemap API

		w.write("POST /api/0/1/gallery/add HTTP/1.0\r\n");
		w.write("Host: dev.freemap.sk\r\n");
		w.write("Accept: */*\r\n");
		w.write("Content-Type: multipart/form-data; boundary=" + BOUNDARY +"\r\n");

		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		final Writer w1 = new OutputStreamWriter(baos, "UTF-8");
		writeParameter(w1, "rules", "1");
		writeParameter(w1, "k", appKey);
		writeParameter(w1, "lat", Double.toString(photo.latitude));
		writeParameter(w1, "lon", Double.toString(photo.longitude));
		writeParameter(w1, "title", photo.title);
		writeParameter(w1, "description", photo.description);

		w1.write("--" + BOUNDARY + "\r\n");
		w1.write("Content-Disposition: form-data; name=\"image\"; filename=\"" + new File(photo.url).getName() + "\"\r\n");
		w1.write("Content-Type: image/jpeg\r\n");
		w1.write("\r\n");

		w1.flush();

		// write image to temporary file
		final InputStream photoIs = new URL(photo.url).openStream();
		final File imageFile = File.createTempFile("image", "jpg");
		final File scaledImageFile = File.createTempFile("image", "jpg");
		final OutputStream fos = new FileOutputStream(imageFile);
		final byte[] buffer = new byte[4096];
		int len;
		while ((len = photoIs.read(buffer)) != -1) {
			fos.write(buffer, 0, len);
		}
		photoIs.close();
		fos.close();

		// resize the image
		final Process process = new ProcessBuilder("gm", "convert", "-resize", "1600x1600>", "-quality", "90", imageFile.getAbsolutePath(), scaledImageFile.getAbsolutePath()).start();
		process.waitFor();

		imageFile.delete();

		// write the resized image
		final InputStream scaledIs = new FileInputStream(scaledImageFile);
		while ((len = scaledIs.read(buffer)) != -1) {
			baos.write(buffer, 0, len);
		}

		w1.write("\r\n");
		w1.write("--" + BOUNDARY + "--\r\n");

		w1.flush();
		w1.close();
		baos.close(); // redundant

		scaledImageFile.delete();

		final byte[] bytes = baos.toByteArray();
		w.write("Content-Length: " + bytes.length + "\r\n");
		w.write("\r\n");
		w.flush();
		os.write(bytes);

		// dump Freemap server response
		final InputStream is1 = socket.getInputStream();
		final BufferedReader br = new BufferedReader(new InputStreamReader(is1));
		String line;
		while ((line = br.readLine()) != null) {
			System.out.println(line);
		}
		is1.close();

		socket.close();
	}

	private static void writeParameter(final Writer w, final String name, final String value) throws IOException {
		w.write("--" + BOUNDARY + "\r\n");
		w.write("Content-Disposition: form-data; name=\"" + name + "\"\r\n");
		w.write("Content-Type: text/plain;charset=UTF-8\r\n");
		w.write("\r\n");
		if (value != null) {
			w.write(value + "\r\n");
		}
	}

}
