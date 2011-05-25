package sk.zdila.panoramoi2freemap;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * Imports photos from Panoramio.com to Freemap.sk (after photo author has approved it)
 * Note that the code is written rather dirty way and all parameters must be set in the code.
 *
 * @see <a href="http://www.panoramio.com/api/data/api.html">Panoramio API</a>
 * @author <a href="mailto:martin.zdila@freemap.sk">Martin Ždila</a>
 */
public class Main {

	// file containing Panoramip Photo IDs of already imported photos - one per line.
	private static final String IMPORTED_IDS = "/home/martin/panora";

	// batch size - max 100 is supported by Panoramio API
	private static final int SIZE = 50;

	// Freemap Autentifikačný kľúč
	private static final String APP_ID = "30ebf8cb1b5ee6cfdb28e37c82ece610";

	// Panoramio User ID
	private static final String PANORAMIO_USER_ID = "535318";


	private static final String BOUNDARY = "FreeMapTaNeviemCiNeHejIbaZeByNe";


	private static class Photo {
		int id;
		String url;
		String title;
		double longitude;
		double latitude;
	}


	public static void main(final String[] args) throws MalformedURLException, IOException, JSONException, InterruptedException {
		final Set<Integer> importedSet = new HashSet<Integer>();
		final File file = new File(IMPORTED_IDS);
		if (file.exists()) {
			final BufferedReader br = new BufferedReader(new FileReader(file));
			String line;
			while ((line = br.readLine()) != null) {
				importedSet.add(Integer.parseInt(line));
			}
			br.close();
		}

		for (int from = 0; ; from += SIZE) {
			final InputStream is = new URL("http://www.panoramio.com/map/get_panoramas.php?set=" + PANORAMIO_USER_ID + "&from=" + from + "&to=" + (from + SIZE) + "&size=original&mapfilter=false").openStream();

			final JSONObject jo = new JSONObject(new JSONTokener(is));

//			System.out.println(jo.get("count"));

			final JSONArray photosJA = jo.getJSONArray("photos");

			final List<Photo> photos = new ArrayList<Photo>();

			for (int i = 0, j = photosJA.length(); i < j; i++) {
				final JSONObject item = photosJA.getJSONObject(i);
				final int photoId = item.getInt("photo_id");
				if (importedSet.contains(photoId)) {
					System.out.println("Already imported: " + photoId);
				} else {
					System.out.println(photoId);

					final Photo photo = new Photo();
					photo.id = item.getInt("photo_id");
					photo.title = item.getString("photo_title");
					photo.url = item.getString("photo_file_url");
					photo.longitude = item.getDouble("longitude");
					photo.latitude = item.getDouble("latitude");
					photos.add(photo);
				}
			}

			is.close();

			for (final Photo photo : photos) {
				System.out.println("=====================================> " + photo.id + " .. " + photo.url);

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
				writeParameter(w1, "k", APP_ID);
				writeParameter(w1, "lat", Double.toString(photo.latitude));
				writeParameter(w1, "lon", Double.toString(photo.longitude));
				writeParameter(w1, "title", photo.title);
				writeParameter(w1, "description", "Panoramio Photo ID: " + photo.id);

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

			if (new File("/home/martin/stop").exists()) {
				System.out.println("STOP");
				break;
			}

			if (!jo.getBoolean("has_more")) {
				System.out.println("DONE");
				break;
			}
		}
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
