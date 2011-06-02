package sk.zdila.panoramoi2freemap;

import java.io.BufferedReader;
import java.io.File;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
public class Panoramio2Freemap {

	// file containing Panoramip Photo IDs of blacklisted photos - one per line.
	private static final String BLACKLIST_IDS = "/home/martin/panora-blacklist";

	// batch size - max 100 is supported by Panoramio API
	private static final int SIZE = 50;

	// Panoramio User ID
	private static final String PANORAMIO_USER_ID = "4667040";


	public static void main(final String[] args) throws MalformedURLException, IOException, JSONException, InterruptedException {
		final Set<Integer> importedSet = new HashSet<Integer>();
		final File file = new File(BLACKLIST_IDS);
		if (file.exists()) {
			final BufferedReader br = new BufferedReader(new FileReader(file));
			String line;
			while ((line = br.readLine()) != null) {
				importedSet.add(Integer.parseInt(line));
			}
			br.close();
		}

		final Socket socket = new Socket("dev.freemap.sk", 80);
		final OutputStream os = socket.getOutputStream();
		final Writer w = new OutputStreamWriter(os, "UTF-8");
		w.write("GET /api/0/1/gallery/query?k=" + PhotoUploader.APP_ID + " HTTP/1.0\r\n");
		w.write("Host: dev.freemap.sk\r\n");
		w.write("\r\n");
		w.flush();

		final InputStream is1 = socket.getInputStream(); // new URL("http://dev.freemap.sk/api/0/1/gallery/query?k=" + PhotoUploader.APP_ID).openStream();
		final BufferedReader br = new BufferedReader(new InputStreamReader(is1, "UTF-8"));
		if (!"HTTP/1.0 200 OK".equals(br.readLine())) {
			throw new IOException("bad response");
		}

		// skip other headers O:-)
		while (!br.readLine().isEmpty()) {
			// nothing
		}

		final JSONArray ja = new JSONArray(new JSONTokener(br));

		socket.close();


		for (int i = 0, j = ja.length(); i < j; i++) {
			final Matcher m = Pattern.compile("Panoramio Photo ID: (\\d+)").matcher(ja.getJSONObject(i).getString("description"));
			if (m.matches()) {
				importedSet.add(Integer.valueOf(m.group(1)));
			}
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
					System.out.println("Importing: " + photoId);

					final Photo photo = new Photo();
					photo.title = item.getString("photo_title");
					photo.url = item.getString("photo_file_url");
					photo.longitude = item.getDouble("longitude");
					photo.latitude = item.getDouble("latitude");
					photo.description = "Panoramio Photo ID: " + item.getInt("photo_id");
					photos.add(photo);

					importedSet.add(photoId);
				}
			}

			is.close();

			for (final Photo photo : photos) {
				PhotoUploader.upload(photo);
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

}
