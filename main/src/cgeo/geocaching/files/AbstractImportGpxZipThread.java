package cgeo.geocaching.files;

import cgeo.geocaching.R;
import cgeo.geocaching.models.Geocache;
import cgeo.geocaching.utils.CancellableHandler;

import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.lang3.StringUtils;

import android.os.Handler;
import android.text.Html;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.Collections;
import java.util.zip.ZipEntry;

abstract class AbstractImportGpxZipThread extends AbstractImportGpxThread {

    public static final String ENCODING = "cp437"; // Geocaching.com used windows cp 437 encoding
    private String gpxFileName = null;

    protected AbstractImportGpxZipThread(final int listId, final Handler importStepHandler, final CancellableHandler progressHandler) {
        super(listId, importStepHandler, progressHandler);
    }

    @Override
    protected Collection<Geocache> doImport(final GPXParser parser) throws IOException, ParserException {
        Collection<Geocache> caches = Collections.emptySet();
        // can't assume that GPX file comes before waypoint file in zip -> so we need two passes
        // 1. parse GPX files
        final ZipArchiveInputStream zisPass1 = new ZipArchiveInputStream(new BufferedInputStream(getInputStream()), ENCODING);
        try {
            int acceptedFiles = 0;
            int ignoredFiles = 0;
            for (ZipEntry zipEntry = zisPass1.getNextZipEntry(); zipEntry != null; zipEntry = zisPass1.getNextZipEntry()) {
                gpxFileName = zipEntry.getName();
                if (StringUtils.endsWithIgnoreCase(gpxFileName, GPXImporter.GPX_FILE_EXTENSION)) {
                    if (!StringUtils.endsWithIgnoreCase(gpxFileName, GPXImporter.WAYPOINTS_FILE_SUFFIX_AND_EXTENSION)) {
                        importStepHandler.sendMessage(importStepHandler.obtainMessage(GPXImporter.IMPORT_STEP_READ_FILE, R.string.gpx_import_loading_caches, (int) zipEntry.getSize()));
                        caches = parser.parse(new NoCloseInputStream(zisPass1), progressHandler);
                        acceptedFiles++;
                    }
                } else {
                    ignoredFiles++;
                }
            }
            if (ignoredFiles > 0 && acceptedFiles == 0) {
                throw new ParserException("Imported ZIP does not contain a GPX file.");
            }
        } finally {
            zisPass1.close();
        }

        // 2. parse waypoint files
        final ZipArchiveInputStream zisPass2 = new ZipArchiveInputStream(new BufferedInputStream(getInputStream()), ENCODING);
        try {
            for (ZipEntry zipEntry = zisPass2.getNextZipEntry(); zipEntry != null; zipEntry = zisPass2.getNextZipEntry()) {
                if (StringUtils.endsWithIgnoreCase(zipEntry.getName(), GPXImporter.WAYPOINTS_FILE_SUFFIX_AND_EXTENSION)) {
                    importStepHandler.sendMessage(importStepHandler.obtainMessage(GPXImporter.IMPORT_STEP_READ_WPT_FILE, R.string.gpx_import_loading_waypoints, (int) zipEntry.getSize()));
                    caches = parser.parse(new NoCloseInputStream(zisPass2), progressHandler);
                }
            }
        } finally {
            zisPass2.close();
        }

        return caches;
    }

    @Override
    protected String getSourceDisplayName() {
        return Html.fromHtml(gpxFileName).toString();
    }

    protected abstract InputStream getInputStream() throws IOException;
}