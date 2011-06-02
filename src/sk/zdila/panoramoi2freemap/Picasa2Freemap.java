package sk.zdila.panoramoi2freemap;

import java.io.IOException;
import java.net.URL;

import com.google.gdata.client.photos.PicasawebService;
import com.google.gdata.data.photos.AlbumEntry;
import com.google.gdata.data.photos.AlbumFeed;
import com.google.gdata.data.photos.PhotoEntry;
import com.google.gdata.data.photos.UserFeed;
import com.google.gdata.util.ServiceException;

public class Picasa2Freemap {

	/**
	 * @param args
	 * @throws ServiceException
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public static void main(final String[] args) throws IOException, ServiceException, InterruptedException {
		final PicasawebService myService = new PicasawebService("exampleCo-exampleApp-1");
		final URL feedUrl1 = new URL("http://picasaweb.google.com/data/feed/api/user/peter.misovic?kind=album");

		final UserFeed myUserFeed = myService.getFeed(feedUrl1, UserFeed.class);

//		final URL feedUrl;
		final AlbumFeed feed;

		// HajskeVodopady
		// CergovskeVrchyCergov
		// SKHUBorderII
		// PloskeToryskaPahorkatina -- ignore
		// NizkeTatryDumbier
		// NizkeTatryKamienkaChopok
		// KoniarskaPlanina
		// GolgotaBukovecGolgotaJahodna
		// PrielomMurana
		// SlanskeVrchyRankovskeSkaly
		// ZapadneTatryBystra2248m
		// VysokeTatryZbojnickaChata
		// TheGulleyOfZadiel
		// ZadielskaTiesnava
		// SivaBradaChapelOfTheHolyCross
		// MalaFatraChleb
		// VelkaFatraZvolen
		// NizkeTatryKralovaHola
		// KosiceErikaNalepkovoStratenaPoprad

		found: {
			for (final AlbumEntry myAlbum : myUserFeed.getAlbumEntries()) {
				// System.out.println(myAlbum.getHtmlLink().getHref());
				if (myAlbum.getName().equals("KosiceErikaNalepkovoStratenaPoprad")) {
					feed = myAlbum.getFeed("photo");
//					feedUrl = new URL(myAlbum.getId());
					break found;
				}
			}
			System.err.println("not found");
			System.exit(1);
			return;
		}

//		final URL feedUrl = new URL("https://picasaweb.google.com/data/feed/api/user/peter.misovic/albumid/5363100042313820497");
//		final AlbumFeed feed = myService.getFeed(feedUrl, AlbumFeed.class);

		for(final PhotoEntry photo : feed.getPhotoEntries()) {
			System.out.println(photo.getId());

			if (photo.getGeoLocation() == null) {
				System.out.println("no GeoLocation - skipping");
				continue;
			}

		    System.out.println(photo.getGeoLocation().getLatitude());
		    System.out.println(photo.getGeoLocation().getLongitude());
		    // filename: System.out.println("========> " + photo.getTitle().getPlainText());
		    System.out.println(photo.getDescription().getPlainText());
		    System.out.println(((com.google.gdata.data.MediaContent) photo.getContent()).getUri());

		    final Photo photo1 = new Photo();
		    photo1.latitude = photo.getGeoLocation().getLatitude();
		    photo1.longitude = photo.getGeoLocation().getLongitude();
		    photo1.title = photo.getDescription().getPlainText();
			photo1.description = "Picasa Photo ID: " + photo.getId();
		    photo1.url = ((com.google.gdata.data.MediaContent) photo.getContent()).getUri();
		    PhotoUploader.upload(photo1);
		}
	}

}
